package utils;

import org.jpos.iso.ISOMsg;

public class IsoDebugHelper {
    public static void debugIso(String label, ISOMsg iso) {
        System.out.println();
        System.out.println("=== " + label + " ===");
        try {
            System.out.println("MTI=" + iso.getMTI());
            for(int i=2; i<=128; i++){
                if(iso.hasField(i)){
                    System.out.println("F"+i+"=" + iso.getString(i));
                }
            }
        } catch(Exception e){
            e.printStackTrace();
        }
        System.out.println("==========");
        System.out.println();
    }
}
