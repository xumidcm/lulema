package com.example.myjizhang

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.myjizhang.ui.theme.MyJizhangTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyJizhangTheme {
                AppRoot()
            }
        }
    }
}

private enum class HomeTab {
    Home,
    Flights,
    Reflections,
    Stats
}

private sealed class Screen {
    data object Home : Screen()
    data object FlightList : Screen()
    data object ReflectionList : Screen()
    data object Stats : Screen()
    data class FlightEdit(val id: Long?) : Screen()
    data class ReflectionEdit(
        val id: Long?,
        val relatedFlightId: Long? = null,
        val requireCompletion: Boolean = false
    ) : Screen()
}

private data class FlightRecord(
    val id: Long,
    val durationMinutes: Int,
    val dateTimeMillis: Long,
    val attribute: String,
    val note: String
)

private data class ReflectionEntry(
    val id: Long,
    val content: String,
    val moodTags: List<String>,
    val relatedFlightId: Long?,
    val createdAtMillis: Long
)

@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun AppRoot() {
    val context = LocalContext.current
    val storage = remember { LocalStorage(context) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var flights by remember { mutableStateOf<List<FlightRecord>>(emptyList()) }
    var reflections by remember { mutableStateOf<List<ReflectionEntry>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isRefreshing by remember { mutableStateOf(false) }
    var tab by remember { mutableStateOf(HomeTab.Home) }
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }

    val refreshState = rememberPullRefreshState(
        refreshing = isRefreshing,
        onRefresh = {
            scope.launch {
                isRefreshing = true
                reloadData(storage, onData = { flights = it.first; reflections = it.second }, onLoading = { isLoading = it })
                delay(200)
                isRefreshing = false
            }
        }
    )

    LaunchedEffect(Unit) {
        reloadData(storage, onData = { flights = it.first; reflections = it.second }, onLoading = { isLoading = it })
    }

    val showBottomBar = screen is Screen.Home || screen is Screen.FlightList || screen is Screen.ReflectionList || screen is Screen.Stats

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    NavigationBarItem(
                        selected = tab == HomeTab.Home,
                        onClick = {
                            tab = HomeTab.Home
                            screen = Screen.Home
                        },
                        icon = { Text("🏠") },
                        label = { Text("首页") }
                    )
                    NavigationBarItem(
                        selected = tab == HomeTab.Flights,
                        onClick = {
                            tab = HomeTab.Flights
                            screen = Screen.FlightList
                        },
                        icon = { Text("📝") },
                        label = { Text("起飞") }
                    )
                    NavigationBarItem(
                        selected = tab == HomeTab.Reflections,
                        onClick = {
                            tab = HomeTab.Reflections
                            screen = Screen.ReflectionList
                        },
                        icon = { Text("💬") },
                        label = { Text("感悟") }
                    )
                    NavigationBarItem(
                        selected = tab == HomeTab.Stats,
                        onClick = {
                            tab = HomeTab.Stats
                            screen = Screen.Stats
                        },
                        icon = { Text("📊") },
                        label = { Text("统计") }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .pullRefresh(refreshState)
        ) {
            when (val current = screen) {
                is Screen.Home -> HomeScreen(
                    flights = flights,
                    reflections = reflections,
                    onAddFlight = { screen = Screen.FlightEdit(null) },
                    onOpenFlights = {
                        tab = HomeTab.Flights
                        screen = Screen.FlightList
                    },
                    onOpenReflections = {
                        tab = HomeTab.Reflections
                        screen = Screen.ReflectionList
                    },
                    onOpenStats = {
                        tab = HomeTab.Stats
                        screen = Screen.Stats
                    }
                )
                is Screen.FlightList -> FlightListScreen(
                    flights = flights,
                    isLoading = isLoading,
                    onAdd = { screen = Screen.FlightEdit(null) },
                    onEdit = { screen = Screen.FlightEdit(it) },
                    onDelete = { id ->
                        val updatedFlights = flights.filterNot { it.id == id }
                        val updatedReflections = reflections.map { entry ->
                            if (entry.relatedFlightId == id) entry.copy(relatedFlightId = null) else entry
                        }
                        storage.saveFlights(updatedFlights)
                        storage.saveReflections(updatedReflections)
                        flights = updatedFlights
                        reflections = updatedReflections
                        scope.launch { snackbarHostState.showSnackbar("已删除起飞记录") }
                    }
                )

                is Screen.ReflectionList -> ReflectionListScreen(
                    reflections = reflections,
                    flights = flights,
                    isLoading = isLoading,
                    onAdd = { screen = Screen.ReflectionEdit(null) },
                    onEdit = { screen = Screen.ReflectionEdit(it) },
                    onDelete = { id ->
                        val updatedReflections = reflections.filterNot { it.id == id }
                        storage.saveReflections(updatedReflections)
                        reflections = updatedReflections
                        scope.launch { snackbarHostState.showSnackbar("已删除感悟") }
                    }
                )

                is Screen.FlightEdit -> FlightEditScreen(
                    existing = flights.firstOrNull { it.id == current.id },
                    onSave = { record ->
                        val result = upsertFlight(storage, flights, record)
                        flights = result.first
                        if (current.id == null) {
                            screen = Screen.ReflectionEdit(
                                id = null,
                                relatedFlightId = result.second.id,
                                requireCompletion = true
                            )
                            scope.launch { snackbarHostState.showSnackbar("请填写本次起飞感受") }
                        } else {
                            tab = HomeTab.Flights
                            screen = Screen.FlightList
                            scope.launch { snackbarHostState.showSnackbar("起飞记录已保存") }
                        }
                    },
                    onCancel = {
                        tab = HomeTab.Flights
                        screen = Screen.FlightList
                    },
                    onMessage = { message -> scope.launch { snackbarHostState.showSnackbar(message) } }
                )

                is Screen.ReflectionEdit -> ReflectionEditScreen(
                    existing = reflections.firstOrNull { it.id == current.id },
                    flights = flights,
                    preselectedFlightId = current.relatedFlightId,
                    requireCompletion = current.requireCompletion,
                    onSave = { entry ->
                        val updated = upsertReflection(storage, reflections, entry)
                        reflections = updated
                        tab = HomeTab.Reflections
                        screen = Screen.ReflectionList
                        scope.launch { snackbarHostState.showSnackbar("感悟已保存") }
                    },
                    onDelete = { id ->
                        val updated = reflections.filterNot { it.id == id }
                        storage.saveReflections(updated)
                        reflections = updated
                        tab = HomeTab.Reflections
                        screen = Screen.ReflectionList
                        scope.launch { snackbarHostState.showSnackbar("已删除感悟") }
                    },
                    onCancel = {
                        tab = HomeTab.Reflections
                        screen = Screen.ReflectionList
                    },
                    onMessage = { message -> scope.launch { snackbarHostState.showSnackbar(message) } }
                )

                is Screen.Stats -> StatsScreen(
                    flights = flights,
                    reflections = reflections,
                    isLoading = isLoading
                )
            }

            PullRefreshIndicator(
                refreshing = isRefreshing,
                state = refreshState,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
    }
}

private fun reloadData(
    storage: LocalStorage,
    onData: (Pair<List<FlightRecord>, List<ReflectionEntry>>) -> Unit,
    onLoading: (Boolean) -> Unit
) {
    onLoading(true)
    val flights = storage.loadFlights().sortedByDescending { it.dateTimeMillis }
    val reflections = storage.loadReflections().sortedByDescending { it.createdAtMillis }
    onData(flights to reflections)
    onLoading(false)
}

@Composable
private fun HomeScreen(
    flights: List<FlightRecord>,
    reflections: List<ReflectionEntry>,
    onAddFlight: () -> Unit,
    onOpenFlights: () -> Unit,
    onOpenReflections: () -> Unit,
    onOpenStats: () -> Unit
) {
    val totalFlights = flights.size
    val totalMinutes = flights.sumOf { it.durationMinutes }
    val last7DaysMillis = System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000L
    val last7DaysCount = flights.count { it.dateTimeMillis >= last7DaysMillis }
    val latestFlight = flights.maxByOrNull { it.dateTimeMillis }
    val latestReflection = reflections.maxByOrNull { it.createdAtMillis }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "撸了吗", style = MaterialTheme.typography.displayLarge)
        Text(text = "轻量记录 · 本地保存")
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Button(
                onClick = onAddFlight,
                modifier = Modifier
                    .width(240.dp)
                    .height(72.dp)
            ) {
                Text(text = "＋ 添加起飞记录")
            }
        }
        Text(text = "完成起飞后需要填写感受")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(
                title = "累计起飞",
                value = "${totalFlights} 次",
                icon = "📌",
                modifier = Modifier.weight(1f)
            )
            StatCard(
                title = "本周起飞",
                value = "${last7DaysCount} 次",
                icon = "⚡",
                modifier = Modifier.weight(1f)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(
                title = "总时长",
                value = "${totalMinutes} 分钟",
                icon = "⏱️",
                modifier = Modifier.weight(1f)
            )
            StatCard(
                title = "感悟条数",
                value = "${reflections.size} 条",
                icon = "💬",
                modifier = Modifier.weight(1f)
            )
        }
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = "最近记录")
                Text(text = latestFlight?.let { "起飞 ${formatDateTime(it.dateTimeMillis)} · ${it.durationMinutes}分钟" } ?: "暂无起飞记录")
                Text(text = latestReflection?.let { "感悟 ${formatDateTime(it.createdAtMillis)}" } ?: "暂无感悟记录")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onOpenFlights, modifier = Modifier.weight(1f)) { Text("查看起飞") }
            Button(onClick = onOpenReflections, modifier = Modifier.weight(1f)) { Text("查看感悟") }
        }
        Button(onClick = onOpenStats, modifier = Modifier.fillMaxWidth()) { Text("查看统计总结") }
    }
}

@Composable
private fun StatsScreen(
    flights: List<FlightRecord>,
    reflections: List<ReflectionEntry>,
    isLoading: Boolean
) {
    if (isLoading) {
        LoadingSection()
        return
    }
    val totalFlights = flights.size
    val totalMinutes = flights.sumOf { it.durationMinutes }
    val avgMinutes = if (totalFlights == 0) 0 else totalMinutes / totalFlights
    val last7DaysMillis = System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000L
    val last7DaysCount = flights.count { it.dateTimeMillis >= last7DaysMillis }
    val reflectionCount = reflections.size
    val relatedReflectionCount = reflections.count { it.relatedFlightId != null }
    val moodTop = reflections
        .flatMap { it.moodTags }
        .groupingBy { it }
        .eachCount()
        .entries
        .sortedByDescending { it.value }
        .take(3)
        .joinToString(" · ") { "${it.key} ${it.value}" }
        .ifBlank { "暂无" }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(text = "统计总结", style = MaterialTheme.typography.titleLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(
                title = "起飞次数",
                value = "$totalFlights 次",
                icon = "📌",
                modifier = Modifier.weight(1f)
            )
            StatCard(
                title = "总时长",
                value = "$totalMinutes 分钟",
                icon = "⏱️",
                modifier = Modifier.weight(1f)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(
                title = "平均时长",
                value = "$avgMinutes 分钟",
                icon = "📈",
                modifier = Modifier.weight(1f)
            )
            StatCard(
                title = "近7天",
                value = "$last7DaysCount 次",
                icon = "🗓️",
                modifier = Modifier.weight(1f)
            )
        }
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = "感悟统计")
                Text(text = "总感悟 $reflectionCount 条")
                Text(text = "关联起飞 $relatedReflectionCount 条")
                Text(text = "情绪偏好 $moodTop")
            }
        }
    }
}

@Composable
private fun StatCard(
    title: String,
    value: String,
    icon: String,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = "$icon $title")
            Text(text = value, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun FlightListScreen(
    flights: List<FlightRecord>,
    isLoading: Boolean,
    onAdd: () -> Unit,
    onEdit: (Long) -> Unit,
    onDelete: (Long) -> Unit
) {
    val attributes = remember { listOf("全部", "普通", "高强度", "疲惫", "恢复") }
    var attributeFilter by remember { mutableStateOf(attributes.first()) }
    var pendingDeleteId by remember { mutableStateOf<Long?>(null) }
    val filtered = flights.filter { attributeFilter == "全部" || it.attribute == attributeFilter }
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "起飞记录", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.weight(1f))
            TextButton(onClick = onAdd) { Text("新增") }
        }
        FilterDropdown(
            label = "筛选",
            options = attributes,
            selected = attributeFilter,
            onSelected = { attributeFilter = it },
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        when {
            isLoading -> LoadingSection()
            filtered.isEmpty() -> EmptySection(message = "暂无起飞记录")
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filtered, key = { it.id }) { record ->
                        FlightCard(
                            record = record,
                            onEdit = { onEdit(record.id) },
                            onDelete = { pendingDeleteId = record.id }
                        )
                    }
                }
            }
        }
    }
    if (pendingDeleteId != null) {
        AlertDialog(
            onDismissRequest = { pendingDeleteId = null },
            title = { Text("确认删除？") },
            text = { Text("删除后无法恢复，确定要删除吗？") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(pendingDeleteId!!)
                    pendingDeleteId = null
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteId = null }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun ReflectionListScreen(
    reflections: List<ReflectionEntry>,
    flights: List<FlightRecord>,
    isLoading: Boolean,
    onAdd: () -> Unit,
    onEdit: (Long) -> Unit,
    onDelete: (Long) -> Unit
) {
    var pendingDeleteId by remember { mutableStateOf<Long?>(null) }
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "撸后感", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.weight(1f))
            TextButton(onClick = onAdd) { Text("新增") }
        }
        when {
            isLoading -> LoadingSection()
            reflections.isEmpty() -> EmptySection(message = "还没有感悟，去写一条吧")
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(reflections, key = { it.id }) { entry ->
                        ReflectionCard(
                            entry = entry,
                            relatedFlight = flights.firstOrNull { it.id == entry.relatedFlightId },
                            onOpen = { onEdit(entry.id) },
                            onEdit = { onEdit(entry.id) },
                            onDelete = { pendingDeleteId = entry.id }
                        )
                    }
                }
            }
        }
    }
    if (pendingDeleteId != null) {
        AlertDialog(
            onDismissRequest = { pendingDeleteId = null },
            title = { Text("确认删除？") },
            text = { Text("删除后无法恢复，确定要删除吗？") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(pendingDeleteId!!)
                    pendingDeleteId = null
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteId = null }) { Text("取消") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FlightCard(
    record: FlightRecord,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> onEdit()
                SwipeToDismissBoxValue.EndToStart -> onDelete()
                else -> {}
            }
            false
        }
    )
    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            Row(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(text = "编辑")
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Text(text = "删除")
                }
            }
        },
        content = {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = formatDateTime(record.dateTimeMillis),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(text = "时长 ${record.durationMinutes} 分钟")
                    Text(text = "属性 ${record.attribute}")
                    if (record.note.isNotBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(text = record.note, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReflectionCard(
    entry: ReflectionEntry,
    relatedFlight: FlightRecord?,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> onEdit()
                SwipeToDismissBoxValue.EndToStart -> onDelete()
                else -> {}
            }
            false
        }
    )
    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            Row(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(text = "编辑")
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Text(text = "删除")
                }
            }
        },
        content = {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpen() }
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = formatDateTime(entry.createdAtMillis), style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(text = entry.content, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    if (entry.moodTags.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(text = entry.moodTags.joinToString(" · "))
                    }
                    relatedFlight?.let {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(text = "关联 ${formatDateTime(it.dateTimeMillis)}")
                    }
                }
            }
        }
    )
}

@Composable
private fun LoadingSection() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator(modifier = Modifier.size(36.dp))
            Text(text = "加载中...")
        }
    }
}

@Composable
private fun EmptySection(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = message)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterDropdown(
    label: String,
    options: List<String>,
    selected: String,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                Text(
                    text = option,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onSelected(option)
                            expanded = false
                        }
                        .padding(16.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FlightEditScreen(
    existing: FlightRecord?,
    onSave: (FlightRecord) -> Unit,
    onCancel: () -> Unit,
    onMessage: (String) -> Unit
) {
    var showDiscardDialog by remember { mutableStateOf(false) }
    val isEditing = existing != null
    val initialDateTime = existing?.let { millisToLocalDateTime(it.dateTimeMillis) } ?: LocalDateTime.now()

    var durationText by remember { mutableStateOf(existing?.durationMinutes?.toString().orEmpty()) }
    var attribute by remember { mutableStateOf(existing?.attribute ?: "普通") }
    var note by remember { mutableStateOf(existing?.note.orEmpty()) }
    var date by remember { mutableStateOf(initialDateTime.toLocalDate()) }
    var time by remember { mutableStateOf(initialDateTime.toLocalTime().withSecond(0).withNano(0)) }

    val hasChanges = remember(durationText, attribute, note, date, time, existing) {
        if (existing == null) {
            durationText.isNotBlank() || note.isNotBlank()
        } else {
            durationText.toIntOrNull() != existing.durationMinutes ||
                attribute != existing.attribute ||
                note != existing.note ||
                LocalDateTime.of(date, time) != millisToLocalDateTime(existing.dateTimeMillis)
        }
    }

    BackHandler(enabled = true) {
        if (hasChanges) showDiscardDialog = true else onCancel()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(text = if (isEditing) "编辑起飞记录" else "新增起飞记录", style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(
            value = durationText,
            onValueChange = { durationText = it.filter { ch -> ch.isDigit() } },
            label = { Text("时长（分钟）") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )
        AttributeDropdown(
            selected = attribute,
            onSelected = { attribute = it }
        )
        DateTimePickerRow(
            date = date,
            time = time,
            onDateChange = { date = it },
            onTimeChange = { time = it }
        )
        OutlinedTextField(
            value = note,
            onValueChange = { note = it },
            label = { Text("备注") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = {
                    val duration = durationText.toIntOrNull()
                    if (duration == null || duration <= 0) {
                        onMessage("请输入有效时长")
                        return@Button
                    }
                    val dateTimeMillis = LocalDateTime.of(date, time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    val record = FlightRecord(
                        id = existing?.id ?: -1,
                        durationMinutes = duration,
                        dateTimeMillis = dateTimeMillis,
                        attribute = attribute,
                        note = note.trim()
                    )
                    onSave(record)
                },
                modifier = Modifier.weight(1f)
            ) { Text("保存") }
            Button(
                onClick = {
                    if (hasChanges) showDiscardDialog = true else onCancel()
                },
                modifier = Modifier.weight(1f)
            ) { Text("取消") }
        }
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text("放弃编辑？") },
            text = { Text("当前内容未保存，确定要放弃吗？") },
            confirmButton = {
                TextButton(onClick = { showDiscardDialog = false; onCancel() }) { Text("放弃") }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) { Text("继续编辑") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReflectionEditScreen(
    existing: ReflectionEntry?,
    flights: List<FlightRecord>,
    preselectedFlightId: Long?,
    requireCompletion: Boolean,
    onSave: (ReflectionEntry) -> Unit,
    onDelete: (Long) -> Unit,
    onCancel: () -> Unit,
    onMessage: (String) -> Unit
) {
    val moodOptions = remember { listOf("开心", "平静", "满足", "疲惫", "后悔") }
    var content by remember { mutableStateOf(existing?.content.orEmpty()) }
    var selectedTags by remember { mutableStateOf(existing?.moodTags ?: emptyList()) }
    var relatedFlightId by remember { mutableStateOf(existing?.relatedFlightId ?: preselectedFlightId) }
    var showDiscardDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    val hasChanges = remember(content, selectedTags, relatedFlightId, existing) {
        if (existing == null) {
            content.isNotBlank() || selectedTags.isNotEmpty() || relatedFlightId != null
        } else {
            content != existing.content ||
                selectedTags.sorted() != existing.moodTags.sorted() ||
                relatedFlightId != existing.relatedFlightId
        }
    }

    BackHandler(enabled = true) {
        if (requireCompletion) {
            onMessage("请先填写感受并保存")
        } else {
            if (hasChanges) showDiscardDialog = true else onCancel()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(text = if (existing == null) "新增感悟" else "感悟详情", style = MaterialTheme.typography.titleLarge)
        if (requireCompletion) {
            Text(text = "完成起飞后需填写本次感受")
        }
        OutlinedTextField(
            value = content,
            onValueChange = { content = it },
            label = { Text("感悟内容") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 4
        )
        Text(text = "情绪标签")
        FlowRowTags(
            options = moodOptions,
            selected = selectedTags,
            onToggle = { tag ->
                selectedTags = if (selectedTags.contains(tag)) selectedTags - tag else selectedTags + tag
            }
        )
        RelatedFlightDropdown(
            flights = flights,
            selectedId = relatedFlightId,
            onSelected = { relatedFlightId = it }
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = {
                    if (content.isBlank()) {
                        onMessage("请输入感悟内容")
                        return@Button
                    }
                    val entry = ReflectionEntry(
                        id = existing?.id ?: -1,
                        content = content.trim(),
                        moodTags = selectedTags,
                        relatedFlightId = relatedFlightId,
                        createdAtMillis = existing?.createdAtMillis ?: System.currentTimeMillis()
                    )
                    onSave(entry)
                },
                modifier = Modifier.weight(1f)
            ) { Text("保存") }
            if (existing != null && !requireCompletion) {
                Button(
                    onClick = { showDeleteDialog = true },
                    modifier = Modifier.weight(1f)
                ) { Text("删除") }
            } else if (!requireCompletion) {
                Button(
                    onClick = {
                        if (hasChanges) showDiscardDialog = true else onCancel()
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("取消") }
            }
        }
    }

    if (showDiscardDialog && !requireCompletion) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text("放弃编辑？") },
            text = { Text("当前内容未保存，确定要放弃吗？") },
            confirmButton = {
                TextButton(onClick = { showDiscardDialog = false; onCancel() }) { Text("放弃") }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) { Text("继续编辑") }
            }
        )
    }

    if (showDeleteDialog && existing != null && !requireCompletion) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("确认删除？") },
            text = { Text("删除后无法恢复，确定要删除吗？") },
            confirmButton = {
                TextButton(onClick = { showDeleteDialog = false; onDelete(existing.id) }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun FlowRowTags(
    options: List<String>,
    selected: List<String>,
    onToggle: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.chunked(3).forEach { row ->
            TagRow(row, selected, onToggle)
        }
    }
}

@Composable
private fun TagRow(
    tags: List<String>,
    selected: List<String>,
    onToggle: (String) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        tags.forEach { tag ->
            FilterChip(
                selected = selected.contains(tag),
                onClick = { onToggle(tag) },
                label = { Text(tag) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AttributeDropdown(
    selected: String,
    onSelected: (String) -> Unit
) {
    val options = listOf("普通", "高强度", "疲惫", "恢复")
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            label = { Text("相关属性") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                Text(
                    text = option,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onSelected(option)
                            expanded = false
                        }
                        .padding(16.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RelatedFlightDropdown(
    flights: List<FlightRecord>,
    selectedId: Long?,
    onSelected: (Long?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = flights.firstOrNull { it.id == selectedId }?.let {
        "${formatDateTime(it.dateTimeMillis)} · ${it.durationMinutes}分钟"
    } ?: "不关联"
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text("关联起飞记录") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            Text(
                text = "不关联",
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onSelected(null)
                        expanded = false
                    }
                    .padding(16.dp)
            )
            flights.sortedByDescending { it.dateTimeMillis }.forEach { record ->
                Text(
                    text = "${formatDateTime(record.dateTimeMillis)} · ${record.durationMinutes}分钟",
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onSelected(record.id)
                            expanded = false
                        }
                        .padding(16.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateTimePickerRow(
    date: LocalDate,
    time: LocalTime,
    onDateChange: (LocalDate) -> Unit,
    onTimeChange: (LocalTime) -> Unit
) {
    var showDateDialog by remember { mutableStateOf(false) }
    var showTimeDialog by remember { mutableStateOf(false) }

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = date.toString(),
            onValueChange = {},
            readOnly = true,
            label = { Text("日期") },
            modifier = Modifier
                .weight(1f)
                .clickable { showDateDialog = true }
        )
        OutlinedTextField(
            value = time.format(DateTimeFormatter.ofPattern("HH:mm")),
            onValueChange = {},
            readOnly = true,
            label = { Text("时间") },
            modifier = Modifier
                .weight(1f)
                .clickable { showTimeDialog = true }
        )
    }

    if (showDateDialog) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        )
        androidx.compose.material3.DatePickerDialog(
            onDismissRequest = { showDateDialog = false },
            confirmButton = {
                TextButton(onClick = {
                    val selectedMillis = datePickerState.selectedDateMillis
                    if (selectedMillis != null) {
                        onDateChange(Instant.ofEpochMilli(selectedMillis).atZone(ZoneId.systemDefault()).toLocalDate())
                    }
                    showDateDialog = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showDateDialog = false }) { Text("取消") }
            }
        ) {
            androidx.compose.material3.DatePicker(state = datePickerState)
        }
    }

    if (showTimeDialog) {
        val timeState = rememberTimePickerState(initialHour = time.hour, initialMinute = time.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { showTimeDialog = false },
            title = { Text("选择时间") },
            text = {
                TimePicker(state = timeState)
            },
            confirmButton = {
                TextButton(onClick = {
                    onTimeChange(LocalTime.of(timeState.hour, timeState.minute))
                    showTimeDialog = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showTimeDialog = false }) { Text("取消") }
            }
        )
    }
}

private fun upsertFlight(
    storage: LocalStorage,
    list: List<FlightRecord>,
    record: FlightRecord
): Pair<List<FlightRecord>, FlightRecord> {
    val newRecord = if (record.id == -1L) record.copy(id = storage.nextFlightId()) else record
    val updated = list.filterNot { it.id == newRecord.id } + newRecord
    storage.saveFlights(updated)
    return updated.sortedByDescending { it.dateTimeMillis } to newRecord
}

private fun upsertReflection(
    storage: LocalStorage,
    list: List<ReflectionEntry>,
    entry: ReflectionEntry
): List<ReflectionEntry> {
    val newEntry = if (entry.id == -1L) entry.copy(id = storage.nextReflectionId()) else entry
    val updated = list.filterNot { it.id == newEntry.id } + newEntry
    storage.saveReflections(updated)
    return updated.sortedByDescending { it.createdAtMillis }
}

private fun formatDateTime(millis: Long): String {
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    return Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDateTime().format(formatter)
}

private fun millisToLocalDateTime(millis: Long): LocalDateTime {
    return Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDateTime()
}

private class LocalStorage(context: Context) {
    private val prefs = context.getSharedPreferences("luma_store", Context.MODE_PRIVATE)

    fun loadFlights(): List<FlightRecord> {
        val raw = prefs.getString("flights", "[]") ?: "[]"
        val array = JSONArray(raw)
        val result = mutableListOf<FlightRecord>()
        for (index in 0 until array.length()) {
            val obj = array.getJSONObject(index)
            result.add(
                FlightRecord(
                    id = obj.getLong("id"),
                    durationMinutes = obj.getInt("durationMinutes"),
                    dateTimeMillis = obj.getLong("dateTimeMillis"),
                    attribute = obj.getString("attribute"),
                    note = obj.optString("note", "")
                )
            )
        }
        return result
    }

    fun saveFlights(list: List<FlightRecord>) {
        val array = JSONArray()
        list.forEach { record ->
            val obj = JSONObject()
            obj.put("id", record.id)
            obj.put("durationMinutes", record.durationMinutes)
            obj.put("dateTimeMillis", record.dateTimeMillis)
            obj.put("attribute", record.attribute)
            obj.put("note", record.note)
            array.put(obj)
        }
        prefs.edit().putString("flights", array.toString()).apply()
    }

    fun loadReflections(): List<ReflectionEntry> {
        val raw = prefs.getString("reflections", "[]") ?: "[]"
        val array = JSONArray(raw)
        val result = mutableListOf<ReflectionEntry>()
        for (index in 0 until array.length()) {
            val obj = array.getJSONObject(index)
            val tagsArray = obj.optJSONArray("moodTags") ?: JSONArray()
            val tags = mutableListOf<String>()
            for (i in 0 until tagsArray.length()) {
                tags.add(tagsArray.getString(i))
            }
            result.add(
                ReflectionEntry(
                    id = obj.getLong("id"),
                    content = obj.getString("content"),
                    moodTags = tags,
                    relatedFlightId = if (obj.isNull("relatedFlightId")) null else obj.getLong("relatedFlightId"),
                    createdAtMillis = obj.getLong("createdAtMillis")
                )
            )
        }
        return result
    }

    fun saveReflections(list: List<ReflectionEntry>) {
        val array = JSONArray()
        list.forEach { entry ->
            val obj = JSONObject()
            obj.put("id", entry.id)
            obj.put("content", entry.content)
            obj.put("moodTags", JSONArray(entry.moodTags))
            if (entry.relatedFlightId == null) {
                obj.put("relatedFlightId", JSONObject.NULL)
            } else {
                obj.put("relatedFlightId", entry.relatedFlightId)
            }
            obj.put("createdAtMillis", entry.createdAtMillis)
            array.put(obj)
        }
        prefs.edit().putString("reflections", array.toString()).apply()
    }

    fun nextFlightId(): Long {
        val next = prefs.getLong("flight_next_id", 1L)
        prefs.edit().putLong("flight_next_id", next + 1).apply()
        return next
    }

    fun nextReflectionId(): Long {
        val next = prefs.getLong("reflection_next_id", 1L)
        prefs.edit().putLong("reflection_next_id", next + 1).apply()
        return next
    }
}
