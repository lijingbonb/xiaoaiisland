package com.xiaoai.islandnotify;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.NotificationCompat;
import androidx.core.widget.ImageViewCompat;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.DynamicColors;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputLayout;

/**
 * 模块主界面
 * - 展示模块激活状态（由 LSPosed Hook 动态注入）
 * - 展示权限状态
 * - 支持 Material You 动态取色（Android 12+）
 */
public class MainActivity extends AppCompatActivity {

    /** Android 13+ 通知权限运行时请求 */
    private ActivityResultLauncher<String> notifPermLauncher;
    /** 权限通过后执行的挂起动作 */
    private Runnable pendingTestAction;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // 必须在 super.onCreate 前调用，才能正确应用动态色彩
        DynamicColors.applyToActivityIfAvailable(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 注册通知权限请求（Android 13+）
        notifPermLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(), granted -> {
                    if (granted && pendingTestAction != null) {
                        pendingTestAction.run();
                    } else {
                        showTestHint("通知权限被拒绝，无法发送测试通知");
                    }
                    pendingTestAction = null;
                });

        // 设置 Toolbar 为 ActionBar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // 测试通知：1分钟后上课（倒计时）
        findViewById(R.id.btn_send_test).setOnClickListener(v ->
                requireNotifPermAndRun(() -> {
                    sendTestNotification(60_000L);
                    showTestHint("已发送测试通知（倒计时），请下拉通知栏查看超级岛效果");
                }));

        updateModuleStatus();
        initCustomCard();
        initTimeoutCard();
        initHideIconSwitch();
        initAboutSection(); // 初始化关于页面的版本信息
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    // ─────────────────────────────────────────────────────────────

    /**
     * 根据模块是否激活，设置状态卡片的颜色、图标和文字。
     */
    private void updateModuleStatus() {
        boolean active = isModuleActive();

        MaterialCardView card = findViewById(R.id.card_status);
        ImageView icon = findViewById(R.id.iv_status);
        TextView title = findViewById(R.id.tv_status_title);
        TextView desc = findViewById(R.id.tv_status_desc);

        if (active) {
            int bg      = MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimaryContainer,   Color.LTGRAY);
            int onColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnPrimaryContainer, Color.WHITE);
            card.setCardBackgroundColor(bg);
            icon.setImageResource(R.drawable.ic_module_active);
            ImageViewCompat.setImageTintList(icon, ColorStateList.valueOf(onColor));
            title.setText("模块已激活");
            title.setTextColor(onColor);
            desc.setText("Hook 正常运行，课程通知将自动转换为超级岛");
            desc.setTextColor(onColor);
        } else {
            int bg      = MaterialColors.getColor(this, com.google.android.material.R.attr.colorErrorContainer,   Color.LTGRAY);
            int onColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnErrorContainer, Color.BLACK);
            card.setCardBackgroundColor(bg);
            icon.setImageResource(R.drawable.ic_module_inactive);
            ImageViewCompat.setImageTintList(icon, ColorStateList.valueOf(onColor));
            title.setText("模块未激活");
            title.setTextColor(onColor);
            desc.setText("请在 LSPosed 管理器中启用，并将作用域设为超级小爱");
            desc.setTextColor(onColor);
        }
    }

    /** SharedPreferences 名称（与 MainHook 保持一致） */
    static final String PREFS_NAME = "island_custom";

    private void initCustomCard() {
        SharedPreferences sp = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        // 三个阶段 SP 后缀：_pre=上课前  _active=上课中  _post=下课后
        final String[] SUFFIXES = {"_pre", "_active", "_post"};
        // 各阶段 A/B/ticker 的默认值
        final String[] DEFAULT_A      = {"{教室}",             "{课名}",         "{课名}"};
        final String[] DEFAULT_B      = {"{开始}上课",         "{结束}下课",     "已经下课"};
        final String[] DEFAULT_TICKER = {"{教室}｜{开始}上课", "{课名}｜{结束}下课", "{课名}｜已经下课"};
        // 各阶段输入框 View ID
        final int[] IDS_A      = {R.id.et_tpl_a_pre,      R.id.et_tpl_a_active,      R.id.et_tpl_a_post};
        final int[] IDS_B      = {R.id.et_tpl_b_pre,      R.id.et_tpl_b_active,      R.id.et_tpl_b_post};
        final int[] IDS_TICKER = {R.id.et_tpl_ticker_pre,  R.id.et_tpl_ticker_active,  R.id.et_tpl_ticker_post};

        SwitchMaterial swIconA = findViewById(R.id.sw_icon_a);
        TextView tvHint        = findViewById(R.id.tv_save_hint);

        // 读取已保存配置，无则用默认值
        for (int i = 0; i < 3; i++) {
            ((EditText) findViewById(IDS_A[i])).setText(
                    sp.getString("tpl_a"      + SUFFIXES[i], DEFAULT_A[i]));
            ((EditText) findViewById(IDS_B[i])).setText(
                    sp.getString("tpl_b"      + SUFFIXES[i], DEFAULT_B[i]));
            ((EditText) findViewById(IDS_TICKER[i])).setText(
                    sp.getString("tpl_ticker" + SUFFIXES[i], DEFAULT_TICKER[i]));
        }
        swIconA.setChecked(sp.getBoolean("icon_a", true));

        findViewById(R.id.btn_save_custom).setOnClickListener(v -> {
            SharedPreferences.Editor ed = sp.edit();
            // 通知 voiceassist 进程同步最新配置（绕过 SELinux 跨 UID 文件读取限制）
            Intent sync = new Intent("com.xiaoai.islandnotify.ACTION_SYNC_PREFS");
            sync.setPackage("com.miui.voiceassist");

            for (int i = 0; i < 3; i++) {
                String tplA      = ((EditText) findViewById(IDS_A[i])).getText().toString().trim();
                String tplB      = ((EditText) findViewById(IDS_B[i])).getText().toString().trim();
                String tplTicker = ((EditText) findViewById(IDS_TICKER[i])).getText().toString().trim();
                ed.putString("tpl_a"      + SUFFIXES[i], tplA);
                ed.putString("tpl_b"      + SUFFIXES[i], tplB);
                ed.putString("tpl_ticker" + SUFFIXES[i], tplTicker);
                sync.putExtra("tpl_a"      + SUFFIXES[i], tplA);
                sync.putExtra("tpl_b"      + SUFFIXES[i], tplB);
                sync.putExtra("tpl_ticker" + SUFFIXES[i], tplTicker);
            }
            boolean iconA = swIconA.isChecked();
            ed.putBoolean("icon_a", iconA);
            sync.putExtra("icon_a", iconA);
            ed.apply();
            sendBroadcast(sync);

            tvHint.setText("已保存，下次通知生效");
            tvHint.setVisibility(View.VISIBLE);
        });
    }

    // ─────────────────────────────────────────────────────────────
    // 超时设置卡片
    // ─────────────────────────────────────────────────────────────

    /** 三阶段 key 后缀（与 MainHook/SP 保持一致） */
    private static final String[] TO_PHASES = {"pre", "active", "post"};

    /**
     * 初始化「消失时间」卡片。
     * 岛消失：全局单一值（每次 notify 都会重置，分阶段无意义）。
     * 通知消失：三阶段独立（实现为 AlarmManager + nm.cancel()）。
     * val == -1 表示系统默认；val > 0 表示自定义。
     */
    private void initTimeoutCard() {
        SharedPreferences sp = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        // ── 岛：全局单一值 ────────────────────────────────────────────
        final int[]    islandVal  = {sp.getInt("to_island_val",  -1)};
        final String[] islandUnit = {sp.getString("to_island_unit", "m")};

        // ── 通知：三阶段独立 ───────────────────────────────────────────
        final int[]    notifVals  = new int[3];
        final String[] notifUnits = new String[3];
        for (int i = 0; i < 3; i++) {
            notifVals[i]  = sp.getInt   ("to_notif_val_"  + TO_PHASES[i], -1);
            notifUnits[i] = sp.getString("to_notif_unit_" + TO_PHASES[i], "m");
        }
        final int[] curNotifPhase = {0};
        // 全局默认标志：所有阶段均为 -1 时为默认（不取消通知）
        boolean allDefault = true;
        for (int i = 0; i < 3; i++) if (notifVals[i] >= 0) { allDefault = false; break; }
        final boolean[] notifDefault = {allDefault};

        // ── View 引用 ──────────────────────────────────────────────────
        TextInputLayout  tilIsland       = findViewById(R.id.til_island_to);
        EditText         etIsland        = findViewById(R.id.et_island_to);
        MaterialButtonToggleGroup toggleIslandUnit = findViewById(R.id.toggle_island_unit);
        SwitchMaterial   swIslandDefault = findViewById(R.id.sw_island_to_default);

        MaterialButtonToggleGroup toggleNotifPhase = findViewById(R.id.toggle_notif_phase);
        TextInputLayout  tilNotif        = findViewById(R.id.til_notif_to);
        EditText         etNotif         = findViewById(R.id.et_notif_to);
        MaterialButtonToggleGroup toggleNotifUnit = findViewById(R.id.toggle_notif_unit);
        SwitchMaterial   swNotifDefault  = findViewById(R.id.sw_notif_to_default);

        TextView tvHint = findViewById(R.id.tv_timeout_hint);

        final boolean[] updatingIsland = {false};
        final boolean[] updatingNotif  = {false};

        // ── 岛默认开关 listener ────────────────────────────────────────
        swIslandDefault.setOnCheckedChangeListener((btn, checked) -> {
            if (updatingIsland[0]) return;
            setTimeoutRowEnabled(tilIsland, toggleIslandUnit, !checked);
            if (checked) { etIsland.setText(""); islandVal[0] = -1; }
        });

        // ── 通知默认开关 listener（全局：控制所有阶段） ──────────────────
        swNotifDefault.setOnCheckedChangeListener((btn, checked) -> {
            if (updatingNotif[0]) return;
            notifDefault[0] = checked;
            if (checked) {
                // 全局默认：三阶段全部置 -1
                for (int j = 0; j < 3; j++) notifVals[j] = -1;
                etNotif.setText("");
            }
            boolean en = !checked;
            setTimeoutRowEnabled(tilNotif, toggleNotifUnit, en);
            toggleNotifPhase.setEnabled(en);
            toggleNotifPhase.setAlpha(en ? 1f : 0.4f);
        });

        // ── 初始化岛 UI（全局单一值，不涉及阶段） ─────────────────────
        {
            boolean def = (islandVal[0] < 0);
            updatingIsland[0] = true;
            swIslandDefault.setChecked(def);
            updatingIsland[0] = false;
            etIsland.setText(def ? "" : String.valueOf(islandVal[0]));
            toggleIslandUnit.check("s".equals(islandUnit[0])
                    ? R.id.btn_island_s : R.id.btn_island_m);
            setTimeoutRowEnabled(tilIsland, toggleIslandUnit, !def);
        }

        // ── 通知 UI 刷新（只加载当前阶段值；默认开关由 notifDefault[] 全局控制） ──
        final Runnable loadNotifUI = () -> {
            int idx = curNotifPhase[0];
            etNotif.setText((notifVals[idx] > 0) ? String.valueOf(notifVals[idx]) : "");
            toggleNotifUnit.check("s".equals(notifUnits[idx])
                    ? R.id.btn_notif_s : R.id.btn_notif_m);
        };

        // ── 通知 UI 写回内存 ─────────────────────────────────────────
        final Runnable saveNotifUI = () -> {
            int idx = curNotifPhase[0];
            if (notifDefault[0]) {
                notifVals[idx] = -1;
            } else {
                String s = etNotif.getText() != null ? etNotif.getText().toString().trim() : "";
                try { notifVals[idx] = s.isEmpty() ? -1 : Integer.parseInt(s); }
                catch (NumberFormatException e) { notifVals[idx] = -1; }
            }
            notifUnits[idx] = (toggleNotifUnit.getCheckedButtonId() == R.id.btn_notif_s) ? "s" : "m";
        };

        // ── 初始化通知 UI ─────────────────────────────────────────────
        {
            boolean def = notifDefault[0];
            updatingNotif[0] = true;
            swNotifDefault.setChecked(def);
            updatingNotif[0] = false;
            boolean en = !def;
            setTimeoutRowEnabled(tilNotif, toggleNotifUnit, en);
            toggleNotifPhase.setEnabled(en);
            toggleNotifPhase.setAlpha(en ? 1f : 0.4f);
            toggleNotifPhase.check(R.id.btn_notif_phase_pre);
            loadNotifUI.run();
        }

        // ── 通知阶段切换 ──────────────────────────────────────────────
        toggleNotifPhase.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            saveNotifUI.run();
            if      (checkedId == R.id.btn_notif_phase_pre)    curNotifPhase[0] = 0;
            else if (checkedId == R.id.btn_notif_phase_active) curNotifPhase[0] = 1;
            else                                                curNotifPhase[0] = 2;
            loadNotifUI.run();
        });

        // ── 保存按钮 ──────────────────────────────────────────────────
        findViewById(R.id.btn_save_timeout).setOnClickListener(v -> {
            saveNotifUI.run();

            // 读取岛当前 UI 值
            if (!swIslandDefault.isChecked()) {
                String s = etIsland.getText() != null ? etIsland.getText().toString().trim() : "";
                try { islandVal[0] = s.isEmpty() ? -1 : Integer.parseInt(s); }
                catch (NumberFormatException e) { islandVal[0] = -1; }
            }
            islandUnit[0] = (toggleIslandUnit.getCheckedButtonId() == R.id.btn_island_s) ? "s" : "m";

            SharedPreferences.Editor ed = sp.edit();
            Intent sync = new Intent("com.xiaoai.islandnotify.ACTION_SYNC_PREFS");
            sync.setPackage("com.miui.voiceassist");

            // 岛：单一全局键
            ed.putInt   ("to_island_val",  islandVal[0]);
            ed.putString("to_island_unit", islandUnit[0]);
            sync.putExtra("to_island_val",  islandVal[0]);
            sync.putExtra("to_island_unit", islandUnit[0]);

            // 通知：三阶段
            for (int i = 0; i < 3; i++) {
                ed.putInt   ("to_notif_val_"  + TO_PHASES[i], notifVals[i]);
                ed.putString("to_notif_unit_" + TO_PHASES[i], notifUnits[i]);
                sync.putExtra("to_notif_val_"  + TO_PHASES[i], notifVals[i]);
                sync.putExtra("to_notif_unit_" + TO_PHASES[i], notifUnits[i]);
            }
            ed.apply();
            sendBroadcast(sync);

            tvHint.setText("已保存，下次通知生效");
            tvHint.setVisibility(View.VISIBLE);
        });
    }

    /** 设置超时行（TextInputLayout + 单位切换按钮）的启用/禁用状态。 */
    private void setTimeoutRowEnabled(TextInputLayout til,
            MaterialButtonToggleGroup unitToggle, boolean enabled) {
        til.setEnabled(enabled);
        til.setAlpha(enabled ? 1f : 0.4f);
        unitToggle.setEnabled(enabled);
        unitToggle.setAlpha(enabled ? 1f : 0.4f);
    }

    private static final String ALIAS = "com.xiaoai.islandnotify.MainActivityAlias";

    private void initHideIconSwitch() {
        SwitchMaterial sw = findViewById(R.id.sw_hide_icon);
        PackageManager pm = getPackageManager();
        ComponentName alias = new ComponentName(this, ALIAS);

        // 读取当前状态：第一次安装后为 DEFAULT（显示）或 ENABLED
        int state = pm.getComponentEnabledSetting(alias);
        sw.setChecked(state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED);

        sw.setOnCheckedChangeListener((btn, checked) -> {
            pm.setComponentEnabledSetting(alias,
                    checked ? PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                            : PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP);
        });
    }

    // ─────────────────────────────────────────────────────────────
    // ─────────────────────────────────────────────────────────────

    /** 初始化关于页面，动态显示版本号 */
    private void initAboutSection() {
        TextView versionText = findViewById(R.id.version_text);
        if (versionText != null) {
            String versionName = getAppVersionName();
            versionText.setText(versionName);
        }
    }

    /** 获取应用版本名称 */
    private String getAppVersionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            Log.e("MainActivity", "获取版本信息失败", e);
            return "未知版本";
        }
    }

    /** 检查/请求通知权限，通过后再执行 action。 */
    private void requireNotifPermAndRun(Runnable action) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                pendingTestAction = action;
                notifPermLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS);
                return;
            }
        }
        action.run();
    }

    /**
     * 发送一条模拟课程提醒通知。
     *
     * @param startOffsetMs 上课时间相对于"现在"的偏移毫秒：
     *                      正数 = N 毫秒后上课（倒计时），负数 = N 毫秒前已开始（正计时）
     */
    private void sendTestNotification(long startOffsetMs) {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;

        // 读取输入框内容，为空时使用默认值
        android.widget.EditText etName      = findViewById(R.id.et_course_name);
        android.widget.EditText etClassroom = findViewById(R.id.et_classroom);
        String courseName  = etName.getText() != null ? etName.getText().toString().trim() : "";
        String classroom   = etClassroom.getText() != null ? etClassroom.getText().toString().trim() : "";
        if (courseName.isEmpty()) courseName = "高等数学";
        if (classroom.isEmpty())  classroom  = "教科A-101";

        // 确保通知频道存在（channelId 必须包含 COURSE_SCHEDULER_REMINDER）
        final String CHANNEL_ID = "COURSE_SCHEDULER_REMINDER_sound";
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID, "课程提醒", NotificationManager.IMPORTANCE_HIGH);
            nm.createNotificationChannel(ch);
        }

        // 开始时间 = 当前时间 + 偏移，结束时间 = 当前时间 + 2 分钟
        long now = System.currentTimeMillis();
        long startMs = now + startOffsetMs;
        long endMs   = now + 2 * 60_000L;
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.setTimeInMillis(startMs);
        String startTime = String.format(java.util.Locale.getDefault(), "%02d:%02d",
                cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE));
        cal.setTimeInMillis(endMs);
        String endTime = String.format(java.util.Locale.getDefault(), "%02d:%02d",
                cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE));

        // 不再打印假 JSON，也不再依赖 lastPushedInfo 跨进程缓存。
        // 测试通知必须走与真实通知完全相同的代码路径：
        //   extractFromRemoteViews → 反射 bigContentView.mActions → 读 CharSequence 值
        //
        // 真实 voiceassist bigContentView mActions 文本顺序（实测）：
        //   [0] "[课程名]快到了，提前准备一下吧"
        //   [1] 开始时间 "HH:mm"
        //   [2] 结束时间 "HH:mm"
        //   [3] 课程名（纯文字）
        //   [4] 教室
        //   [5] "上课静音"（按钮，会被过滤）
        //   [6] "完整课表"（按钮，会被过滤）
        android.widget.RemoteViews bigView = new android.widget.RemoteViews(
                getPackageName(), R.layout.notification_test_big);
        bigView.setTextViewText(R.id.tv_notif_title, "[" + courseName + "]快到了，提前准备一下吧");
        bigView.setTextViewText(R.id.tv_notif_start, startTime);
        bigView.setTextViewText(R.id.tv_notif_end,   endTime);
        bigView.setTextViewText(R.id.tv_notif_name,  courseName);
        bigView.setTextViewText(R.id.tv_notif_room,  classroom);

        android.app.Notification notif = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build();
        // 真实通知 EXTRA_TITLE / EXTRA_TEXT 均为 null，测试保持一致
        notif.extras.remove("android.title");
        notif.extras.remove("android.text");
        notif.extras.putBoolean("android.contains.customView", true);
        notif.bigContentView = bigView;

        nm.notify(1001, notif);
    }

    private void showTestHint(String msg) {
        TextView tv = findViewById(R.id.tv_test_hint);
        tv.setText(msg);
        tv.setVisibility(View.VISIBLE);
    }

    // ─────────────────────────────────────────────────────────────

    /**
     * 此方法默认返回 false。
     * 当模块在 LSPosed 中激活并注入本进程后，
     * MainHook#hookSelfStatus 会将此方法的返回值替换为 true，
     * 从而让界面正确显示「模块已激活」状态。
     */
    public static boolean isModuleActive() {
        return false;
    }
}
