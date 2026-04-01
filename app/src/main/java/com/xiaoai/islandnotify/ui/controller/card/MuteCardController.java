package com.xiaoai.islandnotify;

import android.content.SharedPreferences;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.switchmaterial.SwitchMaterial;

final class MuteCardController {

    private static final String KEY_REPOST_ENABLED = "repost_enabled";

    private MuteCardController() {}

    static void bind(AppCompatActivity activity, SharedPreferences prefs) {
        SwitchMaterial swMute = activity.findViewById(R.id.sw_mute_enabled);
        SwitchMaterial swRepost = activity.findViewById(R.id.sw_repost_enabled);
        View llMute = activity.findViewById(R.id.ll_mute_content);
        EditText etMuteBefore = activity.findViewById(R.id.et_mute_mins_before);
        SwitchMaterial swUnmute = activity.findViewById(R.id.sw_unmute_enabled);
        View llUnmute = activity.findViewById(R.id.ll_unmute_content);
        EditText etUnmuteAfter = activity.findViewById(R.id.et_unmute_mins_after);
        SwitchMaterial swDnd = activity.findViewById(R.id.sw_dnd_enabled);
        View llDnd = activity.findViewById(R.id.ll_dnd_content);
        EditText etDndBefore = activity.findViewById(R.id.et_dnd_mins_before);
        SwitchMaterial swUnDnd = activity.findViewById(R.id.sw_undnd_enabled);
        View llUnDnd = activity.findViewById(R.id.ll_undnd_content);
        EditText etUnDndAfter = activity.findViewById(R.id.et_undnd_mins_after);
        TextView tvMuteHint = activity.findViewById(R.id.tv_mute_hint);
        MaterialButtonToggleGroup toggleMode = activity.findViewById(R.id.toggle_island_button_mode);
        View saveButton = activity.findViewById(R.id.btn_save_mute);
        if (swMute == null || swRepost == null || llMute == null || etMuteBefore == null
                || swUnmute == null || llUnmute == null || etUnmuteAfter == null
                || swDnd == null || llDnd == null || etDndBefore == null
                || swUnDnd == null || llUnDnd == null || etUnDndAfter == null
                || tvMuteHint == null || toggleMode == null || saveButton == null) {
            return;
        }

        CardUiController.bindSwitch(
                swRepost,
                PrefsAccess.readConfigBool(prefs, KEY_REPOST_ENABLED, ConfigDefaults.REPOST_ENABLED),
                checked -> PrefsAccess.edit(prefs).putBoolean(KEY_REPOST_ENABLED, checked).apply());

        etMuteBefore.setText(String.valueOf(PrefsAccess.readConfigInt(
                prefs, "mute_mins_before", ConfigDefaults.MINUTES_OFFSET)));
        etUnmuteAfter.setText(String.valueOf(PrefsAccess.readConfigInt(
                prefs, "unmute_mins_after", ConfigDefaults.MINUTES_OFFSET)));
        etDndBefore.setText(String.valueOf(PrefsAccess.readConfigInt(
                prefs, "dnd_mins_before", ConfigDefaults.MINUTES_OFFSET)));
        etUnDndAfter.setText(String.valueOf(PrefsAccess.readConfigInt(
                prefs, "undnd_mins_after", ConfigDefaults.MINUTES_OFFSET)));

        CardUiController.bindSwitchContent(
                swMute,
                llMute,
                PrefsAccess.readConfigBool(prefs, "mute_enabled", ConfigDefaults.SWITCH_DISABLED),
                checked -> PrefsAccess.edit(prefs).putBoolean("mute_enabled", checked).apply());
        CardUiController.bindSwitchContent(
                swUnmute,
                llUnmute,
                PrefsAccess.readConfigBool(prefs, "unmute_enabled", ConfigDefaults.SWITCH_DISABLED),
                checked -> PrefsAccess.edit(prefs).putBoolean("unmute_enabled", checked).apply());
        CardUiController.bindSwitchContent(
                swDnd,
                llDnd,
                PrefsAccess.readConfigBool(prefs, "dnd_enabled", ConfigDefaults.SWITCH_DISABLED),
                checked -> PrefsAccess.edit(prefs).putBoolean("dnd_enabled", checked).apply());
        CardUiController.bindSwitchContent(
                swUnDnd,
                llUnDnd,
                PrefsAccess.readConfigBool(prefs, "undnd_enabled", ConfigDefaults.SWITCH_DISABLED),
                checked -> PrefsAccess.edit(prefs).putBoolean("undnd_enabled", checked).apply());

        int savedMode = PrefsAccess.readConfigInt(
                prefs, "island_button_mode", ConfigDefaults.ISLAND_BUTTON_MODE);
        if (savedMode == 0) {
            toggleMode.check(R.id.btn_mode_mute);
        } else if (savedMode == 1) {
            toggleMode.check(R.id.btn_mode_dnd);
        } else {
            toggleMode.check(R.id.btn_mode_both);
        }

        saveButton.setOnClickListener(v -> {
            int muteBefore = parseMinutes(etMuteBefore);
            int unmuteAfter = parseMinutes(etUnmuteAfter);
            int dndBefore = parseMinutes(etDndBefore);
            int unDndAfter = parseMinutes(etUnDndAfter);

            int selectedId = toggleMode.getCheckedButtonId();
            int buttonMode = (selectedId == R.id.btn_mode_mute)
                    ? 0
                    : (selectedId == R.id.btn_mode_dnd ? 1 : 2);

            etMuteBefore.setText(String.valueOf(muteBefore));
            etUnmuteAfter.setText(String.valueOf(unmuteAfter));
            etDndBefore.setText(String.valueOf(dndBefore));
            etUnDndAfter.setText(String.valueOf(unDndAfter));

            PrefsAccess.edit(prefs)
                    .putInt("mute_mins_before", muteBefore)
                    .putInt("unmute_mins_after", unmuteAfter)
                    .putInt("dnd_mins_before", dndBefore)
                    .putInt("undnd_mins_after", unDndAfter)
                    .putInt("island_button_mode", buttonMode)
                    .apply();

            CardUiController.showHint(
                    tvMuteHint, "\u8bbe\u7f6e\u5df2\u4fdd\u5b58\u5e76\u91cd\u65b0\u8c03\u5ea6");
        });
    }

    private static int parseMinutes(EditText et) {
        try {
            int value = Integer.parseInt(et.getText() != null ? et.getText().toString().trim() : "0");
            return Math.max(0, Math.min(60, value));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
