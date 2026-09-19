package com.lukaswhite.pos.server;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Owns the SQLite connection for one ServerNode, and creates/seeds the
 * database schema the very first time a node runs.
 *
 * This now covers TWO domains sharing one database file:
 *   - the original point-of-sale "items" catalog (laptops, sofas,
 *     t-shirts, etc. - unchanged from the retail homework)
 *   - the Turbo Auto Service scheduling tables (customers, vehicles,
 *     mechanics, bays, services, schedule - ported from the CIS 357
 *     scheduling homework)
 *
 * Unlike the very first version of this class, the database file is now
 * PERSISTENT (DB_FILE_NAME, created in the node's working directory)
 * rather than a fresh temp file every run - that's essential once the
 * database holds real accumulated data like customers and appointments,
 * not just a static reference catalog. initializeSchema() is safe to run
 * on every startup, existing data or not: every statement is
 * "CREATE TABLE IF NOT EXISTS" or "INSERT OR IGNORE", so it only ever
 * fills in what's missing.
 */
public class DatabaseManager {

    /** Shared by DBManager too - both classes talk to the same file. */
    public static final String DB_FILE_NAME = "TurboAutoService.db";

    private final Connection connection;

    public DatabaseManager() throws IOException, SQLException {
        loadDriver();

        boolean isNewDatabase = !new File(DB_FILE_NAME).exists();
        this.connection = DriverManager.getConnection("jdbc:sqlite:" + DB_FILE_NAME);
        System.out.println("[database] Connected to SQLite database at "
                + new File(DB_FILE_NAME).getAbsolutePath());

        initializeSchema(connection);
        if (isNewDatabase) {
            System.out.println("[database] New database - seeded default items, mechanics, bays, and services.");
        }
    }

    public Connection getConnection() {
        return connection;
    }

    /**
     * Makes sure the schema exists without holding a connection open
     * afterward - used by console tools (see AdminConsole) that just need
     * the tables to be there before they start their own short-lived
     * DBManager connections.
     */
    public static void ensureSchemaExists() throws IOException, SQLException {
        loadDriver();
        try (Connection con = DriverManager.getConnection("jdbc:sqlite:" + DB_FILE_NAME)) {
            initializeSchema(con);
        }
    }

    private static void loadDriver() throws SQLException {
        // Explicitly loading the driver class is a one-line safety net:
        // modern JDBC drivers usually self-register via a META-INF/services
        // file, but that doesn't always survive every way a jar can end up
        // on the classpath, and the failure mode without this line is a
        // confusing "No suitable driver found" even though the jar is
        // right there.
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw new SQLException("SQLite JDBC driver not found on the classpath. "
                    + "Make sure the sqlite-jdbc dependency is included (see pom.xml).", e);
        }
    }

    /** Creates every table this project needs and seeds their starting data. Idempotent. */
    static void initializeSchema(Connection connection) throws SQLException {
        try (Statement stmt = connection.createStatement()) {

            // ---------------- Retail items (point-of-sale) ----------------
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS items (
                    item_code TEXT PRIMARY KEY,
                    name TEXT NOT NULL,
                    description TEXT,
                    price REAL NOT NULL
                );
            """);
            stmt.executeUpdate("""
                INSERT OR IGNORE INTO items (item_code, name, description, price) VALUES
                ('E001','Laptop','14-inch display 8GB RAM 256GB SSD',1200.0),
                ('E002','Smartphone','6.5-inch display 128GB storage',800.0),
                ('E003','Tablet','10-inch display 64GB storage',300.0),
                ('E004','Monitor','24-inch Full HD display',150.0),
                ('E005','Keyboard','Mechanical keyboard with RGB lighting',80.0),
                ('F001','Sofa','3-seater sofa',500.0),
                ('F002','Dining Table','Wooden dining table for 6',350.0),
                ('F003','Chair','Ergonomic office chair',120.0),
                ('F004','Bed','Queen size bed',600.0),
                ('F005','Bookshelf','5-tier bookshelf',100.0),
                ('C001','Running Shoes','Lightweight running shoes',70.0),
                ('C002','T-shirt','Cotton T-shirt',20.0),
                ('C003','Jeans','Denim jeans',50.0),
                ('C004','Jacket','Winter jacket',100.0),
                ('C005','Cap','Baseball cap',15.0);
            """);

            // ---------------- Turbo Auto Service (scheduling) ----------------
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS CUSTOMER_TABLE (
                    TUID INTEGER PRIMARY KEY AUTOINCREMENT,
                    NAME TEXT NOT NULL UNIQUE
                );
            """);
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS MECHANICS_TABLE (
                    TUID INTEGER PRIMARY KEY AUTOINCREMENT,
                    MECHANIC_NAME TEXT NOT NULL UNIQUE,
                    HOURLY_PAYRATE REAL NOT NULL
                );
            """);
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS BAYS_TABLE (
                    TUID INTEGER PRIMARY KEY AUTOINCREMENT,
                    MECHANIC_TUID INTEGER NOT NULL,
                    FOREIGN KEY (MECHANIC_TUID) REFERENCES MECHANICS_TABLE(TUID)
                );
            """);
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS SERVICES_TABLE (
                    TUID INTEGER PRIMARY KEY AUTOINCREMENT,
                    SERVICE_NAME TEXT NOT NULL COLLATE NOCASE UNIQUE,
                    SERVICE_TIME REAL NOT NULL
                );
            """);
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS VEHICLES_TABLE (
                    TUID INTEGER PRIMARY KEY AUTOINCREMENT,
                    CUSTOMER_TUID INTEGER NOT NULL,
                    VEHICLE_DESCRIPTION TEXT NOT NULL,
                    FOREIGN KEY (CUSTOMER_TUID) REFERENCES CUSTOMER_TABLE(TUID)
                );
            """);
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS SCHEDULE_TABLE (
                    TUID INTEGER PRIMARY KEY AUTOINCREMENT,
                    VEHICLE_TUID INTEGER NOT NULL,
                    SERVICE_TUID INTEGER NOT NULL,
                    BAY_TUID INTEGER NOT NULL,
                    APPOINTMENT_TIME TEXT NOT NULL,
                    FOREIGN KEY (VEHICLE_TUID) REFERENCES VEHICLES_TABLE(TUID),
                    FOREIGN KEY (SERVICE_TUID) REFERENCES SERVICES_TABLE(TUID),
                    FOREIGN KEY (BAY_TUID) REFERENCES BAYS_TABLE(TUID)
                );
            """);

            // Bay 1 -> Steve, Bay 2 -> Sue. Scheduler.java breaks ties in
            // favor of bay 2 (see its "Preferences Sue" comment), so Sue
            // wins whenever both bays are free at the exact same time.
            stmt.executeUpdate("""
                INSERT OR IGNORE INTO MECHANICS_TABLE (TUID, MECHANIC_NAME, HOURLY_PAYRATE) VALUES
                (1, 'SUE', 10.00),
                (2, 'STEVE', 9.00);
            """);
            stmt.executeUpdate("""
                INSERT OR IGNORE INTO BAYS_TABLE (TUID, MECHANIC_TUID) VALUES
                (1, 2),
                (2, 1);
            """);
            stmt.executeUpdate("""
                INSERT OR IGNORE INTO SERVICES_TABLE (TUID, SERVICE_NAME, SERVICE_TIME) VALUES
                (1, 'Oil Change', 30),
                (2, 'Tire Replacement', 60),
                (3, 'Brakes', 180),
                (4, 'Transmission Filter Replacement', 120),
                (5, 'Cooling System Cleaning', 240);
            """);
        }
    }
}
