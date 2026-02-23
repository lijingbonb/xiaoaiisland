package com.xiaoai.islandnotify;

import android.Manifest;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * 测试 Activity：无需 com.miui.voiceassist，直接向系统发送携带
 * miui.focus.param 的通知，用于在设备上验证超级岛渲染效果。
 */
public class TestActivity extends Activity {

    private static final String TEST_CHANNEL_ID = "island_notify_test";
    private static final int    NOTIF_ID        = 99901;

    private EditText etCourse;
    private EditText etTime;
    private EditText etRoom;
    private TextView tvJson;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // ── 根布局 ────────────────────────────────────────────────
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(20), dp(20), dp(20));
        root.setBackgroundColor(Color.parseColor("#F5F5F5"));

        // ── 标题 ──────────────────────────────────────────────────
        TextView title = new TextView(this);
        title.setText("超级岛通知测试");
        title.setTextSize(20);
        title.setTextColor(Color.BLACK);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, dp(16));
        root.addView(title);

        // ── 输入区 ────────────────────────────────────────────────
        etCourse = makeInput("课程名称", "高等数学");
        etTime   = makeInput("上课时间", "10:20");
        etRoom   = makeInput("教室",     "教1-201");
        root.addView(labelWrap("课程名称", etCourse));
        root.addView(labelWrap("上课时间", etTime));
        root.addView(labelWrap("教室",     etRoom));

        // ── 按钮组 ────────────────────────────────────────────────
        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setGravity(Gravity.CENTER);

        Button btnSend = makeButton("发送超级岛通知", Color.parseColor("#FF6200EE"));
        btnSend.setOnClickListener(v -> sendIslandNotification());

        Button btnRaw = makeButton("发送原始通知(测试Hook)", Color.parseColor("#FF03DAC5"));
        btnRaw.setOnClickListener(v -> sendRawNotification());

        btnRow.addView(btnSend);
        btnRow.addView(space(dp(12)));
        btnRow.addView(btnRaw);
        root.addView(btnRow);
        root.addView(space(dp(12)));

        // ── JSON 预览 ─────────────────────────────────────────────
        TextView jsonLabel = new TextView(this);
        jsonLabel.setText("注入的 JSON 预览：");
        jsonLabel.setTextColor(Color.DKGRAY);
        root.addView(jsonLabel);

        tvJson = new TextView(this);
        tvJson.setTextSize(11);
        tvJson.setTextColor(Color.parseColor("#212121"));
        tvJson.setBackgroundColor(Color.WHITE);
        tvJson.setPadding(dp(8), dp(8), dp(8), dp(8));
        tvJson.setText("点击按钮后此处显示 JSON");

        ScrollView scroll = new ScrollView(this);
        scroll.addView(tvJson);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(300));
        lp.topMargin = dp(4);
        scroll.setLayoutParams(lp);
        root.addView(scroll);

        setContentView(root);

        // ── 申请通知权限（Android 13+）────────────────────────────
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(
                        new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
            }
        }
        createChannel();
    }

    // ─────────────────────────────────────────────────────────────
    // 发送超级岛通知（已注入 miui.focus.param）
    // ─────────────────────────────────────────────────────────────

    private void sendIslandNotification() {
        String course = etCourse.getText().toString().trim();
        String time   = etTime.getText().toString().trim();
        String room   = etRoom.getText().toString().trim();
        if (course.isEmpty()) { toast("请输入课程名称"); return; }

        try {
            String json = buildIslandJson(course, time, room);
            tvJson.setText(prettyJson(json));

            Notification.Builder builder = new Notification.Builder(this, TEST_CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle(course)
                    .setContentText(time + (room.isEmpty() ? "" : " | " + room))
                    .setAutoCancel(true);

            Notification notif = builder.build();
            notif.extras.putString("miui.focus.param", json);

            getSystemService(NotificationManager.class).notify(NOTIF_ID, notif);
            toast("已发送超级岛通知 ✓");
        } catch (JSONException e) {
            toast("JSON 构建失败：" + e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 发送原始通知（模拟 com.miui.voiceassist 的格式，测试 Hook 识别）
    // ─────────────────────────────────────────────────────────────

    private void sendRawNotification() {
        String course = etCourse.getText().toString().trim();
        String time   = etTime.getText().toString().trim();
        String room   = etRoom.getText().toString().trim();
        if (course.isEmpty()) { toast("请输入课程名称"); return; }

        // 完全模拟 com.miui.voiceassist 的通知格式（来自 logcat）
        String fakeTitle = "[" + course + "]快到了，提前准备一下吧";
        String fakeBody  = time + (room.isEmpty() ? "" : " | " + room);
        tvJson.setText("原始通知格式（Hook 将自动转换）\n\ntitle: " + fakeTitle
                + "\ntext:  " + fakeBody
                + "\n\n注意：Hook 仅在 com.miui.voiceassist\n进程内生效，"
                + "此按钮从本模块进程发出，\n仅用于验证通知格式是否正确。");

        Notification notif = new Notification.Builder(this, TEST_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(fakeTitle)
                .setContentText(fakeBody)
                .setAutoCancel(true)
                .build();

        getSystemService(NotificationManager.class).notify(NOTIF_ID + 1, notif);
        toast("已发送原始格式通知 ✓");
    }

    // ─────────────────────────────────────────────────────────────
    // 构建超级岛 JSON（与 MainHook.buildIslandParams 保持一致）
    // ─────────────────────────────────────────────────────────────

    private String buildIslandJson(String course, String time, String room)
            throws JSONException {

        // 主要文本（大岛 A 区）
        JSONObject mainTextInfo = new JSONObject();
        mainTextInfo.put("title", course);

        JSONObject imageTextInfoLeft = new JSONObject();
        imageTextInfoLeft.put("type", 1);
        imageTextInfoLeft.put("textInfo", mainTextInfo);

        JSONObject bigIslandArea = new JSONObject();
        bigIslandArea.put("imageTextInfoLeft", imageTextInfoLeft);

        // 小岛摘要
        JSONObject smallTextInfo = new JSONObject();
        smallTextInfo.put("title", course);
        if (!time.isEmpty()) smallTextInfo.put("content", time);
        JSONObject smallIslandArea = new JSONObject();
        smallIslandArea.put("textInfo", smallTextInfo);

        JSONObject paramIsland = new JSONObject();
        paramIsland.put("islandProperty", 1);
        paramIsland.put("bigIslandArea",   bigIslandArea);
        paramIsland.put("smallIslandArea", smallIslandArea);

        // hintInfo：时间 + 教室（PDF 字段：content/title/subContent/subTitle）
        JSONObject hintInfo = new JSONObject();
        hintInfo.put("type", 1);
        hintInfo.put("content", "时间");
        hintInfo.put("title",   time.isEmpty() ? "—" : time);
        if (!room.isEmpty()) {
            hintInfo.put("subContent", "教室");
            hintInfo.put("subTitle",   room);
        }

        // baseInfo：content 只放时间+教室，避免重复课程名
        String ticker = time.isEmpty() ? course : course + "  " + time;
        StringBuilder baseContent = new StringBuilder();
        if (!time.isEmpty()) baseContent.append(time);
        if (!room.isEmpty()) {
            if (baseContent.length() > 0) baseContent.append("  ");
            baseContent.append(room);
        }
        JSONObject baseInfo = new JSONObject();
        baseInfo.put("title",   course);
        baseInfo.put("content", baseContent.length() > 0 ? baseContent.toString() : course);
        baseInfo.put("type", 1);

        // param_v2
        JSONObject paramV2 = new JSONObject();
        paramV2.put("protocol",        1);
        paramV2.put("business",        "course_schedule");
        paramV2.put("islandFirstFloat", true);
        paramV2.put("enableFloat",      false);
        paramV2.put("updatable",        false);
        paramV2.put("ticker",           ticker);
        paramV2.put("aodTitle",         ticker);
        paramV2.put("param_island",     paramIsland);
        paramV2.put("baseInfo",         baseInfo);
        paramV2.put("hintInfo",         hintInfo);

        JSONObject root = new JSONObject();
        root.put("param_v2", paramV2);
        return root.toString();
    }

    // ─────────────────────────────────────────────────────────────
    // 工具方法
    // ─────────────────────────────────────────────────────────────

    private void createChannel() {
        NotificationChannel ch = new NotificationChannel(
                TEST_CHANNEL_ID, "超级岛测试", NotificationManager.IMPORTANCE_HIGH);
        getSystemService(NotificationManager.class).createNotificationChannel(ch);
    }

    private String prettyJson(String json) {
        try {
            return new JSONObject(json).toString(2);
        } catch (JSONException e) {
            return json;
        }
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private EditText makeInput(String hint, String def) {
        EditText et = new EditText(this);
        et.setHint(hint);
        et.setText(def);
        et.setBackgroundColor(Color.WHITE);
        et.setPadding(dp(8), dp(6), dp(8), dp(6));
        et.setSingleLine();
        return et;
    }

    private LinearLayout labelWrap(String label, EditText et) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(8);
        row.setLayoutParams(lp);

        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextColor(Color.DKGRAY);
        tv.setMinWidth(dp(70));
        row.addView(tv);

        LinearLayout.LayoutParams etLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        et.setLayoutParams(etLp);
        row.addView(et);
        return row;
    }

    private Button makeButton(String text, int color) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setTextColor(Color.WHITE);
        btn.setBackgroundColor(color);
        btn.setPadding(dp(12), dp(8), dp(12), dp(8));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        btn.setLayoutParams(lp);
        return btn;
    }

    private View space(int size) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(size, size));
        return v;
    }
}
