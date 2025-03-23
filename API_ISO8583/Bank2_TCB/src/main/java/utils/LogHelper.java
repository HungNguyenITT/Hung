package utils;

import org.jpos.iso.ISOMsg;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class LogHelper {
    private static final Map<String,String> BANK_FILE = new HashMap<>();
    static {
        BANK_FILE.put("970401","VCB.log");
        BANK_FILE.put("970402","TCB.log");
        BANK_FILE.put("970403","ZaloPay.log");
        BANK_FILE.put("970404","NganLuong.log");
    }
    public static void logToBothBanks(ISOMsg iso, String direction) {
        try {
            String f32 = iso.hasField(32)? iso.getString(32): null;
            String f100= iso.hasField(100)? iso.getString(100): null;
            String fileA = f32!=null? BANK_FILE.get(f32): null;
            String fileB = f100!=null? BANK_FILE.get(f100): null;
            String time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new Date());
            StringBuilder sb = new StringBuilder();
            sb.append("[").append(time).append("] (").append(direction).append(")\n");
            sb.append("<isomsg>\n");
            for(int i=0;i<=128;i++){
                if(iso.hasField(i)){
                    sb.append("  <field id=\"").append(i).append("\" value=\"")
                      .append(iso.getString(i)).append("\"/>\n");
                }
            }
            sb.append("</isomsg>\n");
            if(fileA!=null) appendToFile(fileA, sb.toString());
            if(fileB!=null && !fileB.equals(fileA)) {
                appendToFile(fileB, sb.toString());
            }
        } catch(Exception e){
            e.printStackTrace();
        }
    }
    private static void appendToFile(String fileName, String content){
        if(fileName==null) return;
        try(FileWriter fw = new FileWriter(fileName, true);
            PrintWriter pw = new PrintWriter(fw)) {
            pw.println(content);
            pw.flush();
        } catch(IOException e){
            e.printStackTrace();
        }
    }
}

