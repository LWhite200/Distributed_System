package com.lukaswhite.pos.client;

import com.lukaswhite.pos.blackjack.Deck;
import com.lukaswhite.pos.blackjack.Hand;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * A small, entirely self-contained blackjack mini-game - just for fun,
 * no networking or database involved, so it works even if you haven't
 * connected to a load balancer yet. Bet is a fixed $10/hand and your
 * play-money bankroll persists for as long as this window stays open.
 *
 * Dealer plays the standard casino rule: hit while under 17, stand on
 * 17 or more - same logic BlackjackSimulator uses for both sides, so if
 * you like this, running BlackjackSimulator's main() shows you how the
 * odds actually work out over thousands of hands.
 */
public class BlackjackScreen {

    private static final double BET = 10.0;
    private static final int DEALER_STANDS_ON = 17;

    private Deck deck;
    private Hand player;
    private Hand dealer;
    private double bankroll = 100.0;

    private final Label dealerLabel = new Label();
    private final Label playerLabel = new Label();
    private final Label resultLabel = new Label("Press \"Deal\" to start!");
    private final Label bankrollLabel = new Label();

    private final Button hitButton = new Button("Hit");
    private final Button standButton = new Button("Stand");
    private final Button dealButton = new Button("Deal");

    /** Opens the game in its own window. Call this from anywhere, e.g. a button on the welcome screen. */
    public static void open() {
        new BlackjackScreen().show();
    }

    private void show() {
        Stage stage = new Stage();
        stage.setTitle("Blackjack (just for fun)");

        dealerLabel.setStyle("-fx-font-family: 'Courier New'; -fx-font-size: 14;");
        playerLabel.setStyle("-fx-font-family: 'Courier New'; -fx-font-size: 14;");
        resultLabel.setStyle("-fx-font-weight: bold;");

        hitButton.setOnAction(e -> onHit());
        standButton.setOnAction(e -> onStand());
        dealButton.setOnAction(e -> onDeal());
        setInHandControls(false);

        HBox buttons = new HBox(10, dealButton, hitButton, standButton);
        buttons.setAlignment(Pos.CENTER);

        VBox layout = new VBox(12,
                new Label("Dealer:"), dealerLabel,
                new Label("You:"), playerLabel,
                resultLabel, bankrollLabel, buttons);
        layout.setPadding(new Insets(15));
        layout.setAlignment(Pos.CENTER);

        updateBankrollLabel();

        stage.setScene(new Scene(layout, 380, 320));
        stage.show();
    }

    private void onDeal() {
        if (bankroll < BET) {
            resultLabel.setText("Out of chips! Close and reopen the window to play again.");
            return;
        }

        deck = new Deck(1);
        player = new Hand();
        dealer = new Hand();
        player.add(deck.draw());
        dealer.add(deck.draw());
        player.add(deck.draw());
        dealer.add(deck.draw());

        resultLabel.setText("");
        renderHands(true);
        setInHandControls(true);

        if (player.isBlackjack()) {
            onStand(); // nothing left to decide - resolve immediately
        }
    }

    private void onHit() {
        player.add(deck.draw());
        renderHands(true);
        if (player.isBust()) {
            finishHand("Bust! You lose $" + (int) BET + ".", -BET);
        }
    }

    private void onStand() {
        while (dealer.value() < DEALER_STANDS_ON) {
            dealer.add(deck.draw());
        }
        renderHands(false);

        if (player.isBlackjack() && !dealer.isBlackjack()) {
            finishHand("Blackjack! You win $" + (BET * 1.5) + ".", BET * 1.5);
        } else if (dealer.isBust() || player.value() > dealer.value()) {
            finishHand("You win $" + (int) BET + "!", BET);
        } else if (player.value() < dealer.value()) {
            finishHand("Dealer wins. You lose $" + (int) BET + ".", -BET);
        } else {
            finishHand("Push - it's a tie, bet returned.", 0);
        }
    }

    private void finishHand(String message, double outcome) {
        bankroll += outcome;
        resultLabel.setText(message);
        updateBankrollLabel();
        setInHandControls(false);
    }

    private void renderHands(boolean hideDealerHoleCard) {
        playerLabel.setText(player + "   (total: " + player.value() + ")");
        if (hideDealerHoleCard && !dealer.getCards().isEmpty()) {
            dealerLabel.setText(dealer.getCards().get(0) + " ??   (total: ?)");
        } else {
            dealerLabel.setText(dealer + "   (total: " + dealer.value() + ")");
        }
    }

    private void setInHandControls(boolean inHand) {
        hitButton.setDisable(!inHand);
        standButton.setDisable(!inHand);
        dealButton.setDisable(inHand);
    }

    private void updateBankrollLabel() {
        bankrollLabel.setText(String.format("Bankroll: $%.2f", bankroll));
    }
}
