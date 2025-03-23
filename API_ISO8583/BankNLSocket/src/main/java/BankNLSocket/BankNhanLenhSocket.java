package BankNLSocket;

import org.jpos.iso.*;
import org.jpos.iso.packager.ISO87APackager;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;


//Bank nhận lệnh Socket (cổng 9091)
//Tài khoản nhận => inquiry => field39=00 => LƯU cặp (sourcePan,destPan)
//Payment => check cặp => if mismatch => field39=94 => in console => ...
//In console request/response (kể cả mismatch)
 
public class BankNhanLenhSocket {

    private static final int PORT = 9091;

    private static final Map<String, Account> ACCOUNTS_DEST = new HashMap<>();
    // store cặp (src+"_"+dest) => true
    private static final Map<String,Boolean> INQUIRY_MAP    = new HashMap<>();

    static {
        ACCOUNTS_DEST.put("888888", new Account("888888", 3_000_000L,"ACTIVE"));
        ACCOUNTS_DEST.put("999999", new Account("999999", 5_000_000L,"ACTIVE"));
        ACCOUNTS_DEST.put("777777", new Account("777777", 1_000_000L,"LOCKED"));
    }

    public static void main(String[] args){
        try(ServerSocket server=new ServerSocket(PORT)){
            System.out.println("[Bank B socket] listening on port "+PORT);
            while(true){
                Socket client=server.accept();
                new Thread(() -> handleClient(client)).start();
            }
        } catch(Exception e){
            e.printStackTrace();
        }
    }

    private static void handleClient(Socket sock){
        try(InputStream in = sock.getInputStream();
            OutputStream out = sock.getOutputStream()) {

            byte[] buf=new byte[4096];
            int len=in.read(buf);
            if(len<=0) return;

            byte[] reqBytes=new byte[len];
            System.arraycopy(buf,0,reqBytes,0,len);

            ISOMsg isoReq=new ISOMsg();
            isoReq.setPackager(new ISO87APackager());
            isoReq.unpack(reqBytes);

            System.out.println("[Bank B socket] REQ =>");
            debugIso(isoReq);

            ISOMsg isoResp= createResponse(isoReq);

            byte[] respBytes= isoResp.pack();
            out.write(respBytes);
            out.flush();

            System.out.println("[Bank B socket] RESP =>");
            debugIso(isoResp);

        } catch(Exception e){
            e.printStackTrace();
        }
    }

    private static ISOMsg createResponse(ISOMsg req) throws ISOException {
        ISOMsg resp=new ISOMsg();
        resp.setPackager(new ISO87APackager());

        // 0200->0210
        int mti=Integer.parseInt(req.getMTI());
        mti+=10;
        resp.setMTI(String.format("%04d", mti));

        // copy field2,3,4,11,12,13,47,48
        copyIfPresent(req, resp, 2);
        copyIfPresent(req, resp, 3);
        copyIfPresent(req, resp, 4);
        copyIfPresent(req, resp, 11);
        copyIfPresent(req, resp, 12);
        copyIfPresent(req, resp, 13);
        copyIfPresent(req, resp, 47);

        // parse
        String pc   = req.hasField(3)? req.getString(3): "";
        long amt    = req.hasField(4)? Long.parseLong(req.getString(4)): 0;
        String sPan = req.hasField(2)? req.getString(2): "";
        String dPan = req.hasField(47)? req.getString(47): "";

        String rc="96";
        if("432020".equals(pc)){
            rc= inquiry(dPan, sPan);
        } else if("912020".equals(pc)){
            rc= payment(dPan, sPan, amt);
        }

        resp.set(39, rc);
        return resp;
    }

    private static void copyIfPresent(ISOMsg from, ISOMsg to, int f) throws ISOException {
        if(from.hasField(f)){
            to.set(f, from.getString(f));
        }
    }

    private static String inquiry(String dest, String source) {
        // check dest
        Account d= ACCOUNTS_DEST.get(dest);
        if(d==null) return "14";
        if(!"ACTIVE".equalsIgnoreCase(d.status)) return "62";

        // store cặp
        String key= source+"_"+dest;
        INQUIRY_MAP.put(key, true);

        return "00";
    }

    private static String payment(String dest, String source, long amt){
        String key= source+"_"+dest;
        if(!INQUIRY_MAP.containsKey(key)){
            System.out.println("[Bank B socket] Payment mismatch => field39=94 (chưa inquiry cặp?)");
            return "94";
        }
        Account d=ACCOUNTS_DEST.get(dest);
        if(d==null) return "14";
        if(!"ACTIVE".equalsIgnoreCase(d.status)) return "62";

        d.balance += amt;
        // remove cặp if you want
        INQUIRY_MAP.remove(key);

        return "00";
    }


    //debug: only field1,2,3,4,11,12,13,39,47,48 => if missing => ""
    private static void debugIso(ISOMsg iso) throws ISOException {
        System.out.println("  Field 1="+ (iso.getMTI()==null?"": iso.getMTI()));
        System.out.println("  Field 2="+ getOrEmpty(iso,2));
        System.out.println("  Field 3="+ getOrEmpty(iso,3));
        System.out.println("  Field 4="+ getOrEmpty(iso,4));
        System.out.println("  Field 11="+ getOrEmpty(iso,11));
        System.out.println("  Field 12="+ getOrEmpty(iso,12));
        System.out.println("  Field 13="+ getOrEmpty(iso,13));
        System.out.println("  Field 39="+ getOrEmpty(iso,39));
        System.out.println("  Field 47="+ getOrEmpty(iso,47));
    }
    private static String getOrEmpty(ISOMsg iso, int f) throws ISOException {
        if(iso.hasField(f)) return iso.getString(f);
        return "";
    }

    private static class Account {
        String pan;
        long balance;
        String status;
        public Account(String p,long b,String st){
            this.pan=p; this.balance=b; this.status=st;
        }
    }
}
