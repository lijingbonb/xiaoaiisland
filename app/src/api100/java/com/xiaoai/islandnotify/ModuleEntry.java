package com.xiaoai.islandnotify;

import androidx.annotation.NonNull;

import com.xiaoai.islandnotify.modernhook.XposedBridge;

import java.lang.reflect.Method;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

public class ModuleEntry extends XposedModule {

    private static final String TAG = "IslandNotifyModule";

    private final MainHook mainHook = new MainHook();
    private final DeskClockHook deskClockHook = new DeskClockHook();
    private final SystemUiHook systemUiHook = new SystemUiHook();
    private volatile String processName = "";

    // API100 runtime expects this constructor signature.
    @SuppressWarnings("unused")
    public ModuleEntry(@NonNull XposedInterface base,
                       @NonNull XposedModuleInterface.ModuleLoadedParam param) {
        super();
        processName = safeProcessName(param);
        XposedBridge.init(base);
        XposedBridge.log(TAG + ": ctor(api100) process=" + processName);
    }

    @Override
    public void onModuleLoaded(@NonNull ModuleLoadedParam param) {
        processName = param.getProcessName();
        XposedBridge.log(TAG + ": onModuleLoaded process=" + processName);
    }

    @Override
    public void onPackageLoaded(@NonNull PackageLoadedParam param) {
        String packageName = param.getPackageName();
        String resolvedProcessName = processName == null || processName.isEmpty()
                ? packageName
                : processName;
        ClassLoader classLoader = resolveClassLoader(param);
        dispatchHooks(packageName, resolvedProcessName, classLoader);
    }

    private void dispatchHooks(String packageName, String resolvedProcessName, ClassLoader classLoader) {
        try {
            mainHook.handleLoadPackage(packageName, resolvedProcessName, classLoader);
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": MainHook failed -> " + t.getMessage());
            XposedBridge.log(t);
        }

        try {
            deskClockHook.handleLoadPackage(packageName, classLoader);
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": DeskClockHook failed -> " + t.getMessage());
            XposedBridge.log(t);
        }

        try {
            systemUiHook.handleLoadPackage(packageName, classLoader);
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": SystemUiHook failed -> " + t.getMessage());
            XposedBridge.log(t);
        }
    }

    private static String safeProcessName(XposedModuleInterface.ModuleLoadedParam param) {
        try {
            return param.getProcessName();
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static ClassLoader resolveClassLoader(PackageLoadedParam param) {
        try {
            return param.getDefaultClassLoader();
        } catch (Throwable ignored) {
        }
        try {
            Method m = param.getClass().getMethod("getClassLoader");
            Object cl = m.invoke(param);
            if (cl instanceof ClassLoader) {
                return (ClassLoader) cl;
            }
        } catch (Throwable ignored) {
        }
        throw new IllegalStateException("Failed to resolve classloader from PackageLoadedParam");
    }
}
