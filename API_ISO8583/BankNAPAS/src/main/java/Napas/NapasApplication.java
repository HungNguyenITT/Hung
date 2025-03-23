//package Napas;
//
//import Napas.entity.NapasPort;
//import Napas.repository.NapasPortRepository;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.SpringApplication;
//import org.springframework.boot.autoconfigure.SpringBootApplication;
//
//import javax.annotation.PostConstruct;
//import java.util.List;
//import java.util.Scanner;
//
//@SpringBootApplication(scanBasePackages = {
//        "Napas.server",
//        "Napas.repository",
//        "Napas.entity",
//        "utils"
//})
//public class NapasApplication {
//
//    @Autowired
//    private NapasPortRepository portRepo;
//    
//    @Autowired
//    private NapasPortManager portManager;
//
//    public static void main(String[] args) {
//        SpringApplication.run(NapasApplication.class, args);
//    }
//
//    @PostConstruct
//    public void init() {
//        portManager.loadAllFromDB();
//        System.out.println("[NAPAS] Loaded inbound ports from DB. Starting CLI...");
//        new Thread(() -> runConsoleCLI()).start();
//    }
//
//    private void runConsoleCLI() {
//        Scanner sc = new Scanner(System.in);
//        while (true) {
//            System.out.println("\n=== Napas CLI ===");
//            System.out.println("1. Show inbound ports");
//            System.out.println("2. Add/Update inbound port");
//            System.out.println("3. Block/Unblock bank");
//            System.out.println("4. Update port");
//            System.out.println("5. Close inbound");
//            System.out.println("0. Exit CLI");
//            System.out.print("Chọn: ");
//            String c = sc.nextLine().trim();
//            if ("0".equals(c)) {
//                System.out.println("[NAPAS] CLI exit");
//                break;
//            }
//            switch (c) {
//                case "1":
//                    doShowInbound();
//                    break;
//                case "2":
//                    doAddOrUpdate(sc);
//                    break;
//                case "3":
//                    doBlock(sc);
//                    break;
//                case "4":
//                    doUpdatePort(sc);
//                    break;
//                case "5":
//                    doClose(sc);
//                    break;
//            }
//        }
//    }
//
//    private void doShowInbound() {
//        List<NapasPort> list = portRepo.findAll();
//        System.out.println("=== Inbound ports from DB ===");
//        for (NapasPort np : list) {
//            System.out.println(np.getBankCode() + " " + np.getBankName()
//                    + " port=" + np.getPort() + " enabled=" + np.isEnabled());
//        }
//        System.out.println("=============================");
//    }
//
//    private void doAddOrUpdate(Scanner sc) {
//        System.out.print("bankCode: ");
//        String bc = sc.nextLine().trim();
//        System.out.print("bankName: ");
//        String bn = sc.nextLine().trim();
//        System.out.print("port: ");
//        int p = Integer.parseInt(sc.nextLine().trim());
//        System.out.print("enabled (true/false): ");
//        boolean en = Boolean.parseBoolean(sc.nextLine().trim());
//
//        NapasPort np = portRepo.findByBankCode(bc);
//        if (np == null) {
//            np = new NapasPort(bc, bn, p, en);
//        } else {
//            np.setBankName(bn);
//            np.setPort(p);
//            np.setEnabled(en);
//        }
//        portRepo.save(np);
//        portManager.addInbound(bc, bn, p, en);
//        System.out.println("[NAPAS CLI] Added/Updated inbound => bank=" + bc + ", port=" + p + ", enabled=" + en);
//    }
//
//    private void doBlock(Scanner sc) {
//        System.out.print("bankCode: ");
//        String bc = sc.nextLine().trim();
//        System.out.print("enable? (true/false): ");
//        boolean en = Boolean.parseBoolean(sc.nextLine().trim());
//        portManager.blockBank(bc, en);
//        System.out.println("[NAPAS CLI] Bank " + bc + " set enabled=" + en);
//    }
//
//    private void doUpdatePort(Scanner sc) {
//        System.out.print("bankCode: ");
//        String bc = sc.nextLine().trim();
//        System.out.print("new port: ");
//        int newP = Integer.parseInt(sc.nextLine().trim());
//        portManager.updatePort(bc, newP);
//        System.out.println("[NAPAS CLI] Updated port for bank " + bc + " => new port=" + newP);
//    }
//
//    private void doClose(Scanner sc) {
//        System.out.print("bankCode: ");
//        String bc = sc.nextLine().trim();
//        portManager.closeInbound(bc);
//        NapasPort np = portRepo.findByBankCode(bc);
//        if (np != null) {
//            portRepo.delete(np);
//        }
//        System.out.println("[NAPAS CLI] Closed inbound for bank " + bc);
//    }
//}
package Napas;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class NapasApplication {
    public static void main(String[] args) {
        SpringApplication.run(NapasApplication.class, args);
    }
}
