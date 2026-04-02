package dev.lackluster.hyperx.compose.activity

import android.annotation.SuppressLint
import android.app.Activity
import android.content.res.Configuration
import android.os.Bundle
import androidx.annotation.Keep
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.mutableIntStateOf

@Keep
abstract class HyperXActivity : ComponentActivity() {
    companion object {
        private var cornerRadius: Int = 0
        val screenCornerRadius: MutableIntState = mutableIntStateOf(cornerRadius)

        @SuppressLint("DiscouragedApi")
        fun getCornerRadiusTop(context: Activity): Int {
            val radius: Int
            val resourceId = context.resources.getIdentifier("rounded_corner_radius_top", "dimen", "android")
            radius = if (resourceId > 0) {
                (context.resources.getDimension(resourceId) / context.resources.displayMetrics.density).toInt()
            } else {
                0
            }
            cornerRadius = radius
            screenCornerRadius.intValue = cornerRadius
            return radius
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        getCornerRadiusTop(this)
        window.isNavigationBarContrastEnforced = false
//        context = this
        setContent {
            AppContent()
        }
    }

    override fun onMultiWindowModeChanged(isInMultiWindowMode: Boolean, newConfig: Configuration) {
        super.onMultiWindowModeChanged(isInMultiWindowMode, newConfig)
        if (isInMultiWindowMode) {
            screenCornerRadius.intValue = 0
        } else {
            screenCornerRadius.intValue = cornerRadius
        }
    }

    @Composable
    abstract fun AppContent()
}