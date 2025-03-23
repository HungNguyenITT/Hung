package NAPAS.socket;


import org.jpos.iso.*;
import org.jpos.iso.packager.ISO87APackager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import NAPAS.entity.NapasISOMessage;
import NAPAS.repository.NapasISOMessageRepository;

import javax.annotation.PostConstruct;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.LocalDateTime;

@Component
public class SocketServer {

    private static final int SOCKET_PORT = 9090;     // Napas cổng 9090
    private static final String BANK_B_HOST = "localhost";
    private static final int BANK_B_PORT = 9091;     // Bank B cổng 9091

    @Autowired
    private NapasISOMessageRepository napasISOMessageRepository;

    @PostConstruct
    public void startServer() {
        new Thread(() -> {
            try (ServerSocket serverSocket = new ServerSocket(SOCKET_PORT)) {
                System.out.println("[Napas] Listening on port " + SOCKET_PORT);

                while (true) {
                    Socket clientSocket = serverSocket.accept();
                    new Thread(() -> handleClient(clientSocket)).start(); 
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void handleClient(Socket clientSocket) {
        try (InputStream in = clientSocket.getInputStream();
             OutputStream out = clientSocket.getOutputStream()) {

            byte[] buf = new byte[4096];
            int len = in.read(buf);
            if (len <= 0) return;

            byte[] reqBytes = new byte[len];
            System.arraycopy(buf, 0, reqBytes, 0, len);

            ISOMsg isoReq = new ISOMsg();
            isoReq.setPackager(new ISO87APackager());
            isoReq.unpack(reqBytes);

            // Lưu request
            NapasISOMessage reqEntity = createNapasEntity("REQUEST", isoReq);
            napasISOMessageRepository.save(reqEntity);

            // Forward -> Bank B
            ISOMsg isoResp = forwardToBankB(isoReq);

            // Lưu response
            NapasISOMessage respEntity = createNapasEntity("RESPONSE", isoResp);
            napasISOMessageRepository.save(respEntity);

            // Gửi lại cho Bank A
            byte[] respBytes = isoResp.pack();
            out.write(respBytes);
            out.flush();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private ISOMsg forwardToBankB(ISOMsg req) throws Exception {
        try (Socket socket = new Socket(BANK_B_HOST, BANK_B_PORT);
             OutputStream out = socket.getOutputStream();
             InputStream in = socket.getInputStream()) {

            byte[] reqBytes = req.pack();
            out.write(reqBytes);
            out.flush();

            byte[] buf = new byte[4096];
            int len = in.read(buf);
            if (len <= 0) throw new IOException("[Napas] No response from Bank B");

            byte[] respBytes = new byte[len];
            System.arraycopy(buf, 0, respBytes, 0, len);

            ISOMsg isoResp = new ISOMsg();
            isoResp.setPackager(new ISO87APackager());
            isoResp.unpack(respBytes);

            return isoResp;
        }
    }

    private NapasISOMessage createNapasEntity(String type, ISOMsg iso) throws ISOException {
        NapasISOMessage e = new NapasISOMessage();
        e.setType(type);
        e.setCreatedAt(LocalDateTime.now());
        e.setMti(iso.getMTI());
        if (iso.hasField(2)) e.setF2(iso.getString(2));
        if (iso.hasField(3)) e.setF3(iso.getString(3));
        if (iso.hasField(4)) e.setF4(iso.getString(4));
        if (iso.hasField(39)) e.setF39(iso.getString(39));
        return e;
    }
}
