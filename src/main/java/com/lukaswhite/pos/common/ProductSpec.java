package com.lukaswhite.pos.common;

import java.io.Serializable;

/**
 * Represents one row from the "items" table in the SQL database.
 *
 * This travels over the network as a plain serialized object: the
 * ServerNode reads a row out of SQLite, wraps it in a ProductSpec, and
 * sends it straight to the Client, which just calls the getters. No
 * changes from the original homework version other than the package
 * declaration and a serialVersionUID (good practice for any Serializable
 * class that gets sent over a network - it keeps client/server in sync
 * about what "version" of the class they're using).
 */
public class ProductSpec implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String itemCode;
    private final String name;
    private final String description;
    private final double price;

    public ProductSpec(String itemCode, String name, String description, double price) {
        this.itemCode = itemCode;
        this.name = name;
        this.description = description;
        this.price = price;
    }

    public String getItemCode() {
        return itemCode;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public double getPrice() {
        return price;
    }

    @Override
    public String toString() {
        return String.format("%s: %s (%s) - $%.2f", itemCode, name, description, price);
    }
}
