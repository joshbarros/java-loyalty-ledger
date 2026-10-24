package com.loyalty.common;

public final class Channels {

    public static final String ENTRIES = "ledger.entries";
    public static final String COMPENSATIONS = "ledger.compensations";
    public static final String FULFILL = "reward.fulfill";
    public static final String FULFILL_DLQ = "reward.fulfill.dlq";

    private Channels() {
    }
}
