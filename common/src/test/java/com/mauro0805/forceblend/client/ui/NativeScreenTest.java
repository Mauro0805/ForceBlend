package com.mauro0805.forceblend.client.ui;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class NativeScreenTest {
    @Test
    void legacyContinuationWaitsUntilAfterScreenTick() throws ReflectiveOperationException {
        LegacyClient client = new LegacyClient();
        verifyDeferredTransition(client, client.pending);
    }

    @Test
    void modernContinuationWaitsUntilAfterScreenTick() throws ReflectiveOperationException {
        ModernClient client = new ModernClient();
        verifyDeferredTransition(client, client.pending);
    }

    private static void verifyDeferredTransition(Object client, Queue<Runnable> pending)
        throws ReflectiveOperationException {
        byte[] bytes = NativeScreen.adapterBytes(ScreenFixture.class, String.class, "init", "tick", "onClose");
        Class<?> adapter = new AdapterLoader().define(bytes);
        Object[] current = new Object[1];
        AtomicInteger opened = new AtomicInteger();
        Runnable tick = () -> NativeScreen.enqueue(client, () -> {
            current[0] = null;
            opened.incrementAndGet();
        });
        Object screen = adapter.getConstructor(String.class, Consumer.class, Runnable.class, Runnable.class)
            .newInstance("Complete", (Consumer<Object>) ignored -> { }, tick, (Runnable) () -> { });
        current[0] = screen;
        adapter.getMethod("tick").invoke(screen);
        // Fabric 1.18.2 reads the current screen again after Screen.tick returns.
        assertSame(screen, Objects.requireNonNull(current[0], "Screen cannot be null"));
        assertEquals(0, opened.get());
        assertEquals(1, pending.size());
        pending.remove().run();
        assertEquals(1, opened.get());
    }

    public static class LegacyClient {
        final Queue<Runnable> pending = new ArrayDeque<>();

        public void method_18858(Runnable action) {
            pending.add(action);
        }

        public void execute(Runnable action) {
            action.run();
        }
    }

    public static class ModernClient {
        final Queue<Runnable> pending = new ArrayDeque<>();

        public void schedule(Runnable action) {
            pending.add(action);
        }

        public void execute(Runnable action) {
            action.run();
        }
    }

    @Test
    void officialLifecycleUsesNativeParentAndRunsCallbacks() throws ReflectiveOperationException {
        verify("init", "tick", "onClose");
    }

    @Test
    void intermediaryLifecycleUsesTheSameCallbacks() throws ReflectiveOperationException {
        verify("method_25426", "method_25393", "method_25419");
    }

    private static void verify(String init, String tick, String close) throws ReflectiveOperationException {
        byte[] bytes = NativeScreen.adapterBytes(ScreenFixture.class, String.class, init, tick, close);
        Class<?> adapter = new AdapterLoader().define(bytes);
        AtomicInteger initialized = new AtomicInteger();
        AtomicInteger ticks = new AtomicInteger();
        AtomicInteger closed = new AtomicInteger();
        Object[] initializedScreen = new Object[1];
        Consumer<Object> initializer = screen -> {
            initialized.incrementAndGet();
            initializedScreen[0] = screen;
        };
        Object screen = adapter.getConstructor(String.class, Consumer.class, Runnable.class, Runnable.class)
            .newInstance("ForceBlend", initializer, (Runnable) ticks::incrementAndGet, (Runnable) closed::incrementAndGet);
        assertEquals("ForceBlend", ((ScreenFixture) screen).title);
        Method initialize = adapter.getMethod(init);
        initialize.invoke(screen);
        initialize.invoke(screen);
        adapter.getMethod(tick).invoke(screen);
        adapter.getMethod(close).invoke(screen);
        assertSame(screen, initializedScreen[0]);
        assertEquals(2, initialized.get(), "Resizing must rebuild the widgets");
        assertEquals(1, ticks.get());
        assertEquals(1, closed.get());
    }

    public static class ScreenFixture {
        final String title;

        protected ScreenFixture(String title) {
            this.title = title;
        }

        protected void init() { }
        public void tick() { }
        public void onClose() { }
        protected void method_25426() { }
        public void method_25393() { }
        public void method_25419() { }
    }

    private static final class AdapterLoader extends ClassLoader {
        Class<?> define(byte[] bytes) {
            return defineClass(null, bytes, 0, bytes.length);
        }
    }
}
