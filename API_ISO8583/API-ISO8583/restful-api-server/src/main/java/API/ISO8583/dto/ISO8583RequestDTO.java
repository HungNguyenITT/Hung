package API.ISO8583.dto;

public class ISO8583RequestDTO {

    private String mti;               // "0200"
    private String processingCode;
    private String sourcePan;         // Tài khoản gửi (do Bank A quản lý)
    private String destinationPan;    // Tài khoản nhận (Bank B quản lý)
    private String transactionAmount; // Số tiền
    private String confirmCode;       // Mã xác nhận (chỉ do Bank A sinh)

    // Thông tin cục bộ hiển thị cho client (không bắt buộc)
    private Long balanceSource;
    private String statusSource;
    
    private String responseCode;

    public String getResponseCode() {
		return responseCode;
	}
	public void setResponseCode(String responseCode) {
		this.responseCode = responseCode;
	}
	// getters & setters
    public String getMti() {
        return mti;
    }
    public void setMti(String mti) {
        this.mti = mti;
    }

    public String getProcessingCode() {
        return processingCode;
    }
    public void setProcessingCode(String processingCode) {
        this.processingCode = processingCode;
    }

    public String getSourcePan() {
        return sourcePan;
    }
    public void setSourcePan(String sourcePan) {
        this.sourcePan = sourcePan;
    }

    public String getDestinationPan() {
        return destinationPan;
    }
    public void setDestinationPan(String destinationPan) {
        this.destinationPan = destinationPan;
    }

    public String getTransactionAmount() {
        return transactionAmount;
    }
    public void setTransactionAmount(String transactionAmount) {
        this.transactionAmount = transactionAmount;
    }

    public String getConfirmCode() {
        return confirmCode;
    }
    public void setConfirmCode(String confirmCode) {
        this.confirmCode = confirmCode;
    }

    public Long getBalanceSource() {
        return balanceSource;
    }
    public void setBalanceSource(Long balanceSource) {
        this.balanceSource = balanceSource;
    }

    public String getStatusSource() {
        return statusSource;
    }
    public void setStatusSource(String statusSource) {
        this.statusSource = statusSource;
    }
}
