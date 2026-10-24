package com.loyalty.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class PointsPolicyTest {

    @Test
    void debitsAndCredits() {
        assertEquals(60, PointsPolicy.debit(100, 40));
        assertEquals(15, PointsPolicy.credit(10, 5));
    }

    @Test
    void rejectsNonPositiveAndOverdraft() {
        assertThrows(IllegalArgumentException.class, () -> PointsPolicy.debit(10, 0));
        assertThrows(IllegalArgumentException.class, () -> PointsPolicy.credit(10, -1));
        assertThrows(IllegalStateException.class, () -> PointsPolicy.debit(10, 11));
    }
}
