package com.xiaoai.islandnotify;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

public class DebugSyncReceiver extends BroadcastReceiver {

    private static final String ACTION_DEBUG_SYNC = "com.xiaoai.islandnotify.ACTION_DEBUG_SYNC";
    private static final String PREFS_NAME = "island_debug";
    private static final String EXTRA_DEBUG_KEY = "debug_key";
    private static final String EXTRA_DEBUG_TYPE = "debug_type";
    private static final String EXTRA_DEBUG_STRING = "debug_string";
    private static final String EXTRA_DEBUG_INT = "debug_int";
    private static final String EXTRA_DEBUG_LONG = "debug_long";
    private static final int DEBUG_TYPE_STRING = 1;
    private static final int DEBUG_TYPE_INT = 2;
    private static final int DEBUG_TYPE_LONG = 3;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null) return;
        if (!ACTION_DEBUG_SYNC.equals(intent.getAction())) return;
        String key = intent.getStringExtra(EXTRA_DEBUG_KEY);
        if (key == null || key.isEmpty()) return;
        if (!key.startsWith("debug_")) return;
        int type = intent.getIntExtra(EXTRA_DEBUG_TYPE, 0);
        SharedPreferences.Editor ed = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit();
        if (type == DEBUG_TYPE_STRING) {
            ed.putString(key, intent.getStringExtra(EXTRA_DEBUG_STRING));
        } else if (type == DEBUG_TYPE_INT) {
            ed.putInt(key, intent.getIntExtra(EXTRA_DEBUG_INT, 0));
        } else if (type == DEBUG_TYPE_LONG) {
            ed.putLong(key, intent.getLongExtra(EXTRA_DEBUG_LONG, 0L));
        } else {
            return;
        }
        ed.apply();
    }
}
