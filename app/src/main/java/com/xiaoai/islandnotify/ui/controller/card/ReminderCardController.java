package com.xiaoai.islandnotify;

import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

final class ReminderCardController {

    private ReminderCardController() {}

    interface IntAction {
        void run(int value);
    }

    static void bind(AppCompatActivity activity, int initialMinutes, int fallbackMinutes, IntAction onSave) {
        EditText etMinutes = activity.findViewById(R.id.et_reminder_minutes);
        TextView tvHint = activity.findViewById(R.id.tv_reminder_hint);
        View saveButton = activity.findViewById(R.id.btn_save_reminder);
        if (etMinutes == null || tvHint == null || saveButton == null) return;

        etMinutes.setText(String.valueOf(initialMinutes));
        saveButton.setOnClickListener(v -> {
            int minutes = clampMinutes(etMinutes, fallbackMinutes);
            etMinutes.setText(String.valueOf(minutes));
            if (onSave != null) onSave.run(minutes);
            CardUiController.showHint(
                    tvHint,
                    "\u5df2\u4fdd\u5b58\uff0c\u91cd\u65b0\u8c03\u5ea6\u4eca\u65e5\u63d0\u9192\uff08\u63d0\u524d "
                            + minutes
                            + " \u5206\u949f\uff09");
        });
    }

    private static int clampMinutes(EditText etMinutes, int fallback) {
        String str = etMinutes.getText() != null ? etMinutes.getText().toString().trim() : "";
        int minutes;
        try {
            minutes = Integer.parseInt(str);
            if (minutes < 1) minutes = 1;
            if (minutes > 120) minutes = 120;
        } catch (NumberFormatException ignored) {
            minutes = fallback;
        }
        return minutes;
    }
}
