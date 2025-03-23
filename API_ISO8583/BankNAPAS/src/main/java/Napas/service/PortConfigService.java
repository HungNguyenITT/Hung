package Napas.service;

import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.*;
import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Service
public class PortConfigService {
    private final Map<String, Integer> portMap = new HashMap<>();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private static final String CONFIG_FILE = "portConfig.txt";

    @PostConstruct
    public void init() {
        loadPortConfig();
        new Thread(this::watchConfigChanges).start();
    }

    public Map<String,Integer> getPortMap() {
        lock.readLock().lock();
        try {
            return new HashMap<>(portMap);
        } finally {
            lock.readLock().unlock();
        }
    }

    private void loadPortConfig() {
        lock.writeLock().lock();
        try {
            portMap.clear();
            File f = new File(CONFIG_FILE);
            if(!f.exists()) {
                System.err.println("[PortConfig] file not found: " + CONFIG_FILE);
                return;
            }
            try(BufferedReader br = new BufferedReader(new FileReader(f))) {
                String line;
                while((line=br.readLine())!=null) {
                    line=line.trim();
                    if(line.isEmpty() || line.startsWith("#")) continue;
                    // 970401,1212
                    String[] parts=line.split(",");
                    if(parts.length==2) {
                        String bankCode= parts[0].trim();
                        int port = Integer.parseInt(parts[1].trim());
                        portMap.put(bankCode, port);
                    }
                }
            }
            System.out.println("[PortConfig] reloaded => " + portMap);
        } catch(IOException e) {
            e.printStackTrace();
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void watchConfigChanges() {
        long lastModified=0;
        while(true) {
            try {
                File f=new File(CONFIG_FILE);
                if(f.exists()) {
                    long lm=f.lastModified();
                    if(lm!=lastModified) {
                        lastModified=lm;
                        System.out.println("[PortConfig] file changed => reload");
                        loadPortConfig();
                    }
                }
                Thread.sleep(10000);
            } catch(Exception e) {
                e.printStackTrace();
                break;
            }
        }
    }
}
