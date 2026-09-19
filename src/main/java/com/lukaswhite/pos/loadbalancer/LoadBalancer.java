package com.lukaswhite.pos.loadbalancer;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The LoadBalancer is the "front door" of the distributed system, and the
 * one machine/IP address everything else needs to know about.
 *
 * It listens on two ports:
 *
 *   REGISTRATION_PORT (9000) - ServerNodes connect here ONCE at startup and
 *   stay connected, sending a Heartbeat every few seconds. This is how the
 *   load balancer builds and maintains its list of "who's out there and are
 *   they still alive" (see NodeRegistrationHandler).
 *
 *   CLIENT_ASSIGN_PORT (9100) - Clients connect here briefly to ask "who
 *   should I talk to?" and get back a "host:port" for the least-busy alive
 *   node (see ClientAssignmentHandler). The client then disconnects and
 *   opens a NEW connection straight to that node - the load balancer never
 *   sees the actual item-lookup / receipt traffic, only the initial
 *   handshake.
 *
 * A background daemon thread (the "heartbeat monitor") continuously checks
 * every registered node's last-heartbeat time and marks it dead if it's
 * gone quiet for too long, so we never route a client to a node that has
 * crashed or dropped off the network.
 *
 * To run several nodes across several physical machines, just start this
 * class on one machine, note its IP address, and give that IP to every
 * ServerNode and every Client as the "load balancer host".
 */
public class LoadBalancer {

    public static final int REGISTRATION_PORT = 9000;
    public static final int CLIENT_ASSIGN_PORT = 9100;

    // How long a node can go without heartbeating before we call it dead.
    private static final long HEARTBEAT_TIMEOUT_MS = 10_000;
    // How often the monitor thread sweeps the node list looking for staleness.
    private static final long MONITOR_INTERVAL_MS = 2_000;

    // Thread-safe registry of nodeId ("host:port") -> live bookkeeping.
    // ConcurrentHashMap because registration handlers (one thread per
    // node), the heartbeat monitor, and client-assignment handlers (one
    // thread per client request) all read/write this at the same time.
    private final Map<String, NodeInfo> nodes = new ConcurrentHashMap<>();

    public static void main(String[] args) throws IOException {
        new LoadBalancer().start();
    }

    public void start() throws IOException {
        System.out.println("=== Load Balancer starting ===");
        System.out.println("Registration port : " + REGISTRATION_PORT + "  (ServerNodes connect here)");
        System.out.println("Client-assign port: " + CLIENT_ASSIGN_PORT + "  (Clients connect here)");

        startHeartbeatMonitor();

        // The registration listener runs on its own thread so it can accept
        // node connections at the same time the client-assign listener
        // (below) accepts client connections - both need to run forever,
        // in parallel.
        Thread registrationThread = new Thread(this::runRegistrationListener, "registration-listener");
        registrationThread.setDaemon(true);
        registrationThread.start();

        // The client-assign listener runs on the main thread and blocks
        // here for the lifetime of the program.
        runClientAssignListener();
    }

    /** Accepts ServerNode connections and hands each one off to its own handler thread. */
    private void runRegistrationListener() {
        try (ServerSocket serverSocket = new ServerSocket(REGISTRATION_PORT)) {
            while (true) {
                Socket nodeSocket = serverSocket.accept();
                Thread handlerThread = new Thread(
                        new NodeRegistrationHandler(nodeSocket, nodes),
                        "node-handler-" + nodeSocket.getPort());
                handlerThread.start();
            }
        } catch (IOException e) {
            System.err.println("[registration-listener] Crashed: " + e.getMessage());
        }
    }

    /** Accepts client "assign me a server" requests, one short-lived thread each. */
    private void runClientAssignListener() throws IOException {
        try (ServerSocket serverSocket = new ServerSocket(CLIENT_ASSIGN_PORT)) {
            while (true) {
                Socket clientSocket = serverSocket.accept();
                // Each request is tiny (one string in, one string out), so a
                // brand-new thread per request is simple and fine here -
                // no need for a pool the way ServerNode uses one for
                // longer-lived item-lookup connections.
                Thread handlerThread = new Thread(
                        new ClientAssignmentHandler(clientSocket, nodes),
                        "client-assign-" + clientSocket.getPort());
                handlerThread.start();
            }
        }
    }

    /**
     * Background loop: every MONITOR_INTERVAL_MS, check every known node's
     * last-heartbeat timestamp and mark it dead if it's gone stale. This is
     * what catches a node that crashed hard (power loss, kill -9, cable
     * unplugged) without ever cleanly closing its socket.
     */
    private void startHeartbeatMonitor() {
        Thread monitor = new Thread(() -> {
            while (true) {
                sleepQuietly(MONITOR_INTERVAL_MS);
                long now = System.currentTimeMillis();
                for (NodeInfo info : nodes.values()) {
                    boolean stale = (now - info.getLastHeartbeatMillis()) > HEARTBEAT_TIMEOUT_MS;
                    if (stale && info.isAlive()) {
                        info.markDead();
                        System.out.println("[heartbeat-monitor] Node timed out, marking dead: " + info);
                    }
                }
            }
        }, "heartbeat-monitor");
        monitor.setDaemon(true);
        monitor.start();
    }

    private static void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
