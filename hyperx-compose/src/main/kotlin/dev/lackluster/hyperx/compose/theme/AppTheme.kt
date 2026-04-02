package dev.lackluster.hyperx.compose.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

@Composable
fun AppTheme(
    content: @Composable () -> Unit
) {
    val controller = remember { ThemeController(ColorSchemeMode.System) }
//
//        remember(colorMode, keyColor, spec, style) {
//        when (colorMode) {
//            1 -> ThemeController(ColorSchemeMode.Light)
//            2 -> ThemeController(ColorSchemeMode.Dark)
//            else -> ThemeController(ColorSchemeMode.System)
//        }
//    }
    MiuixTheme(
        controller = controller,
        smoothRounding = true,
        content = content
    )
}