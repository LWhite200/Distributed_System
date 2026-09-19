package com.lukaswhite.pos.server;

/* ------ DataImporter.java ------
 * --- --- --- --- --- --- --- ---
 * Handles the importation of structured txt into database.
 * Structured as [Customer, vehicle, Service]
 * After data loaded, uses Scheduler.java to schedule services
 *
 * Ported basically unchanged. This is a LOCAL, server-machine-only tool -
 * the text file has to already be sitting on whatever machine is running
 * the ServerNode/AdminConsole, since a network Client has no way to hand
 * a whole file over through the item/schedule request protocol. See
 * AdminConsole for how this gets invoked.
*/

/* --- Table Overview ---
 *
 * CUSTOMER_TABLE [TUID, NAME]
 * MECHANICS_TABLE [TUID, MECHANIC_NAME, HOURLY_PAYRATE]
 * BAYS_TABLE [TUID, MECHANIC_TUID]
 * SERVICES_TABLE [TUID, SERVICE_NAME, SERVICE_TIME]
 * VEHICLES_TABLE [TUID, CUSTOMER_TUID, VEHICLE_DESCRIPTION]
 * SCHEDULE_TABLE [TUID, VEHICLE_TUID, SERVICE_TUID, BAY_TUID, APPOINTMENT_TIME]
 */

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class DataImporter {

    // ------ ServiceRequest.class ------
    // Helper class to temporarily hold foreign keys to build schedules.
    private static class ServiceRequest {
        int customerId;
        int vehicleId;
        int serviceId;

        ServiceRequest(int cId, int vId, int sId) {
            this.customerId = cId;
            this.vehicleId = vId;
            this.serviceId = sId;
        }
    }

    // ------ importFromFile ------
    // Import from a structured text file given its name, into the
    // database. Also handles scheduling.
    public static void importFromFile(String filename) {

        List<ServiceRequest> requests = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(filename))) {

            String line;
            while ((line = reader.readLine()) != null) {

                if (line.isBlank()) continue;

                // Split by tab. part[0] tells us what kind of line this is:
                // C = customer, V = vehicle, S = service request.
                String[] parts = line.split("\t");
                switch (parts[0]) {

                    // --- CUSTOMER ---
                    case "C":
                        if (parts.length >= 2) {
                            String customerName = parts[1].trim();
                            DBManager.insertCustomer(customerName);
                        }
                        break;

                    // --- VEHICLE ---
                    case "V":
                        if (parts.length >= 3) {
                            String customerName = parts[1].trim();
                            String vehicleDesc = parts[2].trim();

                            int customerId = DBManager.getCustomerID(customerName);
                            DBManager.insertVehicle(customerId, vehicleDesc);
                        }
                        break;

                    // --- SERVICE REQUEST ---
                    case "S":
                        if (parts.length >= 4) {
                            String customerName = parts[1].trim();
                            String vehicleDesc = parts[2].trim();
                            String serviceName = parts[3].trim();

                            int customerId = DBManager.getCustomerID(customerName);
                            int vehicleId = DBManager.getVehicleID(customerId, vehicleDesc);
                            int serviceId = DBManager.getServiceID(serviceName);

                            requests.add(new ServiceRequest(customerId, vehicleId, serviceId));
                        }
                        break;

                    default:
                        System.err.println("Error line: " + line);
                }
            }

            System.out.println("\nData import finished!");
        } catch (IOException | SQLException e) {
            e.printStackTrace();
        }

        // ------ Scheduling Call ------
        try {
            for (ServiceRequest req : requests) {
                Scheduler.scheduleSingleJob(req.vehicleId, req.serviceId);
            }
            System.out.println("All service scheduled successfully.");
        } catch (SQLException e) {
            System.err.println("Error while scheduling: " + e.getMessage());
        }
    }
}
