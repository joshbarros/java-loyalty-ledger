package com.loyalty.common;

public record FulfillmentCommand(String commandId, String memberId, String rewardCode, int points) {
}
