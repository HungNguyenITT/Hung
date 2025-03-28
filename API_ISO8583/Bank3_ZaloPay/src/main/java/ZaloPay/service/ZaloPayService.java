package ZaloPay.service;

import org.springframework.stereotype.Service;
import utils.JsonMapperUtil;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.*;

@Service
public class ZaloPayService {
    private static final String BANK_CODE = "970403";
    private static final String API1_URL  = "http://localhost:8081/api1";

    private static final Map<String, Account> ACCOUNTS = new HashMap<>();
    static {
        ACCOUNTS.put("101010", new Account("101010", 5000000L, "ACTIVE"));
        ACCOUNTS.put("999999", new Account("999999", 3000000L, "ACTIVE"));
        ACCOUNTS.put("777777", new Account("777777", 1000000L, "LOCKED"));
    }

    // =============== Inquiry ===============
    public Map<String,String> processInquiry(Map<String,String> req){
        Map<String,String> isoReq= standardizeRequest(req,"432020");
        String f100= isoReq.getOrDefault("F100","");

        if(!BANK_CODE.equals(f100)){
            // Zalo => Napas => chờ
            printIso("ZaloPay","=> Napas (Inquiry)", isoReq);
            String respJson= doHttpPost(API1_URL+"/inquiry", JsonMapperUtil.toJson(isoReq));
            if(respJson == null){
                // KHÔNG in ra “respond => bank=… F39=96”
                System.out.println("[ZaloPay] doHttpPost => no response => stop");
                return Collections.emptyMap();
            }
            Map<String,String> respMap= JsonMapperUtil.fromJson(respJson);
            printIso("ZaloPay","(Inquiry) response", respMap);
            return respMap;
        } else {
            // Zalo bank nhận
            printIso("ZaloPay","inbound request (Inquiry)", isoReq);
            String destAcct= isoReq.getOrDefault("F103","");
            Account acc= ACCOUNTS.get(destAcct);
            String rc;
            if(acc==null) rc="14";
            else if(!"ACTIVE".equalsIgnoreCase(acc.getStatus())) rc="62";
            else rc="00";

            Map<String,String> resp= new HashMap<>(isoReq);
            resp.put("F1","0210");
            resp.put("F39", rc);
            printIso("ZaloPay","response", resp);
            return resp;
        }
    }

    // =============== Payment ===============
    public Map<String,String> processPayment(Map<String,String> req){
        Map<String,String> isoReq= standardizeRequest(req,"912020");
        String f100= isoReq.getOrDefault("F100","");

        if(!BANK_CODE.equals(f100)){
            // Zalo => Napas
            printIso("ZaloPay","=> Napas (Payment)", isoReq);
            String respJson= doHttpPost(API1_URL+"/payment", JsonMapperUtil.toJson(isoReq));
            if(respJson==null){
                System.out.println("[ZaloPay] doHttpPost => no response => stop");
                return Collections.emptyMap();
            }
            Map<String,String> respMap= JsonMapperUtil.fromJson(respJson);
            printIso("ZaloPay","(Payment)response ", respMap);
            return respMap;
        } else {
            // bank nhận
            printIso("ZaloPay","inbound request (Payment)", isoReq);
            String destAcct= isoReq.getOrDefault("F103","");
            Account acc= ACCOUNTS.get(destAcct);
            String rc;
            if(acc==null) rc="14";
            else if(!"ACTIVE".equalsIgnoreCase(acc.getStatus())) rc="62";
            else {
                long amt= parseLongSafe(isoReq.getOrDefault("F4","0"));
                acc.setBalance(acc.getBalance()+amt);
                rc="00";
            }
            Map<String,String> resp= new HashMap<>(isoReq);
            resp.put("F1","0210");
            resp.put("F39", rc);
            printIso("ZaloPay","response", resp);
            return resp;
        }
    }

    // =============== Utility ===============
    private Map<String,String> standardizeRequest(Map<String,String> req, String defPC){
        Map<String,String> st= new HashMap<>(req);
        if(!st.containsKey("F1")) st.put("F1","0200");
        if(!st.containsKey("F3")) st.put("F3", defPC);
        if(!st.containsKey("F4")){
            st.put("F4","000000000000");
        } else {
            long amt= parseLongSafe(st.get("F4"));
            st.put("F4", String.format("%012d", amt));
        }
        if(!st.containsKey("F11")){
            st.put("F11", String.format("%06d",(int)(Math.random()*1_000_000)));
        }
        if(!st.containsKey("F12")){
            st.put("F12", new SimpleDateFormat("HHmmss").format(new Date()));
        }
        if(!st.containsKey("F13")){
            st.put("F13", new SimpleDateFormat("MMdd").format(new Date()));
        }
        st.put("F32", BANK_CODE);
        if(!st.containsKey("F100")) st.put("F100","");
        if(!st.containsKey("F103") && st.containsKey("destPan")){
            st.put("F103", st.get("destPan"));
        }
        return st;
    }

    // Tăng timeout => 20000ms
    private String doHttpPost(String url, String body){
        HttpURLConnection conn=null;
        try {
            URL u=new URL(url);
            conn=(HttpURLConnection)u.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type","application/json; charset=UTF-8");
            conn.setConnectTimeout(20000);
            conn.setReadTimeout(20000);

            try(OutputStream out=conn.getOutputStream()){
                out.write(body.getBytes("UTF-8"));
            }
            ByteArrayOutputStream baos=new ByteArrayOutputStream();
            try(InputStream in=conn.getInputStream()){
                byte[] buf=new byte[4096];
                int len;
                while((len=in.read(buf))!=-1){
                    baos.write(buf,0,len);
                }
            }
            return baos.toString("UTF-8");
        } catch(Exception e){
            e.printStackTrace();
            return null;
        } finally {
            if(conn!=null) conn.disconnect();
        }
    }

    private void printIso(String bankName, String desc, Map<String,String> iso){
        System.out.println("=== ["+bankName+"] "+desc+" ===");
        System.out.println("MTI="+ iso.getOrDefault("F1","0200"));
        List<Integer> fields= new ArrayList<>();
        for(String k: iso.keySet()){
            if(k.startsWith("F") && !k.equals("F1")){
                try{
                    fields.add(Integer.parseInt(k.substring(1)));
                }catch(Exception ignore){}
            }
        }
        Collections.sort(fields);
        for(int f: fields){
            System.out.println("F"+f+"="+ iso.get("F"+f));
        }
        System.out.println("==========");
    }

    private Map<String,String> buildErrorResp(Map<String,String> req, String rc){
        Map<String,String> e= new HashMap<>(req);
        e.put("F1","0210");
        e.put("F39",rc);
        return e;
    }

    private long parseLongSafe(String s){
        try{return Long.parseLong(s);}catch(Exception e){return 0;}
    }

    private static class Account {
        String pan; long balance; String status;
        public Account(String p,long b,String s){
            pan=p; balance=b; status=s;
        }
        public long getBalance(){return balance;}
        public void setBalance(long x){balance=x;}
        public String getStatus(){return status;}
    }
}
