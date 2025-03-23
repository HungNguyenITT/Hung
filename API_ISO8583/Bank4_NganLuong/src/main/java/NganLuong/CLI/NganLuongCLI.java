package NganLuong.CLI;

import NganLuong.service.NganLuongService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class NganLuongCLI implements CommandLineRunner {

    private final NganLuongService svc;
    private String receivingBankF100=null;

    public NganLuongCLI(NganLuongService s){
        this.svc = s;
    }

    @Override
    public void run(String... args) throws Exception {
        Scanner sc=new Scanner(System.in);
        while(true){
            System.out.println("\n===== [NganLuong CLI] =====");
            System.out.println("1. Set receiving bank (F100)");
            System.out.println("2. Inquiry");
            System.out.println("3. Payment");
            System.out.println("0. Exit CLI");
            System.out.print("Chọn: ");
            String c=sc.nextLine().trim();
            if("0".equals(c)) break;

            if("1".equals(c)){
                System.out.println("1=VCB(970401),2=TCB(970402),3=Zalo(970403)");
                String ch=sc.nextLine().trim();
                if("1".equals(ch)) receivingBankF100="970401";
                else if("2".equals(ch)) receivingBankF100="970402";
                else if("3".equals(ch)) receivingBankF100="970403";
                else {
                    System.out.println("Invalid");
                    continue;
                }
                System.out.println("[NganLuongCLI] => F100="+ receivingBankF100);
            }
            else if("2".equals(c)){
                if(receivingBankF100==null){
                    System.out.println("F100 chưa set!");
                    continue;
                }
                System.out.print("srcPan(F2): ");
                String src=sc.nextLine().trim();
                System.out.print("destPan(F103): ");
                String dst=sc.nextLine().trim();

                Map<String,String> req=new HashMap<>();
                req.put("F2", src);
                req.put("F103", dst);
                req.put("F100", receivingBankF100);

                Map<String,String> resp= svc.processInquiry(req);
            }
            else if("3".equals(c)){
                if(receivingBankF100==null){
                    System.out.println("F100 chưa set!");
                    continue;
                }
                System.out.print("srcPan(F2): ");
                String src=sc.nextLine().trim();
                System.out.print("destPan(F103): ");
                String dst=sc.nextLine().trim();
                System.out.print("amount(F4): ");
                String amt=sc.nextLine().trim();

                Map<String,String> req=new HashMap<>();
                req.put("F2", src);
                req.put("F103", dst);
                req.put("F4", amt);
                req.put("F100", receivingBankF100);

                Map<String,String> resp= svc.processPayment(req);

            }
        }
    }
}
