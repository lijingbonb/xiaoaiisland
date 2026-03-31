package com.xiaoai.islandnotify.modernhook;

import java.lang.reflect.Member;
import java.lang.reflect.Method;

public abstract class XC_MethodHook {

    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
    }

    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
    }

    public class Unhook {
        private final Member hookedMethod;
        private final Object handle;

        Unhook(Member hookedMethod, Object handle) {
            this.hookedMethod = hookedMethod;
            this.handle = handle;
        }

        public Member getHookedMethod() {
            return hookedMethod;
        }

        public void unhook() {
            if (handle != null) {
                invokeUnhook(handle);
            }
        }

        private void invokeUnhook(Object h) {
            for (String name : new String[]{"unhook", "remove", "cancel"}) {
                try {
                    Method m = h.getClass().getMethod(name);
                    m.setAccessible(true);
                    m.invoke(h);
                    return;
                } catch (Throwable ignored) {
                }
            }
        }
    }

    public static final class MethodHookParam {
        public Member method;
        public Object thisObject;
        public Object[] args;

        private Object result;
        private Throwable throwable;
        private boolean returnEarly;

        public Object getResult() {
            return result;
        }

        void setResultNoEarly(Object result) {
            this.result = result;
            this.throwable = null;
            this.returnEarly = false;
        }

        public void setResult(Object result) {
            this.result = result;
            this.throwable = null;
            this.returnEarly = true;
        }

        public Throwable getThrowable() {
            return throwable;
        }

        public boolean hasThrowable() {
            return throwable != null;
        }

        public void setThrowable(Throwable throwable) {
            this.throwable = throwable;
            this.result = null;
            this.returnEarly = true;
        }

        public boolean isReturnEarly() {
            return returnEarly;
        }
    }
}
