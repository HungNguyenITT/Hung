package Napas.server;

import org.jpos.iso.ISOException;
import org.jpos.iso.ISOMsg;
import org.jpos.iso.packager.GenericPackager;
import utils.IsoSocketUtils;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.concurrent.*;

public class BankConnectionHandler {
    private final Socket socket;
    private final String bankCode;
    private final SocketServer parent;

    private final ConcurrentMap<String, CompletableFuture<ISOMsg>> pendingMap = new ConcurrentHashMap<>();
    private final ExecutorService readerThread = Executors.newSingleThreadExecutor();

    private final GenericPackager packager;

    public BankConnectionHandler(Socket s, String bankCode, SocketServer parent, GenericPackager packager) {
        this.socket = s;
        this.bankCode = bankCode;
        this.parent = parent;
        this.packager = packager;

        try {
            // persistent => indefinite read => soTimeout=0
            socket.setSoTimeout(0);
        } catch(Exception e){
            e.printStackTrace();
        }
        System.out.println("[BankConnectionHandler] Created for bank="+ bankCode);

        startReading();
    }

    private void startReading() {
        readerThread.submit(() -> {
            try(InputStream in = socket.getInputStream()) {
                while(!socket.isClosed()){
                    ISOMsg iso=null;
                    try {
                        System.out.println("[BankConnectionHandler] waiting iso from bank="+ bankCode);
                        iso = IsoSocketUtils.readIsoMessageWithHeader(in, packager);
                        System.out.println("[BankConnectionHandler] got iso from bank="+ bankCode);
                    }catch(SocketTimeoutException te){
                        // persistent => just continue
                        continue;
                    }catch(EOFException eof){
                        System.err.println("[BankConnectionHandler] bank="+ bankCode +" EOF => close");
                        break;
                    }catch(Exception e){
                        System.err.println("[BankConnectionHandler] error reading bank="+ bankCode +": "+ e.getMessage());
                        e.printStackTrace();
                        break;
                    }
                    if(iso==null) break;
                    
                    String stan = iso.hasField(11)? iso.getString(11): null;
                    CompletableFuture<ISOMsg> fut = (stan==null)? null: pendingMap.remove(stan);

                    if(fut!=null) {
                        // response to pending
                        System.out.println("[BankConnectionHandler] response STAN="+ stan +" from bank="+ bankCode);
                        fut.complete(iso);
                    } else {
                        // inbound request => forward
                        System.out.println("[BankConnectionHandler] new request from bank="+ bankCode);
                        parent.onInboundMessage(iso, bankCode);
                    }
                }
            }catch(IOException e){
                e.printStackTrace();
            }finally{
                close();
            }
        });
    }

    public void close(){
        readerThread.shutdownNow();
        try{
            if(!socket.isClosed()) socket.close();
            System.out.println("[BankConnectionHandler] socket closed bank="+ bankCode);
        }catch(IOException e){
            e.printStackTrace();
        }
    }

    public void sendMessage(ISOMsg iso) throws ISOException, IOException {
        IsoSocketUtils.sendIsoMessageWithHeader(socket.getOutputStream(), iso);
    }

    public CompletableFuture<ISOMsg> sendAndWaitResponse(ISOMsg iso, long timeoutMs) throws ISOException, IOException {
        if(!iso.hasField(11)) {
            throw new ISOException("[BankConnectionHandler] no F11 => can't match response");
        }
        String stan= iso.getString(11);
        CompletableFuture<ISOMsg> fut= new CompletableFuture<>();
        pendingMap.put(stan, fut);

        sendMessage(iso);

        if(timeoutMs>0){
            // schedule timed out => complete(null)
            ScheduledExecutorService sched = Executors.newSingleThreadScheduledExecutor();
            sched.schedule(() -> {
                if(!fut.isDone()) fut.complete(null);
                sched.shutdown();
            }, timeoutMs, TimeUnit.MILLISECONDS);
        }
        return fut;
    }
}
