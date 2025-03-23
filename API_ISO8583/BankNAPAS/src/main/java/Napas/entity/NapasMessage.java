package Napas.entity;

import javax.persistence.*;

@Entity
@Table(name="napas_message")
public class NapasMessage {

    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Long id;

    private String type;
    private String f1; 
    private String f2;
    private String f3;
    private String f4;
    private String f11;
    private String f12;
    private String f13;
    private String f39;
    private String f32;
    private String f100;
    private String f103;
    
    public String getF103() {
		return f103;
	}

	public void setF103(String f103) {
		this.f103 = f103;
	}

	public NapasMessage() {
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

    public String getF1() {
        return f1;
    }
    public void setF1(String f1) {
        this.f1 = f1;
    }

    public String getF2() {
        return f2;
    }
    public void setF2(String f2) {
        this.f2 = f2;
    }

    public String getF3() {
        return f3;
    }
    public void setF3(String f3) {
        this.f3 = f3;
    }

    public String getF4() {
        return f4;
    }
    public void setF4(String f4) {
        this.f4 = f4;
    }

    public String getF11() {
        return f11;
    }
    public void setF11(String f11) {
        this.f11 = f11;
    }

    public String getF12() {
        return f12;
    }
    public void setF12(String f12) {
        this.f12 = f12;
    }

    public String getF13() {
        return f13;
    }
    public void setF13(String f13) {
        this.f13 = f13;
    }

    public String getF39() {
        return f39;
    }
    public void setF39(String f39) {
        this.f39 = f39;
    } 

    public String getF32() {
        return f32;
    }
    public void setF32(String f32) {
        this.f32 = f32;
    }

    public String getF100() {
        return f100;
    }
    public void setF100(String f100) {
        this.f100 = f100;
    }
}
