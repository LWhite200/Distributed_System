// Homework 7: GUI + OOP + Threads + Network + Database programming
// Course: CIS 357
// Due date: August 15, 2024
// Names: Lukas A. White, Connor Oard, Noah T
// Instructor: Il-Hyung Cho

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
 * Holds the retail point-of-sale "items" catalog (laptops, sofas,
 * t-shirts, etc.) that ClientHandler looks items up against.
 *
 * The database file is PERSISTENT (DB_FILE_NAME, created in the node's
 * working directory), so it survives restarts. initializeSchema() is
 * safe to run on every startup, existing data or not: every statement is
 * "CREATE TABLE IF NOT EXISTS" or "INSERT OR IGNORE", so it only ever
 * fills in what's missing.
 */
public class DatabaseManager {

    public static final String DB_FILE_NAME = "SalesSystem.db";

    private final Connection connection;

    public DatabaseManager() throws IOException, SQLException {
        loadDriver();

        boolean isNewDatabase = !new File(DB_FILE_NAME).exists();
        this.connection = DriverManager.getConnection("jdbc:sqlite:" + DB_FILE_NAME);
        System.out.println("[database] Connected to SQLite database at "
                + new File(DB_FILE_NAME).getAbsolutePath());

        initializeSchema(connection);
        if (isNewDatabase) {
            System.out.println("[database] New database - seeded default items.");
        }
    }

    public Connection getConnection() {
        return connection;
    }

    /**
     * Makes sure the schema exists without holding a connection open
     * afterward - used by console tools (see AdminConsole) that just need
     * the table to be there before they start their own short-lived
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

    /** Creates the items table and seeds its starting data. Idempotent. */
    static void initializeSchema(Connection connection) throws SQLException {
        try (Statement stmt = connection.createStatement()) {

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
        }
    }
}
