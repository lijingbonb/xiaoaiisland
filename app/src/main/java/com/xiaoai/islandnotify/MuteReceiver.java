package com.xiaoai.islandnotify;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** 已废弃，静音操作全部在 voiceassist 进程内由 MainHook 处理。保留仅供编译通过。 */
public class MuteReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {}
}
