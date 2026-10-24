package com.loyalty.common;

public record LedgerEvent(String eventId, String memberId, String kind, int points, int balanceAfter) {
}
