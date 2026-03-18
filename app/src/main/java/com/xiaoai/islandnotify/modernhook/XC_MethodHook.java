package com.xiaoai.islandnotify.modernhook;

import java.lang.reflect.Member;

import io.github.libxposed.api.XposedInterface;

public abstract class XC_MethodHook {

    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
    }

    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
    }

    public class Unhook {
        private final Member hookedMethod;
        private final XposedInterface.HookHandle handle;

        Unhook(Member hookedMethod, XposedInterface.HookHandle handle) {
            this.hookedMethod = hookedMethod;
            this.handle = handle;
        }

        public Member getHookedMethod() {
            return hookedMethod;
        }

        public void unhook() {
            if (handle != null) {
                handle.unhook();
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
