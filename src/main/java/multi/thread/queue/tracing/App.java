package multi.thread.queue.tracing;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import com.dynatrace.oneagent.sdk.*;
import com.dynatrace.oneagent.sdk.api.IncomingMessageProcessTracer;
import com.dynatrace.oneagent.sdk.api.OneAgentSDK;
import com.dynatrace.oneagent.sdk.api.OutgoingMessageTracer;
import com.dynatrace.oneagent.sdk.api.enums.ChannelType;
import com.dynatrace.oneagent.sdk.api.enums.MessageDestinationType;
import com.dynatrace.oneagent.sdk.api.infos.MessagingSystemInfo;

@SpringBootApplication
@RestController
public class App 
{   
    // Create Queue
    public static BlockingQueue<String> queue = new LinkedBlockingQueue<>();
    // OneAgent Setup
    public static OneAgentSDK oneAgentSDK = OneAgentSDKFactory.createInstance();
    public static MessagingSystemInfo messagingSystemInfo = oneAgentSDK.createMessagingSystemInfo("java.util.Queue", "multi-thread-queue", MessageDestinationType.QUEUE, ChannelType.IN_PROCESS, "java.util.Queue");
    public static void main( String[] args )
    {
        try {
            SpringApplication.run(App.class, args);
        }
        catch (Exception e) {
            e.printStackTrace();
        }    
    }

    @GetMapping("/sendMessage")
    public void sendMessage() {
        try {
            // Create Tracer for message placed on Java Queue and begin tracing
            OutgoingMessageTracer outgoingMessageTracer = oneAgentSDK.traceOutgoingMessage(messagingSystemInfo);
            outgoingMessageTracer.start();

            // transport the dynatrace tag along with the message: 	
            String msg = outgoingMessageTracer.getDynatraceStringTag();

            // application code
            queue.put(msg);
            System.out.println("Produced: " + msg);

            // end tracing
            outgoingMessageTracer.end();
            
            // Consumer thread
            Thread consumer = new Thread(() -> {
                try {
                        // Receive message
                        String rmsg = queue.take();

                        // Create tracer for message processing and use dynatrace tag from message for stitching
                        IncomingMessageProcessTracer incomingMessageProcessTracer = oneAgentSDK.traceIncomingMessageProcess(messagingSystemInfo);
                        incomingMessageProcessTracer.setDynatraceStringTag(rmsg);

                        // Start tracing processing
                        incomingMessageProcessTracer.start();

                        // application code
                        System.out.println("Consumed: " + rmsg);

                        // outbound call
                        RestClient client = RestClient.create();
                        String result = client.get().uri("http://localhost:8080/outbound").retrieve().body(String.class);
                        System.out.println(result);
                        // end tracing
                        incomingMessageProcessTracer.end();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            consumer.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    } 
    @GetMapping("/outbound")
    public String receiveMessage() {
        return "success";
    }   
}