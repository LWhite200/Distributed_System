package com.lukaswhite.pos.server;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Owns the SQLite connection for one ServerNode.
 *
 * items.db is bundled as a classpath resource at src/main/resources/items.db
 * so this works the same way whether you run it via `mvn exec:java`, from an
 * IDE, or from a packaged jar. SQLite's JDBC driver needs an actual file on
 * disk though (it can't read straight out of a jar), so on startup we copy
 * the bundled resource out to a temp file and connect to that - this is the
 * same trick the original homework's Server.extractDatabaseFile() used,
 * just pulled out into its own class.
 */
public class DatabaseManager {

    private final Connection connection;

    public DatabaseManager() throws IOException, SQLException {
        // Explicitly load the SQLite JDBC driver class. Modern JDBC drivers
        // usually register themselves automatically via a META-INF/services
        // file, but doing this by hand is a one-line safety net that works
        // regardless of how the sqlite-jdbc jar was packaged/put on the
        // classpath - without it you can hit a confusing
        // "No suitable driver found" SQLException even though the jar is
        // right there.
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw new SQLException("SQLite JDBC driver not found on the classpath. "
                    + "Make sure the sqlite-jdbc dependency is included (see pom.xml).", e);
        }

        File dbFile = extractDatabaseFile();
        this.connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
        System.out.println("[database] Connected to SQLite database at " + dbFile.getAbsolutePath());
    }

    public Connection getConnection() {
        return connection;
    }

    /** Copies the bundled items.db resource to a temp file and returns that file. */
    private static File extractDatabaseFile() throws IOException {
        File tempFile = File.createTempFile("items", ".db");
        tempFile.deleteOnExit();

        try (InputStream resourceStream = DatabaseManager.class.getResourceAsStream("/items.db");
             FileOutputStream fileOut = new FileOutputStream(tempFile)) {

            if (resourceStream == null) {
                throw new FileNotFoundException(
                        "items.db not found on the classpath. It should live at "
                                + "src/main/resources/items.db in this project.");
            }

            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = resourceStream.read(buffer)) != -1) {
                fileOut.write(buffer, 0, bytesRead);
            }
        }

        return tempFile;
    }
}
