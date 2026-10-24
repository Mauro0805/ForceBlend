package com.mauro0805.forceblend.client;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WorldOpenWorkflowTest {
    @Test
    void resumingKeepsArgumentsAndBypassesAnalysis() throws ReflectiveOperationException {
        OpenFixture owner = new OpenFixture();
        Runnable onCancel = () -> { };
        Method method = OpenFixture.class.getMethod("openWorld", String.class, Runnable.class);
        WorldOpenWorkflow.resumeWorldOpen(owner, method, new Object[]{"test world", onCancel});
        assertEquals(1, owner.opens);
        assertEquals("test world", owner.levelId);
        assertSame(onCancel, owner.onCancel);
        assertFalse(owner.intercepted, "The resumed call must not open a second confirmation");
    }

    @Test
    void resumingPreservesMinecraftFailureAndClearsBypass() throws ReflectiveOperationException {
        OpenFixture owner = new OpenFixture();
        Method method = OpenFixture.class.getMethod("fail");
        IllegalStateException failure = assertThrows(IllegalStateException.class,
            () -> WorldOpenWorkflow.resumeWorldOpen(owner, method, new Object[0]));
        assertSame(owner.failure, failure);
        var field = WorldOpenWorkflow.class.getDeclaredField("RESUMING");
        field.setAccessible(true);
        assertEquals(false, ((ThreadLocal<?>) field.get(null)).get());
    }

    public static class OpenFixture {
        int opens;
        String levelId;
        Runnable onCancel;
        boolean intercepted;
        final IllegalStateException failure = new IllegalStateException("Minecraft failure");

        public void openWorld(String levelId, Runnable onCancel) {
            opens++;
            this.levelId = levelId;
            this.onCancel = onCancel;
            AtomicBoolean cancelled = new AtomicBoolean();
            intercepted = WorldOpenWorkflow.intercept(this, "openWorld", new Object[]{levelId, onCancel},
                levelId, null, null, null, () -> cancelled.set(true));
            assertFalse(cancelled.get());
        }

        public void fail() {
            throw failure;
        }
    }
}
