package com.mauro0805.forceblend.client.config;

import net.fabricmc.loader.api.LanguageAdapter;
import net.fabricmc.loader.api.LanguageAdapterException;
import net.fabricmc.loader.api.ModContainer;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/**
 * Supplies Mod Menu's optional API without linking ForceBlend bytecode to a
 * particular Minecraft Screen namespace or to Mod Menu at compile time.
 */
public final class ReflectiveModMenuLanguageAdapter implements LanguageAdapter {
    private static final String MOD_MENU_API = "com.terraformersmc.modmenu.api.ModMenuApi";

    private static Object invokeModMenuApi(Object proxy, Method method, Object[] arguments) throws Throwable {
        if (method.getName().equals("getModConfigScreenFactory")) {
            Class<?> factoryType = method.getReturnType();
            return Proxy.newProxyInstance(
                factoryType.getClassLoader(),
                new Class<?>[]{factoryType},
                ReflectiveModMenuLanguageAdapter::invokeConfigScreenFactory
            );
        }
        if (method.isDefault()) {
            return InvocationHandler.invokeDefault(proxy, method, normalizedArguments(arguments));
        }
        return objectOrEmptyValue(proxy, method, arguments);
    }

    private static Object invokeConfigScreenFactory(Object proxy, Method method, Object[] arguments) throws Throwable {
        if (method.getName().equals("create") && arguments != null && arguments.length == 1) {
            ForceBlendConfigController.openEditor();
            return arguments[0];
        }
        if (method.isDefault()) {
            return InvocationHandler.invokeDefault(proxy, method, normalizedArguments(arguments));
        }
        return objectOrEmptyValue(proxy, method, arguments);
    }

    private static Object objectOrEmptyValue(Object proxy, Method method, Object[] arguments) {
        return switch (method.getName()) {
            case "toString" -> "ForceBlend optional Mod Menu entrypoint";
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> arguments != null && arguments.length == 1 && proxy == arguments[0];
            default -> emptyValue(method.getReturnType());
        };
    }

    private static Object emptyValue(Class<?> returnType) {
        if (!returnType.isPrimitive()) {
            if (Map.class.isAssignableFrom(returnType)) {
                return Map.of();
            }
            if (Collection.class.isAssignableFrom(returnType)) {
                return java.util.List.of();
            }
            if (Optional.class.isAssignableFrom(returnType)) {
                return Optional.empty();
            }
            return null;
        }
        if (returnType == boolean.class) {
            return false;
        }
        if (returnType == char.class) {
            return '\0';
        }
        if (returnType == byte.class) {
            return (byte) 0;
        }
        if (returnType == short.class) {
            return (short) 0;
        }
        if (returnType == int.class) {
            return 0;
        }
        if (returnType == long.class) {
            return 0L;
        }
        if (returnType == float.class) {
            return 0.0F;
        }
        return 0.0D;
    }

    private static Object[] normalizedArguments(Object[] arguments) {
        return arguments == null ? new Object[0] : arguments;
    }

    @Override
    public <T> T create(ModContainer mod, String value, Class<T> type) throws LanguageAdapterException {
        if (!type.getName().equals(MOD_MENU_API)) {
            throw new LanguageAdapterException("Unsupported ForceBlend entrypoint type: " + type.getName());
        }
        Object proxy = Proxy.newProxyInstance(
            type.getClassLoader(),
            new Class<?>[]{type},
            ReflectiveModMenuLanguageAdapter::invokeModMenuApi
        );
        return type.cast(proxy);
    }
}
