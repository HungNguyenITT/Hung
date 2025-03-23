package Listener;


import org.jpos.iso.*;
import org.jpos.iso.packager.ISO87APackager;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Bank B cổng 9091: 
 *  - inquiry => check destPan
 *  - payment => + amt
 */
public class SimulatorSocketListener {

    private static final int PORT = 9091;

    // Tài khoản nhận Bank B
    private static final Map<String, Account> ACCOUNTS = new HashMap<>();

    static {
        ACCOUNTS.put("888888", new Account("888888", 3_000_000L, "ACTIVE"));
        ACCOUNTS.put("999999", new Account("999999", 5_000_000L, "ACTIVE"));
        ACCOUNTS.put("777777", new Account("777777", 1_000_000L,"LOCKED"));
    }

    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("[Bank B] listening on port " + PORT);
            while (true) {
                Socket client = serverSocket.accept();
                new Thread(() -> handleClient(client)).start();
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    private static void handleClient(Socket socket) {
        try (InputStream in = socket.getInputStream();
             OutputStream out = socket.getOutputStream()) {

            byte[] buf = new byte[4096];
            int len = in.read(buf);
            if (len <= 0) return;

            byte[] reqBytes = new byte[len];
            System.arraycopy(buf, 0, reqBytes, 0, len);

            ISOMsg isoReq = new ISOMsg();
            isoReq.setPackager(new ISO87APackager());
            isoReq.unpack(reqBytes);

            System.out.println("[Bank B] REQ => " + new String(reqBytes, StandardCharsets.UTF_8));

            // Tạo response
            ISOMsg isoResp = createResponse(isoReq);

            byte[] respBytes = isoResp.pack();
            out.write(respBytes);
            out.flush();

            System.out.println("[Bank B] RESP => " + new String(respBytes, StandardCharsets.UTF_8));

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static ISOMsg createResponse(ISOMsg req) throws ISOException {
        ISOMsg resp = new ISOMsg();
        resp.setPackager(new ISO87APackager());

        // 0200 -> 0210
        int mti = Integer.parseInt(req.getMTI());
        mti += 10; 
        resp.setMTI(String.format("%04d", mti));

        if (req.hasField(2))  resp.set(2, req.getString(2));
        if (req.hasField(3))  resp.set(3, req.getString(3));
        if (req.hasField(4))  resp.set(4, req.getString(4));
        if (req.hasField(47)) resp.set(47, req.getString(47));

        String pc = req.hasField(3) ? req.getString(3) : "";
        long amt = req.hasField(4) ? Long.parseLong(req.getString(4)) : 0;
        String destPan = req.hasField(47) ? req.getString(47) : "";

        // Bank B logic
        String rc;
        if ("432020".equals(pc)) {
            // Inquiry
            rc = inquiry(destPan);
        } else if ("912020".equals(pc)) {
            // Payment
            rc = payment(destPan, amt);
        } else {
            rc = "UNKNOWN_PC";
        }

        String iso39;
        switch (rc) {
            case "OK":
                iso39 = "00";
                break;
            case "ACCOUNT_NOT_FOUND":
                iso39 = "14";
                break;
            case "ACCOUNT_NOT_ACTIVE":
                iso39 = "62";
                break;
            case "INSUFFICIENT_FUNDS":
                iso39 = "51";
                break;
            default:
                iso39 = "96";
                break;
        }
        resp.set(39, iso39);

        return resp;
    }

    private static String inquiry(String pan) {
        Account d = ACCOUNTS.get(pan);
        if (d == null) return "DEST_NOT_FOUND";
        if (!"ACTIVE".equalsIgnoreCase(d.getStatus())) return "DEST_NOT_ACTIVE";
        return "OK";
    }

    private static String payment(String pan, long amt) {
        Account d = ACCOUNTS.get(pan);
        if (d == null) return "DEST_NOT_FOUND";
        if (!"ACTIVE".equalsIgnoreCase(d.getStatus())) return "DEST_NOT_ACTIVE";
        // + amount
        d.setBalance(d.getBalance() + amt);
        return "OK";
    }

    private static class Account {
        private String pan;
        private long balance;
        private String status;

        public Account(String pan, long bal, String st) {
            this.pan = pan;
            this.balance = bal;
            this.status = st;
        }

        public String getPan() { return pan; }
        public long getBalance() { return balance; }
        public void setBalance(long b) { this.balance = b; }
        public String getStatus() { return status; }
        public void setStatus(String s) { this.status = s; }
    }
}
