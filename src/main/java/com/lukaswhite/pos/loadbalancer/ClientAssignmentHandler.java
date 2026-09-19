package com.lukaswhite.pos.loadbalancer;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;

/**
 * Handles one Client's "assign me a server" request.
 *
 * Protocol is deliberately tiny:
 *   client sends the String "ASSIGN"
 *   we reply with either "host:port" of the chosen node, or "NONE"
 * Then the connection closes. The client opens a brand-new, separate
 * connection directly to the chosen node for the actual item/receipt
 * traffic - the load balancer is only ever in the business of picking
 * a node, never of forwarding item requests itself.
 */
public class ClientAssignmentHandler implements Runnable {

    private final Socket socket;
    private final Map<String, NodeInfo> nodes;

    public ClientAssignmentHandler(Socket socket, Map<String, NodeInfo> nodes) {
        this.socket = socket;
        this.nodes = nodes;
    }

    @Override
    public void run() {
        try (ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
             ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream())) {

            Object request = in.readObject();
            System.out.println("[client-assign] Request from " + socket.getInetAddress() + ": " + request);

            // Node-balancing strategy: "least connections". Among nodes we
            // currently believe are alive, pick whichever is reporting the
            // fewest active clients. This naturally spreads new clients
            // across every healthy node instead of always hammering one.
            Optional<NodeInfo> chosen = nodes.values().stream()
                    .filter(NodeInfo::isAlive)
                    .min(Comparator.comparingInt(NodeInfo::getLoad));

            if (chosen.isPresent()) {
                NodeInfo node = chosen.get();
                String answer = node.getId();
                out.writeObject(answer);
                out.flush();
                System.out.println("[client-assign] Assigned client to " + answer);
            } else {
                out.writeObject("NONE");
                out.flush();
                System.out.println("[client-assign] No alive nodes registered - told client NONE");
            }

        } catch (IOException | ClassNotFoundException e) {
            System.err.println("[client-assign] Error handling client request: " + e.getMessage());
        } finally {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
    }
}
