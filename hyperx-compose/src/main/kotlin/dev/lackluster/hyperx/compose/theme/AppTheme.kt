package dev.lackluster.hyperx.compose.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

@Composable
fun AppTheme(
    controller: ThemeController = remember { ThemeController(ColorSchemeMode.System) },
    smoothRounding: Boolean = true,
    content: @Composable () -> Unit
) {
    MiuixTheme(
        controller = controller,
        smoothRounding = smoothRounding,
        content = content
    )
}
