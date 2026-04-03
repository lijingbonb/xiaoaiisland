package com.xiaoai.islandnotify

data class OpenSourceRef(
    val name: String,
    val license: String,
    val link: String,
)

object OpenSourceRefs {
    val list = listOf(
        OpenSourceRef("androidx.appcompat:appcompat", "Apache-2.0", "https://android.googlesource.com/platform/frameworks/support"),
        OpenSourceRef("androidx.activity:activity / activity-compose", "Apache-2.0", "https://android.googlesource.com/platform/frameworks/support"),
        OpenSourceRef("androidx.constraintlayout:constraintlayout", "Apache-2.0", "https://android.googlesource.com/platform/frameworks/support"),
        OpenSourceRef("androidx.compose:foundation / ui / ui-tooling-preview", "Apache-2.0", "https://android.googlesource.com/platform/frameworks/support"),
        OpenSourceRef("androidx.navigation3:navigation3-runtime", "Apache-2.0", "https://android.googlesource.com/platform/frameworks/support"),
        OpenSourceRef("androidx.lifecycle:lifecycle-viewmodel-compose", "Apache-2.0", "https://android.googlesource.com/platform/frameworks/support"),
        OpenSourceRef("HowieHChen/hyperx-compose", "Apache-2.0", "https://github.com/HowieHChen/hyperx-compose"),
        OpenSourceRef("compose-miuix-ui/miuix", "Apache-2.0", "https://github.com/compose-miuix-ui/miuix"),
        OpenSourceRef("org.jetbrains.androidx.navigationevent:navigationevent-compose", "Apache-2.0", "https://github.com/JetBrains/compose-multiplatform-core"),
        OpenSourceRef("xzakota/HyperNotification (focus-api)", "Apache-2.0", "https://github.com/xzakota/HyperNotification"),
        OpenSourceRef("chrisbanes/haze", "Apache-2.0", "https://github.com/chrisbanes/haze"),
        OpenSourceRef("coil-kt/coil", "Apache-2.0", "https://github.com/coil-kt/coil"),
        OpenSourceRef("promeg/TinyPinyin", "Apache-2.0", "https://github.com/promeG/TinyPinyin"),
        OpenSourceRef("libxposed/api", "Apache-2.0", "https://github.com/libxposed/api"),
        OpenSourceRef("libxposed/service", "Apache-2.0", "https://github.com/libxposed/service"),
        OpenSourceRef("KyuubiRan/EzXHelper", "MIT", "https://github.com/KyuubiRan/EzXHelper"),
    )
}
