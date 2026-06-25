package org.jumpmind.symmetric.util;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class CounterStatTest {
    @Test
    void newCounterStatStartsAtZero() {
        CounterStat stat = new CounterStat("myError");
        assertEquals(0, stat.getCount());
        assertEquals("myError", stat.getObject());
    }

    @Test
    void twoArgConstructorSetsInitialCount() {
        CounterStat stat = new CounterStat("myError", 5);
        assertEquals(5, stat.getCount());
        assertEquals("myError", stat.getObject());
    }

    @Test
    void incrementCountIncreasesByTwo() {
        CounterStat stat = new CounterStat("myError");
        stat.incrementCount();
        stat.incrementCount();
        assertEquals(2, stat.getCount());
    }
}
