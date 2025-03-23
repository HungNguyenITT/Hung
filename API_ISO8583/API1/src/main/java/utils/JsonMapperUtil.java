package utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;

public class JsonMapperUtil {
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    public static String toJson(Map<String, String> map) {
        try {
            return objectMapper.writeValueAsString(map);
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }
    
    public static Map<String, String> fromJson(String json) {
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}