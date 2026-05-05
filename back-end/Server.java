// Homework 7: GUI + OOP + Threads + Network + Database programming
// Course: CIS 357
// Due date: August 15, 2024
// Name: Lukas A. White
// Instructor: Il-Hyung Cho
// Program description: This is the server class that runs a surver that makes use of
// and sql file and some helper classes. This sets up a server on the user's ip address and
// handles connections with the client classes. This class prints server debug things like the database
// and when connectios are made or broken.

import java.io.*;
import java.net.*;
import java.sql.*;

/**
 * Main server class for handling client connections and querying the database.
 * @author LukasWhite
 * @version 8/11/2024
 */
public class Server {
    private static final int PORT = 8001;
    private static Connection connection;

    /**
     * Main entry point for the server application.
     * @param args Command line arguments.
     */
    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            // Extract the database file from the JAR
            File dbFile = extractDatabaseFile();
            System.out.println("Extracted database to: " + dbFile.getAbsolutePath());

            // Connect to SQLite database
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
            System.out.println("Connected to the SQLite database.");

            // Print server information
            System.out.println("Server started. Waiting for clients...");
            System.out.println("Host: " + InetAddress.getLocalHost().getHostName());
            System.out.println("IP Address: " + InetAddress.getLocalHost().getHostAddress());

            // Handle client connections
            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Client connected: " + clientSocket.getInetAddress().getHostAddress());
                new ClientHandler(clientSocket, connection).start();
            }
        } catch (IOException | SQLException e) {
            System.err.println("Server error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Extracts the database file from the JAR and returns the File object.
     * @return The File object for the extracted database.
     * @throws IOException If an I/O error occurs during extraction.
     */
    private static File extractDatabaseFile() throws IOException {
        // Create a temporary file to store the database
        File tempFile = File.createTempFile("items", ".db");
        tempFile.deleteOnExit(); // Ensure the file is deleted on exit

        // Open the JAR file
        try (InputStream jarStream = Server.class.getResourceAsStream("/items.db");
             FileOutputStream fileOutputStream = new FileOutputStream(tempFile)) {
            if (jarStream == null) {
                throw new FileNotFoundException("Database file not found in JAR.");
            }

            // Copy the database file from the JAR to the temporary file
            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = jarStream.read(buffer)) != -1) {
                fileOutputStream.write(buffer, 0, bytesRead);
            }
        }

        return tempFile;
    }
}

