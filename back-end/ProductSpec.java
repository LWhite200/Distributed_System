// Homework 7: GUI + OOP + Threads + Network + Database programming
// Course: CIS 357
// Due date: August 15, 2024
// Name: Lukas A. White
// Instructor: Il-Hyung Cho
// Program description: This is the productSpec class that handles what the user wants. I is made up of parameters that
// are within the sql file and is sort of like an object, so that the client handler can remember/store user's items.

import java.io.Serializable;


/**
 * Represents a product
 */
public class ProductSpec implements Serializable {
	
	/**
	 * Code of item.
	 */
    private String itemCode;
    
    /**
	 * Name of item
	 */
    private String name;
    
    /**
	 * Description of item
	 */
    private String description;
    
    /**
	 * Price of item.
	 */
    private double price;

    /**
     * Constructs a ProductSpec with the specified details.
     * @param itemCode The item code.
     * @param name The product name.
     * @param description The product description.
     * @param price The product price.
     */
    public ProductSpec(String itemCode, String name, String description, double price) {
        this.itemCode = itemCode;
        this.name = name;
        this.description = description;
        this.price = price;
    }

    /**
     * Gets 
     * @return The item code.
     */
    public String getItemCode() {
        return itemCode;
    }

    /**
     * Gets
     * @return The product name.
     */
    public String getName() {
        return name;
    }

    /**
     * Gets 
     * @return The product description.
     */
    public String getDescription() {
        return description;
    }

    /**
     * Gets 
     * @return The product price.
     */
    public double getPrice() {
        return price;
    }

    /**
     * Returns 
     * @return The product details as a string.
     */
    @Override
    public String toString() {
        return String.format("%s: %s (%s) - $%.2f", itemCode, name, description, price);
    }
}
