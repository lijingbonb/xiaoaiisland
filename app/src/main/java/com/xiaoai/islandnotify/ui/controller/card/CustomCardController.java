package com.xiaoai.islandnotify;

import android.content.SharedPreferences;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputLayout;

final class CustomCardController {

    private static final String TOKEN_COUNTDOWN = "{\u5012\u8ba1\u65f6}";
    private static final String TOKEN_ELAPSED = "{\u6b63\u8ba1\u65f6}";

    private CustomCardController() {}

    static void applyExpandedFieldOrderHints(AppCompatActivity activity) {
        for (int stage = 0; stage < ConfigDefaults.STAGE_PHASES.length; stage++) {
            for (int field = 0; field < ConfigDefaults.EXPANDED_TPL_EDIT_IDS.length
                    && field < ConfigDefaults.EXPANDED_TPL_HINTS.length; field++) {
                setTextInputLayoutHint(
                        activity,
                        ConfigDefaults.EXPANDED_TPL_EDIT_IDS[field][stage],
                        ConfigDefaults.EXPANDED_TPL_HINTS[field]);
            }
        }
    }

    static void bindDirtyWatcher(EditText editText, Runnable onDirtyChanged) {
        if (editText == null) return;
        editText.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}

            @Override
            public void onTextChanged(CharSequence s, int st, int b, int c) {
                if (onDirtyChanged != null) onDirtyChanged.run();
            }

            @Override public void afterTextChanged(Editable s) {}
        });
    }

    static int alignExpandedTimerDirectionWithStatusBarFromUi(
            AppCompatActivity activity, int[] statusIdsB, int[][] expandedIdsV2) {
        int changed = 0;
        for (int i = 0; i < statusIdsB.length; i++) {
            EditText etStatusBarB = activity.findViewById(statusIdsB[i]);
            EditText etExpandedHintTitle = activity.findViewById(expandedIdsV2[1][i]);
            EditText etExpandedHintSubTitle = activity.findViewById(expandedIdsV2[2][i]);
            if (etStatusBarB == null || etExpandedHintTitle == null || etExpandedHintSubTitle == null) continue;

            String statusBarText = etStatusBarB.getText() == null ? "" : etStatusBarB.getText().toString().trim();
            int statusKind = detectTimerKind(statusBarText);
            changed += alignOneExpandedTimerField(etExpandedHintTitle, statusKind);
            changed += alignOneExpandedTimerField(etExpandedHintSubTitle, statusKind);
        }
        return changed;
    }

    static int alignStatusBarTimerDirectionWithExpandedFromUi(
            AppCompatActivity activity, int[] statusIdsB, int[][] expandedIdsV2) {
        int changed = 0;
        for (int i = 0; i < statusIdsB.length; i++) {
            EditText etStatusBarB = activity.findViewById(statusIdsB[i]);
            EditText etExpandedHintTitle = activity.findViewById(expandedIdsV2[1][i]);
            EditText etExpandedHintSubTitle = activity.findViewById(expandedIdsV2[2][i]);
            if (etStatusBarB == null || etExpandedHintTitle == null || etExpandedHintSubTitle == null) continue;

            int expandedKind = detectExpandedTimerKindForStage(
                    etExpandedHintTitle.getText() == null ? "" : etExpandedHintTitle.getText().toString().trim(),
                    etExpandedHintSubTitle.getText() == null ? "" : etExpandedHintSubTitle.getText().toString().trim());
            if (expandedKind != -1 && expandedKind != 1) continue;
            changed += alignStatusBarTimerField(etStatusBarB, expandedKind);
        }
        return changed;
    }

    static void refreshFromPrefs(
            AppCompatActivity activity,
            SharedPreferences prefs,
            int[] idsA,
            int[] idsB,
            int[] idsTicker,
            int[][] expandedIdsV2,
            String[] suffixes) {
        for (int i = 0; i < suffixes.length; i++) {
            EditText etA = activity.findViewById(idsA[i]);
            EditText etB = activity.findViewById(idsB[i]);
            EditText etTicker = activity.findViewById(idsTicker[i]);
            if (etA != null) {
                etA.setText(PrefsAccess.readStagedTemplate(prefs, "tpl_a", suffixes[i], ""));
            }
            if (etB != null) {
                etB.setText(PrefsAccess.readStagedTemplate(prefs, "tpl_b", suffixes[i], ""));
            }
            if (etTicker != null) {
                etTicker.setText(PrefsAccess.readStagedTemplate(prefs, "tpl_ticker", suffixes[i], ""));
            }
            for (int k = 0; k < ConfigDefaults.EXPANDED_TPL_KEYS.length; k++) {
                EditText etExpanded = activity.findViewById(expandedIdsV2[k][i]);
                if (etExpanded == null) continue;
                etExpanded.setText(PrefsAccess.readStagedString(
                        prefs,
                        ConfigDefaults.EXPANDED_TPL_KEYS[k],
                        suffixes[i],
                        ConfigDefaults.expandedTemplateDefault(i, k, "")));
            }
        }
        SwitchMaterial swIconA = activity.findViewById(R.id.sw_icon_a);
        if (swIconA != null) swIconA.setChecked(PrefsAccess.readConfigBool(prefs, "icon_a", true));
    }

    static boolean isStatusDirty(
            AppCompatActivity activity,
            SharedPreferences prefs,
            int[] idsA,
            int[] idsB,
            int[] idsTicker,
            String[] suffixes) {
        for (int i = 0; i < suffixes.length; i++) {
            EditText etA = activity.findViewById(idsA[i]);
            EditText etB = activity.findViewById(idsB[i]);
            EditText etTicker = activity.findViewById(idsTicker[i]);
            if (etA == null || etB == null || etTicker == null) continue;

            String curA = etA.getText() == null ? "" : etA.getText().toString().trim();
            String curB = etB.getText() == null ? "" : etB.getText().toString().trim();
            String curT = etTicker.getText() == null ? "" : etTicker.getText().toString().trim();
            String savedA = PrefsAccess.readStagedTemplate(prefs, "tpl_a", suffixes[i], "");
            String savedB = PrefsAccess.readStagedTemplate(prefs, "tpl_b", suffixes[i], "");
            String savedT = PrefsAccess.readStagedTemplate(prefs, "tpl_ticker", suffixes[i], "");
            if (!curA.equals(savedA) || !curB.equals(savedB) || !curT.equals(savedT)) return true;
        }
        SwitchMaterial swIconA = activity.findViewById(R.id.sw_icon_a);
        return swIconA != null && (swIconA.isChecked() != PrefsAccess.readConfigBool(prefs, "icon_a", true));
    }

    static boolean isExpandedDirty(
            AppCompatActivity activity,
            SharedPreferences prefs,
            int[][] expandedIdsV2,
            String[] suffixes) {
        for (int i = 0; i < suffixes.length; i++) {
            for (int k = 0; k < ConfigDefaults.EXPANDED_TPL_KEYS.length; k++) {
                EditText etExpanded = activity.findViewById(expandedIdsV2[k][i]);
                if (etExpanded == null) continue;
                String cur = etExpanded.getText() == null ? "" : etExpanded.getText().toString().trim();
                String saved = PrefsAccess.readStagedString(
                        prefs,
                        ConfigDefaults.EXPANDED_TPL_KEYS[k],
                        suffixes[i],
                        ConfigDefaults.expandedTemplateDefault(i, k, ""));
                if (!cur.equals(saved)) return true;
            }
        }
        return false;
    }

    private static void setTextInputLayoutHint(AppCompatActivity activity, int editTextId, String hint) {
        View child = activity.findViewById(editTextId);
        if (child == null) return;
        View parent = (View) child.getParent();
        if (parent instanceof TextInputLayout) {
            ((TextInputLayout) parent).setHint(hint);
        }
    }

    private static int detectExpandedTimerKindForStage(String hintTitle, String hintSubTitle) {
        int titleKind = detectTimerKind(hintTitle);
        if (titleKind == -1 || titleKind == 1) return titleKind;
        int subKind = detectTimerKind(hintSubTitle);
        if (subKind == -1 || subKind == 1) return subKind;
        return 0;
    }

    private static int alignStatusBarTimerField(EditText target, int expandedKind) {
        if (target == null) return 0;
        String text = target.getText() == null ? "" : target.getText().toString().trim();
        int kind = detectTimerKind(text);
        if ((kind == -1 || kind == 1) && kind != expandedKind) {
            String aligned = forceTimerKind(text, expandedKind);
            if (!aligned.equals(text)) {
                target.setText(aligned);
                return 1;
            }
        }
        return 0;
    }

    private static int alignOneExpandedTimerField(EditText target, int statusKind) {
        if (target == null) return 0;
        String text = target.getText() == null ? "" : target.getText().toString().trim();
        int kind = detectTimerKind(text);
        if ((statusKind == -1 || statusKind == 1)
                && (kind == -1 || kind == 1)
                && statusKind != kind) {
            String aligned = forceTimerKind(text, statusKind);
            if (!aligned.equals(text)) {
                target.setText(aligned);
                return 1;
            }
        }
        return 0;
    }

    private static int detectTimerKind(String text) {
        if (text == null || text.isEmpty()) return 0;
        boolean hasCountdown = text.contains(TOKEN_COUNTDOWN);
        boolean hasElapsed = text.contains(TOKEN_ELAPSED);
        if (hasCountdown && hasElapsed) return 2;
        if (hasCountdown) return -1;
        if (hasElapsed) return 1;
        return 0;
    }

    private static String forceTimerKind(String text, int targetKind) {
        if (text == null) return "";
        if (targetKind >= 0) {
            return text.replace(TOKEN_COUNTDOWN, TOKEN_ELAPSED);
        }
        return text.replace(TOKEN_ELAPSED, TOKEN_COUNTDOWN);
    }
}
