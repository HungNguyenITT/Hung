package API.ISO8583.entity;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "iso8583_messages")
public class ISO8583Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String type;              // "REQUEST" hoặc "RESPONSE"
    private String mti;               // Message Type Indicator
    private String pan;               // Field 2
    private String processingCode;    // Field 3
    private String transactionAmount; // Field 4
    private String responseCode;      // Field 39

    private LocalDateTime createdAt;

    public ISO8583Message() {
        this.createdAt = LocalDateTime.now();
    }


    public Long getId() {
        return id;
    }
    public void setId(Long id) {
        this.id = id;
    }

    public String getType() {
        return type;
    }
    public void setType(String type) {
        this.type = type;
    }

    public String getMti() {
        return mti;
    }
    public void setMti(String mti) {
        this.mti = mti;
    }

    public String getPan() {
        return pan;
    }
    public void setPan(String pan) {
        this.pan = pan;
    }

    public String getProcessingCode() {
        return processingCode;
    }
    public void setProcessingCode(String processingCode) {
        this.processingCode = processingCode;
    }

    public String getTransactionAmount() {
        return transactionAmount;
    }
    public void setTransactionAmount(String transactionAmount) {
        this.transactionAmount = transactionAmount;
    }

    public String getResponseCode() {
        return responseCode;
    }
    public void setResponseCode(String responseCode) {
        this.responseCode = responseCode;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
