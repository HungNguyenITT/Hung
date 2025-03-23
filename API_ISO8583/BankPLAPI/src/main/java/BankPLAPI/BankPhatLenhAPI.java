package BankPLAPI;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

public class BankPhatLenhAPI {

    // Lưu thông tin tài khoản cục bộ
    private static final Map<String, Account> ACCOUNTS_SOURCE = new HashMap<>();
    static {
        ACCOUNTS_SOURCE.put("111111", new Account("111111", 2_000_000L, "ACTIVE"));
        ACCOUNTS_SOURCE.put("222222", new Account("222222", 1_000_000L, "ACTIVE"));
        ACCOUNTS_SOURCE.put("333333", new Account("333333", 5_000_000L, "LOCKED"));
    }

    // Mặc định f32=970401 (Bank phát lệnh dạng API)
    private static final String F32_THIS_BANK = "970401";
    // Bank nhận lệnh => f100 = 970402 (socket) hoặc 970403 (API)
    private static String receivingBankF100 = null;

    // URL Napas (API1Controller) 
    // Giả sử Napas cung cấp endpoint /api/isoRequest 
    // (hoặc bạn có logic route sang Napas, tuỳ mô hình)
    private static final String NAPAS_URL = "http://localhost:8081/api/isoRequest";

    // Socket inbound (nếu muốn lắng nghe cổng 9098 - tuỳ mục đích)
    private static final int INBOUND_SOCKET_PORT = 9098;

    public static void main(String[] args) {
        BankPhatLenhAPI app = new BankPhatLenhAPI();

        // Nếu muốn lắng nghe cổng inbound, có thể bật thread:
        app.startInboundSocket();

        // Chạy menu CLI
        app.runMenu();
    }
    
    private void runMenu() {
        Scanner sc = new Scanner(System.in);
        while (true) {
            System.out.println("\n=== BankPhatLenhAPI ===");
            System.out.println("1. Chọn bank nhận lệnh");
            System.out.println("2. Inquiry (432020)");
            System.out.println("3. Payment (912020)");
            System.out.println("0. Exit");
            System.out.print("Chọn: ");
            String choice = sc.nextLine().trim();

            if ("0".equals(choice)) {
                System.out.println("Kết thúc.");
                break;
            }

            switch (choice) {
                case "1":
                    // Chọn bank nhận lệnh => set f100
                    System.out.println("Chọn Bank nhận lệnh:");
                    System.out.println("   1. Bank Nhận Lệnh (Socket) => f100=970402");
                    System.out.println("   2. Bank Nhận Lệnh (API)    => f100=970403");
                    System.out.print("Chọn: ");
                    String c = sc.nextLine().trim();
                    if ("1".equals(c)) {
                        receivingBankF100 = "970402";
                    } else if ("2".equals(c)) {
                        receivingBankF100 = "970403";
                    } else {
                        System.out.println("Lựa chọn không hợp lệ!");
                    }
                    System.out.println("=> Bank nhận lệnh hiện tại (f100): " + receivingBankF100);
                    break;

                case "2":
                    if (receivingBankF100 == null) {
                        System.out.println("Bạn chưa chọn Bank nhận lệnh (menu 1)!");
                        break;
                    }
                    System.out.print("sourcePan: ");
                    String sPanI = sc.nextLine().trim();
                    System.out.print("destPan: ");
                    String dPanI = sc.nextLine().trim();
                    doInquiry(sPanI, dPanI);
                    break;

                case "3":
                    if (receivingBankF100 == null) {
                        System.out.println("Bạn chưa chọn Bank nhận lệnh (menu 1)!");
                        break;
                    }
                    System.out.print("sourcePan: ");
                    String sPanP = sc.nextLine().trim();
                    System.out.print("destPan: ");
                    String dPanP = sc.nextLine().trim();
                    System.out.print("amount: ");
                    long amt = Long.parseLong(sc.nextLine().trim());
                    doPayment(sPanP, dPanP, amt);
                    break;

                default:
                    System.out.println("Lựa chọn không hợp lệ!");
            }
        }
    }

    // Gửi Inquiry:
    //  1) Check source cục bộ => 14,62,... 
    //  2) Nếu OK => Gửi JSON => Napas => isoResp => field39
    private void doInquiry(String sourcePan, String destPan) {
        // local check
        String rc = checkSource(sourcePan, 0L, false);
        if (!"00".equals(rc)) {
            System.out.println("[BankPhatLenhAPI] inquiry => field39=" + rc + " (source fail)");
            return;
        }

        // Xây dựng JSON request (f32,f100, mti=0200, pc=432020,...)
        String jsonReq = "{" +
            "\"mti\":\"0200\"," +
            "\"processingCode\":\"432020\"," +
            "\"sourcePan\":\"" + sourcePan + "\"," +
            "\"destinationPan\":\"" + destPan + "\"," +
            "\"transactionAmount\":\"0\"," +
            "\"f32\":\"" + F32_THIS_BANK + "\"," +
            "\"f100\":\"" + receivingBankF100 + "\"" +
        "}";

        String resp = postJson(NAPAS_URL, jsonReq);
        System.out.println("[BankPhatLenhAPI] inquiry => respJSON=" + resp);

        // Tuỳ ý parse responseCode, v.v...
    }

    // Payment:
    //  1) Kiểm tra cục bộ + trừ tiền
    //  2) Forward Napas (JSON)
    //  3) Napas => Bank nhận lệnh => isoResp => field39
    private void doPayment(String sourcePan, String destPan, long amt) {
        // local check
        String rc = checkSource(sourcePan, amt, true);
        if (!"00".equals(rc)) {
            System.out.println("[BankPhatLenhAPI] payment => field39=" + rc + " (source fail)");
            return;
        }

        // Tạo JSON => Napas
        String amtStr = String.format("%012d", amt);
        String jsonReq = "{" +
            "\"mti\":\"0200\"," +
            "\"processingCode\":\"912020\"," +
            "\"sourcePan\":\"" + sourcePan + "\"," +
            "\"destinationPan\":\"" + destPan + "\"," +
            "\"transactionAmount\":\"" + amtStr + "\"," +
            "\"f32\":\"" + F32_THIS_BANK + "\"," +
            "\"f100\":\"" + receivingBankF100 + "\"" +
        "}";

        String resp = postJson(NAPAS_URL, jsonReq);
        System.out.println("[BankPhatLenhAPI] payment => respJSON=" + resp);

        // Nếu field39 != 00 => có thể rollback tiền cục bộ...
    }

    // check cục bộ: 14=not found, 62=locked, 51=insufficient
    private String checkSource(String pan, long amt, boolean isPayment) {
        Account a = ACCOUNTS_SOURCE.get(pan);
        if (a == null)
            return "14";
        if (!"ACTIVE".equalsIgnoreCase(a.status))
            return "62";
        if (isPayment) {
            if (a.balance < amt)
                return "51";
            a.balance -= amt;
        }
        return "00";
    }

    private String postJson(String urlStr, String body) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setConnectTimeout(15000); // 15 giây
            conn.setReadTimeout(15000);
            conn.connect();

            byte[] bodyBytes = body.getBytes("UTF-8");
            try (OutputStream out = conn.getOutputStream()) {
                out.write(bodyBytes);
            }

            // Đọc phản hồi
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (InputStream in = conn.getInputStream()) {
                byte[] buf = new byte[4096];
                int len;
                while ((len = in.read(buf)) != -1) {
                    baos.write(buf, 0, len);
                }
            }
            return baos.toString("UTF-8");

        } catch (Exception e) {
            e.printStackTrace();
            // Nếu lỗi => trả về JSON timeout
            return createTimeoutResponse();
        } finally {
            if (conn != null)
                conn.disconnect();
        }
    }

    // JSON timeout
    private String createTimeoutResponse() {
        return "{\"field39\":\"68\",\"message\":\"Timeout - No Response\"}";
    }

    // (Tuỳ chọn) Socket inbound cổng 9098 - nếu bạn muốn lắng nghe inbound
    private void startInboundSocket() {
        new Thread(() -> {
            try (ServerSocket ss = new ServerSocket(INBOUND_SOCKET_PORT)) {
                System.out.println("[BankPhatLenhAPI] inbound socket on port=" + INBOUND_SOCKET_PORT);
                while (true) {
                    Socket client = ss.accept();
                    new Thread(() -> handleInbound(client)).start();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void handleInbound(Socket sock) {
        try (InputStream in = sock.getInputStream();
             OutputStream out = sock.getOutputStream()) {

            byte[] buf = new byte[4096];
            int len = in.read(buf);
            if (len <= 0) return;
            String inbound = new String(buf, 0, len, "UTF-8");

            System.out.println("[BankPhatLenhAPI] inbound => " + inbound);

            out.write("OK".getBytes("UTF-8"));
            out.flush();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    static class Account {
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
