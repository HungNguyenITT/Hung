package socketserver.socket;


import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;

public class HttpUtil {

    public static byte[] postIso(String urlStr, byte[] isoData) throws IOException {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type","application/octet-stream");
        conn.connect();

        OutputStream out = null;
        try {
            out = conn.getOutputStream(); // conn đã được cấu hình POST và DoOutPut = true nên có thể đẩy dữ liệu 
            out.write(isoData);
        } finally {
            if(out!=null) out.close();
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        InputStream in = null;
        try {
            in = conn.getInputStream();
            byte[] buf = new byte[4096];
            int len;
            while((len = in.read(buf))!=-1){
                baos.write(buf,0,len);
            }
        } finally {
            if(in!=null) in.close();
        }

        return baos.toByteArray();
    }
}

