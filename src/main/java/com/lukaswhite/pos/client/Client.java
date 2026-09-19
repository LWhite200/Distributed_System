package com.lukaswhite.pos.client;

import com.lukaswhite.pos.common.ProductSpec;
import com.lukaswhite.pos.loadbalancer.LoadBalancer;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.stage.Stage;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

/**
 * JavaFX point-of-sale client - visually and functionally almost the same
 * screens as the original homework, but the connection logic underneath
 * is now "distributed" instead of "one fixed server".
 *
 * Connection flow:
 *   1. The user types in the LOAD BALANCER's IP address (not a specific
 *      server's IP) - the whole point of the load balancer is that the
 *      client shouldn't need to know or care which physical machine
 *      actually ends up serving it.
 *   2. We open a short-lived connection to the load balancer's
 *      CLIENT_ASSIGN_PORT, send "ASSIGN", and get back "host:port" for
 *      whichever alive ServerNode currently has the least load (or "NONE"
 *      if every node is down).
 *   3. We open a SECOND, longer-lived connection directly to that node
 *      and do all the real item-lookup / receipt work on it. This part's
 *      protocol (write itemCode, write quantity, read ProductSpec-or-String
 *      back) is unchanged from the original homework's Client/ClientHandler.
 */
public class Client extends Application {

    // Streams to the ServerNode we were assigned - set up once in
    // connectToAssignedNode(), then reused for every "Add Item" click.
    private ObjectOutputStream out;
    private ObjectInputStream in;

    private double totalAmount = 0.0;
    private final StringBuilder receiptBuilder = new StringBuilder();

    /**
     * Builds the very first screen: enter the load balancer's IP and connect.
     * @param primaryStage The primary stage for this application.
     */
    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("Client");

        Label ipLabel = new Label("Load Balancer IP Address:");
        TextField ipField = new TextField("127.0.0.1");
        Button connectButton = new Button("Connect");
        Label statusLabel = new Label();
        statusLabel.setWrapText(true);

        VBox connectionLayout = new VBox(10, ipLabel, ipField, connectButton, statusLabel);
        connectionLayout.setPadding(new Insets(15));
        Scene connectionScene = new Scene(connectionLayout, 340, 190);

        connectButton.setOnAction(e -> {
            String loadBalancerIp = ipField.getText();
            statusLabel.setText("Asking load balancer for a server...");
            if (connectToAssignedNode(loadBalancerIp)) {
                showWelcomeScreen(primaryStage);
            } else {
                statusLabel.setText("Could not connect - see the error dialog for details.");
            }
        });

        primaryStage.setScene(connectionScene);
        primaryStage.show();
    }

    /**
     * Main
     * @param args Command line arguments.
     */
    public static void main(String[] args) {
        launch(args);
    }

    /**
     * Two-step connect: first ask the load balancer WHICH node to use, then
     * connect directly to that node.
     * @param loadBalancerIp IP address of the LoadBalancer.
     * @return True only if both steps succeed and we're ready to send item requests.
     */
    private boolean connectToAssignedNode(String loadBalancerIp) {
        String assignment = requestAssignment(loadBalancerIp);
        if (assignment == null) {
            return false; // requestAssignment already showed an error dialog
        }
        if (assignment.equals("NONE")) {
            showAlert("No Servers Available", "The load balancer has no live server nodes right now. "
                    + "Make sure at least one ServerNode is running and registered.");
            return false;
        }

        String[] parts = assignment.split(":");
        String nodeHost = parts[0];
        int nodePort = Integer.parseInt(parts[1]);

        try {
            Socket nodeSocket = new Socket(nodeHost, nodePort);
            out = new ObjectOutputStream(nodeSocket.getOutputStream());
            in = new ObjectInputStream(nodeSocket.getInputStream());
            System.out.println("Connected to assigned server node at " + nodeHost + ":" + nodePort);
            return true;

        } catch (IOException e) {
            e.printStackTrace();
            showAlert("Connection Error", "The load balancer assigned us to " + assignment
                    + " but we couldn't connect to it: " + e.getMessage());
            return false;
        }
    }

    /**
     * Talks to the load balancer's client-assign port and asks for a server.
     * @return "host:port" of the assigned node, the literal string "NONE", or null on error.
     */
    private String requestAssignment(String loadBalancerIp) {
        try (Socket lbSocket = new Socket(loadBalancerIp, LoadBalancer.CLIENT_ASSIGN_PORT);
             ObjectOutputStream lbOut = new ObjectOutputStream(lbSocket.getOutputStream());
             ObjectInputStream lbIn = new ObjectInputStream(lbSocket.getInputStream())) {

            lbOut.writeObject("ASSIGN");
            lbOut.flush();
            return (String) lbIn.readObject();

        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
            showAlert("Load Balancer Error", "Could not reach the load balancer at "
                    + loadBalancerIp + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * Displays the welcome screen with a button to start a new sale.
     * @param primaryStage The primary stage for this application.
     */
    private void showWelcomeScreen(Stage primaryStage) {
        Label welcomeLabel = new Label("Welcome To White's Shop");
        Button newSaleButton = new Button("New Sale");

        VBox welcomeLayout = new VBox(20, welcomeLabel, newSaleButton);
        welcomeLayout.setAlignment(Pos.CENTER);
        Scene welcomeScene = new Scene(welcomeLayout, 400, 200);

        newSaleButton.setOnAction(e -> showMainScreen(primaryStage));

        primaryStage.setScene(welcomeScene);
    }

    /**
     * Displays the main sales screen with components to add items and pay.
     * @param primaryStage The primary stage for this application.
     */
    private void showMainScreen(Stage primaryStage) {
        Label itemCodeLabel = new Label("Item Code:");
        TextField itemCodeField = new TextField();
        Label quantityLabel = new Label("Quantity:");
        TextField quantityField = new TextField();
        Button addButton = new Button("Add Item");
        Button payButton = new Button("Pay");
        TextArea receiptArea = new TextArea();
        receiptArea.setEditable(false);
        receiptArea.setFont(Font.font("Courier New"));

        VBox mainLayout = new VBox(10, itemCodeLabel, itemCodeField, quantityLabel, quantityField,
                addButton, receiptArea, payButton);
        mainLayout.setPadding(new Insets(10));
        Scene mainScene = new Scene(mainLayout, 500, 320);

        addButton.setOnAction(e -> {
            String itemCode = itemCodeField.getText();
            try {
                int quantity = Integer.parseInt(quantityField.getText());
                addItemToReceipt(itemCode, quantity, receiptArea);
            } catch (NumberFormatException ex) {
                showAlert("Input Error", "Quantity must be a number.");
            }
        });

        payButton.setOnAction(e -> showReceiptWindow(primaryStage));

        primaryStage.setScene(mainScene);
    }

    /**
     * Adds an item to the receipt and updates the total amount. Sends the
     * item code + quantity to whichever ServerNode we were assigned, and
     * handles either a ProductSpec (success) or a String (error message)
     * coming back - unchanged from the original homework's protocol.
     * @param itemCode The code of the item.
     * @param quantity The quantity of the item.
     * @param receiptArea The text area where the receipt is displayed.
     */
    private void addItemToReceipt(String itemCode, int quantity, TextArea receiptArea) {
        try {
            out.writeObject(itemCode);
            out.flush();
            out.writeObject(quantity);
            out.flush();

            Object response = in.readObject();

            if (response instanceof ProductSpec productSpec) {
                double subTotal = productSpec.getPrice() * quantity;
                double total = subTotal;

                // 6% sales tax, unless the item code starts with 'E'/'e' (exempt items).
                if (!itemCode.toUpperCase().startsWith("E")) {
                    total += subTotal * 0.06;
                }
                totalAmount += total;

                String line = String.format("%s, %s, %d, $%.2f, $%.2f%n",
                        productSpec.getName(), productSpec.getDescription(), quantity, subTotal, total);
                receiptBuilder.append(line);
                receiptArea.appendText(line);

            } else if (response instanceof String message) {
                if (message.equals("Invalid item code.")) {
                    receiptArea.appendText("Invalid item code.\n");
                } else {
                    receiptArea.appendText("Unknown error occurred.\n");
                }
            }
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
            showAlert("Error", "Failed to add item. The assigned server may have gone down - "
                    + "try restarting the app so the load balancer can assign a different one.");
        }
    }

    /**
     * Shows a new window with the receipt and the total amount.
     * @param primaryStage The primary stage for this application.
     */
    private void showReceiptWindow(Stage primaryStage) {
        Stage receiptStage = new Stage();
        receiptStage.setTitle("Receipt");

        Label thankYouLabel = new Label("Thanks for your payment!");
        TextArea receiptArea = new TextArea();
        receiptArea.setEditable(false);
        receiptArea.setFont(Font.font("Courier New"));
        receiptArea.setText(receiptBuilder.toString() + String.format("Total: $%.2f", totalAmount));

        VBox receiptLayout = new VBox(10, thankYouLabel, receiptArea);
        receiptLayout.setPadding(new Insets(10));
        Scene receiptScene = new Scene(receiptLayout, 400, 300);

        receiptStage.setScene(receiptScene);
        receiptStage.show();

        // Reset so orders don't carry over between sales.
        totalAmount = 0.0;
        receiptBuilder.setLength(0);

        receiptStage.setOnHiding(e -> showWelcomeScreen(primaryStage));
    }

    /**
     * Displays an alert with a specified title and message.
     * @param title The title of the alert.
     * @param message The message to display.
     */
    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
