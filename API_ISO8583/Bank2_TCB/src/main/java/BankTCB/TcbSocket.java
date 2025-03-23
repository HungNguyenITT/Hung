package BankTCB;

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
 * TCB bank (970402) => Napas port=1313
 * Napas chờ 15s => f39=68
 */
public class TcbSocket {

    private static final String NAPAS_HOST="localhost";
    private static final int NAPAS_PORT=1313;
    private static final String F32_THIS_BANK="970402";

    private static final Map<String,Account> ACCOUNTS=new HashMap<>();
    static {
        ACCOUNTS.put("444444", new Account("444444",2_000_000L,"ACTIVE"));
        ACCOUNTS.put("555555", new Account("555555",1_000_000L,"ACTIVE"));
        ACCOUNTS.put("666666", new Account("666666",5_000_000L,"LOCKED"));
    }

    private static Socket socket;
    private static OutputStream out;
    private static InputStream in;

    private static final Map<String,CompletableFuture<ISOMsg>> pendingMap= new ConcurrentHashMap<>();

    private static GenericPackager isoPackager;

    public static void main(String[] args){
        loadIsoPackager();
        connectToNapas();
        runCLI();
    }

    private static void loadIsoPackager(){
        try {
            InputStream is= TcbSocket.class.getResourceAsStream("/iso87binary.xml");
            if(is==null){
                throw new RuntimeException("[TCB] iso87binary.xml not found!");
            }
            isoPackager = new GenericPackager(is);
            System.out.println("[TCB] iso87binary.xml loaded => packager OK");
        } catch(Exception e){
            throw new RuntimeException("[TCB] fail loading iso87binary.xml", e);
        }
    }

    private static void connectToNapas(){
        while(true){
            try {
                socket = new Socket(NAPAS_HOST, NAPAS_PORT);
                socket.setSoTimeout(0);
                out= socket.getOutputStream();
                in= socket.getInputStream();
                System.out.println("[TCB] connected Napas => "+ socket.getRemoteSocketAddress());

                new Thread(TcbSocket::readLoop).start();
                break;
            }catch(Exception e){
                System.err.println("[TCB] connect fail => retry 5s... "+ e.getMessage());
                try{Thread.sleep(5000);}catch(Exception ignore){}
            }
        }
    }

    private static void readLoop(){
        while(true){
            try {
                ISOMsg iso= IsoSocketUtils.readIsoMessageWithHeader(in, isoPackager);
                if(iso==null){
                    System.out.println("[TCB] read null => disconnect?");
                    break;
                }
                IsoDebugHelper.debugIso("[TCB] Inbound", iso);

                String stan= iso.hasField(11)? iso.getString(11): null;
                CompletableFuture<ISOMsg> fut= (stan==null)? null: pendingMap.remove(stan);

                if(fut!=null){
                    fut.complete(iso);
                } else {
                    handleInboundRequest(iso);
                }
            } catch(SocketTimeoutException te){
                continue;
            } catch(Exception e){
                e.printStackTrace();
                break;
            }
        }
        System.out.println("[TCB] readLoop ended => close => reconnect?");
        try{socket.close();}catch(Exception ignore){}
        connectToNapas();
    }

    private static void handleInboundRequest(ISOMsg reqIso)throws ISOException,IOException{
        String pc= reqIso.hasField(3)? reqIso.getString(3): "";
        String f103= reqIso.hasField(103)? reqIso.getString(103): "";
        long amt= parseAmount(reqIso);

        String rc="96";
        if("432020".equals(pc)){
            Account a= ACCOUNTS.get(f103);
            if(a==null) rc="14";
            else if(!"ACTIVE".equalsIgnoreCase(a.status)) rc="62";
            else rc="00";
        } else if("912020".equals(pc)){
            Account a= ACCOUNTS.get(f103);
            if(a==null) rc="14";
            else if(!"ACTIVE".equalsIgnoreCase(a.status)) rc="62";
            else {
                a.balance+=amt;
                rc="00";
            }
        }
        ISOMsg resp=new ISOMsg();
        resp.setPackager(isoPackager);
        int reqMti=Integer.parseInt(reqIso.getMTI());
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

        IsoDebugHelper.debugIso("[TCB] Response ", resp);
    }

    private static void runCLI(){
        Scanner sc=new Scanner(System.in);
        String receivingF100=null;

        while(true){
            System.out.println("[TCB CLI] 1.Set bank 2.Inquiry 3.Payment 0.Exit");
            String c=sc.nextLine().trim();
            if("0".equals(c)) break;

            switch(c){
                case "1":
                    System.out.println("1=VCB(970401),2=TCB(970402),3=Zalo(970403),4=NganLuong(970404)");
                    String c2=sc.nextLine().trim();
                    if("1".equals(c2)) receivingF100="970401";
                    else if("2".equals(c2)) receivingF100="970402";
                    else if("3".equals(c2)) receivingF100="970403";
                    else if("4".equals(c2)) receivingF100="970404";
                    else System.out.println("Invalid bank code");
                    break;
                case "2": { 
                    if(receivingF100==null){
                        System.out.println("[TCB] Bank đích chưa set!");
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
                        System.out.println("[TCB] Bank đích chưa set!");
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
        String rc= checkLocalDebit(srcPan,0);
        if(!"00".equals(rc)){
            System.out.println("[TCB] local fail => "+ rc);
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

            IsoDebugHelper.debugIso("[TCB] => Napas (Inquiry)", iso);

            // chờ 30s => Napas => 15s => f39=68
            ISOMsg resp= sendAndWait(iso,30000);
            if(resp==null){
                System.out.println("[TCB] after 30s => no response => ???");
                return;
            }
            IsoDebugHelper.debugIso("[TCB] <= Napas (InquiryResp)", resp);

        }catch(Exception e){
            e.printStackTrace();
        }
    }

    private static void doPayment(String srcPan, String dstPan, long amt, String f100){
        String rc= checkLocalDebit(srcPan, amt);
        if(!"00".equals(rc)){
            System.out.println("[TCB] local fail => "+ rc);
            return;
        }
        try {
            ISOMsg iso= new ISOMsg();
            iso.setPackager(isoPackager);
            iso.setMTI("0200");
            iso.set(2, srcPan);
            iso.set(3, "912020");
            iso.set(4, String.format("%012d", amt));
            iso.set(32, F32_THIS_BANK);
            iso.set(100, f100);
            iso.set(103, dstPan);
            setF11F12F13(iso);

            IsoDebugHelper.debugIso("[TCB] => Napas (Payment)", iso);

            ISOMsg resp= sendAndWait(iso,30000);
            if(resp==null){
                System.out.println("[TCB] after 30s => no response => ???");
                return;
            }
            IsoDebugHelper.debugIso("[TCB] <= Napas (PaymentResp)", resp);

        } catch(Exception e){
            e.printStackTrace();
        }
    }

    /**
     * Gửi ISO => Napas => chờ up to 30s => Napas chờ 15s => 68
     */
    private static ISOMsg sendAndWait(ISOMsg iso, long timeoutMs)throws ISOException,IOException{
        String stan= iso.getString(11);
        if(stan==null) throw new ISOException("[TCB] missing F11 => can't wait resp");

        CompletableFuture<ISOMsg> fut= new CompletableFuture<>();
        pendingMap.put(stan,fut);

        IsoSocketUtils.sendIsoMessageWithHeader(out, iso);

        try {
            return fut.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch(TimeoutException te){
            System.out.println("[TCB] after "+timeoutMs+"ms => no response => ???");
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
