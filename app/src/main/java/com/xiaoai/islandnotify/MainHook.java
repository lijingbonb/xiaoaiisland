package com.xiaoai.islandnotify;

import android.app.Notification;
import android.os.Bundle;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
     * 判断是否为课程表提醒通知。
     *
     * <p>实测 logcat 确认：
     * <ul>
     *   <li>channelId = {@code "COURSE_SCHEDULER_REMINDER_sound"}</li>
     *   <li>msgType   = {@code "COURSE_SCHEDULER_REMINDER"}</li>
     * </ul>
     * 优先通过 Channel ID 精确匹配；Channel ID 变更时自动降级为关键词匹配兜底。
     */
    private boolean isScheduleNotification(Notification notification) {
        // ① 精确匹配 Channel ID（首选，来自实测 logcat）
        String channelId = safeStr(notification.getChannelId());
        if (channelId.contains("COURSE_SCHEDULER_REMINDER")) {
            XposedBridge.log(TAG + ": 命中 channelId=" + channelId);
            return true;
        }

        // ② 关键词兜底（防止 channelId 未来变更）
        Bundle extras = notification.extras;
        if (extras == null) return false;
        String title = safeStr(extras.getString(Notification.EXTRA_TITLE));
        String text  = safeStr(extras.getString(Notification.EXTRA_TEXT));
        String combined = (title + " " + text).toLowerCase();
        for (String kw : SCHEDULE_KEYWORDS) {
            if (combined.contains(kw.toLowerCase())) {
                XposedBridge.log(TAG + ": 命中关键词 [" + kw + "] title=" + title);
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

        try {
            CourseInfo info = extractCourseInfo(extras);
            XposedBridge.log(TAG + ": 解析结果 → 课程=" + info.courseName
                    + " 时间=" + info.startTime + " 教室=" + info.classroom);
            String islandJson = buildIslandParams(info);
            extras.putString(KEY_FOCUS_PARAM, islandJson);
            XposedBridge.log(TAG + ": 注入成功");
        } catch (JSONException e) {
            XposedBridge.log(TAG + ": 构建 JSON 失败 → " + e.getMessage());
        }
    }

    /**
     * 从通知 extras 中精确提取课程信息。
     *
     * <p>实测 logcat 确认的固定格式（com.miui.voiceassist 课程表提醒）：
     * <pre>
     *   EXTRA_TITLE = "[高等数学]快到了，提前准备一下吧"
     *   EXTRA_TEXT  = "19:50 | 教1-201"         ← 固定格式：时间 + " | " + 教室
     * </pre>
     */
    private CourseInfo extractCourseInfo(Bundle extras) {
        String title = safeStr(extras.getString(Notification.EXTRA_TITLE));
        String body  = safeStr(extras.getString(Notification.EXTRA_TEXT));

        // ── 课程名：提取 "[高等数学]" 中的内容 ───────────────────────
        String courseName = "";
        Matcher m = Pattern.compile("\\[([^\\]]+)\\]").matcher(title);
        if (m.find()) {
            courseName = m.group(1).trim();
        }
        // 兜底：去掉"快到了"及后续所有文字
        if (courseName.isEmpty()) {
            courseName = title.replaceAll("(快到了|提醒|通知|课程表).*", "").trim();
        }
        if (courseName.isEmpty()) courseName = "课程提醒";

        // ── 时间 + 教室：body 固定格式 "19:50 | 教1-201" ─────────────
        // 按 " | " 分割（兼容前后空格数量不一致的情况）
        String startTime = "";
        String classroom = "";
        String[] parts = body.split("\\s*\\|\\s*", 2);
        if (parts.length >= 1) startTime = parts[0].trim();
        if (parts.length >= 2) classroom  = parts[1].trim();

        XposedBridge.log(TAG + ": 解析 title=[" + title + "] body=[" + body + "]"
                + " → 课程=" + courseName + " 时间=" + startTime + " 教室=" + classroom);
        return new CourseInfo(courseName, startTime, classroom);
    }

    private static String safeStr(String s) {
        return s != null ? s : "";
    }

    /**
     * 构建超级岛 JSON 参数（param_v2 格式）。
     *
     * <p>字段来源：小米超级岛模板库 hintInfo 组件（按钮组件 3）
     *
     * <pre>
     * 大岛 / 焦点通知展开态：
     * ┌──────────────────────────────────────┐
     * │  高等数学                             │ ← imageTextInfoLeft.textInfo.title（主要文本）
     * │  时间  10:20   教室  教1-201          │ ← hintInfo  content/title/subContent/subTitle
     * └──────────────────────────────────────┘
     *
     * 小岛摘要态：
     * ┌────────────────────────┐
     * │  高等数学   10:20      │ ← smallIslandArea.textInfo.title / .content
     * └────────────────────────┘
     *
     * hintInfo 字段映射（来自 PDF 模板库）：
     *   content    = 前置文本1  → "时间"
     *   title      = 主要小文本1 → 实际时间值（如 10:20）
     *   subContent = 前置文本2  → "教室"
     *   subTitle   = 主要小文本2 → 实际教室值（如 教1-201）
     * </pre>
     */
    private String buildIslandParams(CourseInfo info) throws JSONException {
        // ── 大岛 A 区：主要文本 = 课程名 ──────────────────────────
        JSONObject mainTextInfo = new JSONObject();
        mainTextInfo.put("title", info.courseName); // 主要文本 → title

        JSONObject imageTextInfoLeft = new JSONObject();
        imageTextInfoLeft.put("type", 1);
        imageTextInfoLeft.put("textInfo", mainTextInfo);

        JSONObject bigIslandArea = new JSONObject();
        bigIslandArea.put("imageTextInfoLeft", imageTextInfoLeft);

        // ── 小岛（摘要态）：课程名 + 时间 ────────────────────────
        JSONObject smallTextInfo = new JSONObject();
        smallTextInfo.put("title", info.courseName);
        if (!info.startTime.isEmpty()) {
            smallTextInfo.put("content", info.startTime);
        }
        JSONObject smallIslandArea = new JSONObject();
        smallIslandArea.put("textInfo", smallTextInfo);

        // ── 岛属性 ────────────────────────────────────────────────
        JSONObject paramIsland = new JSONObject();
        paramIsland.put("islandProperty", 1); // 1 = 信息展示为主
        paramIsland.put("bigIslandArea",   bigIslandArea);
        paramIsland.put("smallIslandArea", smallIslandArea);

        // ── hintInfo（param_v2 级，同时作用于焦点通知和岛展开态）────
        // PDF 模板库确认字段：
        //   content    前置文本1   subContent 前置文本2
        //   title      主要小文本1  subTitle   主要小文本2
        JSONObject hintInfo = new JSONObject();
        hintInfo.put("type",    1);
        hintInfo.put("content", "时间");
        hintInfo.put("title",   info.startTime.isEmpty() ? "—" : info.startTime);
        if (!info.classroom.isEmpty()) {
            hintInfo.put("subContent", "教室");
            hintInfo.put("subTitle",   info.classroom);
        }
        // 颜色字段（跟随系统深浅模式，不强制自定义则省略）

        // ── 焦点通知基础内容 ──────────────────────────────────────
        // content 只放时间+教室，title 放课程名，避免重复且补全教室信息
        StringBuilder baseContent = new StringBuilder();
        if (!info.startTime.isEmpty()) baseContent.append(info.startTime);
        if (!info.classroom.isEmpty()) {
            if (baseContent.length() > 0) baseContent.append("  ");
            baseContent.append(info.classroom);
        }
        JSONObject baseInfo = new JSONObject();
        baseInfo.put("title",   info.courseName);
        baseInfo.put("content", baseContent.length() > 0 ? baseContent.toString() : info.courseName);
        baseInfo.put("type", 1);

        // ── 状态栏 / 息屏文案 ─────────────────────────────────────
        String tickerText = buildTickerText(info);

        // ── 组合 param_v2 ─────────────────────────────────────────
        JSONObject paramV2 = new JSONObject();
        paramV2.put("protocol",         1);
        paramV2.put("business",         "course_schedule");
        paramV2.put("islandFirstFloat",  true);  // 首次出现展开大岛
        paramV2.put("enableFloat",       false); // 更新时不自动展开
        paramV2.put("updatable",         false);
        paramV2.put("ticker",            tickerText); // OS2 状态栏文案
        paramV2.put("aodTitle",          tickerText); // 息屏文案
        paramV2.put("param_island",      paramIsland);
        paramV2.put("baseInfo",          baseInfo);
        paramV2.put("hintInfo",          hintInfo);

        JSONObject root = new JSONObject();
        root.put("param_v2", paramV2);
        return root.toString();
    }

    /** 拼接状态栏 / 息屏文案，格式：高等数学  10:20 */
    private String buildTickerText(CourseInfo info) {
        if (info.startTime.isEmpty()) return info.courseName;
        return info.courseName + "  " + info.startTime;
    }

    // ─────────────────────────────────────────────────────────────
    // 数据结构
    // ─────────────────────────────────────────────────────────────

    /**
     * 从通知中提取的结构化课程信息，对应模板字段：
     * <ul>
     *   <li>courseName → 主要文本1（课程名）</li>
     *   <li>startTime  → 次要文本·主要小文本1（前置文本="时间"）</li>
     *   <li>classroom  → 次要文本·主要小文本2（前置文本="教室"）</li>
     * </ul>
     */
    private static class CourseInfo {
        final String courseName;
        final String startTime;
        final String classroom;

        CourseInfo(String courseName, String startTime, String classroom) {
            this.courseName = courseName;
            this.startTime  = startTime;
            this.classroom  = classroom;
        }
    }
}
