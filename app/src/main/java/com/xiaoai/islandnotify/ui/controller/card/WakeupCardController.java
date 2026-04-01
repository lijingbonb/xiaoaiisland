package com.xiaoai.islandnotify;

import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.switchmaterial.SwitchMaterial;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Locale;

final class WakeupCardController {

    private WakeupCardController() {}

    static void bind(AppCompatActivity activity, SharedPreferences prefs) {
        SwitchMaterial swMorning = activity.findViewById(R.id.sw_wakeup_morning);
        View llMorning = activity.findViewById(R.id.ll_wakeup_morning_content);
        EditText etLastSec = activity.findViewById(R.id.et_wakeup_morning_last_sec);
        LinearLayout llMorningRules = activity.findViewById(R.id.ll_wakeup_morning_rules);
        SwitchMaterial swAfternoon = activity.findViewById(R.id.sw_wakeup_afternoon);
        View llAfternoon = activity.findViewById(R.id.ll_wakeup_afternoon_content);
        EditText etFirstSec = activity.findViewById(R.id.et_wakeup_afternoon_first_sec);
        LinearLayout llAfternoonRules = activity.findViewById(R.id.ll_wakeup_afternoon_rules);
        TextView tvHint = activity.findViewById(R.id.tv_wakeup_hint);
        View btnAddMorning = activity.findViewById(R.id.btn_add_morning_rule);
        View btnAddAfternoon = activity.findViewById(R.id.btn_add_afternoon_rule);
        View btnSave = activity.findViewById(R.id.btn_save_wakeup);
        if (swMorning == null || llMorning == null || etLastSec == null || llMorningRules == null
                || swAfternoon == null || llAfternoon == null || etFirstSec == null
                || llAfternoonRules == null || tvHint == null || btnAddMorning == null
                || btnAddAfternoon == null || btnSave == null) {
            return;
        }

        loadRuleRows(activity, llMorningRules, PrefsAccess.readConfigString(
                prefs, "wakeup_morning_rules_json", ConfigDefaults.WAKEUP_MORNING_RULES_JSON));
        loadRuleRows(activity, llAfternoonRules, PrefsAccess.readConfigString(
                prefs, "wakeup_afternoon_rules_json", ConfigDefaults.WAKEUP_AFTERNOON_RULES_JSON));

        etLastSec.setText(String.valueOf(PrefsAccess.readConfigInt(
                prefs, "wakeup_morning_last_sec", ConfigDefaults.WAKEUP_MORNING_LAST_SEC)));
        etFirstSec.setText(String.valueOf(PrefsAccess.readConfigInt(
                prefs, "wakeup_afternoon_first_sec", ConfigDefaults.WAKEUP_AFTERNOON_FIRST_SEC)));

        CardUiController.bindSwitchContent(
                swMorning,
                llMorning,
                PrefsAccess.readConfigBool(prefs, "wakeup_morning_enabled", ConfigDefaults.SWITCH_DISABLED),
                checked -> PrefsAccess.edit(prefs).putBoolean("wakeup_morning_enabled", checked).apply());
        CardUiController.bindSwitchContent(
                swAfternoon,
                llAfternoon,
                PrefsAccess.readConfigBool(prefs, "wakeup_afternoon_enabled", ConfigDefaults.SWITCH_DISABLED),
                checked -> PrefsAccess.edit(prefs).putBoolean("wakeup_afternoon_enabled", checked).apply());

        btnAddMorning.setOnClickListener(v -> addRuleRow(activity, llMorningRules, 1, 7, 0));
        btnAddAfternoon.setOnClickListener(v -> addRuleRow(activity, llAfternoonRules, 5, 12, 0));

        btnSave.setOnClickListener(v -> {
            int lastSec = parseRuleInt(etLastSec, 4);
            if (lastSec < 1) lastSec = 1;
            int firstSec = parseRuleInt(etFirstSec, 5);
            if (firstSec < 1) firstSec = 1;
            etLastSec.setText(String.valueOf(lastSec));
            etFirstSec.setText(String.valueOf(firstSec));

            String morningRulesJson = collectRulesJson(llMorningRules);
            String afternoonRulesJson = collectRulesJson(llAfternoonRules);

            PrefsAccess.edit(prefs)
                    .putInt("wakeup_morning_last_sec", lastSec)
                    .putInt("wakeup_afternoon_first_sec", firstSec)
                    .putString("wakeup_morning_rules_json", morningRulesJson)
                    .putString("wakeup_afternoon_rules_json", afternoonRulesJson)
                    .apply();

            CardUiController.showHint(
                    tvHint,
                    "\u8bbe\u7f6e\u5df2\u4fdd\u5b58\u5e76\u91cd\u65b0\u8c03\u5ea6\u53eb\u9192\u95f9\u949f");
        });
    }

    private static void loadRuleRows(AppCompatActivity activity, LinearLayout container, String rulesJson) {
        container.removeAllViews();
        try {
            JSONArray arr = new JSONArray(rulesJson);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                addRuleRow(activity, container, obj.getInt("sec"), obj.getInt("hour"), obj.getInt("minute"));
            }
        } catch (Exception ignored) {
            addRuleRow(activity, container, 1, 7, 0);
        }
    }

    private static void addRuleRow(
            AppCompatActivity activity, LinearLayout container, int sec, int hour, int minute) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        float dp = activity.getResources().getDisplayMetrics().density;
        rowLp.topMargin = (int) (4 * dp);
        row.setLayoutParams(rowLp);
        row.setGravity(Gravity.CENTER_VERTICAL);

        TextView tvSec = new TextView(activity);
        tvSec.setText("\u7b2c");
        row.addView(tvSec);

        EditText etSec = new EditText(activity);
        LinearLayout.LayoutParams secLp = new LinearLayout.LayoutParams(
                (int) (56 * dp), ViewGroup.LayoutParams.WRAP_CONTENT);
        etSec.setLayoutParams(secLp);
        etSec.setInputType(InputType.TYPE_CLASS_NUMBER);
        etSec.setMaxLines(1);
        etSec.setText(String.valueOf(sec));
        etSec.setGravity(Gravity.CENTER);
        row.addView(etSec);

        TextView tvArrow = new TextView(activity);
        tvArrow.setText(" \u8282\u2192");
        row.addView(tvArrow);

        EditText etHour = new EditText(activity);
        LinearLayout.LayoutParams hourLp = new LinearLayout.LayoutParams(
                (int) (52 * dp), ViewGroup.LayoutParams.WRAP_CONTENT);
        etHour.setLayoutParams(hourLp);
        etHour.setInputType(InputType.TYPE_CLASS_NUMBER);
        etHour.setMaxLines(1);
        etHour.setText(String.valueOf(hour));
        etHour.setGravity(Gravity.CENTER);
        row.addView(etHour);

        TextView tvColon = new TextView(activity);
        tvColon.setText(" : ");
        row.addView(tvColon);

        EditText etMin = new EditText(activity);
        LinearLayout.LayoutParams minLp = new LinearLayout.LayoutParams(
                (int) (52 * dp), ViewGroup.LayoutParams.WRAP_CONTENT);
        etMin.setLayoutParams(minLp);
        etMin.setInputType(InputType.TYPE_CLASS_NUMBER);
        etMin.setMaxLines(1);
        etMin.setText(String.format(Locale.getDefault(), "%02d", minute));
        etMin.setGravity(Gravity.CENTER);
        row.addView(etMin);

        MaterialButton btnDel = new MaterialButton(
                activity, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        btnDel.setText("\u5220\u9664");
        btnDel.setTextSize(12f);
        btnDel.setStrokeColor(ColorStateList.valueOf(0xFFBA1A1A));
        btnDel.setTextColor(0xFFBA1A1A);
        LinearLayout.LayoutParams delLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, (int) (36 * dp));
        delLp.setMarginStart((int) (4 * dp));
        btnDel.setLayoutParams(delLp);
        btnDel.setOnClickListener(delV -> container.removeView(row));
        row.addView(btnDel);

        container.addView(row);
    }

    private static String collectRulesJson(LinearLayout container) {
        JSONArray arr = new JSONArray();
        for (int i = 0; i < container.getChildCount(); i++) {
            View rowView = container.getChildAt(i);
            if (!(rowView instanceof LinearLayout)) continue;
            LinearLayout row = (LinearLayout) rowView;
            try {
                EditText etSec = (EditText) row.getChildAt(1);
                EditText etHour = (EditText) row.getChildAt(3);
                EditText etMin = (EditText) row.getChildAt(5);
                int sec = Integer.parseInt(etSec.getText().toString().trim());
                int hour = parseHour(etHour);
                int minute = parseMinute(etMin);
                JSONObject obj = new JSONObject();
                obj.put("sec", sec);
                obj.put("hour", hour);
                obj.put("minute", minute);
                arr.put(obj);
            } catch (Exception ignored) {
            }
        }
        return arr.toString();
    }

    private static int parseRuleInt(EditText et, int defaultVal) {
        try {
            return Integer.parseInt(et.getText().toString().trim());
        } catch (NumberFormatException e) {
            return defaultVal;
        }
    }

    private static int parseHour(EditText et) {
        try {
            int value = Integer.parseInt(et.getText() != null ? et.getText().toString().trim() : "0");
            return Math.max(0, Math.min(23, value));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static int parseMinute(EditText et) {
        try {
            int value = Integer.parseInt(et.getText() != null ? et.getText().toString().trim() : "0");
            return Math.max(0, Math.min(59, value));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
