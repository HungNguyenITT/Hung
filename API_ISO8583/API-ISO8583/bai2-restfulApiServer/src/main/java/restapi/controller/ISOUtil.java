package restapi.controller;


import org.jpos.iso.ISOException;
import org.jpos.iso.ISOMsg;
import java.util.HashMap;
import java.util.Map;

public class ISOUtil {

    public static Map<String,Object> isoToMap(ISOMsg iso) throws ISOException {
        Map<String,Object> map = new HashMap<>();
        // Lấy MTI
        map.put("field0", iso.getMTI());
        // Lấy các field
        for(int i=1; i<=iso.getMaxField(); i++){
            if(iso.hasField(i)){
                map.put("field"+i, iso.getString(i));
            }
        }
        return map;
    }
}



