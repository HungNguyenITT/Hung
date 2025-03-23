package API.ISO8583.service;

import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class AccountService {

    //quản lý tài khoản gửi
    private static final Map<String, Account> ACCOUNTS_SOURCE = new HashMap<>();

    // Map confirmCode: key = (sourcePan + "_" + destPan), value = confirmCode
    private static final Map<String, String> CONFIRM_CODES = new HashMap<>();

    static {
    	// tài khoản gửi
        ACCOUNTS_SOURCE.put("111111", new Account("111111", 1_000_000L, "ACTIVE"));
        ACCOUNTS_SOURCE.put("222222", new Account("222222", 500_000L,  "ACTIVE"));
        ACCOUNTS_SOURCE.put("333333", new Account("333333", 2_000_000L,"LOCKED"));
    }

    // Kiểm tra sourcePan (chỉ local check)
    public String checkSource(String sourcePan) {
        Account s = ACCOUNTS_SOURCE.get(sourcePan);
        if (s == null) {
            return "SOURCE_NOT_FOUND";
        }
        if (!"ACTIVE".equalsIgnoreCase(s.getStatus())) {
            return "SOURCE_NOT_ACTIVE";
        }
        return "OK";
    }

    // Tạo confirmCode (nếu Bank B cũng OK)
    public String generateConfirmCode(String sourcePan, String destPan) {
        String code = UUID.randomUUID().toString().substring(0,5).toUpperCase();
        String key  = sourcePan + "_" + destPan;
        CONFIRM_CODES.put(key, code);
        return code;
    }

    public String checkConfirmCode(String sourcePan, String destPan, String userConfirmCode) {
        String key = sourcePan + "_" + destPan;
        String realCode = CONFIRM_CODES.get(key);
        if (realCode == null || !realCode.equals(userConfirmCode)) {
            return "INVALID_CONFIRM_CODE";
        }
        return "OK";
    }

    // Trừ tiền tài khoản gửi
    public String debit(String sourcePan, long amount) {
        Account s = ACCOUNTS_SOURCE.get(sourcePan);
        if (s == null) {
            return "SOURCE_NOT_FOUND";
        }
        if (!"ACTIVE".equalsIgnoreCase(s.getStatus())) {
            return "SOURCE_NOT_ACTIVE";
        }
        if (s.getBalance() < amount) {
            return "SOURCE_INSUFFICIENT_FUNDS";
        }
        s.setBalance(s.getBalance() - amount);
        return "OK";
    }

    // Xóa confirmCode sau khi chuyển
    public void removeConfirmCode(String sourcePan, String destPan) {
        String key = sourcePan + "_" + destPan;
        CONFIRM_CODES.remove(key);
    }

    // Lấy balance/status cho hiển thị
    public long getSourceBalance(String sourcePan) {
        Account s = ACCOUNTS_SOURCE.get(sourcePan);
        if (s == null) return -1;
        return s.getBalance();
    }
    public String getSourceStatus(String sourcePan) {
        Account s = ACCOUNTS_SOURCE.get(sourcePan);
        if (s == null) return "NOT_FOUND";
        return s.getStatus();
    }

    // Lớp Account nội bộ
    private static class Account {
        private String pan;
        private long balance;
        private String status;

        public Account(String pan, long balance, String status) {
            this.pan = pan;
            this.balance = balance;
            this.status = status;
        }
        public String getPan() { return pan; }
        public long getBalance() { return balance; }
        public void setBalance(long b) { this.balance = b; }
        public String getStatus() { return status; }
        public void setStatus(String s) { this.status = s; }
    }
}
