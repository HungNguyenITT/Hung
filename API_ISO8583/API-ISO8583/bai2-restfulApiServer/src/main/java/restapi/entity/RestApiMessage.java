package restapi.entity;


import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * Lưu mti, pan, processingCode, amount, confirmCode, destPan, responseCode, ...
 */
@Entity
@Table(name="restapi_message")
public class RestApiMessage {

    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Long id;

    private String type; // REQUEST / RESPONSE

    private String mti;
    private String pan;
    private String processingCode;
    private String amount;
    private String confirmCode;
    private String destPan;
    private String responseCode;

    private LocalDateTime createdAt;

    public RestApiMessage(){
        this.createdAt=LocalDateTime.now();
    }

    // getters & setters...
    public Long getId(){return id;}
    public void setId(Long id){this.id=id;}

    public String getType(){return type;}
    public void setType(String type){this.type=type;}

    public String getMti(){return mti;}
    public void setMti(String mti){this.mti=mti;}

    public String getPan(){return pan;}
    public void setPan(String pan){this.pan=pan;}

    public String getProcessingCode(){return processingCode;}
    public void setProcessingCode(String pc){this.processingCode=pc;}

    public String getAmount(){return amount;}
    public void setAmount(String a){this.amount=a;}

    public String getConfirmCode(){return confirmCode;}
    public void setConfirmCode(String c){this.confirmCode=c;}

    public String getDestPan(){return destPan;}
    public void setDestPan(String dp){this.destPan=dp;}

    public String getResponseCode(){return responseCode;}
    public void setResponseCode(String rc){this.responseCode=rc;}

    public LocalDateTime getCreatedAt(){return createdAt;}
    public void setCreatedAt(LocalDateTime c){this.createdAt=c;}
}
