package socketserver.socket;

import org.jpos.iso.ISOMsg;
import org.jpos.iso.packager.ISO87APackager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import socketserver.entity.SocketMessage;
import socketserver.repository.SocketMessageRepository;

import javax.annotation.PostConstruct;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

@Component
public class SocketServer {

    private static final int TCP_PORT = 1234;

    @Autowired
    private SocketMessageRepository msgRepo;

    @PostConstruct
    public void startServer() {
        new Thread(new Runnable(){
            @Override
            public void run() {
                try(ServerSocket server = new ServerSocket(TCP_PORT)) {
                    System.out.println("[SocketServer] Listening on TCP port " + TCP_PORT);
                    while(true){
                        final Socket client = server.accept();
                        new Thread(new Runnable(){
                            @Override
                            public void run() {
                                handleClient(client);
                            }
                        }).start();
                    }
                } catch(IOException e){
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private void handleClient(Socket client){
        try(InputStream in = client.getInputStream();
            OutputStream out = client.getOutputStream()) {

            byte[] buf = new byte[4096];
            int len = in.read(buf);
            if(len<=0) return;

            byte[] reqBytes = Arrays.copyOf(buf, len);

            // Unpack request
            ISOMsg isoReq = new ISOMsg();
            isoReq.setPackager(new ISO87APackager());
            isoReq.unpack(reqBytes);

            // Lưu request
            SocketMessage smReq = new SocketMessage();
            smReq.setType("REQUEST");
            smReq.setF1(isoReq.getMTI());  // f1=MTI
            if(isoReq.hasField(2))  smReq.setF2(isoReq.getString(2));
            if(isoReq.hasField(3))  smReq.setF3(isoReq.getString(3));
            if(isoReq.hasField(4))  smReq.setF4(isoReq.getString(4));
            if(isoReq.hasField(47)) smReq.setF47(isoReq.getString(47));
            if(isoReq.hasField(48)) smReq.setF48(isoReq.getString(48));
            // f39 chưa có ở request
            msgRepo.save(smReq);

            // Forward sang (3) => cổng 8085
            byte[] respBytes = HttpUtil.postIso("http://localhost:8085/api/iso8583/v2", reqBytes);

            // unpack response
            ISOMsg isoResp = new ISOMsg();
            isoResp.setPackager(new ISO87APackager());
            isoResp.unpack(respBytes);

            // Lưu response
            SocketMessage smResp = new SocketMessage();
            smResp.setType("RESPONSE");
            smResp.setF1(isoResp.getMTI());
            if(isoResp.hasField(2))  smResp.setF2(isoResp.getString(2));
            if(isoResp.hasField(3))  smResp.setF3(isoResp.getString(3));
            if(isoResp.hasField(4))  smResp.setF4(isoResp.getString(4));
            if(isoResp.hasField(47)) smResp.setF47(isoResp.getString(47));
            if(isoResp.hasField(48)) smResp.setF48(isoResp.getString(48));
            if(isoResp.hasField(39)) smResp.setF39(isoResp.getString(39));
            msgRepo.save(smResp);

            // Trả response về client (1)
            out.write(respBytes);
            out.flush();

        } catch(Exception e){
            e.printStackTrace();
        }
    }
}
