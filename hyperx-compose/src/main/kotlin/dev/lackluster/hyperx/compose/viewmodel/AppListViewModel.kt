package dev.lackluster.hyperx.compose.viewmodel

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.os.Parcelable
import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.github.promeg.pinyinhelper.Pinyin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.parcelize.Parcelize
import java.text.Collator
import java.util.Locale

class AppListViewModel(
    private val comparator: Comparator<AppInfo> = compareBy { 0 }
) : ViewModel() {
    companion object {
        private const val TAG = "AppListViewModel"
        private var apps by mutableStateOf<List<AppInfo>>(emptyList())
    }

    @Parcelize
    data class AppInfo(
        val label: String,
        val packageInfo: PackageInfo
    ) : Parcelable {
        val packageName: String
            get() = packageInfo.packageName
        val uid: Int
            get() = packageInfo.applicationInfo!!.uid
    }

    var search by mutableStateOf("")
    var showSystemApps by mutableStateOf(false)
    var isRefreshing by mutableStateOf(false)
        private set

    private val sortedList by derivedStateOf {
        val comparator = comparator.then(compareBy(Collator.getInstance(Locale.getDefault()), AppInfo::label))
        apps.sortedWith(comparator).also {
            isRefreshing = false
        }
    }

    val appList by derivedStateOf {
        sortedList.filter {
            it.label.contains(search, true) || it.packageName.contains(
                search,
                true
            ) || Pinyin.toPinyin(it.label, "").contains(search, true)
        }.filter {
            it.uid == 2000 // Always show shell
                    || showSystemApps || it.packageInfo.applicationInfo!!.flags.and(ApplicationInfo.FLAG_SYSTEM) == 0
        }
    }

    @SuppressLint("QueryPermissionsNeeded")
    suspend fun fetchAppList(context: Context) {
        isRefreshing = true

        withContext(Dispatchers.IO) {
            val pm = context.packageManager

            val start = SystemClock.elapsedRealtime()

            val packages = pm.getInstalledPackages(0)

            apps = packages.map {
                val appInfo = it.applicationInfo!!
                AppInfo(
                    label = appInfo.loadLabel(pm).toString(),
                    packageInfo = it,
                )
            }
            Log.i(TAG, "load cost: ${SystemClock.elapsedRealtime() - start}")
        }
    }
}