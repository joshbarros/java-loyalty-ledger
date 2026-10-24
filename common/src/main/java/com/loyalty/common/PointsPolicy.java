package com.loyalty.common;

public final class PointsPolicy {

    private PointsPolicy() {
    }

    public static int debit(int balance, int points) {
        if (points <= 0) {
            throw new IllegalArgumentException("points must be positive");
        }
        if (points > balance) {
            throw new IllegalStateException("insufficient balance");
        }
        return balance - points;
    }

    public static int credit(int balance, int points) {
        if (points <= 0) {
            throw new IllegalArgumentException("points must be positive");
        }
        return balance + points;
    }
}
