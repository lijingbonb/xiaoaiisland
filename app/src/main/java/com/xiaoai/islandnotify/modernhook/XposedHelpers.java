package com.xiaoai.islandnotify.modernhook;

import java.lang.reflect.Method;

public final class XposedHelpers {

    private XposedHelpers() {
    }

    public static XC_MethodHook.Unhook findAndHookMethod(
            String className,
            ClassLoader classLoader,
            String methodName,
            Object... parameterTypesAndCallback
    ) {
        if (parameterTypesAndCallback == null || parameterTypesAndCallback.length == 0) {
            throw new IllegalArgumentException("no callback defined");
        }
        Object callbackObj = parameterTypesAndCallback[parameterTypesAndCallback.length - 1];
        if (!(callbackObj instanceof XC_MethodHook)) {
            throw new IllegalArgumentException("last argument must be XC_MethodHook");
        }
        XC_MethodHook callback = (XC_MethodHook) callbackObj;

        Class<?>[] parameterTypes = new Class<?>[parameterTypesAndCallback.length - 1];
        for (int i = 0; i < parameterTypes.length; i++) {
            Object type = parameterTypesAndCallback[i];
            if (type instanceof Class<?>) {
                parameterTypes[i] = (Class<?>) type;
            } else if (type instanceof String) {
                try {
                    parameterTypes[i] = Class.forName((String) type, false, classLoader);
                } catch (ClassNotFoundException e) {
                    throw new IllegalStateException("class not found: " + type, e);
                }
            } else {
                throw new IllegalArgumentException("unsupported parameter type: " + type);
            }
        }

        try {
            Class<?> targetClass = Class.forName(className, false, classLoader);
            Method method = findMethodExact(targetClass, methodName, parameterTypes);
            method.setAccessible(true);
            return XposedBridge.hookMethod(method, callback);
        } catch (Throwable t) {
            throw new IllegalStateException("findAndHookMethod failed: " + className + "#" + methodName, t);
        }
    }

    private static Method findMethodExact(Class<?> cls, String methodName, Class<?>[] parameterTypes)
            throws NoSuchMethodException {
        Class<?> current = cls;
        while (current != null) {
            try {
                return current.getDeclaredMethod(methodName, parameterTypes);
            } catch (NoSuchMethodException ignored) {
                current = current.getSuperclass();
            }
        }
        return cls.getMethod(methodName, parameterTypes);
    }
}
