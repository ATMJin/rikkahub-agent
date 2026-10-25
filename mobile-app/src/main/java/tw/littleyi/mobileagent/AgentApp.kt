package tw.littleyi.mobileagent

import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.SystemClock
import android.view.MotionEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.rerere.ai.provider.InternetState
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.readInternetState
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.codex.CodexOAuthStatus

enum class AgentPermission { NOTIFICATIONS, ACCESSIBILITY, BRIGHTNESS }

@Composable
private fun currentInternetState(): InternetState {
    val context = LocalContext.current.applicationContext
    var internet by remember { mutableStateOf(readInternetState(context)) }
    val scope = rememberCoroutineScope()
    DisposableEffect(context) {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        val callback = object : ConnectivityManager.NetworkCallback() {
            private fun refresh() { scope.launch(Dispatchers.Main.immediate) { internet = readInternetState(context) } }
            override fun onAvailable(network: Network) = refresh()
            override fun onLost(network: Network) = refresh()
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) = refresh()
        }
        runCatching { manager?.registerDefaultNetworkCallback(callback) }
        onDispose { runCatching { manager?.unregisterNetworkCallback(callback) } }
    }
    return internet
}

@Composable
fun AgentApp(
    agent: AgentController,
    initiallyOpenSettings: Boolean,
    importModel: () -> Unit,
    openPermission: (AgentPermission) -> Unit,
) {
    val state by agent.state.collectAsState()
    val accounts by agent.accounts.accounts.collectAsState()
    val loginStatus by agent.login.status.collectAsState()
    val connection = currentInternetState()
    var settingsOpen by rememberSaveable { mutableStateOf(initiallyOpenSettings) }
    var configuration by remember { mutableStateOf(agent.configuration) }
    var models by remember { mutableStateOf<List<Model>?>(null) }
    var modelsLoading by remember { mutableStateOf(false) }
    var logoutRequested by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    fun notice(text: String) { scope.launch { snackbar.showSnackbar(text.take(500)) } }
    fun save(value: Configuration): Boolean = runCatching {
        agent.save(value)
        configuration = agent.configuration
    }.fold(onSuccess = { true }, onFailure = { notice(it.message.orEmpty()); false })

    BackHandler(enabled = settingsOpen) { settingsOpen = false }
    LaunchedEffect(loginStatus) {
        when(val result = loginStatus) {
            is CodexOAuthStatus.Error -> { notice("登入失敗：${result.message}"); agent.login.consumeResult() }
            is CodexOAuthStatus.Success -> { notice("已登入；請選擇模型並實測帳號可用性。"); agent.login.consumeResult() }
            else -> Unit
        }
    }
    val route = AgentPresentation.route(
        internet = if(state.busy) state.inferenceRoute ?: connection else connection,
        cloudModel = if(state.busy && state.inferenceRoute == InternetState.ONLINE) state.activeModel ?: configuration.model else configuration.model,
        hasOfflineModel = agent.hasOfflineModel(),
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbar) },
    ) { insets ->
        Column(Modifier.fillMaxSize().padding(insets).consumeWindowInsets(insets)) {
            AgentHeader(settingsOpen, route, !state.busy && !modelsLoading,
                compact=WindowInsets.ime.getBottom(LocalDensity.current)>0) { settingsOpen = !settingsOpen }
            if(settingsOpen) {
                AgentSettingsScreen(
                    configuration = configuration,
                    accounts = accounts.map { it.email.ifBlank { it.name } },
                    hasOfflineModel = agent.hasOfflineModel(),
                    enabled = !state.busy && !modelsLoading,
                    modelsLoading = modelsLoading,
                    operationStatus = if(state.busy) state.text else null,
                    onSave = { value -> if(save(value)) notice("設定已儲存。") },
                    onLogin = { value -> if(save(value)) agent.login.startLogin() },
                    onDiscover = { value ->
                        if(save(value)) {
                            modelsLoading = true
                            scope.launch {
                                try {
                                    val available = agent.listModels()
                                    if(available.isEmpty()) notice("Provider 未回傳可選模型。") else models = available
                                } catch (e: Exception) { notice(e.message.orEmpty()) }
                                finally { modelsLoading = false }
                            }
                        }
                    },
                    onLogout = { logoutRequested = true },
                    onImport = importModel,
                    onPermission = openPermission,
                    onStop = agent::cancel,
                    onBack = { settingsOpen = false },
                )
            } else {
                AgentConversationScreen(state, onSend = agent::send, onStop = agent::cancel)
            }
        }
    }

    state.approval?.let { request ->
        AgentApprovalDialog(request) { accepted -> agent.answerApproval(request.id, accepted) }
    }
    models?.let { available ->
        AlertDialog(
            onDismissRequest = { models = null },
            properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn),
            title = { Text(stringResource(R.string.select_model)) },
            text = {
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(available, key = { it.id.toString() }) { model ->
                        TextButton(onClick = {
                            if(save(configuration.copy(model = model.modelId))) models = null
                        }, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.fillMaxWidth()) {
                                Text(model.displayName.ifBlank { model.modelId })
                                if(model.displayName.isNotBlank()) Text(model.modelId, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { models = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if(logoutRequested) {
        AlertDialog(
            onDismissRequest = { logoutRequested = false },
            properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn),
            title = { Text(stringResource(R.string.logout_title)) },
            text = { Text(stringResource(R.string.logout_description)) },
            confirmButton = {
                TextButton(onClick = {
                    logoutRequested = false
                    scope.launch {
                        try {
                            agent.accounts.accounts.value.toList().forEach { agent.accounts.delete(it.id) }
                            notice("已清除本地帳號；未確認遠端撤銷。")
                        } catch(e: Exception) { notice(e.message.orEmpty()) }
                    }
                }) { Text(stringResource(R.string.logout_confirm)) }
            },
            dismissButton = { TextButton(onClick = { logoutRequested = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun AgentHeader(settingsOpen: Boolean, route: RouteLabel, enabled: Boolean, compact: Boolean, onSettings: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(horizontal=22.dp, vertical=if(compact) 6.dp else 18.dp), verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                if(!compact) Text(stringResource(R.string.agent_kicker), style=MaterialTheme.typography.labelSmall, color=MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(if(settingsOpen) R.string.agent_settings else R.string.agent_title), fontSize=if(compact) 21.sp else 26.sp, fontWeight=FontWeight.Medium)
            }
            Surface(shape=CircleShape, color=MaterialTheme.colorScheme.surface, border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)) {
                IconButton(onClick=onSettings, enabled=enabled,
                    modifier=Modifier.semantics { contentDescription=if(settingsOpen) "回到對話" else "開啟 Agent 設定" }) {
                    AgentIcon(if(settingsOpen) AgentGlyph.BACK else AgentGlyph.SETTINGS)
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(start=22.dp,end=22.dp,bottom=if(compact) 8.dp else 16.dp), verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
            Text(route.title, style=MaterialTheme.typography.labelMedium, color=MaterialTheme.colorScheme.primary)
            Text(route.detail, modifier=Modifier.weight(1f), style=MaterialTheme.typography.bodySmall, color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun ColumnScope.AgentConversationScreen(state: AgentState, onSend: (String,Boolean)->Unit, onStop: ()->Unit) {
    var draft by remember { mutableStateOf("") }
    var allowNotifications by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val keyboardVisible=WindowInsets.ime.getBottom(LocalDensity.current)>0
    LaunchedEffect(state.userPrompt) { if(state.userPrompt != null) listState.scrollToItem(0) }
    LazyColumn(
        modifier=Modifier.weight(1f).fillMaxWidth(),
        state=listState,
        contentPadding=PaddingValues(horizontal=20.dp,vertical=22.dp),
        verticalArrangement=Arrangement.spacedBy(15.dp),
    ) {
        if(state.userPrompt == null) {
            item {
                Column(Modifier.padding(top=8.dp,bottom=8.dp), verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    Surface(shape=RoundedCornerShape(14.dp),color=MaterialTheme.colorScheme.primaryContainer) {
                        Box(Modifier.size(44.dp), contentAlignment=Alignment.Center) { AgentIcon(AgentGlyph.SPARKLE,color=MaterialTheme.colorScheme.primary) }
                    }
                    Text(stringResource(R.string.welcome_title),fontSize=21.sp,fontWeight=FontWeight.Medium)
                    Text(stringResource(R.string.welcome_detail),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item {
                Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    AgentPresentation.examples.chunked(2).forEachIndexed { rowIndex, examples ->
                        Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                            examples.forEachIndexed { index, example ->
                                val glyph=listOf(AgentGlyph.CAMERA,AgentGlyph.SUN,AgentGlyph.BELL,AgentGlyph.WIFI)[rowIndex*2+index]
                                OutlinedButton(
                                    onClick={ onSend(example.prompt,example.allowNotifications) }, enabled=!state.busy,
                                    shape=RoundedCornerShape(12.dp), border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant),
                                    colors=ButtonDefaults.outlinedButtonColors(containerColor=MaterialTheme.colorScheme.surface),
                                    contentPadding=PaddingValues(horizontal=10.dp,vertical=12.dp), modifier=Modifier.weight(1f),
                                ) {
                                    AgentIcon(glyph,Modifier.size(17.dp));Spacer(Modifier.width(6.dp))
                                    Text(example.title,style=MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                }
            }
        } else {
            item {
                Box(Modifier.fillMaxWidth(),contentAlignment=Alignment.CenterEnd) {
                    Surface(Modifier.padding(start=38.dp),shape=RoundedCornerShape(16.dp,16.dp,4.dp,16.dp),color=MaterialTheme.colorScheme.primaryContainer) {
                        SelectionContainer { Text(state.userPrompt,modifier=Modifier.padding(14.dp),style=MaterialTheme.typography.bodyLarge) }
                    }
                }
            }
            item {
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(7.dp)) {
                    AgentIcon(AgentGlyph.SPARKLE,Modifier.size(17.dp),MaterialTheme.colorScheme.primary)
                    Text(stringResource(R.string.agent_title),style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary)
                }
            }
        }
        items(state.events) { event -> AgentEventView(event) }
        if(state.busy) item {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                CircularProgressIndicator(Modifier.size(16.dp),strokeWidth=2.dp)
                Text(stringResource(if(state.approval == null) R.string.agent_working else R.string.waiting_approval),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    Column(Modifier.fillMaxWidth().imePadding().padding(horizontal=18.dp,vertical=10.dp)) {
        Surface(shape=RoundedCornerShape(18.dp),color=MaterialTheme.colorScheme.surface,border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)) {
            Column(Modifier.padding(12.dp)) {
                OutlinedTextField(
                    value=draft,onValueChange={draft=it},enabled=!state.busy,modifier=Modifier.fillMaxWidth(),
                    placeholder={Text(stringResource(R.string.task_placeholder))},minLines=if(keyboardVisible) 1 else 2,maxLines=4,
                    colors=OutlinedTextFieldDefaults.colors(
                        focusedBorderColor=Color.Transparent,unfocusedBorderColor=Color.Transparent,disabledBorderColor=Color.Transparent,
                    ),
                )
                Row(verticalAlignment=Alignment.CenterVertically) {
                    AgentIcon(AgentGlyph.SHIELD,Modifier.size(14.dp),MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(5.dp))
                    Text(stringResource(R.string.ask_before_action),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.weight(1f))
                    if(state.busy) {
                        IconButton(onClick=onStop,modifier=Modifier.semantics { contentDescription="停止目前工作" }) { AgentIcon(AgentGlyph.STOP) }
                    } else {
                        FilledIconButton(onClick={
                            if(draft.isNotBlank()) {
                                val request=draft.trim();val grant=allowNotifications
                                draft="";allowNotifications=false;onSend(request,grant)
                            }
                        },enabled=draft.isNotBlank(),shape=RoundedCornerShape(13.dp),modifier=Modifier.semantics { contentDescription="送出指令" }) {
                            AgentIcon(AgentGlyph.SEND)
                        }
                    }
                }
            }
        }
        Row(verticalAlignment=Alignment.CenterVertically) {
            Checkbox(checked=allowNotifications,onCheckedChange={allowNotifications=it},enabled=!state.busy)
            Text(stringResource(R.string.allow_notification_turn),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.weight(1f))
        }
        if(!keyboardVisible) Text(stringResource(R.string.voice_not_implemented),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.align(Alignment.CenterHorizontally))
    }
}

@Composable
private fun AgentEventView(event: AgentEvent) {
    when(event.kind) {
        AgentEventKind.ASSISTANT -> SelectionContainer { Text(event.text,style=MaterialTheme.typography.bodyLarge) }
        AgentEventKind.STATUS -> Row(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.Top) {
            AgentIcon(AgentGlyph.SPARKLE,Modifier.size(16.dp),MaterialTheme.colorScheme.onSurfaceVariant)
            Text(event.text,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        AgentEventKind.TOOL -> {
            var expanded by remember(event) { mutableStateOf(false) }
            Surface(shape=RoundedCornerShape(12.dp),color=MaterialTheme.colorScheme.surfaceVariant.copy(alpha=.5f)) {
                Column(Modifier.padding(12.dp)) {
                    Text(event.toolName?.let(AgentPresentation::toolTitle).orEmpty(),style=MaterialTheme.typography.labelMedium)
                    if(event.text.trimStart().startsWith("[") || event.text.trimStart().startsWith("{")) {
                        TextButton(onClick={expanded=!expanded}) { Text(stringResource(if(expanded) R.string.hide_tool_result else R.string.show_tool_result)) }
                        if(expanded) SelectionContainer { Text(event.text,style=MaterialTheme.typography.bodySmall,fontFamily=FontFamily.Monospace) }
                    } else SelectionContainer { Text(event.text,style=MaterialTheme.typography.bodyMedium) }
                }
            }
        }
        AgentEventKind.CANCELLED, AgentEventKind.ERROR -> {
            Surface(shape=RoundedCornerShape(12.dp),color=if(event.kind==AgentEventKind.ERROR) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant) {
                Column(Modifier.padding(12.dp)) {
                    event.toolName?.let { Text(AgentPresentation.toolTitle(it),style=MaterialTheme.typography.labelMedium) }
                    SelectionContainer { Text(event.text,style=MaterialTheme.typography.bodyMedium) }
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.AgentSettingsScreen(
    configuration: Configuration, accounts: List<String>, hasOfflineModel: Boolean,
    enabled: Boolean, modelsLoading: Boolean, operationStatus: String?,
    onSave:(Configuration)->Unit,onLogin:(Configuration)->Unit,onDiscover:(Configuration)->Unit,
    onLogout:()->Unit,onImport:()->Unit,onPermission:(AgentPermission)->Unit,onStop:()->Unit,onBack:()->Unit,
) {
    // Do not put API keys in rememberSaveable, logs, navigation arguments or saved UI state.
    var useCodex by remember(configuration) { mutableStateOf(configuration.useCodex) }
    var endpoint by remember(configuration) { mutableStateOf(configuration.endpoint) }
    var apiKey by remember(configuration) { mutableStateOf(configuration.apiKey) }
    var model by remember(configuration) { mutableStateOf(configuration.model) }
    var providerMenu by remember { mutableStateOf(false) }
    fun value()=Configuration(useCodex,endpoint.trim(),apiKey,model.trim())

    Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().padding(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        SettingsGroup(R.string.online_model) {
            Box {
                OutlinedButton(onClick={providerMenu=true},enabled=enabled,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(10.dp)) {
                    Text(stringResource(if(useCodex) R.string.provider_codex else R.string.provider_api))
                }
                DropdownMenu(expanded=providerMenu,onDismissRequest={providerMenu=false}) {
                    DropdownMenuItem(text={Text(stringResource(R.string.provider_codex))},onClick={useCodex=true;providerMenu=false})
                    DropdownMenuItem(text={Text(stringResource(R.string.provider_api))},onClick={useCodex=false;providerMenu=false})
                }
            }
            if(useCodex) {
                Text(accounts.joinToString().ifBlank { stringResource(R.string.not_signed_in) },style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick={onLogin(value())},enabled=enabled,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(12.dp)) { Text(stringResource(R.string.login)) }
                if(accounts.isNotEmpty()) TextButton(onClick=onLogout,enabled=enabled) { Text(stringResource(R.string.remove_local_account)) }
            } else {
                OutlinedTextField(value=endpoint,onValueChange={endpoint=it},label={Text(stringResource(R.string.api_endpoint))},singleLine=true,enabled=enabled,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Uri),modifier=Modifier.fillMaxWidth())
                OutlinedTextField(value=apiKey,onValueChange={apiKey=it},label={Text(stringResource(R.string.api_key))},singleLine=true,enabled=enabled,visualTransformation=PasswordVisualTransformation(),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Password),modifier=Modifier.fillMaxWidth())
            }
            OutlinedTextField(value=model,onValueChange={model=it},label={Text(stringResource(R.string.model_id))},singleLine=true,enabled=enabled,modifier=Modifier.fillMaxWidth())
            OutlinedButton(onClick={onDiscover(value())},enabled=enabled,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(12.dp)) {
                if(modelsLoading) { CircularProgressIndicator(Modifier.size(15.dp),strokeWidth=2.dp);Spacer(Modifier.width(8.dp)) }
                Text(stringResource(R.string.discover_models))
            }
            Button(onClick={onSave(value())},enabled=enabled,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(12.dp)) { Text(stringResource(R.string.save_configuration)) }
            Text(stringResource(R.string.account_validation_note),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        SettingsGroup(R.string.offline_model) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Text("Gemma 4 E2B",modifier=Modifier.weight(1f),style=MaterialTheme.typography.titleSmall)
                Text(stringResource(if(hasOfflineModel) R.string.model_imported else R.string.model_missing),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.primary)
            }
            Text(stringResource(R.string.offline_routing_note),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick=onImport,enabled=enabled&&!hasOfflineModel,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(12.dp)) { Text(stringResource(R.string.import_e2b)) }
        }
        SettingsGroup(R.string.system_permissions) {
            PermissionRow(AgentGlyph.BELL,R.string.notification_permission,enabled) { onPermission(AgentPermission.NOTIFICATIONS) }
            PermissionRow(AgentGlyph.SHIELD,R.string.accessibility_permission,enabled) { onPermission(AgentPermission.ACCESSIBILITY) }
            PermissionRow(AgentGlyph.SUN,R.string.brightness_permission,enabled) { onPermission(AgentPermission.BRIGHTNESS) }
            Text(stringResource(R.string.permission_note),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if(operationStatus!=null) {
            Text(operationStatus,style=MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick=onStop,modifier=Modifier.fillMaxWidth()) { Text(stringResource(R.string.stop)) }
        }
        OutlinedButton(onClick=onBack,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(12.dp)) { AgentIcon(AgentGlyph.BACK);Spacer(Modifier.width(8.dp));Text(stringResource(R.string.back_to_conversation)) }
    }
}

@Composable
private fun SettingsGroup(title: Int, content: @Composable ColumnScope.()->Unit) {
    Surface(shape=RoundedCornerShape(16.dp),color=MaterialTheme.colorScheme.surface,border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text(stringResource(title),style=MaterialTheme.typography.titleSmall)
            content()
        }
    }
}

@Composable
private fun PermissionRow(glyph: AgentGlyph, label: Int, enabled: Boolean, onClick:()->Unit) {
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        AgentIcon(glyph,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(label),modifier=Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium)
        TextButton(onClick=onClick,enabled=enabled) { Text(stringResource(R.string.configure)) }
    }
}

@Composable
private fun AgentApprovalDialog(request: Approval, onDecision:(Boolean)->Unit) {
    val label=remember(request) { AgentPresentation.approval(request.name,request.arguments) }
    var seconds by remember(request.id) { mutableIntStateOf(AgentPresentation.remainingApprovalSeconds(request.createdAt,SystemClock.elapsedRealtime())) }
    LaunchedEffect(request.id) {
        while(seconds>0) {
            delay(250)
            seconds=AgentPresentation.remainingApprovalSeconds(request.createdAt,SystemClock.elapsedRealtime())
        }
    }
    Dialog(onDismissRequest={onDecision(false)},properties=DialogProperties(
        usePlatformDefaultWidth=false,dismissOnClickOutside=false,securePolicy=SecureFlagPolicy.SecureOn,
    )) {
        Surface(Modifier.padding(20.dp).widthIn(max=430.dp).fillMaxWidth(),shape=RoundedCornerShape(24.dp),color=MaterialTheme.colorScheme.surface) {
            Column(Modifier.heightIn(max=600.dp).verticalScroll(rememberScrollState()).padding(24.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
                Surface(shape=RoundedCornerShape(14.dp),color=MaterialTheme.colorScheme.primaryContainer) {
                    Box(Modifier.size(46.dp),contentAlignment=Alignment.Center) { AgentIcon(AgentGlyph.SHIELD,color=MaterialTheme.colorScheme.primary) }
                }
                Text(stringResource(R.string.approval_kicker),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                Text(label.title,fontSize=22.sp,fontWeight=FontWeight.Medium)
                Text(label.description,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                Surface(shape=RoundedCornerShape(12.dp),color=MaterialTheme.colorScheme.background,border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)) {
                    Column(Modifier.fillMaxWidth().padding(14.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                        Text(stringResource(R.string.bound_action),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(request.name,fontFamily=FontFamily.Monospace,style=MaterialTheme.typography.bodySmall)
                        Text(request.arguments,fontFamily=FontFamily.Monospace,style=MaterialTheme.typography.bodySmall)
                    }
                }
                Text(stringResource(R.string.approval_expiry,seconds),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                SecureActionButton(stringResource(R.string.approve_once),seconds>0,primary=true) { onDecision(true) }
                SecureActionButton(stringResource(R.string.deny),enabled=true,primary=false) { onDecision(false) }
            }
        }
    }
}

/** Keep native obscured-touch filtering on the actual confirmation controls. */
@Composable
private fun SecureActionButton(label: String, enabled: Boolean, primary: Boolean, onClick:()->Unit) {
    val background=if(primary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
    val foreground=if(primary) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    AndroidView(modifier=Modifier.fillMaxWidth().height(48.dp),factory={ context ->
        android.widget.Button(context).apply {
            isAllCaps=false;textSize=14f;typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL)
            filterTouchesWhenObscured=true
            setOnTouchListener { _, event ->
                event.flags and (MotionEvent.FLAG_WINDOW_IS_OBSCURED or MotionEvent.FLAG_WINDOW_IS_PARTIALLY_OBSCURED) != 0
            }
        }
    },update={ view ->
        view.text=label;view.isEnabled=enabled;view.setTextColor(foreground.toArgb())
        view.alpha=if(enabled) 1f else .45f
        view.background=GradientDrawable().apply { cornerRadius=12*view.resources.displayMetrics.density;setColor(background.toArgb()) }
        view.setOnClickListener { onClick() }
    })
}
