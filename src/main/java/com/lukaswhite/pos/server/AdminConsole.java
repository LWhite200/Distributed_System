package com.lukaswhite.pos.server;

/* ------ AdminConsole.java ------
 * --- --- --- --- --- --- --- ---
 * A local, console-based admin tool for whoever is sitting at the
 * machine running a ServerNode - bulk-importing customers/vehicles/
 * service requests from a text file, resetting data, or eyeballing the
 * raw tables. This is basically the original TurboAutoService.java's
 * menu (minus the "New Sale"-equivalent screens, which are now the
 * JavaFX Client's job over the network) - renamed since it's no longer
 * the whole program, just the local admin half of it.
 *
 * Run this on the SAME machine/working directory as a ServerNode so it
 * reads and writes the same TurboAutoService.db file. It doesn't need
 * the load balancer or any network connection at all - it talks to the
 * database directly.
 *
 * Usage: mvn exec:java -Dexec.mainClass="com.lukaswhite.pos.server.AdminConsole"
 */

import java.io.File;
import java.util.Scanner;

public class AdminConsole {

    // ------ Data Menu ------
    // Handles the user's choice for database operations
    // such as importing, resetting, and deleting data.
    private static void dataMenu(Scanner scanner) {

        boolean inDataMenu = true;

        while (inDataMenu) {
            System.out.println("\n--- Data Management ---");
            System.out.println("1. Import Data");
            System.out.println("2. Reset Data");
            System.out.println("3. Delete Database");
            System.out.println("4. Return to Main Menu");
            System.out.print("Enter choice (1-4): ");

            String subChoice = scanner.nextLine().trim();

            switch (subChoice) {

                // --- Import Data ---
                case "1":
                    while (true) {
                        System.out.print("\nEnter file name (including .txt) (\" \" to exit): ");
                        String filePath = scanner.nextLine().trim();

                        if (filePath.isEmpty()) {
                            System.out.println("Leaving Adding screen");
                            break;
                        }

                        try {
                            DataImporter.importFromFile(filePath);
                        } catch (Exception e) {
                            System.out.println("Error importing data: " + e.getMessage());
                        }

                        System.out.print("\nWould you like to import another file? (y/n): ");
                        String again = scanner.nextLine().trim().toLowerCase();
                        if (!again.equals("y")) break;
                    }
                    break;

                // --- Reset Data ---
                case "2":
                    try {
                        System.out.print("Are you sure you want to reset all imported data? (y/n): ");
                        String confirm = scanner.nextLine().trim().toLowerCase();

                        if (confirm.equals("y")) {
                            DBManager.clearDynamicData();
                            System.out.println("All dynamic data reset (customers, vehicles, schedule).");
                        } else {
                            System.out.println("Reset cancelled.");
                        }
                    } catch (Exception e) {
                        System.out.println("Error Reseting data: " + e.getMessage());
                    }
                    break;

                // --- Delete the database file and close application ---
                case "3":
                    System.out.print("Are you sure you want to permanently delete the database file? (y/n): ");
                    String deleteConfirm = scanner.nextLine().trim().toLowerCase();

                    if (deleteConfirm.equals("y")) {
                        try {
                            File dbFile = new File(DatabaseManager.DB_FILE_NAME);
                            if (dbFile.exists() && dbFile.delete()) {
                                System.out.println("Database file permanently deleted.");
                            }
                        } catch (Exception e) {
                            System.out.println("Error deleting database file: " + e.getMessage());
                        }

                        System.out.println("Goodbye World!");
                        System.exit(0);
                    } else {
                        System.out.println("Cancelled database deletion.");
                    }
                    break;

                case "4":
                    inDataMenu = false;
                    break;

                default:
                    System.out.println("Invalid option, Try again.");
            }
        }
    }

    // ------ Schedule Viewing Menu ------
    private static void scheduleMenu(Scanner scanner) {

        boolean viewing = true;

        while (viewing) {
            System.out.println("\n--- View Mechanic Schedule & Pay ---");
            System.out.println("1. Sue");
            System.out.println("2. Steve");
            System.out.println("3. Both");
            System.out.println("4. Return to Main Menu");
            System.out.print("Enter choice (1-4): ");

            String mechChoice = scanner.nextLine().trim();
            String mech = null;

            switch (mechChoice) {
                case "1": mech = "SUE"; break;
                case "2": mech = "STEVE"; break;
                case "3": mech = "BOTH"; break;
                case "4": viewing = false; continue;
                default:
                    System.out.println("Invalid choice. Try again.");
                    continue;
            }

            String timeFrame = null;
            while (true) {
                System.out.println("\nSelect time frame to view schedule:");
                System.out.println("1. Day");
                System.out.println("2. Week");
                System.out.println("3. Month");
                System.out.println("4. Year");
                System.out.print("Enter choice (1-4): ");

                String timeChoice = scanner.nextLine().trim();
                switch (timeChoice) {
                    case "1": timeFrame = "DAY"; break;
                    case "2": timeFrame = "WEEK"; break;
                    case "3": timeFrame = "MONTH"; break;
                    case "4": timeFrame = "YEAR"; break;
                    default:
                        System.out.println("Invalid choice. Try again.");
                        continue;
                }
                break;
            }

            try {
                DBManager.displayMechanicScheduleAndPay(mech, timeFrame);
            } catch (Exception e) {
                System.out.println("Error displaying schedule: " + e.getMessage());
            }
        }
    }

    // ------ Debug Menu ------
    private static void debugMenu(Scanner scanner) {

        boolean inDebug = true;

        while (inDebug) {
            System.out.println("\n--- Debug Menu ---");
            System.out.println("1. View Customers");
            System.out.println("2. View Vehicles");
            System.out.println("3. View Services");
            System.out.println("4. View Mechanics");
            System.out.println("5. View Bays");
            System.out.println("6. View Schedule");
            System.out.println("7. Return to Main Menu");
            System.out.print("Enter choice (1-7): ");

            String debugChoice = scanner.nextLine().trim();

            try {
                switch (debugChoice) {
                    case "1": DBManager.displayCustomers(); break;
                    case "2": DBManager.displayVehicles(); break;
                    case "3": DBManager.displayServices(); break;
                    case "4": DBManager.displayMechanics(); break;
                    case "5": DBManager.displayBays(); break;
                    case "6": DBManager.displaySchedule(); break;
                    case "7": inDebug = false; break;
                    default: System.out.println("Invalid option. Try again.");
                }
            } catch (Exception e) {
                System.out.println("Error displaying data: " + e.getMessage());
            }
        }
    }

    // ------ main ------
    public static void main(String[] args) {
        try {
            // Creates the database file and tables if they don't already
            // exist yet - safe to call even if a ServerNode already has.
            DatabaseManager.ensureSchemaExists();

            Scanner scanner = new Scanner(System.in);
            boolean running = true;

            while (running) {
                System.out.println("\n--- Turbo Auto Service Admin Console ---");
                System.out.println("1. Data Management");
                System.out.println("2. Check Mechanic Schedule and Pay");
                System.out.println("3. Debug");
                System.out.println("4. Exit");
                System.out.print("Enter choice (1-4): ");

                String choice = scanner.nextLine().trim();

                switch (choice) {
                    case "1": dataMenu(scanner); break;
                    case "2": scheduleMenu(scanner); break;
                    case "3": debugMenu(scanner); break;
                    case "4":
                        running = false;
                        System.out.println("Goodbye World!");
                        break;
                    default:
                        System.out.println("Invalid Option. Try Again.");
                }
            }

            scanner.close();
        } catch (Exception e) {
            System.out.println("Critical error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
