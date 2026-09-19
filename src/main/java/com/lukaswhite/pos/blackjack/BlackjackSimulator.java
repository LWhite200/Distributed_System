package com.lukaswhite.pos.blackjack;

/**
 * Runs a bunch of automated blackjack hands and reports how the "player"
 * did - the fun, literal "simulation" half of the blackjack addition
 * (see BlackjackScreen in the client package for the interactive version
 * you can actually play).
 *
 * Usage:
 *   java com.lukaswhite.pos.blackjack.BlackjackSimulator [numHands]
 *   (or: mvn exec:java -Dexec.mainClass="com.lukaswhite.pos.blackjack.BlackjackSimulator" -Dexec.args="50000")
 *
 * Strategy is intentionally simple - both the simulated player and the
 * dealer hit on anything under 17 and stand on 17+ (the standard dealer
 * rule). Real "basic strategy" also factors in the dealer's up-card and
 * whether to double down or split, which would play noticeably better
 * than this - that's an interesting exercise if you want to extend it,
 * and part of why this version's house edge comes out higher than a
 * real casino's.
 */
public class BlackjackSimulator {

    private static final int STAND_ON = 17;
    private static final double BET = 10.0;

    public static void main(String[] args) {
        int numHands = args.length >= 1 ? Integer.parseInt(args[0]) : 10_000;

        int wins = 0, losses = 0, pushes = 0, blackjacks = 0;
        double bankroll = 0.0;

        for (int i = 0; i < numHands; i++) {
            // A fresh, freshly-shuffled deck every hand keeps the math
            // simple (no shoe penetration/reshuffle-point logic to worry
            // about) at the cost of not being quite how a real table runs.
            Deck deck = new Deck(1);
            Hand player = new Hand();
            Hand dealer = new Hand();

            player.add(deck.draw());
            dealer.add(deck.draw());
            player.add(deck.draw());
            dealer.add(deck.draw());

            while (player.value() < STAND_ON) {
                player.add(deck.draw());
            }

            double outcome;
            if (player.isBlackjack() && !dealer.isBlackjack()) {
                outcome = BET * 1.5; // blackjack traditionally pays 3:2
                blackjacks++;
                wins++;
            } else if (player.isBust()) {
                outcome = -BET;
                losses++;
            } else {
                while (dealer.value() < STAND_ON) {
                    dealer.add(deck.draw());
                }
                if (dealer.isBust() || player.value() > dealer.value()) {
                    outcome = BET;
                    wins++;
                } else if (player.value() < dealer.value()) {
                    outcome = -BET;
                    losses++;
                } else {
                    outcome = 0;
                    pushes++;
                }
            }
            bankroll += outcome;
        }

        System.out.println("=== Blackjack Simulation ===");
        System.out.printf("Hands played : %,d%n", numHands);
        System.out.printf("Wins         : %,d (%.1f%%)%n", wins, 100.0 * wins / numHands);
        System.out.printf("  of which blackjacks: %,d%n", blackjacks);
        System.out.printf("Losses       : %,d (%.1f%%)%n", losses, 100.0 * losses / numHands);
        System.out.printf("Pushes       : %,d (%.1f%%)%n", pushes, 100.0 * pushes / numHands);
        System.out.printf("Net result   : $%.2f on $%.2f/hand ($%,.2f total wagered)%n",
                bankroll, BET, BET * numHands);
        System.out.printf("House edge (approx): %.2f%%%n", -100.0 * bankroll / (BET * numHands));
    }
}
