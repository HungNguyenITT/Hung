package socketserver.entity;


import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * Lưu ISO fields: f1, f2, f3, f4, f47, f48, f39
 */
@Entity
@Table(name="socket_message")
public class SocketMessage {

    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Long id;

    private String type;   // REQUEST / RESPONSE

    // f1=MTI
    private String f1;  
    private String f2;  
    private String f3;  
    private String f4;  
    private String f47; 
    private String f48; 
    private String f39;  

    private LocalDateTime createdAt;

    public SocketMessage(){
        this.createdAt=LocalDateTime.now();
    }

    // getters & setters...
    public Long getId(){return id;}
    public void setId(Long id){this.id=id;}

    public String getType(){return type;}
    public void setType(String type){this.type=type;}

    public String getF1(){return f1;}
    public void setF1(String f1){this.f1=f1;}

    public String getF2(){return f2;}
    public void setF2(String f2){this.f2=f2;}

    public String getF3(){return f3;}
    public void setF3(String f3){this.f3=f3;}

    public String getF4(){return f4;}
    public void setF4(String f4){this.f4=f4;}

    public String getF47(){return f47;}
    public void setF47(String f47){this.f47=f47;}

    public String getF48(){return f48;}
    public void setF48(String f48){this.f48=f48;}

    public String getF39(){return f39;}
    public void setF39(String f39){this.f39=f39;}

    public LocalDateTime getCreatedAt(){return createdAt;}
    public void setCreatedAt(LocalDateTime createdAt){this.createdAt=createdAt;}
}

