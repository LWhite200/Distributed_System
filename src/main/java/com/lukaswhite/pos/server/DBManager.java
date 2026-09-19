package com.lukaswhite.pos.server;

/* ------ DBManager.java -----
 * --- --- --- --- --- --- ---
 * - Handles the database
 * and display of information s.a. schedules
 *
 * Ported into the distributed project mostly as-is. The main change is
 * that getConnection() now points at DatabaseManager.DB_FILE_NAME (the
 * same file the retail "items" table lives in) instead of its own
 * hardcoded name, and table creation moved to
 * DatabaseManager.initializeSchema() so both domains are set up in one
 * place. Everything else - the FCFS-friendly lookups, the debug table
 * dumps, the pay-summary report - is the original logic.
 */

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class DBManager {

    // ------ Connection ------
    // Every call here opens its own short-lived connection, same as the
    // original homework. SQLite handles the resulting many-small-
    // connections pattern fine via file-level locking; under heavy
    // concurrent writes from several clients at once you can occasionally
    // see a "database is locked" SQLException - acceptable for a learning
    // project, worth knowing about if you ever scale this up for real.
    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + DatabaseManager.DB_FILE_NAME);
    }

    // Inserts person into the database.
    // Really no way of knowing if people share a name; so I will just hope everyone is unique.
    public static void insertCustomer(String name) throws SQLException {
        name = name.trim();
        try (Connection con = getConnection();
            PreparedStatement stmt = con.prepareStatement(
                "INSERT OR IGNORE INTO CUSTOMER_TABLE (NAME) VALUES (?)")) {
            stmt.setString(1, name);
            stmt.executeUpdate(); // returns 1 if inserted, 0 if ignored
        }
    }

    // Insert vehicle, need customer TUID so no duplicates
    public static void insertVehicle(int customerTUID, String vehicleDesc) throws SQLException {
        if (customerTUID <= 0 || vehicleDesc == null || vehicleDesc.isBlank()) return;
        try (Connection con = getConnection();
            PreparedStatement stmt = con.prepareStatement(
                "INSERT OR IGNORE INTO VEHICLES_TABLE (CUSTOMER_TUID, VEHICLE_DESCRIPTION) VALUES (?, ?);")) {
            stmt.setInt(1, customerTUID);
            stmt.setString(2, vehicleDesc.trim());
            stmt.executeUpdate();
        }
    }

    public static int getCustomerID(String name) throws SQLException {
        try (Connection con = getConnection();
            PreparedStatement stmt = con.prepareStatement(
                "SELECT TUID FROM CUSTOMER_TABLE WHERE NAME = ?;")) {
            stmt.setString(1, name.trim());
            ResultSet rs = stmt.executeQuery();
            return rs.next() ? rs.getInt("TUID") : -1;
        }
    }

    // ------ getServiceID ------
    // based on name, get the id of a service
    public static int getServiceID(String serviceName) throws SQLException {
        if (serviceName == null || serviceName.isBlank())
            throw new SQLException("Invalid service name");
        serviceName = capitalizeWords(serviceName.trim());
        try (Connection con = getConnection();
            PreparedStatement stmt = con.prepareStatement(
                "SELECT TUID FROM SERVICES_TABLE WHERE SERVICE_NAME = ?;")) {
            stmt.setString(1, serviceName);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getInt("TUID");
            throw new SQLException("Service not found: " + serviceName);
        }
    }

    // ------ getServiceTime ------
    // get the length of time a service takes
    public static int getServiceTime(int serviceTuid) throws SQLException {
        try (Connection con = getConnection();
            PreparedStatement stmt = con.prepareStatement("SELECT SERVICE_TIME FROM SERVICES_TABLE WHERE TUID = ?")) {
            stmt.setInt(1, serviceTuid);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getInt("SERVICE_TIME");
            throw new SQLException("Service time not found for ID " + serviceTuid);
        }
    }

    // ------ getVehicleID ------
    // Need customerID
    public static int getVehicleID(int customerTUID, String vehicleDesc) throws SQLException {
        if (customerTUID <= 0 || vehicleDesc == null || vehicleDesc.isBlank())
            return -1;
        try (Connection con = getConnection();
            PreparedStatement stmt = con.prepareStatement(
                "SELECT TUID FROM VEHICLES_TABLE WHERE CUSTOMER_TUID = ? AND VEHICLE_DESCRIPTION = ?;")) {
            stmt.setInt(1, customerTUID);
            stmt.setString(2, vehicleDesc.trim());
            ResultSet rs = stmt.executeQuery();
            return rs.next() ? rs.getInt("TUID") : -1;
        }
    }

    // ------ getMechanicNameForBay ------
    // NEW: used to build a friendly "you're booked with Sue" confirmation
    // message for the network/GUI scheduling flow.
    public static String getMechanicNameForBay(int bayTuid) throws SQLException {
        try (Connection con = getConnection();
            PreparedStatement stmt = con.prepareStatement("""
                SELECT M.MECHANIC_NAME FROM BAYS_TABLE B
                JOIN MECHANICS_TABLE M ON B.MECHANIC_TUID = M.TUID
                WHERE B.TUID = ?
            """)) {
            stmt.setInt(1, bayTuid);
            ResultSet rs = stmt.executeQuery();
            return rs.next() ? rs.getString("MECHANIC_NAME") : "Unknown";
        }
    }

    // ------ listServiceCatalog ------
    // NEW: used to populate the GUI's service dropdown so the person
    // scheduling doesn't have to guess/type an exact service name.
    public static List<String> listServiceCatalog() throws SQLException {
        List<String> catalog = new ArrayList<>();
        try (Connection con = getConnection();
            Statement stmt = con.createStatement();
            ResultSet rs = stmt.executeQuery(
                "SELECT SERVICE_NAME, SERVICE_TIME FROM SERVICES_TABLE ORDER BY SERVICE_NAME")) {
            while (rs.next()) {
                catalog.add(rs.getString("SERVICE_NAME") + " (" + rs.getInt("SERVICE_TIME") + " min)");
            }
        }
        return catalog;
    }

    // ------------------------------------------------------------
    // Debug Menu Displays

    // basic function that takes in table name and displays the contents
    public static void displayTable(String tableName) throws SQLException {
        try (Connection con = getConnection();
             Statement stmt = con.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM " + tableName)) {
            while (rs.next()) {
                for (int i = 1; i <= rs.getMetaData().getColumnCount(); i++) {
                    System.out.print(rs.getString(i) + " ");
                }
                System.out.println();
            }
        }
    }

    public static void displayCustomers() throws SQLException {
        displayTable("CUSTOMER_TABLE");
    }

    public static void displayVehicles() throws SQLException {
        displayTable("VEHICLES_TABLE");
    }

    public static void displayServices() throws SQLException {
        displayTable("SERVICES_TABLE");
    }

    public static void displayMechanics() throws SQLException {
        displayTable("MECHANICS_TABLE");
    }

    public static void displayBays() throws SQLException {
        displayTable("BAYS_TABLE");
    }

    public static void displaySchedule() throws SQLException {
        displayTable("SCHEDULE_TABLE");
    }

    // --- End of Debug display ---
    //--------------------------------------------------------------------------------------------------------------------

    // ------ clearDynamicData ------
    // clear everything that changes (mechanics, bays, services do not change; keep them the same)
    public static void clearDynamicData() throws SQLException {
        try (Connection con = getConnection(); Statement stmt = con.createStatement()) {
            stmt.executeUpdate("DELETE FROM SCHEDULE_TABLE;");
            stmt.executeUpdate("DELETE FROM VEHICLES_TABLE;");
            stmt.executeUpdate("DELETE FROM CUSTOMER_TABLE;");
            System.out.println("Dynamic tables cleared: CUSTOMER_TABLE, VEHICLES_TABLE, SCHEDULE_TABLE");
        }
    }

    // ------ capitalizeWords ------
    // Capitalized the first letter of each word.
    // We want this because of things in db may be uppercased
    // avoid confusion.
    private static String capitalizeWords(String input) {
        String[] words = input.toLowerCase().split(" ");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (w.isEmpty()) continue;
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1)).append(" ");
        }
        return sb.toString().trim();
    }

    // ---------------------------------------------------------------------------------------------------

    // ------ HoursPay.class ------
    // object to hold, well, the hours and pay
    // - of a mechanic. So we can reuse if the time
    // - we want to look at changes (day, week, .ect)
    public static class HoursPay {
        public double hours = 0; // total hours worked
        public double pay = 0;   // total pay earned

        public HoursPay(double hours, double pay) {
            this.hours = hours;
            this.pay = pay;
        }

        public void addHours(double h, double rate) {
            this.hours += h;
            this.pay += h * rate;
        }
    }

    // ------ displayMechanicScheduleAndPay ------
    // Console version - unchanged in spirit from the original homework,
    // just delegates to mechanicScheduleAndPayReport() (below) so the
    // network/GUI path can reuse the exact same report-building logic
    // instead of duplicating this whole method.
    public static void displayMechanicScheduleAndPay(String mechanicFilter, String timeFrame) throws SQLException {
        System.out.print(mechanicScheduleAndPayReport(mechanicFilter, timeFrame));
    }

    // ------ mechanicScheduleAndPayReport ------
    // NEW (extracted from the original displayMechanicScheduleAndPay):
    // same query and formatting, but built into a String and returned
    // instead of printed directly - this is what the ClientHandler calls
    // for the GUI's "View Mechanic Schedule & Pay" screen.
    public static String mechanicScheduleAndPayReport(String mechanicFilter, String timeFrame) throws SQLException {

        StringBuilder out = new StringBuilder();

        // Build SQL Query for schedule: we need everything (for debug)
        String sql = """
            SELECT M.MECHANIC_NAME, M.HOURLY_PAYRATE, S.TUID AS APPT_ID,
                C.NAME AS CUSTOMER_NAME, V.VEHICLE_DESCRIPTION,
                SRV.SERVICE_NAME, SRV.SERVICE_TIME,
                B.TUID AS BAY_ID, S.APPOINTMENT_TIME
            FROM SCHEDULE_TABLE S
            JOIN VEHICLES_TABLE V ON S.VEHICLE_TUID = V.TUID
            JOIN CUSTOMER_TABLE C ON V.CUSTOMER_TUID = C.TUID
            JOIN SERVICES_TABLE SRV ON S.SERVICE_TUID = SRV.TUID
            JOIN BAYS_TABLE B ON S.BAY_TUID = B.TUID
            JOIN MECHANICS_TABLE M ON B.MECHANIC_TUID = M.TUID
        """;

        if (mechanicFilter != null && !mechanicFilter.equalsIgnoreCase("both")) {
            sql += " WHERE M.MECHANIC_NAME = ? ";
        }
        sql += " ORDER BY M.MECHANIC_NAME, S.APPOINTMENT_TIME";

        try (Connection con = getConnection();
            PreparedStatement stmt = con.prepareStatement(sql)) {

                if (mechanicFilter != null && !mechanicFilter.equalsIgnoreCase("both")) {
                    stmt.setString(1, mechanicFilter.toUpperCase());
                }

                try (ResultSet rs = stmt.executeQuery()) {

                    Map<String, Map<String, HoursPay>> allMechanicTotals = new TreeMap<>();
                    Map<String, StringBuilder> allMechanicSchedules = new TreeMap<>();

                    DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
                    DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

                    while (rs.next()) {
                        String mechanic = rs.getString("MECHANIC_NAME");
                        double rate = rs.getDouble("HOURLY_PAYRATE");
                        int apptId = rs.getInt("APPT_ID");
                        String customer = rs.getString("CUSTOMER_NAME");
                        String vehicle = rs.getString("VEHICLE_DESCRIPTION");
                        String service = rs.getString("SERVICE_NAME");
                        int durationMins = rs.getInt("SERVICE_TIME");
                        int bay = rs.getInt("BAY_ID");
                        LocalDateTime start = LocalDateTime.parse(rs.getString("APPOINTMENT_TIME"), fmt);
                        LocalDateTime end = start.plusMinutes(durationMins);

                        String periodKey;
                        switch (timeFrame.toUpperCase()) {
                            case "DAY":   periodKey = start.toLocalDate().toString(); break;
                            case "WEEK":  periodKey = "Week of " + start.toLocalDate().with(DayOfWeek.MONDAY); break;
                            case "MONTH": periodKey = start.getYear() + "-" + String.format("%02d", start.getMonthValue()); break;
                            case "YEAR":  periodKey = String.valueOf(start.getYear()); break;
                            default:      periodKey = start.toLocalDate().toString();
                        }

                        if (timeFrame.equalsIgnoreCase("DAY")) {
                            allMechanicSchedules.putIfAbsent(mechanic, new StringBuilder());
                            StringBuilder sb = allMechanicSchedules.get(mechanic);
                            if (sb.length() == 0) {
                                sb.append(String.format("%n--- Schedule for Mechanic: %s ---%n", mechanic));
                                sb.append(String.format("%-5s | %-15s | %-20s | %-25s | %-3s | %-16s | %-16s%n",
                                        "ID", "Customer", "Vehicle", "Service", "Bay", "Start", "End"));
                            }
                            sb.append(String.format("%-5d | %-15s | %-20s | %-25s | %-3d | %-16s | %-16s%n",
                                    apptId, customer, vehicle, service, bay, start.format(timeFmt), end.format(timeFmt)));
                        }

                        allMechanicTotals.putIfAbsent(mechanic, new TreeMap<>());
                        Map<String, HoursPay> mechTotals = allMechanicTotals.get(mechanic);
                        HoursPay hp = mechTotals.getOrDefault(periodKey, new HoursPay(0, 0));
                        hp.addHours(durationMins / 60.0, rate);
                        mechTotals.put(periodKey, hp);
                    }

                    for (String mechanic : allMechanicTotals.keySet()) {
                        if (timeFrame.equalsIgnoreCase("DAY") && allMechanicSchedules.containsKey(mechanic)) {
                            out.append(allMechanicSchedules.get(mechanic));
                        }

                        out.append(String.format("%n--- %s Pay Summary (%s) ---%n", mechanic, timeFrame.toUpperCase()));
                        Map<String, HoursPay> mechTotals = allMechanicTotals.get(mechanic);
                        for (String period : mechTotals.keySet()) {
                            HoursPay hp = mechTotals.get(period);
                            out.append(String.format("%-20s -> Hours: %.2f | Pay: $%.2f%n", period, hp.hours, hp.pay));
                        }
                        out.append("---------------------------------------------------%n".formatted());
                    }
            }
        }

        return out.toString();
    }
}
