package com.lukaswhite.pos.server;

import com.lukaswhite.pos.common.ProductSpec;

import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Handles one connected Client for as long as it stays connected,
 * answering item-lookup requests against the SQL database.
 *
 * This is almost identical to the original homework version. The one
 * addition is the activeClients counter: incrementing it when a client
 * connects and decrementing it when they disconnect is how this
 * ServerNode knows its own current "load", which it then reports to the
 * LoadBalancer in its heartbeats (see ServerNode.registerAndSendHeartbeats).
 */
public class ClientHandler extends Thread {

    private final Socket clientSocket;
    private final Connection connection;
    private final AtomicInteger activeClients;

    public ClientHandler(Socket clientSocket, Connection connection, AtomicInteger activeClients) {
        this.clientSocket = clientSocket;
        this.connection = connection;
        this.activeClients = activeClients;
    }

    @Override
    public void run() {
        activeClients.incrementAndGet();
        try (ObjectInputStream in = new ObjectInputStream(clientSocket.getInputStream());
             ObjectOutputStream out = new ObjectOutputStream(clientSocket.getOutputStream())) {

            // Keep serving requests from this one client until they
            // disconnect (matches the original "one persistent connection
            // per client, many item lookups over it" design).
            while (!clientSocket.isClosed()) {
                try {
                    String itemCode = (String) in.readObject();
                    int quantity = (Integer) in.readObject();

                    System.out.println("[client-handler] Received item code: " + itemCode
                            + " and quantity: " + quantity);

                    String query = "SELECT * FROM items WHERE item_code = ?";
                    try (PreparedStatement stmt = connection.prepareStatement(query)) {
                        stmt.setString(1, itemCode);
                        ResultSet rs = stmt.executeQuery();

                        if (rs.next()) {
                            String name = rs.getString("name");
                            String description = rs.getString("description");
                            double price = rs.getDouble("price");

                            ProductSpec product = new ProductSpec(itemCode, name, description, price);
                            out.writeObject(product);
                            out.flush();
                            System.out.println("[client-handler] Sent product to client: " + product);
                        } else {
                            out.writeObject("Invalid item code.");
                            out.flush();
                            System.out.println("[client-handler] No product found for item code: " + itemCode);
                        }
                    }
                } catch (SocketException e) {
                    System.err.println("[client-handler] Client connection reset: " + e.getMessage());
                    break;
                } catch (EOFException e) {
                    System.out.println("[client-handler] Client disconnected.");
                    break;
                } catch (SQLException e) {
                    System.err.println("[client-handler] Database error: " + e.getMessage());
                    out.writeObject("Database error.");
                    out.flush();
                } catch (ClassNotFoundException | IOException e) {
                    System.err.println("[client-handler] Error handling request: " + e.getMessage());
                    break;
                }
            }
        } catch (IOException e) {
            System.err.println("[client-handler] I/O error setting up streams: " + e.getMessage());
        } finally {
            // However this client leaves (cleanly or not), make sure they
            // stop counting toward this node's reported load.
            activeClients.decrementAndGet();
            try {
                if (!clientSocket.isClosed()) {
                    clientSocket.close();
                    System.out.println("[client-handler] Closed client socket.");
                }
            } catch (IOException e) {
                System.err.println("[client-handler] Error closing client socket: " + e.getMessage());
            }
        }
    }
}
