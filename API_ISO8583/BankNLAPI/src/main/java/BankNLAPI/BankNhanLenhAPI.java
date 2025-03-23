// BankNhanLenhAPI.java
package BankNLAPI;

import org.jpos.iso.ISOException;
import org.jpos.iso.ISOMsg;
import org.jpos.iso.packager.ISO87APackager;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

public class BankNhanLenhAPI {

    private static final int INBOUND_PORT = 9093;
    private static final Map<String, Account> ACCOUNTS_DEST = new HashMap<>();
    private static final Map<String, Boolean> INQUIRY_MAP = new HashMap<>();

    static {
        ACCOUNTS_DEST.put("888888", new Account("888888", 3_000_000L, "ACTIVE"));
        ACCOUNTS_DEST.put("999999", new Account("999999", 5_000_000L, "ACTIVE"));
        ACCOUNTS_DEST.put("777777", new Account("777777", 1_000_000L, "LOCKED"));
    }

    public static void main(String[] args) {
        BankNhanLenhAPI app = new BankNhanLenhAPI();
        app.startServer();
    }

    public void startServer() {
        try (ServerSocket server = new ServerSocket(INBOUND_PORT)) {
            System.out.println("[BankNhanLenhAPI] Listening on port " + INBOUND_PORT);

            while (true) {
                Socket client = server.accept();
                new Thread(() -> handleClient(client)).start();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void handleClient(Socket sock) {
        try (InputStream in = sock.getInputStream(); OutputStream out = sock.getOutputStream()) {
            byte[] requestBytes = readFullMessage(in);
            if (requestBytes == null || requestBytes.length == 0) {
                System.err.println("[BankNhanLenhAPI] No data received or incomplete message.");
                return;
            }

            ISOMsg isoReq = new ISOMsg();
            isoReq.setPackager(new ISO87APackager());
            try {
                isoReq.unpack(requestBytes);
                ensureRequiredFields(isoReq);
            } catch (ISOException e) {
                System.err.println("[BankNhanLenhAPI] Failed to unpack ISO message: " + e.getMessage());
                return;
            }

            String mti = isoReq.getMTI();
            String pc = isoReq.hasField(3) ? isoReq.getString(3) : "";
            String src = isoReq.hasField(2) ? isoReq.getString(2) : "";
            String dst = isoReq.hasField(47) ? isoReq.getString(47) : "";
            long amount = isoReq.hasField(4) ? Long.parseLong(isoReq.getString(4)) : 0;

            System.out.println("[BankNhanLenhAPI] Received MTI=" + mti + ", PC=" + pc + ", src=" + src + ", dst=" + dst + ", amount=" + amount);

            String field39 = processRequest(mti, pc, src, dst, amount);

            ISOMsg isoResp = createResponse(isoReq, field39, amount);
            byte[] responseBytes = isoResp.pack();

            out.write(responseBytes);
            out.flush();

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                sock.close();
            } catch (IOException ignored) {
            }
        }
    }

    private byte[] readFullMessage(InputStream in) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int bytesRead;
        while ((bytesRead = in.read(buffer)) > 0) {
            baos.write(buffer, 0, bytesRead);
            if (in.available() == 0) break; // End of message
        }
        return baos.toByteArray();
    }

    private void ensureRequiredFields(ISOMsg isoReq) throws ISOException {
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HHmmss");
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("MMdd");

        if (!isoReq.hasField(11)) {
            isoReq.set(11, String.format("%06d", (int) (Math.random() * 1000000)));
            System.out.println("[BankNhanLenhAPI] Generated missing Field 11 (Trace Number).");
        }
        if (!isoReq.hasField(12)) {
            isoReq.set(12, now.format(timeFormatter));
            System.out.println("[BankNhanLenhAPI] Generated missing Field 12 (Time).");
        }
        if (!isoReq.hasField(13)) {
            isoReq.set(13, now.format(dateFormatter));
            System.out.println("[BankNhanLenhAPI] Generated missing Field 13 (Date).");
        }
        if (!isoReq.hasField(3)) {
            isoReq.set(3, "432020"); // Default Processing Code for Inquiry
            System.out.println("[BankNhanLenhAPI] Defaulted missing Field 3 (Processing Code) to 432020.");
        }
        if (!isoReq.hasField(4)) {
            isoReq.set(4, ""); // Empty amount if missing
            System.out.println("[BankNhanLenhAPI] Defaulted missing Field 4 (Amount) to empty.");
        }
    }

    private String processRequest(String mti, String pc, String src, String dst, long amount) {
        if (!"0200".equals(mti)) {
            System.err.println("[BankNhanLenhAPI] Unsupported MTI: " + mti);
            return "96"; // System malfunction
        }

        if ("432020".equals(pc)) {
            return doInquiry(src, dst);
        } else if ("912020".equals(pc)) {
            return doPayment(src, dst, amount);
        } else {
            System.err.println("[BankNhanLenhAPI] Unsupported processing code: " + pc);
            return "96";
        }
    }

    private ISOMsg createResponse(ISOMsg isoReq, String field39, long amount) throws ISOException {
        ISOMsg isoResp = new ISOMsg();
        isoResp.setPackager(new ISO87APackager());
        isoResp.setMTI("0210");
        isoResp.set(39, field39);
        if (isoReq.hasField(2)) isoResp.set(2, isoReq.getString(2));
        if (isoReq.hasField(47)) isoResp.set(47, isoReq.getString(47));
        if (isoReq.hasField(3)) isoResp.set(3, isoReq.getString(3));
        if (isoReq.hasField(4)) isoResp.set(4, isoReq.getString(4));
        isoResp.set(11, isoReq.getString(11));
        isoResp.set(12, isoReq.getString(12));
        isoResp.set(13, isoReq.getString(13));
        return isoResp;
    }

    private String doInquiry(String sourcePan, String destPan) {
        Account account = ACCOUNTS_DEST.get(destPan);
        if (account == null) {
            System.err.println("[BankNhanLenhAPI] Inquiry failed: Destination account not found");
            return "14"; // Destination not found
        }
        if (!"ACTIVE".equalsIgnoreCase(account.status)) {
            System.err.println("[BankNhanLenhAPI] Inquiry failed: Account is not active");
            return "62"; // Account locked
        }
        INQUIRY_MAP.put(sourcePan + "_" + destPan, true);
        return "00"; // Success
    }

    private String doPayment(String sourcePan, String destPan, long amount) {
        String key = sourcePan + "_" + destPan;
        if (!INQUIRY_MAP.containsKey(key)) {
            System.err.println("[BankNhanLenhAPI] Payment failed: No prior inquiry");
            return "94"; // No prior inquiry
        }
        Account account = ACCOUNTS_DEST.get(destPan);
        if (account == null) {
            System.err.println("[BankNhanLenhAPI] Payment failed: Destination account not found");
            return "14";
        }
        if (!"ACTIVE".equalsIgnoreCase(account.status)) {
            System.err.println("[BankNhanLenhAPI] Payment failed: Account is not active");
            return "62";
        }
        account.balance += amount;
        INQUIRY_MAP.remove(key);
        return "00"; // Success
    }

    private static class Account {
        String pan;
        long balance;
        String status;

        public Account(String pan, long balance, String status) {
            this.pan = pan;
            this.balance = balance;
            this.status = status;
        }
    }
}
