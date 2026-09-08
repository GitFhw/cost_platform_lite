package com.ruoyi.system.service.impl.cost;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CostDistributedLockSupportTest {
    @Test
    void businessExceptionIsNotExecutedTwice() {
        CostDistributedLockSupport support = new CostDistributedLockSupport();
        AtomicInteger calls = new AtomicInteger();

        assertThrows(IllegalStateException.class, () -> support.executeWithLock(
                "cost:test:exception", "操作繁忙", 10L, () -> {
                    calls.incrementAndGet();
                    throw new IllegalStateException("业务失败");
                }));

        assertEquals(1, calls.get());
        assertEquals("ok", support.executeWithLock(
                "cost:test:exception", "操作繁忙", 10L, () -> "ok"));
    }

    @Test
    void localLockIsExclusiveAndReleasesAfterExecution() throws Exception {
        CostDistributedLockSupport support = new CostDistributedLockSupport();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<Boolean> first = executor.submit(() -> support.executeTaskDispatchLockOrSkip(7L, () -> {
                calls.incrementAndGet();
                entered.countDown();
                try {
                    release.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }));

            assertTrue(entered.await(5, TimeUnit.SECONDS));
            assertFalse(support.executeTaskDispatchLockOrSkip(7L, calls::incrementAndGet));
            release.countDown();
            assertTrue(first.get(5, TimeUnit.SECONDS));
            assertTrue(support.executeTaskDispatchLockOrSkip(7L, calls::incrementAndGet));
            assertEquals(2, calls.get());
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }
}
