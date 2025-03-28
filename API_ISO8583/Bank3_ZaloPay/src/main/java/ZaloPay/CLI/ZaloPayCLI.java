package ZaloPay.CLI;

import ZaloPay.service.ZaloPayService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class ZaloPayCLI implements CommandLineRunner {

    private final ZaloPayService zaloPayService;
    private String receivingBankF100 = null;

    public ZaloPayCLI(ZaloPayService svc){
        this.zaloPayService = svc;
    }

    @Override
    public void run(String... args) throws Exception {
        Scanner sc=new Scanner(System.in);
        while(true) {
            System.out.println("\n===== [ZaloPay CLI] =====");
            System.out.println("1. Set receiving bank (F100)");
            System.out.println("2. Inquiry");
            System.out.println("3. Payment");
            System.out.println("0. Exit CLI");
            System.out.print("Chọn: ");
            String choice = sc.nextLine().trim();
            if("0".equals(choice)) break;

            if("1".equals(choice)){
                System.out.println("Chọn: 1=VCB(970401),2=TCB(970402),4=NganLuong(970404)");
                String ch=sc.nextLine().trim();
                if("1".equals(ch)) receivingBankF100="970401";
                else if("2".equals(ch)) receivingBankF100="970402";
                else if("4".equals(ch)) receivingBankF100="970404";
                else {
                    System.out.println("Ko hợp lệ.");
                    continue;
                }
                System.out.println("[ZaloPay CLI] => F100="+ receivingBankF100);
            }
            else if("2".equals(choice)){
                if(receivingBankF100==null){
                    System.out.println("Chưa set F100!");
                    continue;
                }
                System.out.print("srcPan (F2): ");
                String src=sc.nextLine().trim();
                System.out.print("destPan (F103): ");
                String dst=sc.nextLine().trim();

                Map<String,String> req=new HashMap<>();
                req.put("F2", src);
                req.put("F103", dst);
                req.put("F100", receivingBankF100);

                Map<String,String> resp= zaloPayService.processInquiry(req);
            }
            else if("3".equals(choice)){
                if(receivingBankF100==null){
                    System.out.println("Chưa set F100!");
                    continue;
                }
                System.out.print("srcPan (F2): ");
                String src=sc.nextLine().trim();
                System.out.print("destPan (F103): ");
                String dst=sc.nextLine().trim();
                System.out.print("amount(F4): ");
                String amt=sc.nextLine().trim();

                Map<String,String> req=new HashMap<>();
                req.put("F2", src);
                req.put("F103", dst);
                req.put("F4", amt);
                req.put("F100", receivingBankF100);

                Map<String,String> resp= zaloPayService.processPayment(req);
            }
            else {
                System.out.println("Không hợp lệ.");
            }
        }
    }
}
