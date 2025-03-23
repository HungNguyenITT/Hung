package Napas.server;

import Napas.entity.NapasMessage;
import Napas.repository.NapasMessageRepository;
import Napas.service.PortConfigService;
import org.jpos.iso.ISOException;
import org.jpos.iso.ISOMsg;
import org.jpos.iso.packager.GenericPackager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import utils.IsoSocketUtils;
import utils.LogHelper;

import javax.annotation.PostConstruct;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.*;

@Component
public class SocketServer implements Runnable {

    @Autowired
    private PortConfigService portConfig;

    @Autowired
    private NapasMessageRepository repo;

    private final Map<String, BankConnectionHandler> bankHandlers = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final Map<String, ServerSocket> serverSockets = new ConcurrentHashMap<>();
    private boolean running = true;

    private GenericPackager napasPackager;

    @PostConstruct
    public void init() {
        try {
            InputStream is = getClass().getResourceAsStream("/iso87binary.xml");
            if (is == null) {
                throw new RuntimeException("iso87binary.xml not found in Napas resources!");
            }
            napasPackager = new GenericPackager(is);
            System.out.println("[SocketServer] Napas packager loaded => iso87binary.xml");
        } catch (Exception e) {
            throw new RuntimeException("Failed loading iso87binary.xml for Napas", e);
        }
        new Thread(this).start();
    }

    @Override
    public void run() {
        while (running) {
            try {
                refreshListeningPorts();
                Thread.sleep(15000);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void refreshListeningPorts() {
        Map<String, Integer> pm = portConfig.getPortMap();

        // đóng cũ nếu config thay đổi
        for (Map.Entry<String, ServerSocket> e : serverSockets.entrySet()) {
            String bankCode = e.getKey();
            ServerSocket ss = e.getValue();
            Integer newPort = pm.get(bankCode);
            if (newPort == null || ss.getLocalPort() != newPort) {
                System.out.println("[Napas] Closing old server for bank=" + bankCode);
                try { ss.close(); } catch(Exception ignore){}
                serverSockets.remove(bankCode);
                BankConnectionHandler h = bankHandlers.remove(bankCode);
                if(h != null) h.close();
            }
        }

        // khởi tạo mới nếu cần
        for (Map.Entry<String, Integer> e : pm.entrySet()) {
            String bankCode = e.getKey();
            int port = e.getValue();
            if (!serverSockets.containsKey(bankCode)) {
                executor.submit(() -> startServerForBank(bankCode, port));
            }
        }
    }

    private void startServerForBank(String bankCode, int port) {
        try (ServerSocket ss = new ServerSocket(port)) {
            serverSockets.put(bankCode, ss);
            System.out.println("[Napas] Listening on port="+ port +" for bank="+ bankCode);

            while (!ss.isClosed()) {
                Socket client = ss.accept();
                System.out.println("[Napas] Bank="+ bankCode +" connected from " + client.getRemoteSocketAddress());
                BankConnectionHandler handler = new BankConnectionHandler(client, bankCode, this, napasPackager);
                bankHandlers.put(bankCode, handler);
            }
        } catch(Exception e){
            System.err.println("[Napas] Error bank="+ bankCode +", port="+ port +": "+ e.getMessage());
        }
    }

    /**
     * Napas nhận inbound ISO => forward or respond. 
     * Không trả f39=68 ngay cả khi bank đích offline, 
     * mà chờ 15s => build F39=68 => gửi bank gửi.
     */
    public void onInboundMessage(ISOMsg iso, String fromBank) {
        try {
            System.out.println("[SocketServer] inbound from bank=" + fromBank);
            LogHelper.logToBothBanks(iso, "[Napas] inbound bank="+ fromBank);
            NapasMessage reqEntity = createEntity("REQUEST", iso);
            repo.save(reqEntity);

            // Tạo 1 CompletableFuture chờ 15s => nếu bank đích không trả => F39=68
            CompletableFuture<ISOMsg> waitFut = new CompletableFuture<>();

            // Bắt đầu schedule 15s => sau đó complete => 
            ScheduledExecutorService sched = Executors.newSingleThreadScheduledExecutor();
            sched.schedule(() -> {
                // nếu chưa complete => build 68 => respond
                if(!waitFut.isDone()) {
                    waitFut.complete(null); // => null => ta handle => build f39=68
                }
                sched.shutdown();
            }, 15, TimeUnit.SECONDS);

            // Tạo task forward
            forwardToDest(iso, waitFut);

            // chờ waitFut => khi complete => check null => build 68
            ISOMsg resp = waitFut.get(); 
            if(resp == null) {
                // => time out => build f39=68
                ISOMsg timeoutResp = buildTimeoutResponse(iso, "68");
                respond(timeoutResp, fromBank);
            } else {
                // => forward actual response
                respond(resp, fromBank);
            }
        } catch(Exception e){
            System.err.println("[SocketServer] onInboundMessage error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Gửi iso tới bank đích (nếu có), bank đích reply => complete waitFut
     * Nếu bank đích offline / F100 null => ta "cũng" chờ 15s => 
     * (lúc schedule xong => complete(null) => build 68).
     */
    private void forwardToDest(ISOMsg iso, CompletableFuture<ISOMsg> waitFut){
        try {
            String targetBank = iso.hasField(100) ? iso.getString(100) : null;
            if(targetBank == null){
                // => KHÔNG respond ngay => chờ 15s => waitFut => null => build 68
                System.out.println("[Napas] targetBank=null => do nothing => wait 15s");
                return;
            }
            BankConnectionHandler dest = bankHandlers.get(targetBank);
            if(dest == null){
                // => bank đích offline => chờ 15s => waitFut => null => build 68
                System.out.println("[Napas] targetBank= "+ targetBank +" offline => wait 15s => 68");
                return;
            }
            // bank đích online => call dest.sendAndWaitResponse( iso ), 
            // future => complete => fulfill waitFut 
            CompletableFuture<ISOMsg> f2 = dest.sendAndWaitResponse(iso, 0); 
            // 0 => means we handle the scheduled time ourselves
            f2.whenComplete((resp, ex)->{
                // khi bank đích trả => set waitFut
                if(ex==null){
                    waitFut.complete(resp); 
                } else {
                    // bank đích exception => null => build 68
                    waitFut.complete(null);
                }
            });
        } catch(Exception e){
            e.printStackTrace();
            // => bank đích send fail => waitFut => null => build 68
            waitFut.complete(null);
        }
    }

    private void respond(ISOMsg isoResp, String toBank) throws Exception {
        if (isoResp == null) return;
        System.out.println("[SocketServer] respond => bank=" + toBank);
        LogHelper.logToBothBanks(isoResp, "[Napas] respond => bank="+ toBank);

        NapasMessage respEnt = createEntity("RESPONSE", isoResp);
        repo.save(respEnt);

        BankConnectionHandler h = bankHandlers.get(toBank);
        if (h != null) {
            h.sendMessage(isoResp);
        }
    }

    private ISOMsg buildTimeoutResponse(ISOMsg req, String rc){
        try {
            ISOMsg r = new ISOMsg();
            r.setPackager(napasPackager);
            int mti = Integer.parseInt(req.getMTI());
            r.setMTI(String.format("%04d", mti+10));
            copyIfPresent(req, r, 2);
            copyIfPresent(req, r, 3);
            copyIfPresent(req, r, 4);
            copyIfPresent(req, r, 11);
            copyIfPresent(req, r, 12);
            copyIfPresent(req, r, 13);
            copyIfPresent(req, r, 32);
            copyIfPresent(req, r, 100);
            copyIfPresent(req, r, 103);
            r.set(39, rc);
            return r;
        } catch(Exception e){
            e.printStackTrace();
            return null;
        }
    }

    private NapasMessage createEntity(String type, ISOMsg iso)throws ISOException {
        NapasMessage n= new NapasMessage();
        n.setType(type);
        n.setF1(iso.getMTI());
        if(iso.hasField(2)) n.setF2(iso.getString(2));
        if(iso.hasField(3)) n.setF3(iso.getString(3));
        if(iso.hasField(4)) n.setF4(iso.getString(4));
        if(iso.hasField(11))n.setF11(iso.getString(11));
        if(iso.hasField(12))n.setF12(iso.getString(12));
        if(iso.hasField(13))n.setF13(iso.getString(13));
        if(iso.hasField(32))n.setF32(iso.getString(32));
        if(iso.hasField(39))n.setF39(iso.getString(39));
        if(iso.hasField(100))n.setF100(iso.getString(100));
        if(iso.hasField(103))n.setF103(iso.getString(103));
        return n;
    }

    private void copyIfPresent(ISOMsg from, ISOMsg to, int field)throws ISOException {
        // skip field(1)=bitmap
        if(field==1) return;
        if(from.hasField(field)) {
            to.set(field, from.getString(field));
        }
    }
}
