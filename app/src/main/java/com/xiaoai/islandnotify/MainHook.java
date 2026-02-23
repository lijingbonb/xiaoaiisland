package com.xiaoai.islandnotify;

import android.app.Notification;
import android.os.Bundle;

import org.json.JSONException;
import org.json.JSONObject;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;

/**
 * LSPosed 主 Hook 类
 * 功能：拦截 com.miui.voiceassist 发送的"课程表提醒"通知，
 *       注入 miui.focus.param 参数，将其升级为小米超级岛通知。
 */
public class MainHook implements IXposedHookLoadPackage {

    private static final String TAG = "IslandNotifyHook";

    /** 目标应用包名（小爱同学） */
    private static final String TARGET_PACKAGE = "com.miui.voiceassist";

    /**
     * 用于识别"课程表提醒"通知的关键词列表。
     * 匹配通知标题、正文或 Channel ID 中任意一处包含以下词汇即触发转换。
     */
    private static final String[] SCHEDULE_KEYWORDS = {
            "课程", "课表", "上课", "选课", "schedule", "class reminder"
    };

    // ─────────────────────────────────────────────────────────────
    // 超级岛通知的参数 Key（均为小米私有扩展）
    // ─────────────────────────────────────────────────────────────
    /** 岛通知主参数 Key（JSON 字符串） */
    private static final String KEY_FOCUS_PARAM = "miui.focus.param";
    /** 图片 Bundle Key */
    private static final String KEY_FOCUS_PICS  = "miui.focus.pics";

    // ─────────────────────────────────────────────────────────────

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        // 只注入目标进程
        if (!TARGET_PACKAGE.equals(lpparam.packageName)) {
            return;
        }
        XposedBridge.log(TAG + ": 已注入目标进程 → " + TARGET_PACKAGE);
        hookNotifyMethods(lpparam);
    }

    /**
     * Hook NotificationManager 的两个 notify 重载，在通知发出前注入岛参数。
     */
    private void hookNotifyMethods(XC_LoadPackage.LoadPackageParam lpparam) {

        // ① notify(int id, Notification notification)
        try {
            findAndHookMethod(
                    "android.app.NotificationManager",
                    lpparam.classLoader,
                    "notify",
                    int.class,
                    Notification.class,
                    new NotifyHook(1) // notification 在 args[1]
            );
        } catch (Throwable e) {
            XposedBridge.log(TAG + ": Hook notify(int,Notification) 失败 → " + e.getMessage());
        }

        // ② notify(String tag, int id, Notification notification)
        try {
            findAndHookMethod(
                    "android.app.NotificationManager",
                    lpparam.classLoader,
                    "notify",
                    String.class,
                    int.class,
                    Notification.class,
                    new NotifyHook(2) // notification 在 args[2]
            );
        } catch (Throwable e) {
            XposedBridge.log(TAG + ": Hook notify(String,int,Notification) 失败 → " + e.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 内部 Hook 实现
    // ═══════════════════════════════════════════════════════════════

    /**
     * @param notifArgIndex Notification 对象在 args 数组中的下标
     */
    private class NotifyHook extends XC_MethodHook {

        private final int notifArgIndex;

        NotifyHook(int notifArgIndex) {
            this.notifArgIndex = notifArgIndex;
        }

        @Override
        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
            Notification notification = (Notification) param.args[notifArgIndex];
            if (notification == null) return;

            // 防止重复处理（同一通知被两个 Hook 各触发一次时的保护）
            if (isAlreadyIsland(notification)) return;

            if (isScheduleNotification(notification)) {
                XposedBridge.log(TAG + ": 检测到课程表提醒，开始注入超级岛参数");
                injectIslandParams(notification);
            }
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 通知识别逻辑
    // ─────────────────────────────────────────────────────────────

    /**
     * 判断该通知是否已经携带超级岛参数（避免重复注入）。
     */
    private boolean isAlreadyIsland(Notification notification) {
        if (notification.extras == null) return false;
        return notification.extras.containsKey(KEY_FOCUS_PARAM);
    }

    /**
     * 根据关键词判断是否为"课程表提醒"通知。
     * 匹配范围：通知标题、正文、Channel ID。
     */
    private boolean isScheduleNotification(Notification notification) {
        Bundle extras = notification.extras;
        if (extras == null) return false;

        String title     = extras.getString(Notification.EXTRA_TITLE, "");
        String text      = extras.getString(Notification.EXTRA_TEXT, "");
        String channelId = notification.getChannelId();
        if (channelId == null) channelId = "";

        String combined = (title + " " + text + " " + channelId).toLowerCase();

        for (String kw : SCHEDULE_KEYWORDS) {
            if (combined.contains(kw.toLowerCase())) {
                XposedBridge.log(TAG + ": 命中关键词 [" + kw + "]  title=" + title);
                return true;
            }
        }
        return false;
    }

    // ─────────────────────────────────────────────────────────────
    // 超级岛参数注入
    // ─────────────────────────────────────────────────────────────

    /**
     * 向通知的 extras 中注入 miui.focus.param，使其变为超级岛通知。
     */
    private void injectIslandParams(Notification notification) {
        Bundle extras = notification.extras;
        if (extras == null) {
            extras = new Bundle();
            notification.extras = extras;
        }

        // 读取原始通知内容
        String title   = extras.getString(Notification.EXTRA_TITLE, "课程提醒");
        String content = extras.getString(Notification.EXTRA_TEXT, "");
        if (content == null || content.isEmpty()) {
            CharSequence[] lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES);
            if (lines != null && lines.length > 0) {
                StringBuilder sb = new StringBuilder();
                for (CharSequence line : lines) {
                    if (sb.length() > 0) sb.append("\n");
                    sb.append(line);
                }
                content = sb.toString();
            }
        }

        try {
            String islandJson = buildIslandParams(title, content);
            extras.putString(KEY_FOCUS_PARAM, islandJson);
            XposedBridge.log(TAG + ": 注入成功 → " + islandJson);
        } catch (JSONException e) {
            XposedBridge.log(TAG + ": 构建 JSON 失败 → " + e.getMessage());
        }
    }

    /**
     * 构建完整的超级岛通知 JSON 参数（param_v2 格式）。
     *
     * 参数说明（根据小米超级岛开发文档）：
     * ┌─ param_v2
     * │   ├─ protocol          协议版本，固定 1
     * │   ├─ business          业务场景标识
     * │   ├─ enableFloat       通知更新时是否自动展开
     * │   ├─ islandFirstFloat  首次显示时是否展开
     * │   ├─ updatable         是否为持续性通知
     * │   ├─ ticker            状态栏焦点文案（OS2）
     * │   ├─ aodTitle          息屏显示文案
     * │   ├─ param_island      岛体数据
     * │   │   ├─ islandProperty  1=信息展示为主
     * │   │   ├─ bigIslandArea   大岛（展开态）内容
     * │   │   └─ smallIslandArea 小岛（摘要态）内容
     * │   └─ baseInfo          焦点通知/展开态内容
     */
    private String buildIslandParams(String title, String content) throws JSONException {
        String safeTitle   = title   != null ? title   : "课程提醒";
        String safeContent = content != null ? content : "";

        // ── 大岛（展开态）左侧图文区 ──────────────────────────────
        JSONObject textInfo = new JSONObject();
        textInfo.put("title", safeTitle);
        textInfo.put("content", safeContent);

        JSONObject imageTextInfoLeft = new JSONObject();
        imageTextInfoLeft.put("type", 1);
        imageTextInfoLeft.put("textInfo", textInfo);

        JSONObject bigIslandArea = new JSONObject();
        bigIslandArea.put("imageTextInfoLeft", imageTextInfoLeft);

        // ── 小岛（摘要态）文字区 ──────────────────────────────────
        JSONObject smallTextInfo = new JSONObject();
        smallTextInfo.put("title", safeTitle);

        JSONObject smallIslandArea = new JSONObject();
        smallIslandArea.put("textInfo", smallTextInfo);

        // ── 岛属性 ────────────────────────────────────────────────
        JSONObject paramIsland = new JSONObject();
        paramIsland.put("islandProperty", 1);       // 1 = 信息展示为主
        paramIsland.put("bigIslandArea",   bigIslandArea);
        paramIsland.put("smallIslandArea", smallIslandArea);

        // ── 焦点通知基础内容 ──────────────────────────────────────
        JSONObject baseInfo = new JSONObject();
        baseInfo.put("title",   safeTitle);
        baseInfo.put("content", safeContent);
        baseInfo.put("type", 1);

        // ── 组合 param_v2 ─────────────────────────────────────────
        JSONObject paramV2 = new JSONObject();
        paramV2.put("protocol",        1);
        paramV2.put("business",        "course_schedule"); // 业务场景标识
        paramV2.put("enableFloat",     false);             // 更新时不自动展开
        paramV2.put("islandFirstFloat", true);             // 首次显示时展开
        paramV2.put("updatable",       false);
        paramV2.put("ticker",          safeTitle);         // OS2 状态栏文案
        paramV2.put("aodTitle",        safeTitle);         // 息屏文案
        paramV2.put("param_island",    paramIsland);
        paramV2.put("baseInfo",        baseInfo);

        JSONObject root = new JSONObject();
        root.put("param_v2", paramV2);

        return root.toString();
    }
}
