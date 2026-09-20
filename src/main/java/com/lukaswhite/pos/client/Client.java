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
 * JavaFX point-of-sale client.
 *
 * Connection flow: the user types in the LOAD BALANCER's IP, we ask it
 * which ServerNode to use, then open a direct connection to that node
 * and do item lookups over that one connection, using a command-tag
 * protocol (see ClientHandler on the server side): every request starts
 * with a String naming the command ("ITEM"), followed by that command's
 * arguments, and gets back exactly one response object.
 */
public class Client extends Application {

    private ObjectOutputStream out;
    private ObjectInputStream in;

    // --- state for the "New Sale" item-purchase screen ---
    private double totalAmount = 0.0;
    private final StringBuilder receiptBuilder = new StringBuilder();

    private Stage primaryStage;

    @Override
    public void start(Stage primaryStage) {
        this.primaryStage = primaryStage;
        primaryStage.setTitle("White's Shop - Point of Sale");

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
                showWelcomeScreen();
            } else {
                statusLabel.setText("Could not connect - see the error dialog for details.");
            }
        });

        primaryStage.setScene(connectionScene);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }

    // ================================================================
    // Connecting: ask the load balancer, then connect straight to the
    // node it names.
    // ================================================================

    private boolean connectToAssignedNode(String loadBalancerIp) {
        String assignment = requestAssignment(loadBalancerIp);
        if (assignment == null) {
            return false;
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

    // ================================================================
    // Welcome screen
    // ================================================================

    private void showWelcomeScreen() {
        Label welcomeLabel = new Label("Welcome To White's Shop");
        Button newSaleButton = new Button("New Sale");

        VBox welcomeLayout = new VBox(15, welcomeLabel, newSaleButton);
        welcomeLayout.setAlignment(Pos.CENTER);
        welcomeLayout.setPadding(new Insets(20));
        Scene welcomeScene = new Scene(welcomeLayout, 420, 280);

        newSaleButton.setOnAction(e -> showMainScreen());

        primaryStage.setScene(welcomeScene);
    }

    // ================================================================
    // "New Sale" - retail item purchase. Every item request is tagged
    // with the "ITEM" command.
    // ================================================================

    private void showMainScreen() {
        Label itemCodeLabel = new Label("Item Code:");
        TextField itemCodeField = new TextField();
        Label quantityLabel = new Label("Quantity:");
        TextField quantityField = new TextField();
        Button addButton = new Button("Add Item");
        Button payButton = new Button("Pay");
        Button backButton = new Button("Back");
        TextArea receiptArea = new TextArea();
        receiptArea.setEditable(false);
        receiptArea.setFont(Font.font("Courier New"));

        VBox mainLayout = new VBox(10, itemCodeLabel, itemCodeField, quantityLabel, quantityField,
                addButton, receiptArea, payButton, backButton);
        mainLayout.setPadding(new Insets(10));
        Scene mainScene = new Scene(mainLayout, 500, 380);

        addButton.setOnAction(e -> {
            String itemCode = itemCodeField.getText();
            try {
                int quantity = Integer.parseInt(quantityField.getText());
                addItemToReceipt(itemCode, quantity, receiptArea);
            } catch (NumberFormatException ex) {
                showAlert("Input Error", "Quantity must be a number.");
            }
        });

        payButton.setOnAction(e -> showReceiptWindow());
        backButton.setOnAction(e -> showWelcomeScreen());

        primaryStage.setScene(mainScene);
    }

    private void addItemToReceipt(String itemCode, int quantity, TextArea receiptArea) {
        try {
            out.writeObject("ITEM");
            out.writeObject(itemCode);
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
                receiptArea.appendText(message.equals("Invalid item code.")
                        ? "Invalid item code.\n" : "Unknown error occurred.\n");
            }
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
            showAlert("Error", "Failed to add item. The assigned server may have gone down - "
                    + "try restarting the app so the load balancer can assign a different one.");
        }
    }

    private void showReceiptWindow() {
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

        totalAmount = 0.0;
        receiptBuilder.setLength(0);

        receiptStage.setOnHiding(e -> showWelcomeScreen());
    }

    // ================================================================
    // Shared helper
    // ================================================================

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
