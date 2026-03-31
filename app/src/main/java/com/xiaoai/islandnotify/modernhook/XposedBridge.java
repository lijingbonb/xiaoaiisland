package com.xiaoai.islandnotify.modernhook;

import android.content.SharedPreferences;
import android.util.Log;

import java.lang.reflect.Executable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.List;

import io.github.libxposed.api.XposedInterface;

public final class XposedBridge {

    private static volatile XposedInterface sXposed;

    private XposedBridge() {
    }

    public static void init(XposedInterface xposedInterface) {
        sXposed = xposedInterface;
    }

    public static void log(String text) {
        XposedInterface api = sXposed;
        if (api != null) {
            api.log(Log.INFO, "IslandNotify", text == null ? "null" : text);
        } else {
            Log.i("IslandNotify", text == null ? "null" : text);
        }
    }

    public static void log(Throwable t) {
        XposedInterface api = sXposed;
        if (api != null) {
            api.log(Log.ERROR, "IslandNotify", "Hook exception", t);
        } else {
            Log.e("IslandNotify", "Hook exception", t);
        }
    }

    public static SharedPreferences getRemotePreferences(String group) {
        XposedInterface api = sXposed;
        if (api == null) {
            throw new IllegalStateException("Xposed API context is not initialized");
        }
        try {
            return api.getRemotePreferences(group);
        } catch (Throwable directError) {
            try {
                Object value = invokeBest(api, "getRemotePreferences", new Object[]{group});
                if (value instanceof SharedPreferences) {
                    return (SharedPreferences) value;
                }
            } catch (Throwable ignored) {
            }
            if (directError instanceof RuntimeException) {
                throw (RuntimeException) directError;
            }
            throw new IllegalStateException("getRemotePreferences failed", directError);
        }
    }

    public static XC_MethodHook.Unhook hookMethod(Member method, XC_MethodHook callback) {
        XposedInterface api = sXposed;
        if (api == null) {
            throw new IllegalStateException("Xposed API context is not initialized");
        }
        if (!(method instanceof Executable)) {
            throw new IllegalArgumentException("Only Method/Constructor can be hooked");
        }
        Executable executable = (Executable) method;
        Object hookBuilder;
        try {
            hookBuilder = api.hook(executable);
        } catch (Throwable t) {
            try {
                hookBuilder = invokeBest(api, "hook", new Object[]{executable});
            } catch (Throwable t2) {
                throw new IllegalStateException("hook(...) failed", t2);
            }
        }

        Method interceptMethod = findSingleParamMethod(hookBuilder.getClass(), "intercept");
        if (interceptMethod == null) {
            throw new IllegalStateException("hook builder has no intercept(...) method");
        }
        Class<?> hookerType = interceptMethod.getParameterTypes()[0];
        Object hooker = Proxy.newProxyInstance(
                hookerType.getClassLoader(),
                new Class<?>[]{hookerType},
                (proxy, invoked, args) -> {
                    if (invoked.getDeclaringClass() == Object.class) {
                        String n = invoked.getName();
                        if ("toString".equals(n)) return "IslandNotifyHookerProxy";
                        if ("hashCode".equals(n)) return System.identityHashCode(proxy);
                        if ("equals".equals(n)) return proxy == (args != null && args.length > 0 ? args[0] : null);
                        return null;
                    }
                    Object chain = (args != null && args.length > 0) ? args[0] : null;
                    return dispatchHookInvocation(method, callback, chain);
                }
        );

        Object handle;
        try {
            interceptMethod.setAccessible(true);
            handle = interceptMethod.invoke(hookBuilder, hooker);
        } catch (InvocationTargetException e) {
            throw rethrowAsState("intercept(...) failed", e);
        } catch (Throwable t) {
            throw new IllegalStateException("intercept(...) failed", t);
        }
        return callback.new Unhook(method, handle);
    }

    private static Object dispatchHookInvocation(Member method, XC_MethodHook callback, Object chain) throws Throwable {
        XC_MethodHook.MethodHookParam param = new XC_MethodHook.MethodHookParam();
        param.method = method;
        Object originalThis = getChainThisObject(chain);
        param.thisObject = originalThis;
        Object[] originalArgs = getChainArgs(chain);
        param.args = originalArgs.clone();

        callback.beforeHookedMethod(param);

        if (!param.isReturnEarly()) {
            try {
                Object result = callChainProceed(chain, param.thisObject, param.args, originalThis, originalArgs);
                param.setResultNoEarly(result);
            } catch (Throwable t) {
                param.setThrowable(unwrapCause(t));
            }
        }

        callback.afterHookedMethod(param);

        if (param.hasThrowable()) {
            throw param.getThrowable();
        }
        return param.getResult();
    }

    private static Object callChainProceed(
            Object chain,
            Object thisObject,
            Object[] args,
            Object originalThis,
            Object[] originalArgs
    ) throws Throwable {
        boolean thisChanged = thisObject != originalThis;
        boolean argsChanged = !Arrays.equals(args, originalArgs);

        try {
            if (thisChanged) {
                if (argsChanged) {
                    return invokeBest(chain, "proceedWith", new Object[]{thisObject, args});
                }
                return invokeBest(chain, "proceedWith", new Object[]{thisObject});
            }
            if (argsChanged) {
                return invokeBest(chain, "proceed", new Object[]{args});
            }
            return invokeBest(chain, "proceed", new Object[]{});
        } catch (Throwable t) {
            throw unwrapCause(t);
        }
    }

    private static Object[] getChainArgs(Object chain) {
        if (chain == null) {
            return new Object[0];
        }
        try {
            Object value = invokeBest(chain, "getArgs", new Object[]{});
            if (value instanceof List<?>) {
                return ((List<?>) value).toArray(new Object[0]);
            }
            if (value instanceof Object[]) {
                return ((Object[]) value).clone();
            }
        } catch (Throwable ignored) {
        }
        try {
            Object value = invokeBest(chain, "getArguments", new Object[]{});
            if (value instanceof List<?>) {
                return ((List<?>) value).toArray(new Object[0]);
            }
            if (value instanceof Object[]) {
                return ((Object[]) value).clone();
            }
        } catch (Throwable ignored) {
        }
        return new Object[0];
    }

    private static Object getChainThisObject(Object chain) {
        if (chain == null) {
            return null;
        }
        try {
            return invokeBest(chain, "getThisObject", new Object[]{});
        } catch (Throwable ignored) {
        }
        try {
            return invokeBest(chain, "thisObject", new Object[]{});
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static Method findSingleParamMethod(Class<?> cls, String name) {
        for (Method m : cls.getMethods()) {
            if (m.getName().equals(name) && m.getParameterCount() == 1) {
                return m;
            }
        }
        for (Method m : cls.getDeclaredMethods()) {
            if (m.getName().equals(name) && m.getParameterCount() == 1) {
                return m;
            }
        }
        return null;
    }

    private static Object invokeBest(Object target, String methodName, Object[] args) throws Throwable {
        Method[] methods = target.getClass().getMethods();
        Object[] safeArgs = args == null ? new Object[0] : args;
        Method candidate = null;
        for (Method m : methods) {
            if (!m.getName().equals(methodName)) {
                continue;
            }
            if (m.getParameterCount() != safeArgs.length) {
                continue;
            }
            if (isArgsAssignable(m.getParameterTypes(), safeArgs)) {
                candidate = m;
                break;
            }
        }
        if (candidate == null) {
            for (Method m : target.getClass().getDeclaredMethods()) {
                if (!m.getName().equals(methodName)) {
                    continue;
                }
                if (m.getParameterCount() != safeArgs.length) {
                    continue;
                }
                if (isArgsAssignable(m.getParameterTypes(), safeArgs)) {
                    candidate = m;
                    break;
                }
            }
        }
        if (candidate == null) {
            throw new NoSuchMethodException(target.getClass().getName() + "#" + methodName);
        }
        try {
            candidate.setAccessible(true);
            return candidate.invoke(target, safeArgs);
        } catch (InvocationTargetException e) {
            throw unwrapCause(e);
        }
    }

    private static boolean isArgsAssignable(Class<?>[] parameterTypes, Object[] args) {
        for (int i = 0; i < parameterTypes.length; i++) {
            Object arg = args[i];
            if (arg == null) {
                if (parameterTypes[i].isPrimitive()) {
                    return false;
                }
                continue;
            }
            Class<?> wrapped = wrapPrimitive(parameterTypes[i]);
            if (!wrapped.isAssignableFrom(arg.getClass())) {
                return false;
            }
        }
        return true;
    }

    private static Class<?> wrapPrimitive(Class<?> c) {
        if (!c.isPrimitive()) {
            return c;
        }
        if (c == boolean.class) return Boolean.class;
        if (c == byte.class) return Byte.class;
        if (c == short.class) return Short.class;
        if (c == int.class) return Integer.class;
        if (c == long.class) return Long.class;
        if (c == float.class) return Float.class;
        if (c == double.class) return Double.class;
        if (c == char.class) return Character.class;
        return c;
    }

    private static Throwable unwrapCause(Throwable t) {
        if (t instanceof InvocationTargetException && ((InvocationTargetException) t).getCause() != null) {
            return ((InvocationTargetException) t).getCause();
        }
        return t;
    }

    private static IllegalStateException rethrowAsState(String message, InvocationTargetException e) {
        Throwable cause = e.getCause() != null ? e.getCause() : e;
        return new IllegalStateException(message, cause);
    }
}
