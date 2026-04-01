package com.xiaoai.islandnotify.modernhook;

import android.content.SharedPreferences;
import android.util.Log;

import java.lang.reflect.Constructor;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import io.github.kyuubiran.ezxhelper.xposed.EzXposed;
import io.github.kyuubiran.ezxhelper.xposed.api.XposedApi;
import io.github.kyuubiran.ezxhelper.xposed.common.AfterHookParam;
import io.github.kyuubiran.ezxhelper.xposed.common.BeforeHookParam;
import io.github.kyuubiran.ezxhelper.xposed.interfaces.IMethodAfterHookCallback;
import io.github.kyuubiran.ezxhelper.xposed.interfaces.IMethodBeforeHookCallback;
import io.github.libxposed.api.XposedInterface;

public final class XposedBridge {

    private static final int PRIORITY_DEFAULT = 50;

    private XposedBridge() {
    }

    public static void init(XposedInterface xposedInterface) {
        EzXposed.initXposedModule(xposedInterface);
    }

    public static void log(String text) {
        String msg = text == null ? "null" : text;
        try {
            XposedApi.log(msg);
        } catch (Throwable t) {
            Log.i("IslandNotify", msg, t);
        }
    }

    public static void log(Throwable t) {
        try {
            XposedApi.log("Hook exception", t);
        } catch (Throwable ignored) {
            Log.e("IslandNotify", "Hook exception", t);
        }
    }

    public static SharedPreferences getRemotePreferences(String group) {
        return XposedApi.getRemotePreferences(group);
    }

    public static XC_MethodHook.Unhook hookMethod(Member method, XC_MethodHook callback) {
        if (method instanceof Method) {
            Method target = (Method) method;
            Object beforeUnhook = callHookFactory(
                    "createMethodBeforeHook",
                    new Object[]{PRIORITY_DEFAULT, target, (IMethodBeforeHookCallback) p ->
                            runBeforeCallback(method, callback, p)}
            );
            Object afterUnhook = callHookFactory(
                    "createMethodAfterHook",
                    new Object[]{PRIORITY_DEFAULT, target, (IMethodAfterHookCallback) p ->
                            runAfterCallback(method, callback, p)}
            );
            return callback.new Unhook(method, new CombinedUnhook(beforeUnhook, afterUnhook));
        }
        if (method instanceof Constructor<?>) {
            Constructor<?> target = (Constructor<?>) method;
            Object beforeUnhook = callHookFactory(
                    "createConstructorBeforeHook",
                    new Object[]{PRIORITY_DEFAULT, target, (IMethodBeforeHookCallback) p ->
                            runBeforeCallback(method, callback, p)}
            );
            Object afterUnhook = callHookFactory(
                    "createConstructorAfterHook",
                    new Object[]{PRIORITY_DEFAULT, target, (IMethodAfterHookCallback) p ->
                            runAfterCallback(method, callback, p)}
            );
            return callback.new Unhook(method, new CombinedUnhook(beforeUnhook, afterUnhook));
        }
        throw new IllegalArgumentException("Only Method/Constructor can be hooked");
    }

    private static Object callHookFactory(String name, Object[] args) {
        try {
            Class<?> cls = Class.forName("io.github.kyuubiran.ezxhelper.xposed.dsl.HookFactory");
            Method[] methods = cls.getMethods();
            Throwable last = null;

            for (Method m : methods) {
                if (!name.equals(m.getName())) continue;
                if (!Modifier.isStatic(m.getModifiers())) continue;
                Object[] invokeArgs = buildCompatibleArgs(m.getParameterTypes(), args);
                if (invokeArgs == null) continue;
                try {
                    m.setAccessible(true);
                    return m.invoke(null, invokeArgs);
                } catch (Throwable t) {
                    last = t;
                }
            }

            throw new NoSuchMethodException(name + " compatible signature not found");
        } catch (Throwable t) {
            throw new IllegalStateException("HookFactory call failed: " + name, t);
        }
    }

    private static Object[] buildCompatibleArgs(Class<?>[] parameterTypes, Object[] args) {
        if (parameterTypes == null) return null;
        List<Object[]> candidates = new ArrayList<>();
        candidates.add(args);
        if (args != null && args.length > 0 && args[0] instanceof Integer) {
            Object[] noPriority = new Object[args.length - 1];
            System.arraycopy(args, 1, noPriority, 0, noPriority.length);
            candidates.add(noPriority);
        }
        for (Object[] candidate : candidates) {
            Object[] mapped = mapArgsByType(parameterTypes, candidate);
            if (mapped != null) return mapped;
        }
        return null;
    }

    private static Object[] mapArgsByType(Class<?>[] paramTypes, Object[] srcArgs) {
        if (srcArgs == null) srcArgs = new Object[0];
        if (paramTypes.length != srcArgs.length) return null;
        Object[] out = new Object[paramTypes.length];
        boolean[] used = new boolean[srcArgs.length];
        if (mapArgsDfs(paramTypes, srcArgs, used, out, 0)) return out;
        return null;
    }

    private static boolean mapArgsDfs(Class<?>[] paramTypes, Object[] srcArgs, boolean[] used, Object[] out, int index) {
        if (index >= paramTypes.length) return true;
        Class<?> p = paramTypes[index];
        for (int i = 0; i < srcArgs.length; i++) {
            if (used[i]) continue;
            Object arg = srcArgs[i];
            if (!isAssignable(p, arg)) continue;
            used[i] = true;
            out[index] = arg;
            if (mapArgsDfs(paramTypes, srcArgs, used, out, index + 1)) return true;
            used[i] = false;
            out[index] = null;
        }
        return false;
    }

    private static boolean isAssignable(Class<?> paramType, Object arg) {
        if (paramType == null) return false;
        if (arg == null) return !paramType.isPrimitive();
        Class<?> argType = arg.getClass();
        if (paramType.isAssignableFrom(argType)) return true;
        if (!paramType.isPrimitive()) return false;
        if (paramType == int.class) return argType == Integer.class;
        if (paramType == long.class) return argType == Long.class || argType == Integer.class;
        if (paramType == boolean.class) return argType == Boolean.class;
        if (paramType == float.class) return argType == Float.class || argType == Double.class;
        if (paramType == double.class) return argType == Double.class || argType == Float.class;
        if (paramType == short.class) return argType == Short.class || argType == Integer.class;
        if (paramType == byte.class) return argType == Byte.class || argType == Integer.class;
        if (paramType == char.class) return argType == Character.class;
        return false;
    }

    private static void runBeforeCallback(Member method, XC_MethodHook callback, BeforeHookParam param) {
        XC_MethodHook.MethodHookParam bridge = new XC_MethodHook.MethodHookParam();
        bridge.method = method;
        bridge.thisObject = param.getThisObjectOrNull();
        bridge.args = param.getArgs();
        try {
            callback.beforeHookedMethod(bridge);
            if (bridge.isReturnEarly()) {
                if (bridge.hasThrowable()) {
                    param.setThrowable(bridge.getThrowable());
                } else {
                    param.setResult(bridge.getResult());
                }
            }
        } catch (Throwable t) {
            param.setThrowable(t);
        }
    }

    private static void runAfterCallback(Member method, XC_MethodHook callback, AfterHookParam param) {
        XC_MethodHook.MethodHookParam bridge = new XC_MethodHook.MethodHookParam();
        bridge.method = method;
        bridge.thisObject = param.getThisObjectOrNull();
        bridge.args = param.getArgs();
        if (param.getThrowable() != null) {
            bridge.setThrowable(param.getThrowable());
        } else {
            bridge.setResultNoEarly(param.getResult());
        }
        try {
            callback.afterHookedMethod(bridge);
            if (bridge.hasThrowable()) {
                param.setThrowable(bridge.getThrowable());
            } else {
                param.setResult(bridge.getResult());
            }
        } catch (Throwable t) {
            param.setThrowable(t);
        }
    }

    private static final class CombinedUnhook {
        private final Object before;
        private final Object after;

        CombinedUnhook(Object before, Object after) {
            this.before = before;
            this.after = after;
        }

        @SuppressWarnings("unused")
        public void unhook() {
            unhookOne(before);
            unhookOne(after);
        }

        private void unhookOne(Object handle) {
            if (handle == null) {
                return;
            }
            if (handle instanceof AutoCloseable) {
                try {
                    ((AutoCloseable) handle).close();
                    return;
                } catch (Throwable ignored) {
                }
            }
            try {
                handle.getClass().getMethod("unhook").invoke(handle);
            } catch (Throwable ignored) {
            }
        }
    }
}
