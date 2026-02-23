package com.xiaoai.islandnotify;

import android.app.Activity;
import android.media.AudioManager;
import android.os.Bundle;

/**
 * 无界面静音 Activity。
 *
 * <p>Super Island 按钮使用 {@code actionIntentType=1}（startActivity）调用此 Activity，
 * 比广播（type=2）更可靠，在所有 MIUI 版本中均能触发。
 * 主题设为 {@code Theme.NoDisplay}，不产生任何窗口闪烁。
 */
public class MuteActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
            if (am != null) {
                am.setRingerMode(AudioManager.RINGER_MODE_SILENT);
            }
        } catch (Exception e) {
            // 忽略权限异常（极少数机型）
        }
        finish(); // 立即关闭，无 UI
    }
}
