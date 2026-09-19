package com.lukaswhite.pos.blackjack;

/**
 * A single playing card. This is a "record" - a newer, compact bit of
 * Java (16+) for a small immutable data holder: writing
 * "record Card(String rank, String suit) {}" gets you a constructor,
 * getters (rank() and suit()), equals/hashCode, and toString for free,
 * without typing all of that out by hand.
 */
public record Card(String rank, String suit) {

    /**
     * Blackjack's base value for this card. Aces come back as 11 here -
     * Hand.value() is what knows how to soften an Ace down to 1 if
     * counting it as 11 would bust the hand.
     */
    public int baseValue() {
        return switch (rank) {
            case "A" -> 11;
            case "K", "Q", "J" -> 10;
            default -> Integer.parseInt(rank);
        };
    }

    @Override
    public String toString() {
        return rank + suit;
    }
}
