// Homework 7: GUI + OOP + Threads + Network + Database programming
// Course: CIS 357
// Due date: August 15, 2024
// Name: Lukas A. White
// Instructor: Il-Hyung Cho
// Program description: This is the ClientHandler class that works directly with the server and sql file
// to help them manage different events and tasks that otherwise would be difficult for a single server to do.
// this is used mainly to handle client's inputs that are then used to fetch data from the sql database.

import java.io.*;
import java.net.*;
import java.sql.*;


/**
 * Handles communication includes processing item requests and querying the database.
 */
public class ClientHandler extends Thread {
    private Socket clientSocket;  
    private Connection connection;  
    
    /**
     * Constructs a ClientHandler instnaces.
     * @param socket The client socket.
     * @param connection The database connection.
     */
    public ClientHandler(Socket socket, Connection connection) {
        this.clientSocket = socket;
        this.connection = connection;
    }

    /**
     * Processes requests from the client.
     */
    @Override
    public void run() {
    	
    	// tries to make the thing work, useful comment right here
        try (ObjectInputStream in = new ObjectInputStream(clientSocket.getInputStream());
             ObjectOutputStream out = new ObjectOutputStream(clientSocket.getOutputStream())) {

            // Continuously listens until closed
            while (!clientSocket.isClosed()) {
                try {
                	
                    // Reads these codes from client
                    String itemCode = (String) in.readObject();
                    int quantity = (Integer) in.readObject();

                    // Debug output for received item code and quantity
                    System.out.println("Received item code: " + itemCode + " and quantity: " + quantity);

                    // Prepare SQL query to fetch product details from the database
                    // a lot of errors and debug because it cannot find the database
                    String query = "SELECT * FROM items WHERE item_code = ?";
                    try (PreparedStatement stmt = connection.prepareStatement(query)) {
                        stmt.setString(1, itemCode);
                        ResultSet rs = stmt.executeQuery();

                        // If found
                        if (rs.next()) {
                            String name = rs.getString("name");
                            String description = rs.getString("description");
                            double price = rs.getDouble("price");

                            ProductSpec product = new ProductSpec(itemCode, name, description, price);
                            out.writeObject(product);
                            out.flush(); // Send it out immeditely
                            System.out.println("Sent product to client: " + product);
                        } else {
                            // Error message
                            out.writeObject("Invalid item code.");
                            out.flush(); // return the error 
                            System.out.println("No product found for item code: " + itemCode);
                        }
                    }
                } catch (SocketException e) {
                    System.err.println("Client connection reset: " + e.getMessage());
                    break; 
                } catch (EOFException e) {
                    System.out.println("Client disconnected.");
                    break; 
                } catch (SQLException e) {
                    System.err.println("Database error: " + e.getMessage());
                    out.writeObject("Database error.");
                    out.flush();
                } catch (ClassNotFoundException | IOException e) {
                    System.err.println("Error in ClientHandler: " + e.getMessage());
                    break; 
                }
                
                // need all this error catching as nothing seems to work.
                // actually useful, helped me figure out the data base was being erased 
            }
        } catch (IOException e) {
            System.err.println("I/O error in ClientHandler: " + e.getMessage());
        } finally {
            // Ensure the client socket is closed properly
            try {
                if (clientSocket != null && !clientSocket.isClosed()) {
                    clientSocket.close();
                    System.out.println("Closed client socket.");
                }
            } catch (IOException e) {
                System.err.println("Error closing client socket: " + e.getMessage());
            }
        }
    }
}
