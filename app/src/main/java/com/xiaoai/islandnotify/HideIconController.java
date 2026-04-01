package com.xiaoai.islandnotify;

import android.content.ComponentName;
import android.content.pm.PackageManager;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.switchmaterial.SwitchMaterial;

final class HideIconController {

    private HideIconController() {}

    static void bind(AppCompatActivity activity, String aliasClassName) {
        SwitchMaterial sw = activity.findViewById(R.id.sw_hide_icon);
        if (sw == null) return;

        PackageManager pm = activity.getPackageManager();
        ComponentName alias = new ComponentName(activity, aliasClassName);
        int state = pm.getComponentEnabledSetting(alias);
        sw.setChecked(state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED);

        sw.setOnCheckedChangeListener((btn, checked) ->
                pm.setComponentEnabledSetting(
                        alias,
                        checked ? PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                                : PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                        PackageManager.DONT_KILL_APP));
    }
}
