package API2.controller;


import org.jpos.iso.ISOException;
import org.jpos.iso.ISOMsg;
import org.jpos.iso.packager.ISO87APackager;
import java.util.HashMap;
import java.util.Map;

public class Api2JsonIsoConverter {

    public static ISOMsg buildIsoForInquiry(Map<String, String> body) {
        return buildIso(body, "432020");
    }
    
    public static ISOMsg buildIsoForPayment(Map<String, String> body) {
        return buildIso(body, "912020");
    }
    
    private static ISOMsg buildIso(Map<String, String> b, String defPC) {
        try {
            ISOMsg iso = new ISOMsg();
            iso.setPackager(new ISO87APackager());
            iso.setMTI("0200");
            if(b.containsKey("sourcePan"))
                iso.set(2, b.get("sourcePan"));
            iso.set(3, b.containsKey("processingCode") ? b.get("processingCode") : defPC);
            if(b.containsKey("amount")){
                long amt = Long.parseLong(b.get("amount"));
                iso.set(4, String.format("%012d", amt));
            }
            // API2 dành cho NganLuong: F32 = "970404"
            iso.set(32, "970404");
            if(b.containsKey("f100"))
                iso.set(100, b.get("f100"));
            if(b.containsKey("destPan"))
                iso.set(103, b.get("destPan"));
            if(b.containsKey("f11"))
                iso.set(11, b.get("f11"));
            if(b.containsKey("f12"))
                iso.set(12, b.get("f12"));
            if(b.containsKey("f13"))
                iso.set(13, b.get("f13"));
            return iso;
        } catch(Exception e) {
            e.printStackTrace();
        }
        return null;
    }
    
    public static Map<String, String> parseIsoToJson(ISOMsg iso) throws ISOException {
        Map<String, String> m = new HashMap<String, String>();
        if(iso == null) {
            m.put("field39", "");
            return m;
        }
        String rc = "";
        if(iso.hasField(39)) {
            rc = iso.getString(39);
        }
        m.put("field39", rc);
        return m;
    }
}
