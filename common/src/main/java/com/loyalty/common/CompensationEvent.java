package com.loyalty.common;

public record CompensationEvent(String commandId, String memberId, int points) {
}
