package com.rinse.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.rinse.app.data.LaundryRepository
import com.rinse.app.data.MachineStore
import com.rinse.app.model.LaundryMachine
import com.rinse.app.model.LaundryStatus
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class Page { LAUNDRY, ADD_MACHINE, DELETE_MACHINE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RinseRoot() {
    val context = LocalContext.current
    val store = remember(context) { MachineStore(context) }
    val machines = remember(store) { mutableStateListOf<LaundryMachine>().apply { addAll(store.load()) } }
    val selectedUrls = remember { mutableStateListOf<String>() }
    val statuses = remember { mutableStateMapOf<String, LaundryStatus>() }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var page by remember { mutableStateOf(Page.LAUNDRY) }
    var pendingUrl by remember { mutableStateOf<String?>(null) }
    var machineName by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var querying by remember { mutableStateOf(false) }
    var lastChecked by remember { mutableStateOf<String?>(null) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }

    val scanner = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.let { scanned ->
            val url = MachineStore.validatedUrl(scanned)
            when {
                url == null -> message = "二维码不是受支持的胖乖生活设备地址（https://h5.qiekj.com/skip?...NQT=...）。"
                machines.any { MachineStore.validatedUrl(it.url)?.let { existing ->
                    android.net.Uri.parse(existing).getQueryParameter("NQT") ==
                        android.net.Uri.parse(url).getQueryParameter("NQT")
                } == true } -> message = "这台设备已经在列表中。"
                else -> {
                    pendingUrl = url
                    machineName = ""
                    message = null
                }
            }
        }
    }

    fun startScan() {
        message = null
        scanner.launch(ScanOptions().apply {
            setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            setPrompt("请对准洗衣机上的二维码")
            setBeepEnabled(false)
            setOrientationLocked(false)
        })
    }

    fun startQuery() {
        if (querying) return
        val snapshot = machines.toList()
        querying = true
        lastChecked = null
        snapshot.forEach { statuses[it.url] = LaundryStatus.QUERYING }
        scope.launch {
            try {
                LaundryRepository.checkAll(context, snapshot).forEach { result ->
                    statuses[result.machine.url] = result.status
                }
                lastChecked = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            } catch (_: Exception) {
                snapshot.forEach { statuses[it.url] = LaundryStatus.UNKNOWN }
            } finally {
                querying = false
            }
        }
    }

    pendingUrl?.let { url ->
        AlertDialog(
            onDismissRequest = { pendingUrl = null },
            title = { Text("命名机器") },
            text = {
                Column {
                    Text("已读取设备二维码。请输入一个方便识别的名称。")
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = machineName,
                        onValueChange = { machineName = it },
                        label = { Text("机器名称") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("地址：$url", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                TextButton(
                    enabled = machineName.isNotBlank(),
                    onClick = {
                        val machine = LaundryMachine(machineName.trim(), "洗衣机", url)
                        if (store.save(machines.toList() + machine)) {
                            machines.add(machine)
                            statuses[url] = LaundryStatus.WAITING
                            pendingUrl = null
                            page = Page.LAUNDRY
                            message = "已添加 ${machine.name}，点击开始查询查看状态。"
                        } else {
                            message = "保存失败，请重试。"
                        }
                    }
                ) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { pendingUrl = null }) { Text("取消") } }
        )
    }

    if (showDeleteConfirmation) {
        val selectedMachines = machines.filter { it.url in selectedUrls }
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("确认删除机器？") },
            text = { Text("将从这台手机删除已选中的 ${selectedMachines.size} 台机器：\n" +
                selectedMachines.joinToString("、") { it.name }) },
            confirmButton = {
                TextButton(onClick = {
                    val next = machines.filterNot { it.url in selectedUrls }
                    if (store.save(next)) {
                        machines.clear()
                        machines.addAll(next)
                        selectedUrls.forEach { statuses.remove(it) }
                        val count = selectedUrls.size
                        selectedUrls.clear()
                        showDeleteConfirmation = false
                        page = Page.LAUNDRY
                        message = "已删除 $count 台机器。"
                    } else {
                        showDeleteConfirmation = false
                        message = "保存失败，机器未删除，请重试。"
                    }
                }) { Text("确认删除") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) { Text("取消") }
            }
        )
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(drawerContainerColor = Color(0xFF171A21), drawerContentColor = Color.White) {
                Column(
                    modifier = Modifier.width(280.dp).fillMaxSize()
                        .padding(horizontal = 18.dp, vertical = 28.dp)
                ) {
                    Text("Rinse", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
                    Text("TOOLS", color = Color(0xFF7F8796), fontSize = 11.sp,
                        fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                    Spacer(Modifier.height(28.dp))
                    listOf(
                        Page.LAUNDRY to "Laundry 查询",
                        Page.ADD_MACHINE to "添加机器",
                        Page.DELETE_MACHINE to "删除机器"
                    ).forEach { (target, title) ->
                        NavigationDrawerItem(
                            label = { Text(title, fontWeight = FontWeight.Bold) },
                            selected = page == target,
                            onClick = {
                                if (target == Page.DELETE_MACHINE && page != target) {
                                    selectedUrls.clear()
                                    message = null
                                }
                                page = target
                                scope.launch { drawerState.close() }
                            },
                            colors = NavigationDrawerItemDefaults.colors(
                                selectedContainerColor = Color(0xFF2C3442), selectedTextColor = Color.White,
                                unselectedContainerColor = Color.Transparent, unselectedTextColor = Color(0xFFB9C0CC)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    HorizontalDivider(color = Color(0xFF2A2F38))
                    Spacer(Modifier.height(14.dp))
                    Text("Rinse v2.0", color = Color(0xFF626A78), fontSize = 11.sp)
                }
            }
        }
    ) {
        Scaffold(topBar = {
            TopAppBar(
                title = { Text("Rinse", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { scope.launch { drawerState.open() } }) {
                        Text("☰", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    }
                }
            )
        }) { innerPadding ->
            val contentModifier = Modifier.fillMaxSize().padding(innerPadding)
            when (page) {
                Page.LAUNDRY -> LaundryScreen(
                    machines = machines, statuses = statuses, querying = querying,
                    lastChecked = lastChecked, message = message,
                    onQuery = ::startQuery,
                    onAdd = { page = Page.ADD_MACHINE },
                    modifier = contentModifier
                )
                Page.ADD_MACHINE -> AddMachineScreen(
                    onScan = ::startScan, message = message, modifier = contentModifier
                )
                Page.DELETE_MACHINE -> DeleteMachineScreen(
                    machines = machines,
                    selectedUrls = selectedUrls,
                    message = message,
                    onSelectionChange = { url, selected ->
                        if (selected) selectedUrls.add(url) else selectedUrls.remove(url)
                    },
                    onConfirm = { showDeleteConfirmation = true },
                    modifier = contentModifier
                )
            }
        }
    }
}

@Composable
private fun AddMachineScreen(onScan: () -> Unit, message: String?, modifier: Modifier = Modifier) {
    Column(modifier.background(MaterialTheme.colorScheme.background).padding(18.dp)) {
        Text("添加机器", fontSize = 27.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(10.dp))
        Text("扫描胖乖生活设备二维码，命名后将保存在这台手机里。",
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(20.dp))
        Button(onClick = onScan, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Text("打开摄像头扫描二维码")
        }
        Spacer(Modifier.height(12.dp))
        message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
    }
}

@Composable
private fun DeleteMachineScreen(
    machines: List<LaundryMachine>,
    selectedUrls: List<String>,
    message: String?,
    onSelectionChange: (String, Boolean) -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.background(MaterialTheme.colorScheme.background).padding(horizontal = 18.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 24.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("删除机器", fontSize = 27.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(8.dp))
            Text("选择需要删除的机器，包括最初预置的设备。删除后不会自动恢复。",
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            message?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.primary)
            }
        }
        items(machines, key = { it.url }) { machine ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = machine.url in selectedUrls,
                        onCheckedChange = { onSelectionChange(machine.url, it) }
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(machine.name, fontWeight = FontWeight.Bold)
                        Text(machine.type, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    }
                }
            }
        }
        item {
            Button(
                onClick = onConfirm,
                enabled = selectedUrls.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) { Text("确认删除（${selectedUrls.size}）") }
        }
    }
}

@Composable
private fun LaundryScreen(
    machines: List<LaundryMachine>,
    statuses: Map<String, LaundryStatus>,
    querying: Boolean,
    lastChecked: String?,
    message: String?,
    onQuery: () -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.background(MaterialTheme.colorScheme.background).padding(horizontal = 18.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 24.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Laundry 状态查询", fontSize = 27.sp, fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onBackground)
            Spacer(Modifier.height(7.dp))
            Text("查看已添加设备的当前状态。", fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = onQuery, enabled = !querying,
                modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF171A21), contentColor = Color.White,
                    disabledContainerColor = Color(0xFF9CA3AF), disabledContentColor = Color.White
                )
            ) {
                Text(if (querying) "查询中..." else if (lastChecked == null) "开始查询" else "重新查询",
                    fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
            Spacer(Modifier.height(10.dp))
            Text(when {
                querying -> "正在查询设备状态..."
                lastChecked != null -> "最后查询：$lastChecked"
                else -> "尚未查询"
            }, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            message?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
            }
            Spacer(Modifier.height(6.dp))
        }
        items(items = machines, key = { it.url }) { machine ->
            MachineStatusCard(
                machine = machine,
                status = statuses[machine.url] ?: LaundryStatus.WAITING
            )
        }
        item {
            TextButton(onClick = onAdd, modifier = Modifier.fillMaxWidth()) { Text("＋ 添加机器") }
        }
    }
}

@Composable
private fun MachineStatusCard(machine: LaundryMachine, status: LaundryStatus) {
    Card(
        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 17.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(machine.name, color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(Modifier.height(4.dp))
                Text(machine.type, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
            Spacer(Modifier.width(12.dp))
            StatusBadge(status)
        }
    }
}

@Composable
private fun StatusBadge(status: LaundryStatus) {
    val (background, foreground) = when (status) {
        LaundryStatus.AVAILABLE -> Color(0xFFE9F7EF) to Color(0xFF197A43)
        LaundryStatus.BUSY -> Color(0xFFFDECEC) to Color(0xFFC62828)
        LaundryStatus.UNAVAILABLE -> Color(0xFFFFF3E0) to Color(0xFFB85C00)
        LaundryStatus.QUERYING -> Color(0xFFEAF2FF) to Color(0xFF2563EB)
        LaundryStatus.WAITING, LaundryStatus.UNKNOWN -> Color(0xFFF1F3F5) to Color(0xFF666666)
    }
    Box(
        modifier = Modifier.background(background, RoundedCornerShape(10.dp))
            .padding(horizontal = 13.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(status.label, color = foreground, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}
