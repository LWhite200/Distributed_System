// Homework 7: GUI + OOP + Threads + Network + Database programming
// Course: CIS 357
// Due date: August 15, 2024
// Names: Lukas A. White, Connor Oard, Noah T
// Instructor: Il-Hyung Cho
/*
    Client handler - Uses a thread to manage client requests
        then uses codes from client to handle requests
*/

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
 * Handles one connected Client for as long as it stays connected.
 *
 * The wire protocol is a simple command dispatch: the client always sends
 * a command String first, then whatever arguments that command needs, and
 * always gets exactly one response object back. One command is supported:
 *
 *   "ITEM" (String itemCode, int quantity) -> ProductSpec or String error
 *
 * Adding a new kind of request later just means adding one more case here
 * and a matching method on the Client side - the connection, threading,
 * and load-reporting machinery around it doesn't change.
 */
public class ClientHandler extends Thread {

    private final Socket clientSocket;           // The client   
    private final Connection connection;        // shared connection, used for retail item lookup. With Database
    private final AtomicInteger activeClients; // Count of clients the Servernode has

    // Client Handler Object
    public ClientHandler(Socket clientSocket, Connection connection, AtomicInteger activeClients) {
        this.clientSocket = clientSocket;
        this.connection = connection;
        this.activeClients = activeClients;
    }

    @Override
    public void run() {
        
        // We have a new client, so we increment the count.
        activeClients.incrementAndGet();

        
        try (ObjectInputStream in = new ObjectInputStream(clientSocket.getInputStream());
             ObjectOutputStream out = new ObjectOutputStream(clientSocket.getOutputStream())) {

            while (!clientSocket.isClosed()) {
                try {

                    // Wait until we receive a message from client
                    String command = (String) in.readObject();

                    // What is client trying to do
                    switch (command) {
                        case "ITEM" -> handleItemLookup(in, out);
                        default -> {
                            System.err.println("[client-handler] Unknown command: " + command);
                            out.writeObject("Unknown command: " + command);
                            out.flush();
                        }
                    }

                } catch (SocketException e) {
                    System.err.println("[client-handler] Client connection reset: " + e.getMessage());
                    break;
                } catch (EOFException e) {
                    System.out.println("[client-handler] Client disconnected.");
                    break;
                } catch (ClassNotFoundException | IOException e) {
                    System.err.println("[client-handler] Error handling request: " + e.getMessage());
                    break;
                } catch (RuntimeException e) {
                    // A malformed request shouldn't be able to kill this
                    // thread outright - log it, tell the client, and keep
                    // serving whatever it sends next.
                    System.err.println("[client-handler] Unexpected error: " + e.getMessage());
                    try {
                        out.writeObject("Server error: " + e.getMessage());
                        out.flush();
                    } catch (IOException ignored) {
                        break;
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("[client-handler] I/O error setting up streams: " + e.getMessage());
        } finally {
            // Since we lost a client, we gotta decrement the count
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

    /** "ITEM": retail point-of-sale lookup, same protocol as the original homework. */
    private void handleItemLookup(ObjectInputStream in, ObjectOutputStream out)
            throws IOException, ClassNotFoundException {

        String itemCode = (String) in.readObject();
        int quantity = (Integer) in.readObject();

        System.out.println("[client-handler] ITEM lookup: " + itemCode + " x" + quantity);

        String query = "SELECT * FROM items WHERE item_code = ?";
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, itemCode);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                String name = rs.getString("name");
                String description = rs.getString("description");
                double price = rs.getDouble("price");
                out.writeObject(new ProductSpec(itemCode, name, description, price));
            } else {
                out.writeObject("Invalid item code.");
            }
        } catch (SQLException e) {
            System.err.println("[client-handler] Database error: " + e.getMessage());
            out.writeObject("Database error.");
        }
        out.flush();
    }
}
