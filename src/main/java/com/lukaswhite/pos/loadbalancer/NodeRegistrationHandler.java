package com.lukaswhite.pos.loadbalancer;

import com.lukaswhite.pos.common.Heartbeat;
import com.lukaswhite.pos.common.Registration;

import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.net.Socket;
import java.util.Map;

/**
 * Handles a single ServerNode's registration connection, end to end.
 *
 * A node opens exactly one socket to the load balancer and keeps it open
 * for as long as it's running. The very first object it sends is a
 * Registration (its address). After that, every object it sends is a
 * Heartbeat, roughly every 4 seconds (see ServerNode.HEARTBEAT_INTERVAL_MS).
 *
 * We call socket.setSoTimeout(...) so that in.readObject() will throw
 * SocketTimeoutException if we go too long without hearing from the node -
 * that's how we detect a node that has silently crashed or lost its
 * network connection, as opposed to one that closed the socket cleanly
 * (which throws EOFException instead). Either way, we mark the node dead.
 */
public class NodeRegistrationHandler implements Runnable {

    // If we don't hear ANYTHING (registration or heartbeat) within this
    // window, we give up waiting and treat the node as gone. Comfortably
    // longer than the node's own heartbeat interval so we don't flag a
    // healthy node dead just because of a little network jitter.
    private static final int SOCKET_TIMEOUT_MS = 10_000;

    private final Socket socket;
    private final Map<String, NodeInfo> nodes;

    public NodeRegistrationHandler(Socket socket, Map<String, NodeInfo> nodes) {
        this.socket = socket;
        this.nodes = nodes;
    }

    @Override
    public void run() {
        NodeInfo info = null;
        try {
            socket.setSoTimeout(SOCKET_TIMEOUT_MS);
            ObjectInputStream in = new ObjectInputStream(socket.getInputStream());

            // First message on a fresh connection MUST be the Registration.
            Object first = in.readObject();
            if (!(first instanceof Registration reg)) {
                System.err.println("[node-handler] Expected a Registration first, got: " + first);
                return;
            }

            info = new NodeInfo(reg.getHost(), reg.getPort());
            nodes.put(info.getId(), info);
            System.out.println("[node-handler] Registered new node: " + info.getId());

            // From here on it's just a stream of Heartbeats until the node
            // disconnects or goes quiet.
            while (true) {
                Object message = in.readObject();
                if (message instanceof Heartbeat heartbeat) {
                    info.setLoad(heartbeat.getCurrentLoad());
                    info.touch();
                }
            }

        } catch (IOException | ClassNotFoundException e) {
            // Covers: clean disconnect (EOFException), silent timeout
            // (SocketTimeoutException, a subclass of IOException), or any
            // other network hiccup. All of them mean "stop trusting this node".
            String label = info != null ? info.getId() : "unregistered node";
            String reason = (e instanceof EOFException) ? "disconnected" : e.getMessage();
            System.out.println("[node-handler] " + label + " - " + reason);
        } finally {
            if (info != null) {
                info.markDead();
            }
            try {
                socket.close();
            } catch (IOException ignored) {
                // Nothing useful to do if closing the socket itself fails.
            }
        }
    }
}
