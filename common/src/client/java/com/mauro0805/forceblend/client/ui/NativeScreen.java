package com.mauro0805.forceblend.client.ui;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.function.Consumer;

/**
 * Keeps the client UI independent of Minecraft's namespace and rendering signatures.
 * Only the Screen lifecycle is adapted; rendering and input remain native widgets.
 */
public final class NativeScreen {
    private static RuntimeUi runtime;

    private final RuntimeUi ui;
    private final Object screen;
    private Consumer<NativeScreen> initialize = ignored -> { };
    private Runnable tick = () -> { };
    private Runnable close = () -> { };
    private int row;

    public NativeScreen(String title) {
        try {
            ui = runtime();
            screen = ui.adapter.newInstance(ui.text(title), (Consumer<Object>) ignored -> initialize(),
                (Runnable) () -> tick.run(), (Runnable) () -> close.run());
        } catch (ReflectiveOperationException exception) {
            throw failure(exception);
        }
    }

    public static Object client() {
        try {
            return runtime().client;
        } catch (ReflectiveOperationException exception) {
            throw failure(exception);
        }
    }

    public static Object current() {
        try {
            Object owner = screenOwner(runtime().client);
            return field(owner.getClass(), "screen", "field_1755").get(owner);
        } catch (ReflectiveOperationException exception) {
            throw failure(exception);
        }
    }

    public static void show(Object screen) {
        try {
            Object owner = screenOwner(runtime().client);
            method(owner.getClass(), 1, "setScreen", "method_1507").invoke(owner, screen);
        } catch (ReflectiveOperationException exception) {
            throw failure(exception);
        }
    }

    /** Defers screen transitions until the current screen tick and its event hooks finish. */
    public static void enqueue(Runnable action) {
        enqueue(client(), action);
    }

    static void enqueue(Object client, Runnable action) {
        try {
            // Executor.execute can run inline on the render thread; these methods always queue.
            method(client.getClass(), 1, "schedule", "tell", "method_18858").invoke(client, action);
        } catch (ReflectiveOperationException exception) {
            throw failure(exception);
        }
    }

    /** Minecraft 26.2 moved the current screen and {@code setScreen} from {@code Minecraft} to its {@code Gui}. */
    private static Object screenOwner(Object client) throws ReflectiveOperationException {
        try {
            method(client.getClass(), 1, "setScreen", "method_1507");
            return client;
        } catch (NoSuchMethodException exception) {
            return field(client.getClass(), "gui").get(client);
        }
    }

    public Object handle() {
        return screen;
    }

    public NativeScreen content(Consumer<NativeScreen> initializer) {
        initialize = initializer;
        return this;
    }

    public NativeScreen onTick(Runnable action) {
        tick = action;
        return this;
    }

    public NativeScreen onClose(Runnable action) {
        close = action;
        return this;
    }

    private void initialize() {
        row = 0;
        initialize.accept(this);
    }

    public Object line(String text) {
        return button(text, null);
    }

    public Object button(String label, Runnable action) {
        try {
            int width = Math.min(340, field(ui.screenType, "width", "field_22789").getInt(screen) - 20);
            int x = (field(ui.screenType, "width", "field_22789").getInt(screen) - width) / 2;
            int y = 10 + row++ * 22;
            Object callback = Proxy.newProxyInstance(ui.pressType.getClassLoader(), new Class<?>[]{ui.pressType},
                (proxy, invoked, arguments) -> {
                    if (invoked.getDeclaringClass() == Object.class) {
                        return switch (invoked.getName()) {
                            case "hashCode" -> System.identityHashCode(proxy);
                            case "equals" -> arguments != null && proxy == arguments[0];
                            default -> "ForceBlend button action";
                        };
                    }
                    if (action != null) {
                        action.run();
                    }
                    return null;
                });
            Object widget;
            if (ui.builder != null) {
                Object builder = ui.builder.invoke(null, ui.text(label), callback);
                Method bounds = null;
                Method build = null;
                for (Method candidate : builder.getClass().getMethods()) {
                    if (candidate.getParameterCount() == 4
                        && candidate.getParameterTypes()[0] == int.class
                        && candidate.getReturnType() == builder.getClass()) {
                        bounds = candidate;
                    }
                    if (candidate.getParameterCount() == 0
                        && ui.buttonType.isAssignableFrom(candidate.getReturnType())) {
                        build = candidate;
                    }
                }
                if (bounds == null || build == null) {
                    throw new NoSuchMethodException("Minecraft Button builder bounds/build");
                }
                bounds.invoke(builder, x, y, width, 20);
                widget = build.invoke(builder);
            } else {
                widget = ui.buttonType.getConstructor(int.class, int.class, int.class, int.class,
                    ui.textType, ui.pressType).newInstance(x, y, width, 20, ui.text(label), callback);
            }
            field(ui.buttonType, "active", "field_22763").setBoolean(widget, action != null);
            method(ui.screenType, 1, "addRenderableWidget", "method_37063").invoke(screen, widget);
            return widget;
        } catch (ReflectiveOperationException exception) {
            throw failure(exception);
        }
    }

    public Object input(String value) {
        try {
            Class<?> editType = type("net.minecraft.client.gui.components.EditBox", "net.minecraft.class_342");
            Object font = field(ui.client.getClass(), "font", "field_1772").get(ui.client);
            int width = Math.min(340, field(ui.screenType, "width", "field_22789").getInt(screen) - 20);
            int x = (field(ui.screenType, "width", "field_22789").getInt(screen) - width) / 2;
            int y = 10 + row++ * 22;
            Constructor<?> constructor = editType.getConstructor(font.getClass(), int.class, int.class,
                int.class, int.class, ui.textType);
            Object edit = constructor.newInstance(font, x, y, width, 20, ui.text(value));
            method(editType, 1, "setValue", "method_1852").invoke(edit, value);
            method(ui.screenType, 1, "addRenderableWidget", "method_37063").invoke(screen, edit);
            return edit;
        } catch (ReflectiveOperationException exception) {
            throw failure(exception);
        }
    }

    public static String value(Object input) {
        try {
            return (String) method(input.getClass(), 0, "getValue", "method_1882").invoke(input);
        } catch (ReflectiveOperationException exception) {
            throw failure(exception);
        }
    }

    public void label(Object widget, String label) {
        try {
            method(ui.buttonType, 1, "setMessage", "method_25355").invoke(widget, ui.text(label));
        } catch (ReflectiveOperationException exception) {
            throw failure(exception);
        }
    }

    private static synchronized RuntimeUi runtime() throws ReflectiveOperationException {
        if (runtime == null) {
            runtime = new RuntimeUi();
        }
        return runtime;
    }

    private static Class<?> type(String official, String intermediary) throws ClassNotFoundException {
        try {
            return Class.forName(official);
        } catch (ClassNotFoundException exception) {
            return Class.forName(intermediary);
        }
    }

    private static Field field(Class<?> owner, String... names) throws NoSuchFieldException {
        for (Class<?> current = owner; current != null; current = current.getSuperclass()) {
            for (String name : names) {
                try {
                    Field result = current.getDeclaredField(name);
                    result.setAccessible(true);
                    return result;
                } catch (NoSuchFieldException ignored) {
                    // The same field uses a different namespace on older releases.
                }
            }
        }
        throw new NoSuchFieldException(String.join(" / ", names));
    }

    private static Method method(Class<?> owner, int parameters, String... names) throws NoSuchMethodException {
        for (Class<?> current = owner; current != null; current = current.getSuperclass()) {
            for (Method candidate : current.getDeclaredMethods()) {
                for (String name : names) {
                    if (candidate.getName().equals(name) && candidate.getParameterCount() == parameters) {
                        candidate.setAccessible(true);
                        return candidate;
                    }
                }
            }
        }
        throw new NoSuchMethodException(String.join(" / ", names));
    }

    private static IllegalStateException failure(ReflectiveOperationException exception) {
        return new IllegalStateException("Minecraft's native ForceBlend UI could not be initialized", exception);
    }

    private static final class RuntimeUi {
        final Class<?> screenType = type("net.minecraft.client.gui.screens.Screen", "net.minecraft.class_437");
        final Class<?> textType = type("net.minecraft.network.chat.Component", "net.minecraft.class_2561");
        final Class<?> buttonType = type("net.minecraft.client.gui.components.Button", "net.minecraft.class_4185");
        final Class<?> pressType = type("net.minecraft.client.gui.components.Button$OnPress",
            "net.minecraft.class_4185$class_4241");
        final Object client;
        final Method builder;
        final Constructor<?> adapter;

        RuntimeUi() throws ReflectiveOperationException {
            Class<?> clientType = type("net.minecraft.client.Minecraft", "net.minecraft.class_310");
            client = method(clientType, 0, "getInstance", "method_1551").invoke(null);
            Method foundBuilder = null;
            for (Method candidate : buttonType.getDeclaredMethods()) {
                if (Modifier.isStatic(candidate.getModifiers()) && candidate.getParameterCount() == 2
                    && candidate.getParameterTypes()[0] == textType
                    && candidate.getParameterTypes()[1] == pressType) {
                    foundBuilder = candidate;
                    break;
                }
            }
            builder = foundBuilder;
            byte[] bytes = adapterBytes(screenType, textType,
                method(screenType, 0, "init", "method_25426").getName(),
                method(screenType, 0, "tick", "method_25393").getName(),
                method(screenType, 0, "onClose", "method_25419").getName());
            adapter = MethodHandles.lookup().defineClass(bytes)
                .getConstructor(textType, Consumer.class, Runnable.class, Runnable.class);
        }

        Object text(String value) throws ReflectiveOperationException {
            try {
                return method(textType, 1, "literal", "method_43470").invoke(null, value);
            } catch (NoSuchMethodException exception) {
                return Class.forName("net.minecraft.class_2585").getConstructor(String.class).newInstance(value);
            }
        }
    }

    static byte[] adapterBytes(Class<?> screenType, Class<?> textType, String init, String tick, String close) {
        String name = Type.getInternalName(NativeScreen.class) + "$Adapter";
        String parent = Type.getInternalName(screenType);
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL, name, null, parent, null);
        String[] fields = {"initializer", "ticker", "closer"};
        String[] descriptors = {"Ljava/util/function/Consumer;", "Ljava/lang/Runnable;", "Ljava/lang/Runnable;"};
        for (int index = 0; index < fields.length; index++) {
            writer.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL, fields[index], descriptors[index], null, null).visitEnd();
        }
        MethodVisitor constructor = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>",
            "(" + Type.getDescriptor(textType) + descriptors[0] + descriptors[1] + descriptors[2] + ")V", null, null);
        constructor.visitCode();
        constructor.visitVarInsn(Opcodes.ALOAD, 0);
        constructor.visitVarInsn(Opcodes.ALOAD, 1);
        constructor.visitMethodInsn(Opcodes.INVOKESPECIAL, parent, "<init>", "(" + Type.getDescriptor(textType) + ")V", false);
        for (int index = 0; index < fields.length; index++) {
            constructor.visitVarInsn(Opcodes.ALOAD, 0);
            constructor.visitVarInsn(Opcodes.ALOAD, index + 2);
            constructor.visitFieldInsn(Opcodes.PUTFIELD, name, fields[index], descriptors[index]);
        }
        constructor.visitInsn(Opcodes.RETURN);
        constructor.visitMaxs(0, 0);
        constructor.visitEnd();
        String[] methods = {init, tick, close};
        for (int index = 0; index < methods.length; index++) {
            MethodVisitor visitor = writer.visitMethod(Opcodes.ACC_PUBLIC, methods[index], "()V", null, null);
            visitor.visitCode();
            visitor.visitVarInsn(Opcodes.ALOAD, 0);
            visitor.visitFieldInsn(Opcodes.GETFIELD, name, fields[index], descriptors[index]);
            if (index == 0) {
                visitor.visitVarInsn(Opcodes.ALOAD, 0);
                visitor.visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/util/function/Consumer", "accept", "(Ljava/lang/Object;)V", true);
            } else {
                visitor.visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/lang/Runnable", "run", "()V", true);
            }
            visitor.visitInsn(Opcodes.RETURN);
            visitor.visitMaxs(0, 0);
            visitor.visitEnd();
        }
        writer.visitEnd();
        return writer.toByteArray();
    }
}
