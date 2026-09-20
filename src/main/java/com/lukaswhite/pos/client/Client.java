package com.lukaswhite.pos.client;

import com.lukaswhite.pos.common.ProductSpec;
import com.lukaswhite.pos.common.ScheduleResult;
import com.lukaswhite.pos.loadbalancer.LoadBalancer;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
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
import java.util.ArrayList;
import java.util.List;

/**
 * JavaFX point-of-sale AND service-scheduling client. This is now the
 * combined front end for three former projects:
 *   - the retail item/receipt homework ("New Sale")
 *   - the Turbo Auto Service scheduling homework ("Schedule Service" /
 *     "View Mechanic Schedule & Pay")
 *   - a for-fun Blackjack mini-game (entirely local, see BlackjackScreen)
 *
 * Connection flow (unchanged from the distributed version): the user
 * types in the LOAD BALANCER's IP, we ask it which ServerNode to use,
 * then open a direct connection to that node and do everything else -
 * item lookups AND scheduling - over that one connection, using a
 * command-tag protocol (see ClientHandler on the server side): every
 * request starts with a String naming the command ("ITEM", "SCHEDULE",
 * "VIEW_SCHEDULE", "GET_SERVICES"), followed by that command's arguments,
 * and gets back exactly one response object.
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
        primaryStage.setTitle("Turbo Auto Service");

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
    // node it names. Unchanged from the distributed-only version.
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
    // Welcome screen - the hub for all three "apps" this client offers.
    // ================================================================

    private void showWelcomeScreen() {
        Label welcomeLabel = new Label("Welcome To White's Shop & Turbo Auto Service");
        Button newSaleButton = new Button("New Sale");
        Button scheduleButton = new Button("Schedule Service");
        Button viewScheduleButton = new Button("View Mechanic Schedule & Pay");

        VBox welcomeLayout = new VBox(15, welcomeLabel,
                newSaleButton, scheduleButton, viewScheduleButton);
        welcomeLayout.setAlignment(Pos.CENTER);
        welcomeLayout.setPadding(new Insets(20));
        Scene welcomeScene = new Scene(welcomeLayout, 420, 280);

        newSaleButton.setOnAction(e -> showMainScreen());
        scheduleButton.setOnAction(e -> showScheduleScreen());
        viewScheduleButton.setOnAction(e -> showViewScheduleScreen());

        primaryStage.setScene(welcomeScene);
    }

    // ================================================================
    // "New Sale" - retail item purchase, unchanged from before except
    // that every item request is now tagged with the "ITEM" command.
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
    // "Schedule Service" - new: ported from the Turbo Auto Service
    // homework's DataImporter/Scheduler, but driven from the GUI one
    // customer/vehicle/service at a time instead of a bulk text file.
    // ================================================================

    private void showScheduleScreen() {
        Label customerLabel = new Label("Customer Name:");
        TextField customerField = new TextField();
        Label vehicleLabel = new Label("Vehicle Description:");
        TextField vehicleField = new TextField();
        Label serviceLabel = new Label("Service:");
        ComboBox<String> serviceCombo = new ComboBox<>();
        serviceCombo.setPromptText("Loading services...");
        Button submitButton = new Button("Schedule");
        Button backButton = new Button("Back");
        Label statusLabel = new Label();
        statusLabel.setWrapText(true);

        VBox layout = new VBox(10, customerLabel, customerField, vehicleLabel, vehicleField,
                serviceLabel, serviceCombo, submitButton, statusLabel, backButton);
        layout.setPadding(new Insets(15));
        Scene scene = new Scene(layout, 420, 340);

        // Populate the service dropdown from whatever the server's
        // SERVICES_TABLE currently has, so the GUI never gets out of
        // sync with the database (e.g. if new services are added later).
        List<String> catalog = fetchServiceCatalog();
        serviceCombo.getItems().addAll(catalog);
        serviceCombo.setPromptText(catalog.isEmpty() ? "No services available" : "Select a service");

        submitButton.setOnAction(e -> {
            String customer = customerField.getText().trim();
            String vehicle = vehicleField.getText().trim();
            String serviceChoice = serviceCombo.getValue();

            if (customer.isEmpty() || vehicle.isEmpty() || serviceChoice == null) {
                statusLabel.setText("Please fill in customer, vehicle, and service.");
                return;
            }

            // The dropdown shows "Oil Change (30 min)" - strip the
            // duration suffix back off before sending just the name.
            String serviceName = serviceChoice.replaceAll("\\s*\\(\\d+ min\\)$", "");

            ScheduleResult result = scheduleService(customer, vehicle, serviceName);
            if (result == null) {
                statusLabel.setText("Could not reach the server.");
                return;
            }

            statusLabel.setText(result.getMessage());
            if (result.isSuccess()) {
                customerField.clear();
                vehicleField.clear();
                serviceCombo.setValue(null);
            }
        });

        backButton.setOnAction(e -> showWelcomeScreen());

        primaryStage.setScene(scene);
    }

    private List<String> fetchServiceCatalog() {
        try {
            out.writeObject("GET_SERVICES");
            out.flush();
            Object response = in.readObject();
            if (response instanceof List<?> list) {
                List<String> catalog = new ArrayList<>();
                for (Object item : list) {
                    catalog.add((String) item);
                }
                return catalog;
            }
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
            showAlert("Error", "Could not load the service list: " + e.getMessage());
        }
        return new ArrayList<>();
    }

    private ScheduleResult scheduleService(String customerName, String vehicleDescription, String serviceName) {
        try {
            out.writeObject("SCHEDULE");
            out.writeObject(customerName);
            out.writeObject(vehicleDescription);
            out.writeObject(serviceName);
            out.flush();

            Object response = in.readObject();
            if (response instanceof ScheduleResult result) {
                return result;
            }
            return new ScheduleResult(false, "Unexpected response from server.");

        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
            return null;
        }
    }

    // ================================================================
    // "View Mechanic Schedule & Pay" - new: the read-only reporting
    // screen from the Turbo Auto Service console app, now rendered in a
    // TextArea instead of printed to a terminal.
    // ================================================================

    private void showViewScheduleScreen() {
        Label mechanicLabel = new Label("Mechanic:");
        ComboBox<String> mechanicCombo = new ComboBox<>();
        mechanicCombo.getItems().addAll("Sue", "Steve", "Both");
        mechanicCombo.setValue("Both");

        Label timeFrameLabel = new Label("Time Frame:");
        ComboBox<String> timeFrameCombo = new ComboBox<>();
        timeFrameCombo.getItems().addAll("Day", "Week", "Month", "Year");
        timeFrameCombo.setValue("Day");

        Button viewButton = new Button("View");
        Button backButton = new Button("Back");

        VBox layout = new VBox(10, mechanicLabel, mechanicCombo, timeFrameLabel, timeFrameCombo,
                viewButton, backButton);
        layout.setPadding(new Insets(15));
        Scene scene = new Scene(layout, 340, 260);

        viewButton.setOnAction(e -> {
            String mechanic = mechanicCombo.getValue().toUpperCase();
            String timeFrame = timeFrameCombo.getValue().toUpperCase();
            String report = viewSchedule(mechanic, timeFrame);
            if (report != null) {
                showReportWindow(report);
            }
        });

        backButton.setOnAction(e -> showWelcomeScreen());

        primaryStage.setScene(scene);
    }

    private String viewSchedule(String mechanicFilter, String timeFrame) {
        try {
            out.writeObject("VIEW_SCHEDULE");
            out.writeObject(mechanicFilter);
            out.writeObject(timeFrame);
            out.flush();

            Object response = in.readObject();
            return response instanceof String s ? s : "Unexpected response from server.";

        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
            showAlert("Error", "Could not fetch the schedule: " + e.getMessage());
            return null;
        }
    }

    private void showReportWindow(String report) {
        Stage reportStage = new Stage();
        reportStage.setTitle("Mechanic Schedule & Pay");

        TextArea reportArea = new TextArea(report);
        reportArea.setEditable(false);
        reportArea.setFont(Font.font("Courier New"));
        reportArea.setWrapText(false);

        VBox layout = new VBox(10, reportArea);
        layout.setPadding(new Insets(10));

        reportStage.setScene(new Scene(layout, 650, 450));
        reportStage.show();
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
