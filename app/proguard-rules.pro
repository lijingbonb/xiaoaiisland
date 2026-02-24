# ── Xposed 模块入口（xposed_init 内写死，不能改名）────────────────────────────
-keep class com.xiaoai.islandnotify.MainHook implements de.robv.android.xposed.IXposedHookLoadPackage {
    public void handleLoadPackage(de.robv.android.xposed.callbacks.XC_LoadPackage$LoadPackageParam);
}

# ── AndroidManifest + actionIntent 里硬编码的组件 ────────────────────────
-keep class com.xiaoai.islandnotify.MuteReceiver extends android.content.BroadcastReceiver {
    public void onReceive(android.content.Context, android.content.Intent);
}

# ── Xposed API 本身不能混淆 ─────────────────────────────────────────────
-keep interface de.robv.android.xposed.** { *; }
-keep class de.robv.android.xposed.** { *; }

# ── 移除日志（release 下增强逆向难度）─────────────────────────────────────
-assumenosideeffects class de.robv.android.xposed.XposedBridge {
    public static void log(...);
}
