package com.wangzan101.mika.ui

import com.wangzan101.mika.R
import com.wangzan101.mika.i18n.tr
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wangzan101.mika.core.cap.sandbox.SandboxStatus
import com.wangzan101.mika.cap.sandbox.RootfsInstaller
import com.wangzan101.mika.cap.sandbox.RuntimePreparer
import com.wangzan101.mika.cap.sandbox.SandboxState
import kotlinx.coroutines.launch
import com.wangzan101.mika.ui.components.GroupCard
import com.wangzan101.mika.ui.components.ListRow
import com.wangzan101.mika.ui.theme.MikaTheme
import com.wangzan101.mika.ui.theme.MikaIcons
import com.wangzan101.mika.ui.components.TileTone
import com.wangzan101.mika.ui.components.MikaSwitch
import com.wangzan101.mika.ui.theme.MikaType
import com.wangzan101.mika.settings.FeatureFlags
import com.wangzan101.mika.ui.components.RowValue
import com.wangzan101.mika.ui.components.RowChevron
import com.wangzan101.mika.ui.components.ValueTone

/**
 * 设置 › 文件与计算环境 的文案(纯函数,便于测试)。运行环境对用户是看不见的实现细节(产品决定):
 * 字面上只说「脚本」「处理文件」「运行环境」,不出现沙箱、Linux、PRoot、Alpine 等技术名词。
 */
object SandboxCopy {
    val NETWORK_TITLE: String get() = tr(R.string.sandbox_network)
    val NETWORK_SUBTITLE: String get() = tr(R.string.sandbox_network_sub)
    val NETWORK_EXPLAIN: String get() = tr(R.string.sandbox_network_explain)
    val PROXY_TITLE: String get() = tr(R.string.sandbox_proxy)
    val PROXY_HINT: String get() = tr(R.string.sandbox_proxy_hint)
    val PROXY_NONE: String get() = tr(R.string.sandbox_proxy_none)
    val PROXY_SET: String get() = tr(R.string.sandbox_proxy_set)
    val STORAGE_USAGE_TITLE: String get() = tr(R.string.sandbox_storage_usage)
    val STORAGE_USAGE_SUB: String get() = tr(R.string.sandbox_storage_usage_sub)
    fun proxySub(proxy: String): String = if (proxy.isBlank()) tr(R.string.sandbox_proxy_sub) else proxy
    val ISOLATION_UNAVAILABLE: String get() = tr(R.string.sandbox_isolation_unavailable)
    val RESET_TITLE: String get() = tr(R.string.sandbox_reset_title)
    val RESET_BODY: String get() = tr(R.string.sandbox_reset_body)

    /** 「重置运行环境」下面的一行:平时说明会怎样,准备中或准备失败时如实说。 */
    fun resetSubtitle(s: SandboxStatus): String = when (s) {
        is SandboxStatus.Installing -> tr(R.string.runtime_preparing)
        is SandboxStatus.Broken -> tr(R.string.sandbox_broken, s.reason)
        else -> tr(R.string.sandbox_reset_sub)
    }
}

/** 接上真实状态的「文件与计算环境」页内容:联网开关读写 [SandboxState];重置后马上在后台重新准备。 */
@Composable
fun AdvancedSettings(onOpenStorage: () -> Unit = {}) {
    val context = LocalContext.current
    val state = remember { SandboxState.get(context) }
    val status by state.status.collectAsState()
    var network by remember { mutableStateOf(state.networkAllowed) }
    val flags = remember { FeatureFlags.get(context) }
    var proxy by remember { mutableStateOf(flags.containerProxy()) }
    var editingProxy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    AdvancedSection(
        status = status, networkAllowed = network, isolationIssue = state.isolationIssue != null,
        onNetworkChange = { state.networkAllowed = it; network = it },
        proxy = proxy, onProxy = { editingProxy = true }, onOpenStorage = onOpenStorage,
        onReset = {
            scope.launch {
                RootfsInstaller.create(context).reset()
                RuntimePreparer.ensure(context, retryFailed = true)
            }
        },
    )
    if (editingProxy) ProxyDialog(
        initial = proxy,
        onDismiss = { editingProxy = false },
        onSave = { flags.setContainerProxy(it); proxy = it.trim(); editingProxy = false },
    )
}

/** 改容器的网络代理:一个输入框,空着表示不用。 */
@Composable
private fun ProxyDialog(initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(SandboxCopy.PROXY_TITLE) },
        text = {
            Column {
                Text(SandboxCopy.PROXY_HINT, style = MikaType.Caption, color = MikaTheme.colors.ink3)
                OutlinedTextField(
                    text, { text = it }, Modifier.fillMaxWidth().padding(top = 8.dp).testTag("sandbox-proxy-input"),
                    singleLine = true, placeholder = { Text("http://127.0.0.1:7890") },
                )
            }
        },
        confirmButton = { TextButton({ onSave(text) }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** 不带状态的「文件与计算环境」页内容,便于测试。不支持的设备整组不显示。 */
@Composable
fun AdvancedSection(
    status: SandboxStatus,
    networkAllowed: Boolean,
    isolationIssue: Boolean,
    onNetworkChange: (Boolean) -> Unit,
    onReset: () -> Unit,
    /** 容器的网络代理,空串表示不用。 */
    proxy: String = "",
    onProxy: () -> Unit = {},
    onOpenStorage: () -> Unit = {},
) {
    if (status is SandboxStatus.Unsupported) return
    var confirming by remember { mutableStateOf(false) }

    GroupCard {
        ListRow(MikaIcons.Link, SandboxCopy.NETWORK_TITLE, SandboxCopy.NETWORK_SUBTITLE, first = true) {
            MikaSwitch(networkAllowed, onNetworkChange, Modifier.testTag("sandbox-network"))
        }
        Note(SandboxCopy.NETWORK_EXPLAIN, if (networkAllowed) MikaTheme.colors.warn else MikaTheme.colors.ink3)
        // 代理只给容器里的命令用,不影响 Mika 自己调模型
        ListRow(MikaIcons.Globe, SandboxCopy.PROXY_TITLE, SandboxCopy.proxySub(proxy), onClick = onProxy) {
            RowValue(if (proxy.isBlank()) SandboxCopy.PROXY_NONE else SandboxCopy.PROXY_SET, ValueTone.Link)
        }
        ListRow(MikaIcons.Doc, SandboxCopy.STORAGE_USAGE_TITLE, SandboxCopy.STORAGE_USAGE_SUB, onClick = onOpenStorage) {
            RowChevron()
        }
        if (isolationIssue) Note(SandboxCopy.ISOLATION_UNAVAILABLE, MikaTheme.colors.bad)
        ListRow(MikaIcons.Close, tr(R.string.sandbox_reset), SandboxCopy.resetSubtitle(status), titleColor = MikaTheme.colors.bad, tone = TileTone.Bad,
            onClick = if (status is SandboxStatus.Installing) null else ({ confirming = true }))
    }

    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text(SandboxCopy.RESET_TITLE) },
            text = { Text(SandboxCopy.RESET_BODY) },
            confirmButton = { TextButton({ confirming = false; onReset() }) { Text(tr(R.string.reset), color = MikaTheme.colors.bad) } },
            dismissButton = { TextButton({ confirming = false }) { Text(tr(R.string.cancel)) } },
        )
    }
}

@Composable
private fun Note(text: String, color: Color) {
    Text(text, Modifier.fillMaxWidth().padding(start = 60.dp, end = 16.dp, bottom = 10.dp), style = MikaType.Hint, color = color)
}
