package com.xiaoai.islandnotify.modernhook;

import android.content.SharedPreferences;
import android.util.Log;

import java.lang.reflect.Constructor;
import java.lang.reflect.Member;
import java.lang.reflect.Method;

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
                    new Class<?>[]{int.class, Method.class, IMethodBeforeHookCallback.class},
                    new Object[]{PRIORITY_DEFAULT, target, (IMethodBeforeHookCallback) p ->
                            runBeforeCallback(method, callback, p)}
            );
            Object afterUnhook = callHookFactory(
                    "createMethodAfterHook",
                    new Class<?>[]{int.class, Method.class, IMethodAfterHookCallback.class},
                    new Object[]{PRIORITY_DEFAULT, target, (IMethodAfterHookCallback) p ->
                            runAfterCallback(method, callback, p)}
            );
            return callback.new Unhook(method, new CombinedUnhook(beforeUnhook, afterUnhook));
        }
        if (method instanceof Constructor<?>) {
            Constructor<?> target = (Constructor<?>) method;
            Object beforeUnhook = callHookFactory(
                    "createConstructorBeforeHook",
                    new Class<?>[]{int.class, Constructor.class, IMethodBeforeHookCallback.class},
                    new Object[]{PRIORITY_DEFAULT, target, (IMethodBeforeHookCallback) p ->
                            runBeforeCallback(method, callback, p)}
            );
            Object afterUnhook = callHookFactory(
                    "createConstructorAfterHook",
                    new Class<?>[]{int.class, Constructor.class, IMethodAfterHookCallback.class},
                    new Object[]{PRIORITY_DEFAULT, target, (IMethodAfterHookCallback) p ->
                            runAfterCallback(method, callback, p)}
            );
            return callback.new Unhook(method, new CombinedUnhook(beforeUnhook, afterUnhook));
        }
        throw new IllegalArgumentException("Only Method/Constructor can be hooked");
    }

    private static Object callHookFactory(String name, Class<?>[] parameterTypes, Object[] args) {
        try {
            Class<?> cls = Class.forName("io.github.kyuubiran.ezxhelper.xposed.dsl.HookFactory");
            Method m = cls.getMethod(name, parameterTypes);
            m.setAccessible(true);
            return m.invoke(null, args);
        } catch (Throwable t) {
            throw new IllegalStateException("HookFactory call failed: " + name, t);
        }
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
