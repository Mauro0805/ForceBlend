package com.mauro0805.forceblend.bukkit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WorkerBudgetTest {
    @Test
    void capsLargeCoreCountsWithoutIncreasingTheHeap() {
        assertEquals(new WorkerBudget(512, 2), WorkerBudget.forHeap(0, 256, 512));
        assertEquals(1, WorkerBudget.forHeap(0, 256, 256).threads());
        assertEquals(6, WorkerBudget.forHeap(0, 256, 1024).threads());
    }

    @Test
    void honorsLowerProcessorAndExplicitThreadLimits() {
        assertEquals(1, WorkerBudget.forHeap(1, 256, 512).threads());
        assertEquals(2, WorkerBudget.forHeap(256, 2, 4096).threads());
        assertThrows(IllegalArgumentException.class, () -> WorkerBudget.forHeap(-1, 8, 512));
        assertThrows(IllegalArgumentException.class, () -> WorkerBudget.forHeap(0, 0, 512));
    }

    @Test
    void refusesInvalidHeapOverrides() {
        String original = System.getProperty(WorkerBudget.HEAP_PROPERTY);
        try {
            for (String value : new String[]{"0", "255", "4097", "all", "999999999999"}) {
                System.setProperty(WorkerBudget.HEAP_PROPERTY, value);
                assertThrows(IllegalArgumentException.class, () -> WorkerBudget.configured(0, 256));
            }
            System.clearProperty(WorkerBudget.HEAP_PROPERTY);
            assertEquals(new WorkerBudget(512, 2), WorkerBudget.configured(0, 256));
        } finally {
            if (original == null) System.clearProperty(WorkerBudget.HEAP_PROPERTY);
            else System.setProperty(WorkerBudget.HEAP_PROPERTY, original);
        }
    }
}
