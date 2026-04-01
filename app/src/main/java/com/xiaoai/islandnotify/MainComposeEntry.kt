package com.xiaoai.islandnotify

import android.app.DatePickerDialog
import android.widget.NumberPicker
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

object MainComposeEntry {

    @JvmStatic
    fun install(activity: MainActivity) {
        activity.setContent {
            MainComposeApp(activity)
        }
    }
}

@Composable
private fun <T> kotlinx.coroutines.flow.StateFlow<T>.collectAsStateCompat(): State<T> = collectAsState()

private data class StageCustomState(
    var tplA: String = "",
    var tplB: String = "",
    var tplTicker: String = "",
    var baseTitle: String = "",
    var hintTitle: String = "",
    var hintSubtitle: String = "",
    var hintContent: String = "",
    var hintSubcontent: String = "",
    var baseContent: String = "",
    var baseSubcontent: String = "",
)

private data class TimeoutUiState(
    val islandVals: MutableList<Int> = mutableListOf(-1, -1, -1),
    val islandUnits: MutableList<String> = mutableListOf("m", "m", "m"),
    val notifVals: MutableList<Int> = mutableListOf(-1, -1, -1),
    val notifUnits: MutableList<String> = mutableListOf("m", "m", "m"),
    var notifTriggerStage: Int = 0,
    var notifGlobalDefault: Boolean = true,
)

private data class WakeRule(
    var sec: String,
    var hour: String,
    var minute: String,
)

private data class HolidayDraft(
    var date: String,
    var endDate: String = "",
    var name: String = "",
)

private data class WorkSwapDraft(
    var date: String,
    var name: String = "",
    var followWeek: Int = 1,
    var followWeekday: Int = 1,
)

@Composable
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
private fun MainComposeApp(activity: MainActivity) {
    val refreshTick by ComposeRefreshBus.tick.collectAsStateCompat()
    var tabIndex by rememberSaveable { mutableIntStateOf(0) }
    var showResetDialog by remember { mutableStateOf(false) }

    val settingsState = remember { SettingsComposeState() }
    val holidayState = remember { HolidayComposeState() }
    val aboutState = remember { AboutComposeState() }

    LaunchedEffect(refreshTick) {
        settingsState.loadFrom(activity)
        holidayState.loadFrom(activity)
        aboutState.loadFrom(activity)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("课程表超级岛") },
                modifier = Modifier.padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()),
            )
        },
        bottomBar = {
            val tabs = listOf("设置", "假期/调休", "关于")
            TabRow(selectedTabIndex = tabIndex) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = tabIndex == index,
                        onClick = { tabIndex = index },
                        text = { Text(title) },
                    )
                }
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()),
        ) {
            when (tabIndex) {
                0 -> SettingsTab(activity = activity, state = settingsState, onReset = { showResetDialog = true })
                1 -> HolidayTab(activity = activity, state = holidayState)
                else -> AboutTab(activity = activity, state = aboutState)
            }
        }
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("恢复默认") },
            text = { Text("将清空所有配置（本地 + LSPosed RemotePrefs）并恢复默认值，是否继续？") },
            confirmButton = {
                TextButton(onClick = {
                    showResetDialog = false
                    val count = activity.uiResetAllConfigToDefaults()
                    Toast.makeText(activity, "已恢复默认配置：$count 项", Toast.LENGTH_SHORT).show()
                    activity.requestComposeRefresh()
                }) { Text("恢复") }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) { Text("取消") }
            },
        )
    }
}

private class SettingsComposeState {
    var frameworkActive by mutableStateOf(false)
    var frameworkDesc by mutableStateOf("")
    var courseName by mutableStateOf("高等数学")
    var classroom by mutableStateOf("教科A-101")
    var testHint by mutableStateOf("")
    val stageStates = mutableStateListOf(StageCustomState(), StageCustomState(), StageCustomState())
    var iconAEnabled by mutableStateOf(true)
    var statusSaveHint by mutableStateOf("")
    var expandedSaveHint by mutableStateOf("")
    var timeoutState by mutableStateOf(TimeoutUiState())
    var timeoutHint by mutableStateOf("")
    var reminderMinutes by mutableStateOf("15")
    var reminderHint by mutableStateOf("")
    var repostEnabled by mutableStateOf(true)
    var muteEnabled by mutableStateOf(false)
    var muteMinsBefore by mutableStateOf("0")
    var unmuteEnabled by mutableStateOf(false)
    var unmuteMinsAfter by mutableStateOf("0")
    var dndEnabled by mutableStateOf(false)
    var dndMinsBefore by mutableStateOf("0")
    var undndEnabled by mutableStateOf(false)
    var undndMinsAfter by mutableStateOf("0")
    var islandButtonMode by mutableIntStateOf(0)
    var muteHint by mutableStateOf("")
    var wakeupMorningEnabled by mutableStateOf(false)
    var wakeupMorningLastSec by mutableStateOf("4")
    val wakeupMorningRules = mutableStateListOf<WakeRule>()
    var wakeupAfternoonEnabled by mutableStateOf(false)
    var wakeupAfternoonFirstSec by mutableStateOf("5")
    val wakeupAfternoonRules = mutableStateListOf<WakeRule>()
    var wakeupHint by mutableStateOf("")

    fun loadFrom(activity: MainActivity) {
        frameworkActive = activity.uiFrameworkActive()
        frameworkDesc = activity.uiFrameworkDesc()
        val prefs = activity.uiConfigPrefs()
        val suffixes = ConfigDefaults.STAGE_SUFFIXES
        for (i in suffixes.indices) {
            val suffix = suffixes[i]
            val prev = stageStates[i]
            stageStates[i] = prev.copy(
                tplA = PrefsAccess.readStagedTemplate(prefs, "tpl_a", suffix, ""),
                tplB = PrefsAccess.readStagedTemplate(prefs, "tpl_b", suffix, ""),
                tplTicker = PrefsAccess.readStagedTemplate(prefs, "tpl_ticker", suffix, ""),
                baseTitle = PrefsAccess.readStagedString(
                    prefs,
                    ConfigDefaults.EXPANDED_TPL_KEYS[0],
                    suffix,
                    ConfigDefaults.expandedTemplateDefault(i, 0, ""),
                ),
                hintTitle = PrefsAccess.readStagedString(
                    prefs,
                    ConfigDefaults.EXPANDED_TPL_KEYS[1],
                    suffix,
                    ConfigDefaults.expandedTemplateDefault(i, 1, ""),
                ),
                hintSubtitle = PrefsAccess.readStagedString(
                    prefs,
                    ConfigDefaults.EXPANDED_TPL_KEYS[2],
                    suffix,
                    ConfigDefaults.expandedTemplateDefault(i, 2, ""),
                ),
                hintContent = PrefsAccess.readStagedString(
                    prefs,
                    ConfigDefaults.EXPANDED_TPL_KEYS[3],
                    suffix,
                    ConfigDefaults.expandedTemplateDefault(i, 3, ""),
                ),
                hintSubcontent = PrefsAccess.readStagedString(
                    prefs,
                    ConfigDefaults.EXPANDED_TPL_KEYS[4],
                    suffix,
                    ConfigDefaults.expandedTemplateDefault(i, 4, ""),
                ),
                baseContent = PrefsAccess.readStagedString(
                    prefs,
                    ConfigDefaults.EXPANDED_TPL_KEYS[5],
                    suffix,
                    ConfigDefaults.expandedTemplateDefault(i, 5, ""),
                ),
                baseSubcontent = PrefsAccess.readStagedString(
                    prefs,
                    ConfigDefaults.EXPANDED_TPL_KEYS[6],
                    suffix,
                    ConfigDefaults.expandedTemplateDefault(i, 6, ""),
                ),
            )
        }
        iconAEnabled = PrefsAccess.readConfigBool(prefs, "icon_a", true)
        timeoutState = readTimeoutState(prefs)
        reminderMinutes = PrefsAccess.readConfigInt(prefs, "reminder_minutes_before", 15).toString()
        repostEnabled = PrefsAccess.readConfigBool(prefs, "repost_enabled", true)
        muteEnabled = PrefsAccess.readConfigBool(prefs, "mute_enabled", false)
        muteMinsBefore = PrefsAccess.readConfigInt(prefs, "mute_mins_before", 0).toString()
        unmuteEnabled = PrefsAccess.readConfigBool(prefs, "unmute_enabled", false)
        unmuteMinsAfter = PrefsAccess.readConfigInt(prefs, "unmute_mins_after", 0).toString()
        dndEnabled = PrefsAccess.readConfigBool(prefs, "dnd_enabled", false)
        dndMinsBefore = PrefsAccess.readConfigInt(prefs, "dnd_mins_before", 0).toString()
        undndEnabled = PrefsAccess.readConfigBool(prefs, "undnd_enabled", false)
        undndMinsAfter = PrefsAccess.readConfigInt(prefs, "undnd_mins_after", 0).toString()
        islandButtonMode = PrefsAccess.readConfigInt(prefs, "island_button_mode", 0)
        wakeupMorningEnabled = PrefsAccess.readConfigBool(prefs, "wakeup_morning_enabled", false)
        wakeupMorningLastSec =
            PrefsAccess.readConfigInt(prefs, "wakeup_morning_last_sec", 4).toString()
        wakeupAfternoonEnabled = PrefsAccess.readConfigBool(prefs, "wakeup_afternoon_enabled", false)
        wakeupAfternoonFirstSec =
            PrefsAccess.readConfigInt(prefs, "wakeup_afternoon_first_sec", 5).toString()
        wakeupMorningRules.clear()
        wakeupMorningRules.addAll(parseWakeRules(PrefsAccess.readConfigString(
            prefs,
            "wakeup_morning_rules_json",
            ConfigDefaults.WAKEUP_MORNING_RULES_JSON,
        )))
        wakeupAfternoonRules.clear()
        wakeupAfternoonRules.addAll(parseWakeRules(PrefsAccess.readConfigString(
            prefs,
            "wakeup_afternoon_rules_json",
            ConfigDefaults.WAKEUP_AFTERNOON_RULES_JSON,
        )))
    }
}

private class HolidayComposeState {
    var year by mutableIntStateOf(Calendar.getInstance().get(Calendar.YEAR))
    var fetchHint by mutableStateOf("")
    val holidayEntries = mutableStateListOf<HolidayManager.HolidayEntry>()
    val workswapEntries = mutableStateListOf<HolidayManager.HolidayEntry>()

    fun loadFrom(activity: MainActivity) {
        val all = HolidayManager.loadEntries(activity, year)
        holidayEntries.clear()
        workswapEntries.clear()
        all.forEach {
            if (it.type == HolidayManager.TYPE_HOLIDAY) holidayEntries += it else workswapEntries += it
        }
    }
}

private class AboutComposeState {
    var version by mutableStateOf("未知版本")
    var hideIcon by mutableStateOf(false)

    fun loadFrom(activity: MainActivity) {
        version = activity.uiReadAppVersionName()
        hideIcon = activity.uiIsHideIconEnabled()
    }
}

@Composable
private fun SettingsTab(
    activity: MainActivity,
    state: SettingsComposeState,
    onReset: () -> Unit,
) {
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StatusCardView(
            active = state.frameworkActive,
            frameworkDesc = state.frameworkDesc,
        )
        TestNotifyCard(activity = activity, state = state)
        StatusCustomCard(activity = activity, state = state)
        ExpandedCustomCard(activity = activity, state = state)
        TimeoutCard(activity = activity, state = state)
        ReminderCard(activity = activity, state = state)
        MuteCard(activity = activity, state = state)
        WakeupCard(activity = activity, state = state)
        ResetCard(onReset = onReset)
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun StatusCardView(active: Boolean, frameworkDesc: String) {
    val bg = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
    val onColor = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
    Card(
        colors = CardDefaults.cardColors(containerColor = bg),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.compose.foundation.Image(
                painter = painterResource(if (active) R.drawable.ic_module_active else R.drawable.ic_module_inactive),
                contentDescription = null,
                modifier = Modifier.size(44.dp),
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (active) "模块已激活" else "模块未激活",
                    color = onColor,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = if (active) {
                        if (frameworkDesc.isBlank()) "LSPosed Service 已连接" else frameworkDesc
                    } else {
                        "LSPosed Service 未连接，请检查模块启用与框架状态"
                    },
                    color = onColor.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun TestNotifyCard(activity: MainActivity, state: SettingsComposeState) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
        ) {
            Text(
                text = "测试通知",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "发送一条模拟课程提醒，验证超级岛效果是否正常。如果未发送，请强制停止作用域和模块重试。存在测试通知不发出的情况，但不影响实际通知。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f),
            )
            Spacer(modifier = Modifier.height(14.dp))
            OutlinedTextField(
                value = state.courseName,
                onValueChange = { state.courseName = it },
                label = { Text("课程名称") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(
                value = state.classroom,
                onValueChange = { state.classroom = it },
                label = { Text("教室") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(modifier = Modifier.height(10.dp))
            Button(
                onClick = {
                    activity.uiSendTestBroadcastToTarget(60_000L, state.courseName, state.classroom)
                    state.testHint = "已发送测试通知（倒计时），请下拉通知栏查看超级岛效果"
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("发送测试通知")
            }
            if (state.testHint.isNotBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = state.testHint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }
}

@Composable
private fun StatusCustomCard(activity: MainActivity, state: SettingsComposeState) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
        ) {
            Text("状态栏岛自定义", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(4.dp))
            MutedText("可用变量：{课名} {开始} {结束} {教室} {节次} {教师}")
            Spacer(modifier = Modifier.height(8.dp))
            MutedText("可用变量补充：{倒计时} {正计时}。状态栏岛仅岛B支持计时变量，计时变量需放在开头，可在后面拼接文本；上课前不支持{正计时}，下课后不支持{倒计时}。")
            Spacer(modifier = Modifier.height(8.dp))
            MutedText("保存时会做同阶段计时冲突校验：保存状态栏岛时会将展开态主要小文本1/2对齐到状态栏岛B；保存展开态时会将状态栏岛B对齐到展开态。同阶段展开态与状态栏岛B只能保留一种计时类型（正计时或倒计时）。")
            Spacer(modifier = Modifier.height(12.dp))

            val stageLabels = listOf("上课前", "上课中", "下课后")
            stageLabels.forEachIndexed { i, label ->
                val stage = state.stageStates[i]
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = stage.tplA,
                    onValueChange = { stage.tplA = it },
                    label = { Text("岛A（左侧文字）") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = stage.tplB,
                    onValueChange = { stage.tplB = it },
                    label = { Text("岛B（右侧文字）") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = stage.tplTicker,
                    onValueChange = { stage.tplTicker = it },
                    label = { Text("息屏显示") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Spacer(modifier = Modifier.height(if (i == 2) 14.dp else 10.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "岛A显示图标",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Switch(
                    checked = state.iconAEnabled,
                    onCheckedChange = { state.iconAEnabled = it },
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
            Button(
                onClick = {
                    val alignedCount = alignExpandedTimerWithStatus(state.stageStates)
                    val editor = activity.uiEditConfigPrefs()
                    ConfigDefaults.STAGE_SUFFIXES.forEachIndexed { i, suffix ->
                        val stage = state.stageStates[i]
                        editor.putString("tpl_a$suffix", stage.tplA.trim())
                        editor.putString("tpl_b$suffix", stage.tplB.trim())
                        editor.putString("tpl_ticker$suffix", stage.tplTicker.trim())
                        editor.putString("tpl_hint_title$suffix", stage.hintTitle.trim())
                        editor.putString("tpl_hint_subtitle$suffix", stage.hintSubtitle.trim())
                    }
                    editor.putBoolean("icon_a", state.iconAEnabled)
                    editor.apply()
                    state.statusSaveHint = if (alignedCount > 0) {
                        "已保存，下次通知生效（已自动对齐 $alignedCount 处计时方向）"
                    } else {
                        "已保存，下次通知生效"
                    }
                    activity.requestComposeRefresh()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("保存")
            }
            if (state.statusSaveHint.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = state.statusSaveHint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun ExpandedCustomCard(activity: MainActivity, state: SettingsComposeState) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
        ) {
            Text("岛展开态自定义", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(4.dp))
            MutedText("可用变量：{课名} {开始} {结束} {教室} {节次} {教师} {倒计时} {正计时}；上课前不支持{正计时}，下课后不支持{倒计时}。计时变量仅主要小文本1/2支持，且不可与其他字符串拼接。")
            Spacer(modifier = Modifier.height(8.dp))
            MutedText("保存时会做同阶段计时冲突校验：保存状态栏岛时会将展开态主要小文本1/2对齐到状态栏岛B；保存展开态时会将状态栏岛B对齐到展开态。同阶段展开态与状态栏岛B只能保留一种计时类型（正计时或倒计时）。")
            Spacer(modifier = Modifier.height(12.dp))

            val sectionTitles = listOf("展开态-课前", "展开态-上课中", "展开态-下课后")
            sectionTitles.forEachIndexed { i, title ->
                val stage = state.stageStates[i]
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(stage.baseTitle, { stage.baseTitle = it }, label = { Text("主要标题") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(stage.baseContent, { stage.baseContent = it }, label = { Text("次要文本1") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(stage.baseSubcontent, { stage.baseSubcontent = it }, label = { Text("次要文本2") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(stage.hintContent, { stage.hintContent = it }, label = { Text("前置文本1") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(stage.hintSubcontent, { stage.hintSubcontent = it }, label = { Text("前置文本2") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(stage.hintTitle, { stage.hintTitle = it }, label = { Text("主要小文本1") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(stage.hintSubtitle, { stage.hintSubtitle = it }, label = { Text("主要小文本2") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Spacer(modifier = Modifier.height(if (i == 2) 0.dp else 14.dp))
            }

            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = {
                    val alignedCount = alignStatusTimerWithExpanded(state.stageStates)
                    val editor = activity.uiEditConfigPrefs()
                    ConfigDefaults.STAGE_SUFFIXES.forEachIndexed { i, suffix ->
                        val stage = state.stageStates[i]
                        editor.putString("tpl_b$suffix", stage.tplB.trim())
                        editor.putString("${ConfigDefaults.EXPANDED_TPL_KEYS[0]}$suffix", stage.baseTitle.trim())
                        editor.putString("${ConfigDefaults.EXPANDED_TPL_KEYS[1]}$suffix", stage.hintTitle.trim())
                        editor.putString("${ConfigDefaults.EXPANDED_TPL_KEYS[2]}$suffix", stage.hintSubtitle.trim())
                        editor.putString("${ConfigDefaults.EXPANDED_TPL_KEYS[3]}$suffix", stage.hintContent.trim())
                        editor.putString("${ConfigDefaults.EXPANDED_TPL_KEYS[4]}$suffix", stage.hintSubcontent.trim())
                        editor.putString("${ConfigDefaults.EXPANDED_TPL_KEYS[5]}$suffix", stage.baseContent.trim())
                        editor.putString("${ConfigDefaults.EXPANDED_TPL_KEYS[6]}$suffix", stage.baseSubcontent.trim())
                    }
                    editor.apply()
                    state.expandedSaveHint = if (alignedCount > 0) {
                        "已保存，下次通知生效（已自动对齐 $alignedCount 处计时方向）"
                    } else {
                        "已保存，下次通知生效"
                    }
                    activity.requestComposeRefresh()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("保存展开态自定义")
            }
            if (state.expandedSaveHint.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = state.expandedSaveHint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun MutedText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
    )
}

@Composable
private fun TimeoutCard(activity: MainActivity, state: SettingsComposeState) {
    var islandStage by remember(state.timeoutState) { mutableIntStateOf(0) }
    var notifStage by remember(state.timeoutState) {
        mutableIntStateOf(state.timeoutState.notifTriggerStage.coerceIn(0, 2))
    }

    val islandDefault = state.timeoutState.islandVals[islandStage] < 0
    var islandInput by remember(state.timeoutState, islandStage) {
        mutableStateOf(if (islandDefault) "" else state.timeoutState.islandVals[islandStage].toString())
    }
    LaunchedEffect(state.timeoutState, islandStage, islandDefault, state.timeoutState.islandVals[islandStage]) {
        islandInput = if (islandDefault) "" else state.timeoutState.islandVals[islandStage].toString()
    }

    val notifDefault = state.timeoutState.notifGlobalDefault
    var notifInput by remember(state.timeoutState, notifStage) {
        mutableStateOf(
            if (notifDefault || state.timeoutState.notifVals[notifStage] < 0) {
                ""
            } else {
                state.timeoutState.notifVals[notifStage].toString()
            },
        )
    }
    LaunchedEffect(state.timeoutState, notifStage, notifDefault, state.timeoutState.notifVals[notifStage]) {
        notifInput = if (notifDefault || state.timeoutState.notifVals[notifStage] < 0) {
            ""
        } else {
            state.timeoutState.notifVals[notifStage].toString()
        }
    }

    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
            Text("消失时间", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(4.dp))
            MutedText("通知消失时岛随之消失；岛消失不影响通知。默认 = 使用系统值（岛 3600 秒，通知 720 分钟）")

            Spacer(modifier = Modifier.height(16.dp))
            Text("岛消失", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "可为三个阶段分别设置岛消失时间（通知后 / 上课后 / 下课后）。注意：每次状态更新会重新 notify，islandTimeout 将在该状态生效并按该阶段值计时。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            )
            Spacer(modifier = Modifier.height(10.dp))
            StageToggle(
                labels = listOf("通知后", "上课后", "下课后"),
                selected = islandStage,
                onSelect = { islandStage = it },
            )
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(
                value = islandInput,
                onValueChange = {
                    val digits = it.filter(Char::isDigit)
                    islandInput = digits
                    if (digits.isNotEmpty()) {
                        state.timeoutState.islandVals[islandStage] = parseIntOrDefault(digits, 1).coerceAtLeast(1)
                    }
                },
                enabled = !islandDefault,
                label = { Text("时长") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                UnitToggle(
                    selected = state.timeoutState.islandUnits[islandStage],
                    enabled = !islandDefault,
                    onSelect = { state.timeoutState.islandUnits[islandStage] = it },
                )
                Spacer(modifier = Modifier.weight(1f))
                Text("默认", style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.width(8.dp))
                Switch(
                    checked = islandDefault,
                    onCheckedChange = {
                        state.timeoutState.islandVals[islandStage] = if (it) -1 else 1
                        islandInput = if (it) "" else "1"
                    },
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(14.dp))

            Text("通知消失", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "设置时间到达后，将取消通知，后续将不再更新状态（上课/下课）。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
            Spacer(modifier = Modifier.height(10.dp))
            StageToggle(
                labels = listOf("通知后", "上课后", "下课后"),
                selected = notifStage,
                enabled = !state.timeoutState.notifGlobalDefault,
                onSelect = {
                    notifStage = it
                    state.timeoutState.notifTriggerStage = it
                },
            )
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(
                value = notifInput,
                onValueChange = {
                    val digits = it.filter(Char::isDigit)
                    notifInput = digits
                    if (digits.isNotEmpty()) {
                        state.timeoutState.notifVals[notifStage] = parseIntOrDefault(digits, 1).coerceAtLeast(1)
                    }
                },
                enabled = !notifDefault,
                label = { Text("时长") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                UnitToggle(
                    selected = state.timeoutState.notifUnits[notifStage],
                    enabled = !notifDefault,
                    onSelect = { state.timeoutState.notifUnits[notifStage] = it },
                )
                Spacer(modifier = Modifier.weight(1f))
                Text("默认", style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.width(8.dp))
                Switch(
                    checked = notifDefault,
                    onCheckedChange = {
                        state.timeoutState.notifGlobalDefault = it
                        if (it) {
                            for (i in state.timeoutState.notifVals.indices) {
                                state.timeoutState.notifVals[i] = -1
                            }
                            notifInput = ""
                        } else if (state.timeoutState.notifVals[notifStage] < 0) {
                            state.timeoutState.notifVals[notifStage] = 1
                            notifInput = "1"
                        }
                    },
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = {
                    val editor = activity.uiEditConfigPrefs()
                    writeTimeoutState(editor, state.timeoutState)
                    editor.apply()
                    state.timeoutHint = "已保存，下次通知生效"
                    activity.requestComposeRefresh()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("保存超时设置")
            }
            if (state.timeoutHint.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(state.timeoutHint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun StageToggle(
    labels: List<String>,
    selected: Int,
    enabled: Boolean = true,
    onSelect: (Int) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        labels.forEachIndexed { idx, label ->
            if (selected == idx) {
                Button(
                    onClick = { onSelect(idx) },
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                ) { Text(label) }
            } else {
                OutlinedButton(
                    onClick = { onSelect(idx) },
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                ) { Text(label) }
            }
        }
    }
}

@Composable
private fun UnitToggle(selected: String, enabled: Boolean, onSelect: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (selected == "s") {
            Button(onClick = { onSelect("s") }, enabled = enabled) { Text("秒") }
        } else {
            OutlinedButton(onClick = { onSelect("s") }, enabled = enabled) { Text("秒") }
        }
        if (selected != "s") {
            Button(onClick = { onSelect("m") }, enabled = enabled) { Text("分") }
        } else {
            OutlinedButton(onClick = { onSelect("m") }, enabled = enabled) { Text("分") }
        }
    }
}

@Composable
private fun ReminderCard(activity: MainActivity, state: SettingsComposeState) {
    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 20.dp)) {
            Text("课前提醒", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(10.dp))
            Text("自定义设置通知发送时机", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "提前提醒（分钟）",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = state.reminderMinutes,
                    onValueChange = { state.reminderMinutes = it.filter(Char::isDigit) },
                    modifier = Modifier.width(96.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            Button(
                onClick = {
                    val minutes = state.reminderMinutes.toIntOrNull()?.coerceIn(1, 120) ?: 15
                    state.reminderMinutes = minutes.toString()
                    activity.uiEditConfigPrefs().putInt("reminder_minutes_before", minutes).apply()
                    state.reminderHint = "已保存，重新调度今日提醒（提前 $minutes 分钟）"
                    activity.requestComposeRefresh()
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("保存并重新调度") }
            if (state.reminderHint.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(state.reminderHint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun MuteCard(activity: MainActivity, state: SettingsComposeState) {
    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 20.dp)) {
            Text("上课免打扰", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(10.dp))
            SwitchRow(
                title = "启用补发机制（全局）",
                subtitle = "关闭后：通知补发、课中即时静音/勿扰均停用，仅保留未来闹钟调度。如果出现手动关闭静音勿扰后仍被开启，请停用此功能。",
                checked = state.repostEnabled,
                onChecked = {
                    state.repostEnabled = it
                    activity.uiEditConfigPrefs().putBoolean("repost_enabled", it).apply()
                },
            )

            Spacer(modifier = Modifier.height(12.dp))
            SwitchRow(
                title = "上课自动静音",
                subtitle = "课程开始前指定时间将手机调为静音",
                checked = state.muteEnabled,
                onChecked = {
                    state.muteEnabled = it
                    activity.uiEditConfigPrefs().putBoolean("mute_enabled", it).apply()
                },
            )
            if (state.muteEnabled) {
                MinuteEditor("上课前多少分钟静音", state.muteMinsBefore) { state.muteMinsBefore = it }
            }

            Spacer(modifier = Modifier.height(8.dp))
            SwitchRow(
                title = "下课自动恢复铃声",
                subtitle = "课程结束后指定时间恢复正常响铃",
                checked = state.unmuteEnabled,
                onChecked = {
                    state.unmuteEnabled = it
                    activity.uiEditConfigPrefs().putBoolean("unmute_enabled", it).apply()
                },
            )
            if (state.unmuteEnabled) {
                MinuteEditor("下课后多少分钟恢复铃声", state.unmuteMinsAfter) { state.unmuteMinsAfter = it }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))

            SwitchRow(
                title = "上课自动开启勿扰",
                subtitle = "课程开始前指定时间开启勿扰（DND）模式",
                checked = state.dndEnabled,
                onChecked = {
                    state.dndEnabled = it
                    activity.uiEditConfigPrefs().putBoolean("dnd_enabled", it).apply()
                },
            )
            if (state.dndEnabled) {
                MinuteEditor("上课前多少分钟开启勿扰", state.dndMinsBefore) { state.dndMinsBefore = it }
            }

            Spacer(modifier = Modifier.height(8.dp))
            SwitchRow(
                title = "下课自动关闭勿扰",
                subtitle = "课程结束后指定时间关闭勿扰，恢复正常通知",
                checked = state.undndEnabled,
                onChecked = {
                    state.undndEnabled = it
                    activity.uiEditConfigPrefs().putBoolean("undnd_enabled", it).apply()
                },
            )
            if (state.undndEnabled) {
                MinuteEditor("下课后多少分钟关闭勿扰", state.undndMinsAfter) { state.undndMinsAfter = it }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(12.dp))
            Text("超级岛按钮功能", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            Text(
                "设置上课岛上显示的按钮执行的操作（不受自动静音开关限制）",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(12.dp))
            StageToggle(
                labels = listOf("仅静音", "仅勿扰", "两者"),
                selected = state.islandButtonMode.coerceIn(0, 2),
                onSelect = { state.islandButtonMode = it },
            )
            Spacer(modifier = Modifier.height(14.dp))
            Button(
                onClick = {
                    val muteBefore = clamp0to60(state.muteMinsBefore)
                    val unmuteAfter = clamp0to60(state.unmuteMinsAfter)
                    val dndBefore = clamp0to60(state.dndMinsBefore)
                    val undndAfter = clamp0to60(state.undndMinsAfter)
                    state.muteMinsBefore = muteBefore.toString()
                    state.unmuteMinsAfter = unmuteAfter.toString()
                    state.dndMinsBefore = dndBefore.toString()
                    state.undndMinsAfter = undndAfter.toString()
                    activity.uiEditConfigPrefs()
                        .putInt("mute_mins_before", muteBefore)
                        .putInt("unmute_mins_after", unmuteAfter)
                        .putInt("dnd_mins_before", dndBefore)
                        .putInt("undnd_mins_after", undndAfter)
                        .putInt("island_button_mode", state.islandButtonMode.coerceIn(0, 2))
                        .apply()
                    state.muteHint = "设置已保存并重新调度"
                    activity.requestComposeRefresh()
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("保存设置") }
            if (state.muteHint.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(state.muteHint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

@Composable
private fun MinuteEditor(label: String, value: String, onValue: (String) -> Unit) {
    Spacer(modifier = Modifier.height(8.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = value,
            onValueChange = { onValue(it.filter(Char::isDigit)) },
            modifier = Modifier.width(96.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
        )
    }
}

@Composable
private fun WakeupCard(activity: MainActivity, state: SettingsComposeState) {
    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 20.dp)) {
            Text("自动叫醒", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Text("根据课表在系统时钟创建叫醒闹钟", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(12.dp))

            SwitchRow(
                title = "上午自动叫醒",
                subtitle = "当今日有上午课程时创建叫醒闹钟",
                checked = state.wakeupMorningEnabled,
                onChecked = {
                    state.wakeupMorningEnabled = it
                    activity.uiEditConfigPrefs().putBoolean("wakeup_morning_enabled", it).apply()
                },
            )
            if (state.wakeupMorningEnabled) {
                MinuteEditor("上午最大节次（≤此节为上午）", state.wakeupMorningLastSec) { state.wakeupMorningLastSec = it }
                Spacer(modifier = Modifier.height(8.dp))
                Text("叫醒规则：上午第一节是第X节时，定对应时间的闹钟", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                WakeRuleList(state.wakeupMorningRules, onAdd = { state.wakeupMorningRules += WakeRule("1", "7", "00") })
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))

            SwitchRow(
                title = "下午自动叫醒",
                subtitle = "当今日有下午课程时创建叫醒闹钟",
                checked = state.wakeupAfternoonEnabled,
                onChecked = {
                    state.wakeupAfternoonEnabled = it
                    activity.uiEditConfigPrefs().putBoolean("wakeup_afternoon_enabled", it).apply()
                },
            )
            if (state.wakeupAfternoonEnabled) {
                MinuteEditor("下午起始节次（≥此节为下午）", state.wakeupAfternoonFirstSec) { state.wakeupAfternoonFirstSec = it }
                Spacer(modifier = Modifier.height(8.dp))
                Text("叫醒规则：下午第一节是第X节时，定对应时间的闹钟", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                WakeRuleList(state.wakeupAfternoonRules, onAdd = { state.wakeupAfternoonRules += WakeRule("5", "12", "00") })
            }

            Spacer(modifier = Modifier.height(14.dp))
            Button(
                onClick = {
                    val morningLast = (state.wakeupMorningLastSec.toIntOrNull() ?: 4).coerceAtLeast(1)
                    val afternoonFirst = (state.wakeupAfternoonFirstSec.toIntOrNull() ?: 5).coerceAtLeast(1)
                    state.wakeupMorningLastSec = morningLast.toString()
                    state.wakeupAfternoonFirstSec = afternoonFirst.toString()
                    activity.uiEditConfigPrefs()
                        .putInt("wakeup_morning_last_sec", morningLast)
                        .putInt("wakeup_afternoon_first_sec", afternoonFirst)
                        .putString("wakeup_morning_rules_json", toWakeRulesJson(state.wakeupMorningRules))
                        .putString("wakeup_afternoon_rules_json", toWakeRulesJson(state.wakeupAfternoonRules))
                        .apply()
                    state.wakeupHint = "设置已保存并重新调度叫醒闹钟"
                    activity.requestComposeRefresh()
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("保存叫醒设置") }
            if (state.wakeupHint.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(state.wakeupHint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun WakeRuleList(rules: MutableList<WakeRule>, onAdd: () -> Unit) {
    Column {
        rules.forEachIndexed { index, rule ->
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = rule.sec,
                    onValueChange = { rules[index] = rule.copy(sec = it.filter(Char::isDigit)) },
                    label = { Text("第X节") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedTextField(
                    value = rule.hour,
                    onValueChange = { rules[index] = rule.copy(hour = it.filter(Char::isDigit)) },
                    label = { Text("时") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedTextField(
                    value = rule.minute,
                    onValueChange = { rules[index] = rule.copy(minute = it.filter(Char::isDigit)) },
                    label = { Text("分") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedButton(onClick = { rules.removeAt(index) }) { Text("删除") }
            }
            Spacer(modifier = Modifier.height(6.dp))
        }
        OutlinedButton(onClick = onAdd) { Text("＋ 添加规则") }
    }
}

@Composable
private fun ResetCard(onReset: () -> Unit) {
    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
            Text("全局恢复默认", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(4.dp))
            MutedText("恢复默认会清空全部配置（状态栏岛、展开态、超时、提醒、静音、叫醒等）")
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(onClick = onReset, modifier = Modifier.fillMaxWidth()) { Text("恢复默认") }
        }
    }
}

private fun clamp0to60(value: String): Int = value.toIntOrNull()?.coerceIn(0, 60) ?: 0

private fun parseIntOrDefault(value: String, default: Int): Int {
    val text = value.trim()
    if (text.isEmpty()) return default
    return text.toIntOrNull() ?: default
}

private fun readTimeoutState(prefs: android.content.SharedPreferences): TimeoutUiState {
    val cfg = TimeoutConfig.read(PrefsAccess.resolve(prefs))
    return TimeoutUiState(
        islandVals = cfg.islandVals.toMutableList(),
        islandUnits = cfg.islandUnits.toMutableList(),
        notifVals = cfg.notifVals.toMutableList(),
        notifUnits = cfg.notifUnits.toMutableList(),
        notifTriggerStage = cfg.notifTriggerStage,
        notifGlobalDefault = cfg.notifGlobalDefault,
    )
}

private fun writeTimeoutState(
    editor: android.content.SharedPreferences.Editor,
    state: TimeoutUiState,
) {
    val save = TimeoutConfig.read(PrefsAccess.resolve(null))
    for (i in state.islandVals.indices) {
        save.islandVals[i] = state.islandVals[i]
        save.islandUnits[i] = if (state.islandUnits[i] == "s") "s" else "m"
        save.notifVals[i] = state.notifVals[i]
        save.notifUnits[i] = if (state.notifUnits[i] == "s") "s" else "m"
    }
    save.notifTriggerStage = state.notifTriggerStage
    save.notifGlobalDefault = state.notifGlobalDefault
    save.write(editor)
}

private fun parseWakeRules(json: String): List<WakeRule> {
    return try {
        val arr = JSONArray(json)
        buildList {
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                add(
                    WakeRule(
                        sec = obj.optInt("sec", 1).toString(),
                        hour = obj.optInt("hour", 7).toString(),
                        minute = String.format(Locale.getDefault(), "%02d", obj.optInt("minute", 0)),
                    ),
                )
            }
        }.ifEmpty { listOf(WakeRule("1", "7", "00")) }
    } catch (_: Exception) {
        listOf(WakeRule("1", "7", "00"))
    }
}

private fun toWakeRulesJson(rules: List<WakeRule>): String {
    val arr = JSONArray()
    rules.forEach { rule ->
        val sec = (rule.sec.toIntOrNull() ?: 1).coerceAtLeast(1)
        val hour = (rule.hour.toIntOrNull() ?: 0).coerceIn(0, 23)
        val minute = (rule.minute.toIntOrNull() ?: 0).coerceIn(0, 59)
        arr.put(JSONObject().apply {
            put("sec", sec)
            put("hour", hour)
            put("minute", minute)
        })
    }
    return arr.toString()
}

private const val TOKEN_COUNTDOWN = "{倒计时}"
private const val TOKEN_ELAPSED = "{正计时}"

private fun alignExpandedTimerWithStatus(stages: List<StageCustomState>): Int {
    var changed = 0
    stages.forEach { stage ->
        val statusKind = detectTimerKind(stage.tplB.trim())
        val title = stage.hintTitle.trim()
        val subtitle = stage.hintSubtitle.trim()
        val titleKind = detectTimerKind(title)
        val subtitleKind = detectTimerKind(subtitle)
        if ((statusKind == -1 || statusKind == 1) && (titleKind == -1 || titleKind == 1) && statusKind != titleKind) {
            stage.hintTitle = forceTimerKind(title, statusKind)
            changed++
        }
        if ((statusKind == -1 || statusKind == 1) && (subtitleKind == -1 || subtitleKind == 1) && statusKind != subtitleKind) {
            stage.hintSubtitle = forceTimerKind(subtitle, statusKind)
            changed++
        }
    }
    return changed
}

private fun alignStatusTimerWithExpanded(stages: List<StageCustomState>): Int {
    var changed = 0
    stages.forEach { stage ->
        val expandedKind = detectExpandedTimerKind(stage.hintTitle.trim(), stage.hintSubtitle.trim())
        val statusKind = detectTimerKind(stage.tplB.trim())
        if ((expandedKind == -1 || expandedKind == 1) && (statusKind == -1 || statusKind == 1) && expandedKind != statusKind) {
            stage.tplB = forceTimerKind(stage.tplB.trim(), expandedKind)
            changed++
        }
    }
    return changed
}

private fun detectExpandedTimerKind(hintTitle: String, hintSubtitle: String): Int {
    val titleKind = detectTimerKind(hintTitle)
    if (titleKind == -1 || titleKind == 1) return titleKind
    val subtitleKind = detectTimerKind(hintSubtitle)
    if (subtitleKind == -1 || subtitleKind == 1) return subtitleKind
    return 0
}

private fun detectTimerKind(text: String): Int {
    if (text.isBlank()) return 0
    val hasCountdown = text.contains(TOKEN_COUNTDOWN)
    val hasElapsed = text.contains(TOKEN_ELAPSED)
    if (hasCountdown && hasElapsed) return 2
    if (hasCountdown) return -1
    if (hasElapsed) return 1
    return 0
}

private fun forceTimerKind(text: String, targetKind: Int): String {
    if (targetKind >= 0) return text.replace(TOKEN_COUNTDOWN, TOKEN_ELAPSED)
    return text.replace(TOKEN_ELAPSED, TOKEN_COUNTDOWN)
}

@Composable
private fun HolidayTab(activity: MainActivity, state: HolidayComposeState) {
    val scope = rememberCoroutineScope()
    var showYearDialog by remember { mutableStateOf(false) }
    var showClearYearDialog by remember { mutableStateOf(false) }
    var holidayEditEntry by remember { mutableStateOf<HolidayManager.HolidayEntry?>(null) }
    var holidayDraft by remember { mutableStateOf<HolidayDraft?>(null) }
    var workswapEditEntry by remember { mutableStateOf<HolidayManager.HolidayEntry?>(null) }
    var workswapDraft by remember { mutableStateOf<WorkSwapDraft?>(null) }
    val maxWeek = remember(state.year) { activity.uiReadTotalWeekFromCourseData().coerceAtLeast(1) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                Text(
                    text = "假期 / 调休管理",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "节假日当天不发课前提醒；调休工作日按指定周次/星期发提醒。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f),
                )
                Spacer(modifier = Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("年份", color = MaterialTheme.colorScheme.onSecondaryContainer)
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedButton(onClick = { showYearDialog = true }) { Text(state.year.toString()) }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            scope.launch {
                                state.fetchHint = "正在获取…"
                                val result = withContext(Dispatchers.IO) { fetchHolidayEntries(state.year) }
                                result.error?.let {
                                    state.fetchHint = "获取失败：$it"
                                    return@launch
                                }
                                val entries = result.entries
                                if (entries.isEmpty()) {
                                    state.fetchHint = "${state.year}年暂无数据"
                                    return@launch
                                }
                                HolidayManager.mergeAndSave(activity, state.year, entries)
                                activity.uiSyncHolidayToHook(state.year)
                                entries.forEach { e ->
                                    val endDate = if (e.endDate.isNullOrEmpty()) e.date else e.endDate
                                    activity.uiRescheduleIfCoversToday(e.date, endDate)
                                }
                                state.loadFrom(activity)
                                state.fetchHint = "获取完成：节假日 ${result.holidayDays} 天，调休 ${result.workswapDays} 天"
                            }
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text("从网络获取") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = { showClearYearDialog = true }) { Text("清除本年") }
                }
                if (state.fetchHint.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = state.fetchHint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
        }

        Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
            Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("节假日", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    OutlinedButton(onClick = {
                        holidayEditEntry = null
                        holidayDraft = HolidayDraft(date = "${state.year}-01-01")
                    }) {
                        Text("＋ 新增")
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                MutedText("节假日当天不发送任何课前提醒")
                Spacer(modifier = Modifier.height(10.dp))

                if (state.holidayEntries.isEmpty()) {
                    Text(
                        text = "暂无节假日数据",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    state.holidayEntries.forEach { entry ->
                        HolidayRow(
                            entry = entry,
                            onEdit = {
                                holidayEditEntry = entry
                                holidayDraft = HolidayDraft(
                                    date = entry.date,
                                    endDate = entry.endDate ?: "",
                                    name = entry.name,
                                )
                            },
                            onDelete = {
                                val all = HolidayManager.loadEntries(activity, state.year).toMutableList()
                                all.removeIf { e ->
                                    e.date == entry.date &&
                                        (e.endDate ?: "") == (entry.endDate ?: "") &&
                                        e.name == entry.name &&
                                        e.type == entry.type
                                }
                                HolidayManager.saveEntries(activity, state.year, all)
                                activity.uiSyncHolidayToHook(state.year)
                                activity.uiRescheduleIfCoversToday(entry.date, entry.endDate)
                                state.loadFrom(activity)
                            },
                        )
                    }
                }
            }
        }

        Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
            Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("调休工作日", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    OutlinedButton(onClick = {
                        workswapEditEntry = null
                        workswapDraft = WorkSwapDraft(date = "${state.year}-01-01")
                    }) { Text("＋ 新增") }
                }
                Spacer(modifier = Modifier.height(6.dp))
                MutedText("调休上班日（原本应休，补课/补班），按指定周次的课程表发送提醒。点击编辑可配置按哪周哪天上课。")
                Spacer(modifier = Modifier.height(10.dp))

                if (state.workswapEntries.isEmpty()) {
                    Text(
                        text = "暂无调休工作日数据",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    )
                } else {
                    state.workswapEntries.forEach { entry ->
                        WorkswapRow(
                            entry = entry,
                            onEdit = {
                                workswapEditEntry = entry
                                workswapDraft = WorkSwapDraft(
                                    date = entry.date,
                                    name = entry.name,
                                    followWeek = if (entry.followWeek > 0) entry.followWeek else 1,
                                    followWeekday = if (entry.followWeekday > 0) entry.followWeekday else 1,
                                )
                            },
                            onDelete = {
                                val all = HolidayManager.loadEntries(activity, state.year).toMutableList()
                                all.removeIf { e ->
                                    e.date == entry.date && e.name == entry.name && e.type == entry.type
                                }
                                HolidayManager.saveEntries(activity, state.year, all)
                                activity.uiSyncHolidayToHook(state.year)
                                activity.uiRescheduleIfCoversToday(entry.date, null)
                                state.loadFrom(activity)
                            },
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }

    if (showYearDialog) {
        YearPickerDialog(
            currentYear = state.year,
            onDismiss = { showYearDialog = false },
            onConfirm = {
                state.year = it
                state.loadFrom(activity)
                showYearDialog = false
            },
        )
    }

    if (showClearYearDialog) {
        AlertDialog(
            onDismissRequest = { showClearYearDialog = false },
            title = { Text("清除本年") },
            text = { Text("将清除 ${state.year} 年已保存的全部假期和调休数据（包括自定义条目）。确定吗？") },
            confirmButton = {
                TextButton(onClick = {
                    showClearYearDialog = false
                    val old = HolidayManager.loadEntries(activity, state.year)
                    HolidayManager.saveEntries(activity, state.year, ArrayList())
                    activity.uiSyncHolidayToHook(state.year)
                    old.forEach { e ->
                        val end = if (e.endDate.isNullOrEmpty()) e.date else e.endDate
                        activity.uiRescheduleIfCoversToday(e.date, end)
                    }
                    state.loadFrom(activity)
                    Toast.makeText(activity, "已清除 ${state.year} 年假期数据", Toast.LENGTH_SHORT).show()
                }) { Text("清除") }
            },
            dismissButton = { TextButton(onClick = { showClearYearDialog = false }) { Text("取消") } },
        )
    }

    holidayDraft?.let { draft ->
        HolidayEditDialog(
            title = if (holidayEditEntry == null) "新增节假日" else "编辑节假日",
            draft = draft,
            onDismiss = { holidayDraft = null },
            onSave = { save ->
                val name = save.name.trim().ifBlank { "节假日" }
                val all = HolidayManager.loadEntries(activity, state.year).toMutableList()
                holidayEditEntry?.let { old ->
                    all.removeIf { e ->
                        e.date == old.date &&
                            (e.endDate ?: "") == (old.endDate ?: "") &&
                            e.name == old.name &&
                            e.type == old.type
                    }
                }
                all += HolidayManager.HolidayEntry(
                    save.date,
                    save.endDate,
                    name,
                    HolidayManager.TYPE_HOLIDAY,
                    true,
                )
                all.sortBy { it.date }
                HolidayManager.saveEntries(activity, state.year, all)
                activity.uiSyncHolidayToHook(state.year)
                val endDate = if (save.endDate.isBlank()) save.date else save.endDate
                activity.uiRescheduleIfCoversToday(save.date, endDate)
                holidayEditEntry?.let { old ->
                    activity.uiRescheduleIfCoversToday(old.date, if (old.endDate.isNullOrEmpty()) old.date else old.endDate)
                }
                state.loadFrom(activity)
                holidayDraft = null
            },
        )
    }

    workswapDraft?.let { draft ->
        WorkswapEditDialog(
            title = if (workswapEditEntry == null) "新增调休工作日" else "编辑调休工作日",
            draft = draft,
            maxWeek = maxWeek,
            onDismiss = { workswapDraft = null },
            onSave = { save ->
                val name = save.name.trim().ifBlank { "调休工作日" }
                val all = HolidayManager.loadEntries(activity, state.year).toMutableList()
                workswapEditEntry?.let { old ->
                    all.removeIf { e -> e.date == old.date && e.name == old.name && e.type == old.type }
                }
                val entry = HolidayManager.HolidayEntry(
                    save.date,
                    "",
                    name,
                    HolidayManager.TYPE_WORKSWAP,
                    true,
                )
                entry.followWeek = save.followWeek.coerceIn(1, maxWeek)
                entry.followWeekday = save.followWeekday.coerceIn(1, 7)
                all += entry
                all.sortBy { it.date }
                HolidayManager.saveEntries(activity, state.year, all)
                activity.uiSyncHolidayToHook(state.year)
                activity.uiRescheduleIfCoversToday(save.date, null)
                workswapEditEntry?.let { old -> activity.uiRescheduleIfCoversToday(old.date, null) }
                state.loadFrom(activity)
                workswapDraft = null
            },
        )
    }
}

private data class FetchHolidayResult(
    val entries: List<HolidayManager.HolidayEntry>,
    val holidayDays: Int,
    val workswapDays: Int,
    val error: String? = null,
)

private fun fetchHolidayEntries(year: Int): FetchHolidayResult {
    return try {
        val url = URL("https://unpkg.com/holiday-calendar@1.3.0/data/CN/$year.json")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("User-Agent", "XiaoaiIsland/1.0")
        }
        val text = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { it.readText() }
        conn.disconnect()
        val entries = HolidayManager.parseApiResponse(text)
        var holidayDays = 0
        var workswapDays = 0
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        entries.forEach { e ->
            val days = if (e.endDate.isNullOrBlank()) {
                1
            } else {
                try {
                    val d1 = sdf.parse(e.date) ?: return@forEach
                    val d2 = sdf.parse(e.endDate) ?: return@forEach
                    ((d2.time - d1.time) / 86_400_000L).toInt() + 1
                } catch (_: Exception) {
                    1
                }
            }
            if (e.type == HolidayManager.TYPE_HOLIDAY) holidayDays += days else workswapDays += days
        }
        FetchHolidayResult(entries = entries, holidayDays = holidayDays, workswapDays = workswapDays)
    } catch (e: Exception) {
        FetchHolidayResult(entries = emptyList(), holidayDays = 0, workswapDays = 0, error = e.message ?: "未知错误")
    }
}

@Composable
private fun HolidayRow(
    entry: HolidayManager.HolidayEntry,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            val dateLabel = if (!entry.endDate.isNullOrBlank() && entry.endDate != entry.date) {
                "${formatShortDate(entry.date)}–${formatShortDate(entry.endDate)}"
            } else {
                formatShortDate(entry.date)
            }
            Text("$dateLabel  ${entry.name}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            Text(
                if (entry.isCustom) "自定义节假日" else "API 节假日",
                style = MaterialTheme.typography.bodySmall,
                color = if (entry.isCustom) Color(0xFF7965AF) else Color(0xFF389E0D),
            )
        }
        OutlinedButton(onClick = onEdit) { Text("编辑") }
        Spacer(modifier = Modifier.width(4.dp))
        OutlinedButton(onClick = onDelete) { Text("删除", color = Color(0xFFBA1A1A)) }
    }
}

@Composable
private fun WorkswapRow(
    entry: HolidayManager.HolidayEntry,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            val dateLabel = if (!entry.endDate.isNullOrBlank()) {
                "${formatShortDate(entry.date)}–${formatShortDate(entry.endDate)}"
            } else {
                formatShortDate(entry.date)
            }
            Text("$dateLabel  ${entry.name}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            Text("替换为: ${entry.followDesc()}", style = MaterialTheme.typography.bodySmall, color = Color(0xFF6750A4))
            Text(
                if (entry.isCustom) "自定义调休" else "API 调休",
                style = MaterialTheme.typography.bodySmall,
                color = if (entry.isCustom) Color(0xFF7965AF) else Color(0xFF389E0D),
            )
        }
        OutlinedButton(onClick = onEdit) { Text("编辑") }
        Spacer(modifier = Modifier.width(4.dp))
        OutlinedButton(onClick = onDelete) { Text("删除", color = Color(0xFFBA1A1A)) }
    }
}

@Composable
private fun YearPickerDialog(
    currentYear: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    var year by remember { mutableIntStateOf(currentYear) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择年份") },
        text = {
            androidx.compose.ui.viewinterop.AndroidView(factory = { context ->
                NumberPicker(context).apply {
                    minValue = 2020
                    maxValue = 2099
                    value = currentYear
                    wrapSelectorWheel = false
                    setOnValueChangedListener { _, _, newVal -> year = newVal }
                }
            })
        },
        confirmButton = { TextButton(onClick = { onConfirm(year) }) { Text("确定") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun HolidayEditDialog(
    title: String,
    draft: HolidayDraft,
    onDismiss: () -> Unit,
    onSave: (HolidayDraft) -> Unit,
) {
    var form by remember(draft) { mutableStateOf(draft.copy()) }
    val context = androidx.compose.ui.platform.LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedButton(onClick = {
                    val parts = form.date.split("-")
                    DatePickerDialog(
                        context,
                        { _, y, m, d -> form = form.copy(date = "%04d-%02d-%02d".format(y, m + 1, d)) },
                        parts.getOrNull(0)?.toIntOrNull() ?: Calendar.getInstance().get(Calendar.YEAR),
                        (parts.getOrNull(1)?.toIntOrNull() ?: 1) - 1,
                        parts.getOrNull(2)?.toIntOrNull() ?: 1,
                    ).show()
                }, modifier = Modifier.fillMaxWidth()) {
                    Text("开始日期: ${form.date}")
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(onClick = {
                    val base = if (form.endDate.isBlank()) form.date else form.endDate
                    val parts = base.split("-")
                    DatePickerDialog(
                        context,
                        { _, y, m, d -> form = form.copy(endDate = "%04d-%02d-%02d".format(y, m + 1, d)) },
                        parts.getOrNull(0)?.toIntOrNull() ?: Calendar.getInstance().get(Calendar.YEAR),
                        (parts.getOrNull(1)?.toIntOrNull() ?: 1) - 1,
                        parts.getOrNull(2)?.toIntOrNull() ?: 1,
                    ).show()
                }, modifier = Modifier.fillMaxWidth()) {
                    Text("结束日期: ${if (form.endDate.isBlank()) "仅当天" else form.endDate}")
                }
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = form.name,
                    onValueChange = { form = form.copy(name = it) },
                    label = { Text("名称（如：春节、放假）") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(form) }) { Text("确定") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun WorkswapEditDialog(
    title: String,
    draft: WorkSwapDraft,
    maxWeek: Int,
    onDismiss: () -> Unit,
    onSave: (WorkSwapDraft) -> Unit,
) {
    var form by remember(draft) { mutableStateOf(draft.copy()) }
    val context = androidx.compose.ui.platform.LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedButton(onClick = {
                    val parts = form.date.split("-")
                    DatePickerDialog(
                        context,
                        { _, y, m, d -> form = form.copy(date = "%04d-%02d-%02d".format(y, m + 1, d)) },
                        parts.getOrNull(0)?.toIntOrNull() ?: Calendar.getInstance().get(Calendar.YEAR),
                        (parts.getOrNull(1)?.toIntOrNull() ?: 1) - 1,
                        parts.getOrNull(2)?.toIntOrNull() ?: 1,
                    ).show()
                }, modifier = Modifier.fillMaxWidth()) {
                    Text("选择日期: ${form.date}")
                }
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = form.name,
                    onValueChange = { form = form.copy(name = it) },
                    label = { Text("名称（如：补周一课）") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text("当天按以下周次/星期的课表上课：", style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("第 ")
                    OutlinedTextField(
                        value = form.followWeek.toString(),
                        onValueChange = {
                            form = form.copy(followWeek = (it.toIntOrNull() ?: 1).coerceIn(1, maxWeek))
                        },
                        modifier = Modifier.width(72.dp),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    Text(" 周")
                }
                Spacer(modifier = Modifier.height(8.dp))
                StageToggle(
                    labels = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日"),
                    selected = form.followWeekday.coerceIn(1, 7) - 1,
                    onSelect = { form = form.copy(followWeekday = it + 1) },
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(form) }) { Text("确定") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

private fun formatShortDate(isoDate: String?): String {
    if (isoDate.isNullOrBlank() || isoDate.length < 10) return isoDate ?: ""
    return try {
        val m = isoDate.substring(5, 7).toInt()
        val d = isoDate.substring(8, 10).toInt()
        "$m/$d"
    } catch (_: Exception) {
        isoDate
    }
}

@Composable
private fun AboutTab(activity: MainActivity, state: AboutComposeState) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 20.dp)) {
                Text("关于", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("版本", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(state.version, style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("作者", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = { activity.uiOpenAuthorPage() }) { Text("Mercury") }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("隐藏桌面图标", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Switch(
                        checked = state.hideIcon,
                        onCheckedChange = {
                            state.hideIcon = it
                            activity.uiSetHideIconEnabled(it)
                        },
                    )
                }
            }
        }
    }
}
