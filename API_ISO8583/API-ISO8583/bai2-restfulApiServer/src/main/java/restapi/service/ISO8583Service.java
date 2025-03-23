package restapi.service;

import org.jpos.iso.ISOMsg;
import org.jpos.iso.ISOException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import restapi.controller.ISOUtil;
import restapi.controller.NapasHttpJsonClient;
import restapi.entity.RestApiMessage;
import restapi.repository.RestApiMessageRepository;

import java.util.Map;

@Service
public class ISO8583Service {

    @Autowired
    private RestApiMessageRepository msgRepo;

    @Autowired
    private AccountService accountService;

    @Autowired
    private NapasHttpJsonClient napasClient; // forward sang (4)

    public ISOMsg processIso(ISOMsg isoReq) throws Exception {
        // 1) Lưu "request"
        logRequest(isoReq);

        // 2) Parse trường
        String pc     = isoReq.hasField(3)? isoReq.getString(3): "";
        String srcPan = isoReq.hasField(2)? isoReq.getString(2): "";
        String destPan= isoReq.hasField(47)? isoReq.getString(47): "";
        String amtS   = isoReq.hasField(4)? isoReq.getString(4): "";
        if(amtS==null) amtS="";
        long amount = 0L;
        try {
            amount = Long.parseLong(amtS.trim().isEmpty()? "0": amtS);
        } catch(Exception e) { /* ignore, amount=0 */ }

        String userCode = isoReq.hasField(48)? isoReq.getString(48): "";

        // 3) Tạo isoResp (copy isoReq), KHÔNG "mti+10" tại đây
        ISOMsg isoResp = (ISOMsg) isoReq.clone();
        // Giữ nguyên MTI request ban đầu (VD: "0200")
        // Lúc này, isoResp.setMTI( isoReq.getMTI() ); hoặc thậm chí để nguyên.

        // 4) Logic inquiry/payment cục bộ (check sourcePan, confirmCode, v.v.)
        if("432020".equals(pc)) {
            // INQUIRY => check sourcePan (balance > 0)
            String chk = accountService.checkSource(srcPan);
            if(!"OK".equals(chk)) {
                isoResp.set(39, mapError(chk));
                logResponse(isoResp);
                return isoResp;
            }
            long bal = accountService.getBalance(srcPan);
            if(bal <= 0) {
                isoResp.set(39,"51");
                logResponse(isoResp);
                return isoResp;
            }

            // forward sang (4) => check destPan
            Map<String,Object> jmap = ISOUtil.isoToMap(isoReq);
            Map<String,String> simResp = napasClient.sendToBankB(jmap);
            // (4) sẽ trả "MTI"="0210", "field39"=...
            String iso39 = simResp.getOrDefault("field39","96");
            // confirmCode
            if("00".equals(iso39)) {
                String code = accountService.generateConfirmCode(srcPan, destPan);
                isoResp.set(48, code);
            }
            // set field39 => isoResp
            isoResp.set(39, iso39);

            // >>> Đoạn QUAN TRỌNG: set MTI từ (4)
            // (4) mới là nơi "mti+10"
            String newMti = simResp.getOrDefault("MTI", isoReq.getMTI());
            isoResp.setMTI(newMti);

        } else if("912020".equals(pc)) {
            // PAYMENT
            if(userCode==null || userCode.isEmpty()) {
                isoResp.set(39,"94");
                logResponse(isoResp);
                return isoResp;
            }
            String chkCode = accountService.checkConfirmCode(srcPan, destPan, userCode);
            if(!"OK".equals(chkCode)) {
                isoResp.set(39,"94");
                logResponse(isoResp);
                return isoResp;
            }
            // amount >=1000
            if(amount < 1000) {
                isoResp.set(39,"13");
                logResponse(isoResp);
                return isoResp;
            }
            // amount < balance
            long bal = accountService.getBalance(srcPan);
            if(amount >= bal) {
                isoResp.set(39,"51");
                logResponse(isoResp);
                return isoResp;
            }
            // debit
            String debitRes = accountService.debit(srcPan, amount);
            if(!"OK".equals(debitRes)) {
                isoResp.set(39, mapError(debitRes));
                logResponse(isoResp);
                return isoResp;
            }

            // forward => (4)
            Map<String,Object> jmap = ISOUtil.isoToMap(isoReq);
            Map<String,String> simResp = napasClient.sendToBankB(jmap);

            String iso39 = simResp.getOrDefault("field39","96");
            if("00".equals(iso39)) {
                accountService.removeConfirmCode(srcPan,destPan);
            }
            isoResp.set(39, iso39);

            // Lấy MTI từ (4)
            String newMti = simResp.getOrDefault("MTI", isoReq.getMTI());
            isoResp.setMTI(newMti);

        } else {
            isoResp.set(39,"96");
        }

        // 5) Lưu "response"
        logResponse(isoResp);
        return isoResp;
    }

    private String mapError(String code) {
        if("SOURCE_NOT_FOUND".equals(code)) return "14";
        if("SOURCE_NOT_ACTIVE".equals(code)) return "62";
        if("SOURCE_INSUFFICIENT_FUNDS".equals(code)) return "51";
        return "96";
    }

    private void logRequest(ISOMsg iso) throws ISOException {
        RestApiMessage msg = new RestApiMessage();
        msg.setType("REQUEST");
        if(iso.hasField(0)) msg.setMti(iso.getString(0));
        if(iso.hasField(2)) msg.setPan(iso.getString(2));
        if(iso.hasField(3)) msg.setProcessingCode(iso.getString(3));
        if(iso.hasField(4)) msg.setAmount(iso.getString(4));
        if(iso.hasField(47)) msg.setDestPan(iso.getString(47));
        if(iso.hasField(48)) msg.setConfirmCode(iso.getString(48));
        // responseCode null
        msgRepo.save(msg);
    }

    private void logResponse(ISOMsg iso) throws ISOException {
        RestApiMessage msg = new RestApiMessage();
        msg.setType("RESPONSE");
        if(iso.hasField(0)) msg.setMti(iso.getString(0));
        if(iso.hasField(2)) msg.setPan(iso.getString(2));
        if(iso.hasField(3)) msg.setProcessingCode(iso.getString(3));
        if(iso.hasField(4)) msg.setAmount(iso.getString(4));
        if(iso.hasField(47)) msg.setDestPan(iso.getString(47));
        if(iso.hasField(48)) msg.setConfirmCode(iso.getString(48));
        if(iso.hasField(39)) msg.setResponseCode(iso.getString(39));
        msgRepo.save(msg);
    }
}
