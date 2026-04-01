package com.xiaoai.islandnotify;

import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

final class AboutSectionController {

    private AboutSectionController() {}

    static void bind(AppCompatActivity activity) {
        TextView versionText = activity.findViewById(R.id.version_text);
        if (versionText != null) versionText.setText(readAppVersionName(activity));

        TextView tvAuthor = activity.findViewById(R.id.tv_author);
        if (tvAuthor != null) {
            tvAuthor.setOnClickListener(v -> {
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.coolapk.com/u/3336736"));
                    activity.startActivity(intent);
                } catch (Exception ignored) {
                }
            });
        }
    }

    private static String readAppVersionName(AppCompatActivity activity) {
        try {
            return activity.getPackageManager()
                    .getPackageInfo(activity.getPackageName(), 0)
                    .versionName;
        } catch (Exception e) {
            Log.e("MainActivity", "get version failed", e);
            return "\u672a\u77e5\u7248\u672c";
        }
    }
}
