package com.lukaswhite.pos.blackjack;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

/**
 * A shuffled stack of playing cards you can draw from one at a time.
 */
public class Deck {

    private final Deque<Card> cards = new ArrayDeque<>();

    public Deck() {
        this(1);
    }

    /**
     * Builds {@code numDecks} standard 52-card decks shuffled together.
     * Real casinos use several decks combined into one "shoe" so the
     * table doesn't have to reshuffle every hand and so card-counting is
     * harder - here it mostly just means we never run dry mid-game.
     */
    public Deck(int numDecks) {
        String[] ranks = {"A", "2", "3", "4", "5", "6", "7", "8", "9", "10", "J", "Q", "K"};
        String[] suits = {"\u2660", "\u2665", "\u2666", "\u2663"}; // spades, hearts, diamonds, clubs

        List<Card> fresh = new ArrayList<>();
        for (int d = 0; d < numDecks; d++) {
            for (String suit : suits) {
                for (String rank : ranks) {
                    fresh.add(new Card(rank, suit));
                }
            }
        }

        Collections.shuffle(fresh);
        cards.addAll(fresh);
    }

    public Card draw() {
        if (cards.isEmpty()) {
            throw new IllegalStateException("Deck is empty - nothing left to draw.");
        }
        return cards.removeFirst();
    }

    public int remaining() {
        return cards.size();
    }
}
