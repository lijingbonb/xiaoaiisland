package com.xiaoai.islandnotify.modernhook;

import com.xiaoai.islandnotify.modernhook.callbacks.XC_LoadPackage;

public interface IXposedHookLoadPackage {
    void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable;
}
