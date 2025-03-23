package BankPLSocket;

import org.jpos.iso.ISOMsg;
import org.jpos.iso.ISOException;
import org.jpos.iso.packager.ISO87APackager;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

public class BankPhatLenhSocket {

    private static final Map<String, Account> ACCOUNTS_SOURCE = new HashMap<>();
    static {
        ACCOUNTS_SOURCE.put("111111", new Account("111111", 2_000_000L, "ACTIVE"));
        ACCOUNTS_SOURCE.put("222222", new Account("222222", 1_000_000L, "ACTIVE"));
        ACCOUNTS_SOURCE.put("333333", new Account("333333", 5_000_000L, "LOCKED"));
    }

    // Napas
    private static final String NAPAS_HOST = "localhost";
    private static final int NAPAS_PORT = 9090;

    private static final String F32_THIS_BANK = "970400";

    // Bank nhận lệnh => f100 = 970402 (socket) | 970403 (api)
    private static String receivingBankF100 = null;

    public static void main(String[] args) {
        BankPhatLenhSocket app = new BankPhatLenhSocket();
        app.runMenu();
    }

    private void runMenu() {
        Scanner sc = new Scanner(System.in);
        while (true) {
            System.out.println("\n=== Bank phát lệnh socket ===");
            System.out.println("1. Chọn bank nhận lệnh");
            System.out.println("2. Inquiry (432020)");
            System.out.println("3. Payment (912020)");
            System.out.println("0. Thoát");
            System.out.print("Chọn: ");
            String choice = sc.nextLine().trim();
            if ("0".equals(choice)) {
                System.out.println("Kết thúc chương trình.");
                break;
            }

            switch (choice) {
                case "1":
                    System.out.println("Chọn Bank nhận lệnh:");
                    System.out.println("   1. Bank Nhận Lệnh (Socket)");
                    System.out.println("   2. Bank Nhận Lệnh (API)");
                    System.out.print("Chọn: ");
                    String c = sc.nextLine().trim();
                    if ("1".equals(c)) {
                        receivingBankF100 = "970402";
                    } else if ("2".equals(c)) {
                        receivingBankF100 = "970403";
                    } else {
                        System.out.println("Lựa chọn không hợp lệ!");
                    }
                    System.out.println("=> Bank nhận lệnh hiện tại: " + receivingBankF100);
                    break;

                case "2":
                    if (receivingBankF100 == null) {
                        System.out.println("Bạn chưa chọn Bank nhận lệnh (menu 1)!");
                        break;
                    }
                    System.out.print("sourcePan: ");
                    String sPan1 = sc.nextLine().trim();
                    System.out.print("destPan: ");
                    String dPan1 = sc.nextLine().trim();
                    doInquiry(sPan1, dPan1);
                    break;

                case "3":
                    if (receivingBankF100 == null) {
                        System.out.println("Bạn chưa chọn Bank nhận lệnh (menu 1)!");
                        break;
                    }
                    System.out.print("sourcePan: ");
                    String sPan2 = sc.nextLine().trim();
                    System.out.print("destPan: ");
                    String dPan2 = sc.nextLine().trim();
                    System.out.print("amount: ");
                    long amt = Long.parseLong(sc.nextLine().trim());
                    doPayment(sPan2, dPan2, amt);
                    break;

                default:
                    System.out.println("Lựa chọn không hợp lệ!");
            }
        }
    }

    // Inquiry => check source cục bộ => forward Napas => field39
    private void doInquiry(String sourcePan, String destPan) {
        // local check
        String localCheck = checkSource(sourcePan);
        if (!"00".equals(localCheck)) {
            System.out.println("[PhatLenh] sourcePan fail => field39=" + localCheck);
            return;
        }
        // forward Napas
        try {
            ISOMsg isoReq = new ISOMsg();
            isoReq.setPackager(new ISO87APackager());
            isoReq.setMTI("0200");
            isoReq.set(2, sourcePan);
            isoReq.set(3, "432020");
            isoReq.set(4, "0");
            isoReq.set(47, destPan);

            // Set f32, f100
            isoReq.set(32, F32_THIS_BANK);
            isoReq.set(100, receivingBankF100);

            byte[] respBytes = sendToNapas(isoReq.pack());
            if (respBytes == null) {
                System.out.println("[PhatLenh] No response from Napas");
                return;
            }
            ISOMsg isoResp = new ISOMsg();
            isoResp.setPackager(new ISO87APackager());
            isoResp.unpack(respBytes);

            String f39 = isoResp.hasField(39) ? isoResp.getString(39) : "NULL";
            System.out.println("[PhatLenh] inquiry => field39=" + f39);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Payment => cục bộ check & trừ => forward Napas => field39
    private void doPayment(String sourcePan, String destPan, long amt) {
        // local check & subtract
        Account s = ACCOUNTS_SOURCE.get(sourcePan);
        if (s == null) {
            System.out.println("[PhatLenh] sourcePan not found => field39=14");
            return;
        }
        if (!"ACTIVE".equalsIgnoreCase(s.status)) {
            System.out.println("[PhatLenh] sourcePan locked => field39=62");
            return;
        }
        if (s.balance < amt) {
            System.out.println("[PhatLenh] insufficient => field39=51");
            return;
        }
        s.balance -= amt;

        // forward Napas
        try {
            ISOMsg isoReq = new ISOMsg();
            isoReq.setPackager(new ISO87APackager());
            isoReq.setMTI("0200");
            isoReq.set(2, sourcePan);
            isoReq.set(3, "912020");
            isoReq.set(4, String.format("%012d", amt));
            isoReq.set(47, destPan);

            // Set f32, f100
            isoReq.set(32, F32_THIS_BANK);
            isoReq.set(100, receivingBankF100);

            byte[] respBytes = sendToNapas(isoReq.pack());
            if (respBytes == null) {
                System.out.println("[PhatLenh] No response from Napas => rollback?");
                return;
            }
            ISOMsg isoResp = new ISOMsg();
            isoResp.setPackager(new ISO87APackager());
            isoResp.unpack(respBytes);

            String f39 = isoResp.hasField(39) ? isoResp.getString(39) : "NULL";
            System.out.println("[PhatLenh] payment => field39=" + f39);
            if (!"00".equals(f39)) {
                // mismatch => 94 => rollback?
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private byte[] sendToNapas(byte[] data) {
        try (Socket sock = new Socket(NAPAS_HOST, NAPAS_PORT);
             OutputStream out = sock.getOutputStream();
             InputStream in = sock.getInputStream()) {

            out.write(data);
            out.flush();

            sock.setSoTimeout(15000);

            byte[] buf = new byte[4096];
            int len = in.read(buf);
            if (len <= 0) {
                return createTimeoutResponse();
            }

            byte[] resp = new byte[len];
            System.arraycopy(buf, 0, resp, 0, len);
            return resp;

        } catch (Exception e) {
            e.printStackTrace();
            return createTimeoutResponse();
        }
    }

    private byte[] createTimeoutResponse() {
        try {
            ISOMsg isoResp = new ISOMsg();
            isoResp.setPackager(new ISO87APackager());
            isoResp.setMTI("0210");
            isoResp.set(39, "68");
            return isoResp.pack();
        } catch (ISOException e) {
            e.printStackTrace();
            return null;
        }
    }

    private String checkSource(String pan) {
        Account a = ACCOUNTS_SOURCE.get(pan);
        if (a == null) return "14";
        if (!"ACTIVE".equalsIgnoreCase(a.status)) return "62";
        return "00";
    }

    private static class Account {
        String pan;
        long balance;
        String status;
        public Account(String p, long b, String s) {
            this.pan = p;
            this.balance = b;
            this.status = s;
        }
    }
}
