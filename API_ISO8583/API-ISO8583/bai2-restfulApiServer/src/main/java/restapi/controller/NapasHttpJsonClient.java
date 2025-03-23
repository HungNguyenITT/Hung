package restapi.controller;


import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Component
public class NapasHttpJsonClient {

    public Map<String,String> sendToBankB(Map<String,Object> jsonMap) throws IOException {
        URL url = new URL("http://localhost:8086/simulator");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type","application/json; charset=UTF-8");
        conn.connect();

        ObjectMapper mapper = new ObjectMapper();
        String body = mapper.writeValueAsString(jsonMap);

        OutputStream out = null;
        try {
            out = conn.getOutputStream();
            out.write(body.getBytes(StandardCharsets.UTF_8));
        } finally {
            if(out!=null) out.close();
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        InputStream in = null;
        try {
            in = conn.getInputStream();
            byte[] buf = new byte[4096];
            int len;
            while((len=in.read(buf))!=-1){
                baos.write(buf,0,len);
            }
        } finally {
            if(in!=null) in.close();
        }

        String respStr = new String(baos.toByteArray(),StandardCharsets.UTF_8);
        @SuppressWarnings("unchecked")
        Map<String,String> mapResp = mapper.readValue(respStr, Map.class);
        return mapResp;
    }
}
