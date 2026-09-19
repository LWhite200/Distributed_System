package com.lukaswhite.pos.server;

import com.lukaswhite.pos.common.ProductSpec;
import com.lukaswhite.pos.common.ScheduleResult;

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
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Handles one connected Client for as long as it stays connected.
 *
 * The wire protocol is a simple command dispatch: the client always sends
 * a command String first, then whatever arguments that command needs, and
 * always gets exactly one response object back. Four commands are
 * supported:
 *
 *   "ITEM"          (String itemCode, int quantity)             -> ProductSpec or String error
 *   "SCHEDULE"      (String customer, String vehicle, String svc) -> ScheduleResult
 *   "VIEW_SCHEDULE" (String mechanicFilter, String timeFrame)    -> String report
 *   "GET_SERVICES"  (no arguments)                               -> ArrayList<String> catalog
 *
 * Adding a new kind of request later just means adding one more case here
 * and a matching method on the Client side - the connection, threading,
 * and load-reporting machinery around it doesn't change.
 */
public class ClientHandler extends Thread {

    private final Socket clientSocket;
    private final Connection connection; // shared connection, used for retail item lookups
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

            while (!clientSocket.isClosed()) {
                try {
                    String command = (String) in.readObject();

                    switch (command) {
                        case "ITEM" -> handleItemLookup(in, out);
                        case "SCHEDULE" -> handleScheduleService(in, out);
                        case "VIEW_SCHEDULE" -> handleViewSchedule(in, out);
                        case "GET_SERVICES" -> handleGetServices(out);
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

    /** "SCHEDULE": resolve/create customer+vehicle, then book the earliest available bay slot. */
    private void handleScheduleService(ObjectInputStream in, ObjectOutputStream out)
            throws IOException, ClassNotFoundException {

        String customerName = (String) in.readObject();
        String vehicleDescription = (String) in.readObject();
        String serviceName = (String) in.readObject();

        System.out.println("[client-handler] SCHEDULE request: " + customerName
                + " / " + vehicleDescription + " / " + serviceName);

        try {
            DBManager.insertCustomer(customerName);
            int customerId = DBManager.getCustomerID(customerName);

            DBManager.insertVehicle(customerId, vehicleDescription);
            int vehicleId = DBManager.getVehicleID(customerId, vehicleDescription);

            // Throws SQLException with a helpful message if the service
            // name doesn't match anything in SERVICES_TABLE.
            int serviceId = DBManager.getServiceID(serviceName);

            Scheduler.Appointment appt = Scheduler.scheduleSingleJob(vehicleId, serviceId);
            String mechanic = DBManager.getMechanicNameForBay(appt.bay());
            String when = appt.start().format(
                    java.time.format.DateTimeFormatter.ofPattern("EEE, MMM d yyyy 'at' h:mm a"));

            String message = String.format("Booked with %s (Bay %d) on %s.", mechanic, appt.bay(), when);
            out.writeObject(new ScheduleResult(true, message));

        } catch (SQLException e) {
            System.err.println("[client-handler] Scheduling error: " + e.getMessage());
            out.writeObject(new ScheduleResult(false, "Could not schedule: " + e.getMessage()));
        }
        out.flush();
    }

    /** "VIEW_SCHEDULE": build and return the same report the console version prints. */
    private void handleViewSchedule(ObjectInputStream in, ObjectOutputStream out)
            throws IOException, ClassNotFoundException {

        String mechanicFilter = (String) in.readObject();
        String timeFrame = (String) in.readObject();

        try {
            String report = DBManager.mechanicScheduleAndPayReport(mechanicFilter, timeFrame);
            out.writeObject(report.isBlank() ? "No appointments found for that selection." : report);
        } catch (SQLException e) {
            System.err.println("[client-handler] Report error: " + e.getMessage());
            out.writeObject("Error building report: " + e.getMessage());
        }
        out.flush();
    }

    /** "GET_SERVICES": lets the GUI populate its service dropdown from the live catalog. */
    private void handleGetServices(ObjectOutputStream out) throws IOException {
        try {
            out.writeObject(new ArrayList<>(DBManager.listServiceCatalog()));
        } catch (SQLException e) {
            System.err.println("[client-handler] Catalog error: " + e.getMessage());
            out.writeObject(new ArrayList<String>());
        }
        out.flush();
    }
}
