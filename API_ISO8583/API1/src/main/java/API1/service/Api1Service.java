package API1.service;

import org.jpos.iso.ISOMsg;
import org.jpos.iso.packager.GenericPackager;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

@Service
public class Api1Service {
    private GenericPackager packager;
    private NapasConnectionManager napasConn;

    @PostConstruct
    public void init(){
        try {
            InputStream is = getClass().getResourceAsStream("/iso87binary.xml");
            if(is==null){
                throw new RuntimeException("iso87binary.xml not found for API1!");
            }
            packager = new GenericPackager(is);
            System.out.println("[Api1Service] loaded iso87binary.xml");

            napasConn = new NapasConnectionManager("localhost",1414, packager);
            System.out.println("[Api1Service] NapasConnection => 1414");
        } catch(Exception e){
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public Map<String,String> handleInquiry(Map<String,String> json) throws Exception {
        System.out.println("[API1Service] handleInquiry => "+ json);
        Map<String,String> isoReq= prepareIsoRequest(json,"432020","970403");
        ISOMsg isoMsg= mapToIso(isoReq);
        // chờ 20 giây
        ISOMsg isoResp= napasConn.sendAndWait(isoMsg, 20000);

        return isoToMap(isoResp);
    }

    public Map<String,String> handlePayment(Map<String,String> json) throws Exception {
        System.out.println("[API1Service] handlePayment => "+ json);
        Map<String,String> isoReq= prepareIsoRequest(json,"912020","970403");
        ISOMsg isoMsg= mapToIso(isoReq);
        // chờ 20 giây
        ISOMsg isoResp= napasConn.sendAndWait(isoMsg, 20000);

        return isoToMap(isoResp);
    }

    private Map<String,String> prepareIsoRequest(Map<String,String> j, String defPC, String fixF32){
        Map<String,String> isoReq= new HashMap<>();
        for(Map.Entry<String,String> e: j.entrySet()){
            isoReq.put(e.getKey().toUpperCase(), e.getValue());
        }
        if(!isoReq.containsKey("F1")) isoReq.put("F1","0200");
        if(!isoReq.containsKey("F3")) isoReq.put("F3", defPC);

        if(!isoReq.containsKey("F4")){
            isoReq.put("F4","000000000000");
        } else {
            try {
                long amt= Long.parseLong(isoReq.get("F4"));
                isoReq.put("F4", String.format("%012d", amt));
            }catch(Exception e){}
        }
        isoReq.put("F32", fixF32);
        if(!isoReq.containsKey("F100")) isoReq.put("F100","");
        if(!isoReq.containsKey("F103") && isoReq.containsKey("DESTPAN")){
            isoReq.put("F103", isoReq.get("DESTPAN"));
        }
        if(!isoReq.containsKey("F11")){
            isoReq.put("F11", String.format("%06d",(int)(Math.random()*1000000)));
        }
        if(!isoReq.containsKey("F12")){
            isoReq.put("F12", new java.text.SimpleDateFormat("HHmmss").format(new java.util.Date()));
        }
        if(!isoReq.containsKey("F13")){
            isoReq.put("F13", new java.text.SimpleDateFormat("MMdd").format(new java.util.Date()));
        }
        return isoReq;
    }

    private ISOMsg mapToIso(Map<String,String> isoReq)throws Exception {
        ISOMsg iso=new ISOMsg();
        iso.setPackager(packager);
        for(String k: isoReq.keySet()){
            if("F1".equals(k)){
                iso.setMTI(isoReq.get(k));
            } else if(k.startsWith("F")){
                try{
                    int fn=Integer.parseInt(k.substring(1));
                    iso.set(fn, isoReq.get(k));
                }catch(Exception ignore){}
            }
        }
        return iso;
    }

    private Map<String,String> isoToMap(ISOMsg iso)throws Exception {
        Map<String,String> m= new HashMap<>();
        if(iso==null){
            m.put("F39","96");
            return m;
        }
        m.put("F1", iso.getMTI());
        for(int i=1;i<=128;i++){
            if(iso.hasField(i)) {
                m.put("F"+i, iso.getString(i));
            }
        }
        return m;
    }
}
