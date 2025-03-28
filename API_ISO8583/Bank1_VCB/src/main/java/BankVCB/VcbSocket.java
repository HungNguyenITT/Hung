package BankVCB;

import org.jpos.iso.ISOException;
import org.jpos.iso.ISOMsg;
import org.jpos.iso.packager.GenericPackager;
import utils.IsoDebugHelper;
import utils.IsoSocketUtils;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;

/**
 * VCB bank code (970401) => Napas port=1212
 * Chờ Napas => f39=68 sau 15s.
 */
public class VcbSocket {

    private static final String NAPAS_HOST = "localhost";
    private static final int NAPAS_PORT = 1212;
    private static final String F32_THIS_BANK = "970401";

    // local DB
    private static final Map<String, Account> ACCOUNTS = new HashMap<>();
    static {
        ACCOUNTS.put("111111", new Account("111111",2_000_000L,"ACTIVE"));
        ACCOUNTS.put("222222", new Account("222222",1_000_000L,"ACTIVE"));
        ACCOUNTS.put("333333", new Account("333333",5_000_000L,"LOCKED"));
    }

    private static Socket socket;
    private static OutputStream out;
    private static InputStream in;

    // “pending requests” => STAN => future
    private static final Map<String, CompletableFuture<ISOMsg>> pendingMap = new ConcurrentHashMap<>();

    // jPOS packager from iso87binary.xml
    private static GenericPackager isoPackager;

    public static void main(String[] args) {
        loadIsoPackager(); 
        connectToNapas();
        runCLI();
    }

    private static void loadIsoPackager(){
        try {
            InputStream is = VcbSocket.class.getResourceAsStream("/iso87binary.xml");//tìm và tải tệp 
            if(is==null){
                throw new RuntimeException("[VCB] iso87binary.xml not found!");
            }
            isoPackager = new GenericPackager(is);
            System.out.println("[VCB] iso87binary.xml loaded => packager OK");
        }catch(Exception e){
            throw new RuntimeException("[VCB] fail loading iso87binary.xml", e);
        }
    }

    private static void connectToNapas(){
        while(true){
            try {
                socket = new Socket(NAPAS_HOST, NAPAS_PORT);
                socket.setSoTimeout(0);
                out= socket.getOutputStream();
                in= socket.getInputStream();
                System.out.println("[VCB] connected Napas => "+ socket.getRemoteSocketAddress());

                new Thread(VcbSocket::readLoop).start();
                break;
            } catch(Exception e){
                System.err.println("[VCB] connect fail => retry 5s... "+ e.getMessage());
                try{Thread.sleep(5000);}catch(Exception ignore){}
            }
        }
    }

    private static void readLoop(){
        while(true){
            try{
                ISOMsg iso= IsoSocketUtils.readIsoMessageWithHeader(in, isoPackager);
                if(iso==null){
                    System.out.println("[VCB] read null => disconnect?");
                    break;
                }
                IsoDebugHelper.debugIso("[VCB] Inbound", iso);

                String stan= iso.hasField(11)? iso.getString(11):null;
                CompletableFuture<ISOMsg> fut= (stan==null)? null: pendingMap.remove(stan);

                if(fut!=null){
                    fut.complete(iso);
                } else {
                    // inbound request => local process
                    handleInboundRequest(iso);
                }

            } catch(SocketTimeoutException te){
                continue;
            } catch(Exception e){
                e.printStackTrace();
                break;
            }
        }
        System.out.println("[VCB] readLoop ended => close => reconnect?");
        try{socket.close();}catch(Exception ignore){}
        connectToNapas();
    }

    /**
     * inbound request => inquiry/payment => build local resp => Napas
     */
    private static void handleInboundRequest(ISOMsg reqIso)throws ISOException, IOException {
        String pc = reqIso.hasField(3)? reqIso.getString(3): "";
        String destPan= reqIso.hasField(103)? reqIso.getString(103): "";
        long amt= parseAmount(reqIso);

        String rc="96";
        if("432020".equals(pc)){
            Account a= ACCOUNTS.get(destPan);
            if(a==null) rc="14";
            else if(!"ACTIVE".equalsIgnoreCase(a.status)) rc="62";
            else rc="00";
        } else if("912020".equals(pc)){
            Account a= ACCOUNTS.get(destPan);
            if(a==null) rc="14";
            else if(!"ACTIVE".equalsIgnoreCase(a.status)) rc="62";
            else {
                a.balance+=amt;
                rc="00";
            }
        }

        ISOMsg resp=new ISOMsg();
        resp.setPackager(isoPackager);
        int reqMti= Integer.parseInt(reqIso.getMTI());
        resp.setMTI(String.format("%04d", reqMti+10));

        copyIfPresent(reqIso, resp,2);
        copyIfPresent(reqIso, resp,3);
        copyIfPresent(reqIso, resp,4);
        copyIfPresent(reqIso, resp,11);
        copyIfPresent(reqIso, resp,12);
        copyIfPresent(reqIso, resp,13);
        copyIfPresent(reqIso, resp,32);
        copyIfPresent(reqIso, resp,100);
        copyIfPresent(reqIso, resp,103);
        resp.set(39, rc);

        IsoDebugHelper.debugIso("[VCB] Response", resp);
    }

    // CLI
    private static void runCLI(){
        Scanner sc=new Scanner(System.in);
        String receivingF100=null;

        while(true){
            System.out.println("[VCB CLI] 1.Set bank 2.Inquiry 3.Payment 0.Exit");
            String c=sc.nextLine().trim();
            if("0".equals(c)) break;

            switch(c){
                case "1":
                    System.out.println("2=TCB(970402), 3=Zalo(970403), 4=NganLuong(970404)");
                    String c2=sc.nextLine().trim();
                    if("2".equals(c2)) receivingF100="970402";
                    else if("3".equals(c2)) receivingF100="970403";
                    else if("4".equals(c2)) receivingF100="970404";
                    else System.out.println("Invalid bank code");
                    break;
                case "2": {
                    if(receivingF100==null){
                        System.out.println("[VCB] bank đích chưa set!");
                        break;
                    }
                    System.out.print("sourcePan: ");
                    String spI=sc.nextLine().trim();
                    System.out.print("destPan: ");
                    String dpI=sc.nextLine().trim();
                    doInquiry(spI, dpI, receivingF100);
                    break;
                }
                case "3": {
                    if(receivingF100==null){
                        System.out.println("[VCB] bank đích chưa set!");
                        break;
                    }
                    System.out.print("sourcePan: ");
                    String spP=sc.nextLine().trim();
                    System.out.print("destPan: ");
                    String dpP=sc.nextLine().trim();
                    System.out.print("amount: ");
                    long amt= Long.parseLong(sc.nextLine().trim());
                    doPayment(spP, dpP, amt, receivingF100);
                    break;
                }
            }
        }
    }

    private static void doInquiry(String srcPan, String dstPan, String f100){
        // local “debit=0”
        String rc= checkLocalDebit(srcPan,0);
        if(!"00".equals(rc)){
            System.out.println("[VCB] local fail => "+rc);
            return;
        }
        try {
            ISOMsg iso=new ISOMsg();
            iso.setPackager(isoPackager);
            iso.setMTI("0200");
            iso.set(2, srcPan);
            iso.set(3, "432020");
            iso.set(4, "000000000000"); 
            iso.set(32, F32_THIS_BANK);
            iso.set(100, f100);
            iso.set(103, dstPan);
            setF11F12F13(iso);

            IsoDebugHelper.debugIso("[VCB] => Napas (Inquiry)", iso);

            // wait 30s => Napas => if bank đích offline => Napas => f39=68 after 15s
            ISOMsg resp= sendAndWait(iso, 30000);
            if(resp==null){
                System.out.println("[VCB] after 30s => no response => ???");
                return;
            }
            IsoDebugHelper.debugIso("[VCB] <= Napas (InquiryResp)", resp);

        }catch(Exception e){
            e.printStackTrace();
        }
    }

    private static void doPayment(String srcPan, String dstPan, long amt, String f100){
        String rc= checkLocalDebit(srcPan, amt);
        if(!"00".equals(rc)){
            System.out.println("[VCB] local fail => "+ rc);
            return;
        }
        try {
            ISOMsg iso=new ISOMsg();
            iso.setPackager(isoPackager);
            iso.setMTI("0200");
            iso.set(2, srcPan);
            iso.set(3, "912020");
            iso.set(4, String.format("%012d", amt));
            iso.set(32, F32_THIS_BANK);
            iso.set(100, f100);
            iso.set(103, dstPan);
            setF11F12F13(iso);

            IsoDebugHelper.debugIso("[VCB] => Napas (Payment)", iso);

            ISOMsg resp= sendAndWait(iso,30000);
            if(resp==null){
                System.out.println("[VCB] after 30s => no response => ???");
                return;
            }
            IsoDebugHelper.debugIso("[VCB] <= Napas (PaymentResp)", resp);

        }catch(Exception e){
            e.printStackTrace();
        }
    }

    /**
     * send iso => Napas => chờ up to timeoutMs => Napas build 68 after 15s if target offline
     */
    private static ISOMsg sendAndWait(ISOMsg iso, long timeoutMs) throws ISOException, IOException {
        String stan= iso.getString(11);
        if(stan==null) throw new ISOException("[VCB] missing F11 => cannot wait resp");

        CompletableFuture<ISOMsg> fut= new CompletableFuture<>();
        pendingMap.put(stan,fut);

        IsoSocketUtils.sendIsoMessageWithHeader(out, iso);

        try {
            return fut.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch(TimeoutException te){
            System.out.println("[VCB] after "+timeoutMs+" ms => no response => ???");
            return null;
        } catch(Exception e){
            e.printStackTrace();
            return null;
        } finally {
            pendingMap.remove(stan);
        }
    }

    private static String checkLocalDebit(String pan, long amt){
        Account a= ACCOUNTS.get(pan);
        if(a==null) return "14";
        if(!"ACTIVE".equalsIgnoreCase(a.status)) return "62";
        if(a.balance<amt) return "51";
        a.balance-=amt;
        return "00";
    }

    private static void setF11F12F13(ISOMsg iso)throws ISOException{
        iso.set(11, String.format("%06d",(int)(Math.random()*999999)));
        LocalDateTime now= LocalDateTime.now();
        iso.set(12, now.format(DateTimeFormatter.ofPattern("HHmmss")));
        iso.set(13, now.format(DateTimeFormatter.ofPattern("MMdd")));
    }

    private static void copyIfPresent(ISOMsg from, ISOMsg to, int f)throws ISOException{
        if(from.hasField(f)){
            to.set(f, from.getString(f));
        }
    }

    private static long parseAmount(ISOMsg iso)throws ISOException{
        if(!iso.hasField(4)) return 0;
        String s= iso.getString(4).replaceFirst("^0+","");
        if(s.isEmpty()) return 0;
        return Long.parseLong(s);
    }

    static class Account{
        String pan;
        long balance;
        String status;
        Account(String p,long b,String s){
            pan=p; balance=b; status=s;
        }
    }
}
