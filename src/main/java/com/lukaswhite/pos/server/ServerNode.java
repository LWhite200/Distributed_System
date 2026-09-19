package com.lukaswhite.pos.server;

import com.lukaswhite.pos.common.Heartbeat;
import com.lukaswhite.pos.common.Registration;
import com.lukaswhite.pos.loadbalancer.LoadBalancer;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.Connection;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * One worker node in the distributed system. This replaces (and absorbs)
 * both the original single-server "Server.java" from the SQL/GUI homework
 * AND the separate "HeartbeatClient/HeartbeatServer" heartbeat demo -
 * heartbeating is now just one more thing each node does automatically.
 *
 * A running node does three things at once, on three different threads:
 *
 *   1. MAIN THREAD - listens on its own port for direct Client
 *      connections and answers item lookups against the SQL database
 *      (see ClientHandler, DatabaseManager). This is unchanged in spirit
 *      from the original homework server.
 *
 *   2. HEARTBEAT-SENDER THREAD - connects once to the LoadBalancer,
 *      sends a Registration, then loops forever sending a Heartbeat every
 *      few seconds so the load balancer knows this node is alive and how
 *      busy it is. If that connection drops, it keeps retrying so the
 *      node re-registers automatically after a load-balancer restart or a
 *      network blip.
 *
 *   3. Every individual ClientHandler is ALSO its own thread (one per
 *      connected client) - that part is inherited straight from the
 *      original homework design.
 *
 * Usage:
 *   java ServerNode &lt;loadBalancerHost&gt; [clientPort] [advertisedHost]
 *
 *   loadBalancerHost - REQUIRED. IP/hostname of the machine running LoadBalancer.
 *   clientPort       - optional, default 8001. The port THIS node listens on
 *                       for clients. Give each node a different port if you
 *                       run more than one on the same machine.
 *   advertisedHost   - optional. The IP/hostname OTHER machines should use to
 *                       reach THIS node. Auto-detected via
 *                       InetAddress.getLocalHost() if omitted - override this
 *                       explicitly if auto-detection picks the wrong network
 *                       interface (common on machines with multiple NICs, or
 *                       behind certain kinds of NAT/VPN setups).
 */
public class ServerNode {

    // How often we tell the load balancer we're still alive and how busy we are.
    private static final long HEARTBEAT_INTERVAL_MS = 4000;
    // How long to wait before retrying if we lose the load balancer connection.
    private static final long RECONNECT_DELAY_MS = 3000;

    private final String loadBalancerHost;
    private final int clientPort;
    private final String advertisedHost;

    // Shared between the main accept-loop thread (which spawns
    // ClientHandlers) and the heartbeat-sender thread (which reads it to
    // fill in each Heartbeat). AtomicInteger makes that safe without a lock.
    private final AtomicInteger activeClients = new AtomicInteger(0);

    public ServerNode(String loadBalancerHost, int clientPort, String advertisedHost) {
        this.loadBalancerHost = loadBalancerHost;
        this.clientPort = clientPort;
        this.advertisedHost = advertisedHost;
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.out.println("Usage: java ServerNode <loadBalancerHost> [clientPort] [advertisedHost]");
            System.out.println("Example: java ServerNode 192.168.1.10 8001");
            return;
        }

        String loadBalancerHost = args[0];
        int clientPort = args.length >= 2 ? Integer.parseInt(args[1]) : 8001;
        String advertisedHost = args.length >= 3 ? args[2] : InetAddress.getLocalHost().getHostAddress();

        System.out.println("[server-node] Advertising myself to the load balancer as "
                + advertisedHost + ":" + clientPort);

        new ServerNode(loadBalancerHost, clientPort, advertisedHost).start();
    }

    public void start() throws Exception {
        DatabaseManager db = new DatabaseManager();
        Connection connection = db.getConnection();

        // The heartbeat sender holds its own socket open for the entire
        // lifetime of the program, so it must run on a background thread -
        // it must never block the main thread from accepting clients.
        Thread heartbeatThread = new Thread(this::registerAndSendHeartbeats, "heartbeat-sender");
        heartbeatThread.setDaemon(true);
        heartbeatThread.start();

        // Main thread: accept Clients directly and hand each one to its
        // own ClientHandler thread - same shape as the original
        // single-server homework version.
        try (ServerSocket serverSocket = new ServerSocket(clientPort)) {
            System.out.println("[server-node] Listening for clients on port " + clientPort);
            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("[server-node] Client connected: " + clientSocket.getInetAddress());
                new ClientHandler(clientSocket, connection, activeClients).start();
            }
        }
    }

    /**
     * Connects to the LoadBalancer, sends one Registration, then loops
     * forever sending a Heartbeat every HEARTBEAT_INTERVAL_MS on that same
     * connection. If the connection ever drops (load balancer restarted,
     * network blip, etc.), we wait a few seconds and reconnect/re-register
     * from scratch automatically - no manual restart needed on this node.
     */
    private void registerAndSendHeartbeats() {
        while (true) {
            try (Socket socket = new Socket(loadBalancerHost, LoadBalancer.REGISTRATION_PORT);
                 ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream())) {

                out.writeObject(new Registration(advertisedHost, clientPort));
                out.flush();
                System.out.println("[heartbeat-sender] Registered with load balancer at "
                        + loadBalancerHost + ":" + LoadBalancer.REGISTRATION_PORT);

                while (true) {
                    out.writeObject(new Heartbeat(activeClients.get()));
                    out.flush();
                    Thread.sleep(HEARTBEAT_INTERVAL_MS);
                }

            } catch (IOException e) {
                System.err.println("[heartbeat-sender] Lost connection to load balancer ("
                        + e.getMessage() + ") - retrying in " + (RECONNECT_DELAY_MS / 1000) + "s");
                sleepQuietly(RECONNECT_DELAY_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private static void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }
}
