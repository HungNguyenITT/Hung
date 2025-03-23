package API2.service;

import org.jpos.iso.ISOException;
import org.jpos.iso.ISOMsg;
import org.jpos.iso.packager.GenericPackager;
import utils.IsoSocketUtils;
import utils.JsonMapperUtil;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;

public class NapasConnectionManager {
    private final String host;
    private final int port;
    private final GenericPackager packager;

    private Socket socket;
    private DataInputStream in;
    private DataOutputStream out;

    private final ExecutorService readThread = Executors.newSingleThreadExecutor();
    private final ConcurrentMap<String, CompletableFuture<ISOMsg>> pendingMap = new ConcurrentHashMap<>();

    // NganLuong service: port 1112
    private static final String NGANLUONG_HOST="http://localhost:1112";

    public NapasConnectionManager(String host,int port,GenericPackager packager)throws Exception {
        this.host=host;
        this.port=port;
        this.packager=packager;
        connect();
    }

    private void connect() throws Exception {
        socket = new Socket(host,port);
        socket.setSoTimeout(0);

        in= new DataInputStream(new BufferedInputStream(socket.getInputStream()));
        out= new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));

        System.out.println("[API2 NapasConn] connected => "+host+":"+port);
        readThread.submit(this::readLoop);
    }

    private void readLoop(){
        while(!socket.isClosed()){
            try {
                ISOMsg iso = IsoSocketUtils.readIsoMessageWithHeader(in, packager);
                if(iso==null){
                    System.out.println("[API2 NapasConn] read null => break");
                    break;
                }
                String stan= iso.hasField(11)? iso.getString(11): null;
                CompletableFuture<ISOMsg> fut = (stan==null)? null: pendingMap.remove(stan);

                if(fut!=null){
                    // response cho request outbound
                    fut.complete(iso);
                } else {
                    // inbound request => handle
                    System.out.println("[API2 NapasConn] inbound request => handleInbound");
                    handleInboundRequest(iso);
                }

            }catch(IOException|ISOException e){
                e.printStackTrace();
                break;
            }
        }
        close();
    }

    private void handleInboundRequest(ISOMsg reqIso){
        try {
            Map<String,String> jsonReq= isoToMap(reqIso);
            String f3= jsonReq.getOrDefault("F3","");
            String sub= f3.length()>=3? f3.substring(0,3): "000";
            String endpoint;
            if("432".equals(sub)){
                endpoint="/ngl/inquiry";
            } else if("912".equals(sub)){
                endpoint="/ngl/payment";
            } else {
                // invalid => 12
                ISOMsg r12= buildResponse(reqIso,"12");
                IsoSocketUtils.sendIsoMessageWithHeader(out, r12);
                return;
            }
            // call NganLuong
            String url = NGANLUONG_HOST + endpoint;
            String reqBody = JsonMapperUtil.toJson(jsonReq);
            String respBody = doHttpPost(url, reqBody);
            if(respBody==null){
                // 68
                ISOMsg r68= buildResponse(reqIso,"68");
                IsoSocketUtils.sendIsoMessageWithHeader(out, r68);
                return;
            }
            Map<String,String> jsonResp= JsonMapperUtil.fromJson(respBody);
            ISOMsg isoResp= buildIsoResponse(reqIso, jsonResp);
            IsoSocketUtils.sendIsoMessageWithHeader(out, isoResp);

        } catch(Exception e){
            e.printStackTrace();
            try {
                ISOMsg r96= buildResponse(reqIso,"96");
                IsoSocketUtils.sendIsoMessageWithHeader(out, r96);
            }catch(Exception ignore){}
        }
    }

    private ISOMsg buildIsoResponse(ISOMsg reqIso, Map<String,String> j)throws ISOException {
        ISOMsg resp= buildResponse(reqIso, j.getOrDefault("F39","96"));
        // copy fields
        for(String k: j.keySet()){
            if(k.startsWith("F")){
                try {
                    int fn=Integer.parseInt(k.substring(1));
                    if(fn!=39) resp.set(fn, j.get(k));
                }catch(Exception ignore){}
            }
        }
        return resp;
    }

    private ISOMsg buildResponse(ISOMsg reqIso, String rc)throws ISOException {
        ISOMsg r=new ISOMsg();
        r.setPackager(packager);
        int reqMti=Integer.parseInt(reqIso.getMTI());
        r.setMTI(String.format("%04d", reqMti+10));
        copyIfPresent(reqIso,r,2);
        copyIfPresent(reqIso,r,3);
        copyIfPresent(reqIso,r,4);
        copyIfPresent(reqIso,r,11);
        copyIfPresent(reqIso,r,12);
        copyIfPresent(reqIso,r,13);
        copyIfPresent(reqIso,r,32);
        copyIfPresent(reqIso,r,100);
        copyIfPresent(reqIso,r,103);

        r.set(39, rc);
        return r;
    }

    private void copyIfPresent(ISOMsg from, ISOMsg to, int f){
        try {
            if(from.hasField(f)){
                to.set(f, from.getString(f));
            }
        } catch(ISOException e){
            e.printStackTrace();
        }
    }

    private Map<String,String> isoToMap(ISOMsg iso)throws ISOException {
        Map<String,String> m=new HashMap<>();
        m.put("F1", iso.getMTI());
        for(int i=2;i<=128;i++){
            if(iso.hasField(i)){
                m.put("F"+i, iso.getString(i));
            }
        }
        return m;
    }

    private String doHttpPost(String url, String body){
        HttpURLConnection conn=null;
        try {
            URL u=new URL(url);
            conn=(HttpURLConnection)u.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type","application/json; charset=UTF-8");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            OutputStream out=conn.getOutputStream();
            out.write(body.getBytes("UTF-8"));
            out.flush();
            out.close();

            ByteArrayOutputStream baos=new ByteArrayOutputStream();
            InputStream in=conn.getInputStream();
            byte[] buf=new byte[4096];
            int len;
            while((len=in.read(buf))!=-1){
                baos.write(buf,0,len);
            }
            in.close();
            return baos.toString("UTF-8");
        } catch(Exception e){
            e.printStackTrace();
            return null;
        } finally {
            if(conn!=null) conn.disconnect();
        }
    }

    public ISOMsg sendAndWait(ISOMsg iso, long timeoutMs)throws ISOException, IOException {
        if(!iso.hasField(11)){
            throw new ISOException("[API2 NapasConn] missing F11 => can't wait");
        }
        String stan= iso.getString(11);
        CompletableFuture<ISOMsg> fut=new CompletableFuture<>();
        pendingMap.put(stan,fut);

        IsoSocketUtils.sendIsoMessageWithHeader(out, iso);

        if(timeoutMs>0){
            ScheduledExecutorService sch=Executors.newSingleThreadScheduledExecutor();
            sch.schedule(()->{
                if(!fut.isDone()) fut.complete(null);
                sch.shutdown();
            },timeoutMs,TimeUnit.MILLISECONDS);
        }
        try {
            return fut.get(timeoutMs+500, TimeUnit.MILLISECONDS);
        } catch(Exception e){
            return null;
        } finally{
            pendingMap.remove(stan);
        }
    }

    private void close(){
        try{
            if(socket!=null && !socket.isClosed()){
                socket.close();
            }
        }catch(Exception e){
            e.printStackTrace();
        }
        readThread.shutdownNow();
    }
}
