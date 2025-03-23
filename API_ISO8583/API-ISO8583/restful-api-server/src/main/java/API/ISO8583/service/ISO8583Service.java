package API.ISO8583.service;

import org.jpos.iso.ISOException;
import org.jpos.iso.ISOMsg;
import org.jpos.iso.packager.ISO87APackager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import API.ISO8583.dto.ISO8583RequestDTO;
import API.ISO8583.entity.ISO8583Message;
import API.ISO8583.repository.ISO8583MessageRepository;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Service
public class ISO8583Service {

    @Autowired
    private ISO8583MessageRepository messageRepository;

    @Autowired
    private AccountService accountService;  

    private static final String NAPAS_HOST = "localhost";
    private static final int    NAPAS_PORT = 9090;

    public CompletableFuture<String> processISO8583RequestAsync(ISO8583RequestDTO dto) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Parse input
                String pc       = dto.getProcessingCode();
                String sourcePan= dto.getSourcePan();
                String destPan  = dto.getDestinationPan();
                long amount     = (dto.getTransactionAmount() == null)
                                   ? 0L
                                   : Long.parseLong(dto.getTransactionAmount());

                // 1) Check sourcePan cục bộ
                String localCheck = accountService.checkSource(sourcePan);
                if (!"OK".equals(localCheck)) {
                    return buildError("Source check fail: " + localCheck);
                }

                // 2) Tạo ISOMsg request để gửi sang Napas
                ISOMsg isoReq = new ISOMsg();
                isoReq.setPackager(new ISO87APackager());
                isoReq.setMTI(dto.getMti()); // ban đầu "0200" chẳng hạn
                isoReq.set(2,  sourcePan);
                isoReq.set(3,  pc);
                isoReq.set(4,  dto.getTransactionAmount() == null ? "0" : dto.getTransactionAmount());
                isoReq.set(47, destPan);

                // Lưu request
                ISO8583Message dbReq = new ISO8583Message();
                dbReq.setType("REQUEST");
                fillFields(dbReq, isoReq);
                messageRepository.save(dbReq);

                // 3) Gửi sang Napas
                byte[] respBytes = sendToNapas(isoReq.pack());

                // 4) unpack response isoResp
                ISOMsg isoResp = new ISOMsg();
                isoResp.setPackager(new ISO87APackager());
                isoResp.unpack(respBytes);

                // Lưu response
                ISO8583Message dbResp = new ISO8583Message();
                dbResp.setType("RESPONSE");
                fillFields(dbResp, isoResp);
                messageRepository.save(dbResp);
                dto.setMti( isoResp.getMTI() );

                String f39 = isoResp.hasField(39) ? isoResp.getString(39) : "NULL";
                dto.setResponseCode(f39);

                // Check f39
                if (!"00".equals(f39)) {
                    return buildError("Bank B fail with code " + f39);
                }

                if ("432020".equals(pc)) {
                    // inquiry => generate confirmCode
                    String confirmCode = accountService.generateConfirmCode(sourcePan, destPan);
                    dto.setConfirmCode(confirmCode);

                } else if ("912020".equals(pc)) {
                    // payment => check confirmCode => debit => remove confirmCode
                    String ccCheck = accountService.checkConfirmCode(sourcePan, destPan, dto.getConfirmCode());
                    if (!"OK".equals(ccCheck)) {
                        return buildError("Local payment fail: " + ccCheck);
                    }
                    // trừ tiền
                    String debitRes = accountService.debit(sourcePan, amount);
                    if (!"OK".equals(debitRes)) {
                        return buildError("Local debit fail: " + debitRes);
                    }
                    // remove confirmCode
                    accountService.removeConfirmCode(sourcePan, destPan);
                }

                // update info source
                long balSource = accountService.getSourceBalance(sourcePan);
                String stSource= accountService.getSourceStatus(sourcePan);
                dto.setBalanceSource(balSource);
                dto.setStatusSource(stSource);

                // => Trả JSON
                return dtoToJson(dto);

            } catch (Exception e) {
                return buildError("Error processing ISO8583: " + e.getMessage());
            }
        });
    }

    private byte[] sendToNapas(byte[] reqBytes) throws IOException {
        try (Socket socket = new Socket(NAPAS_HOST, NAPAS_PORT);
             OutputStream out = socket.getOutputStream();
             InputStream in = socket.getInputStream()) {

            out.write(reqBytes);
            out.flush();

            byte[] buf = new byte[4096];
            int len = in.read(buf);
            if (len <= 0) throw new IOException("No response from Napas");

            byte[] resp = new byte[len];
            System.arraycopy(buf, 0, resp, 0, len);
            return resp;
        }
    }

    private void fillFields(ISO8583Message entity, ISOMsg iso) throws ISOException {
        entity.setMti(iso.getMTI()); 
        if (iso.hasField(2))  entity.setPan(iso.getString(2));
        if (iso.hasField(3))  entity.setProcessingCode(iso.getString(3));
        if (iso.hasField(4))  entity.setTransactionAmount(iso.getString(4));
        if (iso.hasField(39)) entity.setResponseCode(iso.getString(39));
    }

    private String dtoToJson(ISO8583RequestDTO dto) throws JsonProcessingException {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("mti", dto.getMti()); 
        map.put("processingCode", dto.getProcessingCode());
        map.put("sourcePan", dto.getSourcePan());
        map.put("destinationPan", dto.getDestinationPan());
        map.put("transactionAmount", dto.getTransactionAmount());
        map.put("confirmCode", dto.getConfirmCode());
        map.put("balanceSource", dto.getBalanceSource());
        map.put("responseCode", dto.getResponseCode());

        return new ObjectMapper().writeValueAsString(map);
    }

    private String buildError(String msg) {
        return "{\"error\":\"" + msg + "\"}";
    }
}
