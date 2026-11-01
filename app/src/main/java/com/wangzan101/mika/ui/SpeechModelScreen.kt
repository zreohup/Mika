package com.wangzan101.mika.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wangzan101.mika.MikaApp
import com.wangzan101.mika.R
import com.wangzan101.mika.i18n.tr
import com.wangzan101.mika.settings.ProviderStore
import com.wangzan101.mika.ui.components.CapabilityTag
import com.wangzan101.mika.ui.components.FooterNote
import com.wangzan101.mika.ui.components.GroupCard
import com.wangzan101.mika.ui.components.GroupLabel
import com.wangzan101.mika.ui.components.PillTone
import com.wangzan101.mika.ui.components.RadioRow
import com.wangzan101.mika.ui.components.StatusPill
import com.wangzan101.mika.ui.components.InputRow
import com.wangzan101.mika.ui.components.MikaButton
import com.wangzan101.mika.ui.components.ButtonKind
import com.wangzan101.mika.ui.theme.MikaTheme
import com.wangzan101.mika.voice.SpeechChoice
import com.wangzan101.mika.voice.SpeechOptions
import com.wangzan101.mika.voice.SystemSpeech
import com.wangzan101.mika.voice.DoubaoCredentials
import com.wangzan101.mika.voice.DoubaoProtocol

/** 选择页上的动作。 */
class SpeechPickActions(
    /** 「手机自带」或「不用」:当场生效并返回。 */
    val pick: (SpeechChoice) -> Unit = {},
    /** (服务商 id, 模型名):先试转,通了才选上并返回;列表里点的和手填的都走这里。 */
    val pickModel: (String, String) -> Unit = { _, _ -> },
    val saveDoubao: (String, String, String, String) -> Unit = { _, _, _, _ -> },
    val clearDoubao: () -> Unit = {},
)

/**
 * 设置 › 模型 › 语音转文字:自己带一个模型状态,可以从别处(例如输入框的「还不能语音输入」)直接进入。
 * 选好(手机自带、不用、试转通过的服务商模型)后回到进来的那一页。
 */
@Composable
fun SpeechModelScreen(store: ProviderStore, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val settings = (context.applicationContext as? MikaApp)?.speech
    var doubaoConfigured by remember { mutableStateOf(settings?.hasDoubaoCredentials == true) }
    val vm = remember {
        ModelSetupViewModel(
            store, scope,
            speech = (context.applicationContext as? MikaApp)?.speech,
            systemSpeech = {
                val silent = (context.applicationContext as? MikaApp)?.speech?.systemSilent?.value ?: false
                SystemSpeech.capability(context.applicationContext, silent)
            },
        )
    }
    val ui by vm.state.collectAsState()
    val probe by vm.speechProbe.collectAsState()
    LaunchedEffect(Unit) { vm.ensureListed(); vm.clearSpeechProbe() }
    SpeechModelContent(
        ui, probe,
        SpeechPickActions(
            pick = { vm.pickSpeech(it); onBack() },
            pickModel = { pid, model -> vm.pickSpeechModel(pid, model, onPicked = onBack) },
            saveDoubao = { key, appId, token, resource ->
                checkNotNull(settings).saveDoubao(DoubaoCredentials(key, appId, token), resource)
                onBack()
            },
            clearDoubao = {
                settings?.clearDoubao()
                doubaoConfigured = false
                settings?.let { vm.pickSpeech(it.choice.value) }
            },
        ),
        onBack, doubaoConfigured,
    )
}

/** 不带状态的选择页,便于测试:顶上一段说明,「手机自带」「不用」,下面按服务商分组单选。 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SpeechModelContent(ui: ModelSetupUi, probe: Map<String, String>, actions: SpeechPickActions, onBack: () -> Unit, doubaoConfigured: Boolean = false) {
    val c = MikaTheme.colors
    val system = ui.systemSpeech
    val choice = ui.speech
    // 没选过时有手机自带就是在用它,所以「没选过」也把它标成选中
    // 记成不响应时不标选中;行仍可点,重新选它就清掉记号再试
    val systemSelected = system.usable && (choice == SpeechChoice.System || choice == SpeechChoice.Auto)
    Page(tr(R.string.role_speech), onBack) {
        Text(
            tr(R.string.speech_hint), Modifier.padding(top = 4.dp, bottom = 12.dp), fontSize = 14.5.sp, lineHeight = 22.sp, color = c.ink2,
        )
        GroupCard {
            RadioRow(
                tr(R.string.speech_system), selected = systemSelected, enabled = system.available,
                onClick = { actions.pick(SpeechChoice.System) }, first = true,
                subtitle = tr(
                    when {
                        !system.available -> R.string.speech_system_none_note
                        !system.responsive -> R.string.speech_system_silent_note
                        system.onDevice -> R.string.speech_system_note
                        else -> R.string.speech_system_note_online
                    },
                ),
                trailing = {
                    when {
                        system.usable -> StatusPill(tr(R.string.speech_tag_ok), PillTone.Ok)
                        system.available -> StatusPill(tr(R.string.speech_tag_silent), PillTone.Neutral)
                        else -> StatusPill(tr(R.string.speech_tag_none), PillTone.Neutral)
                    }
                },
            )
            RadioRow(
                tr(R.string.models_off), selected = choice == SpeechChoice.Off, onClick = { actions.pick(SpeechChoice.Off) },
                subtitle = tr(R.string.speech_off_note),
            )
        }
        DoubaoSetup(choice, doubaoConfigured, actions)
        SpeechOptions.groups(ui.providers).forEach { g ->
            val p = g.provider
            val testing = ui.speechTesting == p.id
            GroupLabel(ModelsCopy.providerName(p))
            // 手填选上的模型不在列表里,也补一行标成选中,让人看得出现在用的是哪个
            val picked = (choice as? SpeechChoice.Provider)?.takeIf { it.providerId == p.id }?.model
            val rows = if (picked != null && picked !in g.models) g.models + picked else g.models
            GroupCard {
                rows.forEachIndexed { i, id ->
                    RadioRow(
                        id, selected = id == picked, onClick = { actions.pickModel(p.id, id) }, first = i == 0,
                        titleStyle = ModelIdStyle, enabled = ui.speechTesting == null,
                        trailing = { CapabilityTag(tr(R.string.speech_tag_speech)) },
                    )
                }
                // 列表里没有的(列表不全、或这家不给列表)手填
                Box(Modifier.testTag("speech-manual-${p.name}")) {
                    ManualModel(p, if (testing) ListState.Running else null, first = rows.isEmpty()) { actions.pickModel(p.id, it) }
                }
            }
            when {
                probe[p.id] != null -> {
                    // 列表长时原因落在屏幕外,点了像没反应:出来时滚到看得见
                    val why = probe.getValue(p.id)
                    val into = remember { BringIntoViewRequester() }
                    LaunchedEffect(why) { into.bringIntoView() }
                    FooterNote(why, Modifier.bringIntoViewRequester(into).testTag("speech-pick-failed"), color = c.bad)
                }
                testing && rows.isNotEmpty() -> FooterNote(ModelSetupCopy.TESTING, Modifier.testTag("speech-pick-running"))
            }
        }
        FooterNote(tr(R.string.speech_footer), Modifier.padding(top = 8.dp))
    }
}

/** ASR has its own credentials and resource IDs; it never lists or probes chat models. */
@Composable
private fun DoubaoSetup(choice: SpeechChoice, configured: Boolean, actions: SpeechPickActions) {
    var legacy by remember { mutableStateOf(false) }
    // Secrets are not saveable state and are never populated from stored credentials.
    var key by remember { mutableStateOf("") }
    var appId by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }
    var resource by remember { mutableStateOf((choice as? SpeechChoice.Doubao)?.resourceId ?: DoubaoProtocol.DURATION) }
    var failed by remember { mutableStateOf(false) }
    val credentials = DoubaoCredentials(if (legacy) "" else key.trim(), if (legacy) appId.trim() else "", if (legacy) token.trim() else "")
    val unchanged = key.isBlank() && appId.isBlank() && token.isBlank()
    GroupLabel(tr(R.string.speech_doubao_provider))
    GroupCard {
        RadioRow(tr(R.string.speech_doubao_model), selected = choice is SpeechChoice.Doubao,
            enabled = configured, first = true,
            onClick = { actions.pick(SpeechChoice.Doubao(resource)) },
            subtitle = tr(R.string.speech_doubao_note))
        RadioRow(tr(R.string.speech_doubao_new), selected = !legacy, onClick = { legacy = false; appId = ""; token = "" })
        RadioRow(tr(R.string.speech_doubao_legacy), selected = legacy, onClick = { legacy = true; key = "" })
        if (legacy) {
            InputRow("App ID", appId, { appId = it }, tr(R.string.speech_doubao_enter_app))
            InputRow("Access Token", token, { token = it }, tr(R.string.speech_doubao_enter_key), secret = true)
        } else {
            InputRow("API Key", key, { key = it }, tr(R.string.speech_doubao_enter_key), secret = true)
        }
        RadioRow(tr(R.string.speech_doubao_duration), selected = resource == DoubaoProtocol.DURATION,
            onClick = { resource = DoubaoProtocol.DURATION })
        RadioRow(tr(R.string.speech_doubao_concurrent), selected = resource == DoubaoProtocol.CONCURRENT,
            onClick = { resource = DoubaoProtocol.CONCURRENT })
    }
    FooterNote(tr(R.string.speech_doubao_privacy))
    MikaButton(tr(R.string.speech_doubao_save), modifier = Modifier.padding(top = 8.dp).testTag("speech-doubao-save"),
        enabled = credentials.valid || configured && unchanged,
        onClick = {
            failed = runCatching {
                if (configured && unchanged) actions.pick(SpeechChoice.Doubao(resource))
                else actions.saveDoubao(credentials.apiKey, credentials.appId, credentials.accessKey, resource)
            }.isFailure
            key = ""; appId = ""; token = ""
        })
    if (configured) MikaButton(tr(R.string.speech_doubao_remove), actions.clearDoubao, kind = ButtonKind.Text)
    if (failed) FooterNote(tr(R.string.speech_doubao_save_failed), color = MikaTheme.colors.bad)
}
