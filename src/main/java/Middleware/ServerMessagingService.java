package Middleware;

import Middleware.CausalOrder.CausalOrderHandler;
import Middleware.Logging.Logger;

import Middleware.Marshalling.MessageRecovery;
import io.atomix.cluster.messaging.ManagedMessagingService;
import io.atomix.cluster.messaging.MessagingConfig;
import io.atomix.cluster.messaging.impl.NettyMessagingService;
import io.atomix.utils.net.Address;
import io.atomix.utils.serializer.Serializer;
import org.apache.commons.math3.analysis.function.Add;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ServerMessagingService {
    private int id;
    private CausalOrderHandler coh;
    private ScheduledExecutorService ses;
    private ExecutorService e;
    private ManagedMessagingService mms;
    private Serializer s;
    private List<Address> participants;

    public ServerMessagingService(int id, Address address, List<Address> participants, Logger log, Serializer s){
        this.id = id;
        //TODO passar executor para fora?
        this.ses = Executors.newScheduledThreadPool(1);
        this.e = Executors.newFixedThreadPool(1);
        this.mms = new NettyMessagingService(
                "server",
                address,
                new MessagingConfig());
        this.participants = new ArrayList<>();
        this.s = s;
        int pSize = participants.size();
        //não contém ele próprio
        for(int i = 0; i<pSize; i++){
            if(i==id) continue;
            this.participants.add(participants.get(i));
        }
        this.coh = new CausalOrderHandler(id, pSize, s, log);
    }

    public void start(){
        mms.start();
        registerOperation("causalOrderRecovery", (a,b)->{
            System.out.println("recovery:handler -> Received request from: " + a);
            boolean state = coh.treatRecoveryRequest(decode(b),
                    msg2 -> sendAsync(a, msg2.getOperation(), msg2));
            return encode(state);
        });
    }

    public <T> void registerOperation(String type, Consumer<T> callback){
        mms.registerHandler(type, (a,b) -> {callback.accept(s.decode(b));}, e);
    }

    public void registerCompletableOperation(String type, BiFunction<Address, byte[], CompletableFuture<byte[]>> callback){
        mms.registerHandler(type, callback);

    }

    public void registerOperation(String type, BiConsumer<Address,byte[]> callback){
        mms.registerHandler(type, callback, e);
    }

    public void registerOperation(String type, BiFunction<Address, byte[], byte[]> callback){
        mms.registerHandler(type, callback, e);
    }

    public void registerOrderedOperation(String name, Consumer<Object> callback){
        mms.registerHandler(name, (a,b) -> {
            coh.read(b, callback);
        },e);
    }

    //do participante para o manager
    public CompletableFuture<byte[]> sendToManager(Address a, String type, Object content, int seconds){
        System.out.println("sms:sendToManager -> type == " + type);
        return mms.sendAndReceive(a, type, s.encode(content), Duration.ofSeconds(10), e)
                .handle((m,t)->{
                    if(t!=null){
                        t.printStackTrace();
                        //para debug
                        System.out.println("Server is down try again later");
                        return null;
                    }
                    else
                        return m;
                });
    }
    //Manager para os servidores
    // sendV2("firstphase", content, senconds, (obj,cf) -> {if first phase ready cf.complete}).thenApply(sendV2("secondPhase",)
    public CompletableFuture<Void> sendAndReceiveToCluster(String type, Object content, int seconds, Consumer<Object> callback){
        System.out.println("sms:sendAndReceiveLoopToCluster -> type == " + type);
        List<CompletableFuture<Void>> requests = new ArrayList<>();
        for (Address a : participants){
            requests.add(sendAndReceiveLoop(a, type, content, seconds)
                    .thenAccept(x -> callback.accept(s.decode(x))));
        }
        return CompletableFuture.allOf(requests.toArray(new CompletableFuture[0]));
    }


    public CompletableFuture<Void> sendAndReceiveToCluster(String type, Object content, int seconds){
        System.out.println("sms:sendAndReceiveLoopToCluster -> type == " + type);
        List<CompletableFuture<byte[]>> requests = new ArrayList<>();
        for (Address a : participants)
            requests.add(sendAndReceiveLoop(a, type, content, seconds));
        return CompletableFuture.allOf(requests.toArray(new CompletableFuture[0]));
    }



    public CompletableFuture<byte[]> sendAndReceiveLoop(Address a, String type, Object content, int seconds){
        CompletableFuture<byte[]> cf = new CompletableFuture<>();
        ScheduledFuture<?> scheduledFuture = ses.scheduleAtFixedRate(()->
                mms.sendAndReceive(a, type, s.encode(content), e)
                        .whenComplete((m,t) -> {
                            if(t!=null){
                                System.out.println("timeout");
                            }
                            else{
                                //System.out.println("completing future message " + s.decode(m).toString());
                                cf.complete(m);
                            }}), 0, seconds, TimeUnit.SECONDS);
        cf.whenComplete((m,t) -> scheduledFuture.cancel(true));
        return cf;
    }


    //TODO pq não void?
    public CompletableFuture<Void> sendAsyncToCluster(String type, Object content) {
        System.out.println("sms:sendAsyncToCluster -> type == " + type);
        for (Address a : participants){
            mms.sendAsync(a, type, s.encode(content));
        }
        return CompletableFuture.completedFuture(null);
    }

    public <T> CompletableFuture<T> sendAndReceive(Address a, String type, Object content){
        return mms.sendAndReceive(a, type, s.encode(content),e)
                    .thenApply(b -> s.decode(b));
    }

    public <T> CompletableFuture<Void> sendAsync(Address a, String type, T content){
        return mms.sendAsync(a,type,s.encode(content));
    }

    public CompletableFuture<Void> sendCausalOrderAsyncToCluster(String type, Object content) {
        System.out.println("sms:sendCausalOrderAsyncToCluster ->");
        byte[] toSend = coh.createMsg(content, type);
        for (Address a : participants){
            mms.sendAsync(a, type, toSend);
        }
        //TODO por ao fim de tudo allOf()...talvez
        coh.logAndSaveNonAckedOperation(toSend);
        return CompletableFuture.completedFuture(null);
    }

    public CompletableFuture<Void> sendAndReceiveForRecovery(Duration timout){
        //List<CompletableFuture<Void>> cfs = new ArrayList<>();
        List<Integer> vector = coh.getVector();
        //TODO resolver
        System.out.println("sms:sendAndReceiveForRecovery ->");
        int i = 0;
        for(Address a : participants){
            MessageRecovery mr = new MessageRecovery(id, vector.get(i));
            mms.sendAndReceive(a, "causalOrderRecovery", s.encode(mr), timout, e)
                    .thenAccept(b -> System.out.println("sms:sendAndReceiveForRecovery -> " + (boolean)s.decode(b) + " by " + a));
            i++;
        }
        return CompletableFuture.completedFuture(null);
    }

    public void causalOrderRecover(Object msg, Consumer<Object> callback){
        coh.recoveryRead(encode(msg), callback);
    }

    public <T> byte[] encode(T object){
        return s.encode(object);
    }

    public <T> T decode(byte[] bytes){
        return s.decode(bytes);
    }

    //DEBUG
    public void send(Address address, Object msg, String type){
        System.out.println("sms:send ->");
        mms.sendAsync(address, type, s.encode(msg));
    }

}
