package com.lukaswhite.pos.blackjack;

import java.util.ArrayList;
import java.util.List;

/**
 * A hand of cards - either the player's or the dealer's - and the
 * blackjack-specific rules for what it's worth.
 */
public class Hand {

    private final List<Card> cards = new ArrayList<>();

    public void add(Card card) {
        cards.add(card);
    }

    public List<Card> getCards() {
        return cards;
    }

    /**
     * Total value of the hand. Aces count as 11 by default, but if that
     * would push the total over 21, they drop to 1 one at a time until
     * the hand fits (or there are no more Aces left to soften).
     */
    public int value() {
        int total = 0;
        int aces = 0;

        for (Card card : cards) {
            total += card.baseValue();
            if (card.rank().equals("A")) {
                aces++;
            }
        }

        while (total > 21 && aces > 0) {
            total -= 10; // re-count one Ace as 1 instead of 11
            aces--;
        }

        return total;
    }

    public boolean isBust() {
        return value() > 21;
    }

    /** A two-card 21 dealt straight from the shoe - beats an ordinary 21 built up over more cards. */
    public boolean isBlackjack() {
        return cards.size() == 2 && value() == 21;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (Card card : cards) {
            sb.append(card).append(' ');
        }
        return sb.toString().trim();
    }
}
