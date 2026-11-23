package app.navi.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DepartureBoard
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.navi.Api
import app.navi.AssistantLink
import app.navi.ChatMessage
import app.navi.JourneyForm
import app.navi.Location
import app.navi.Nav
import app.navi.R
import app.navi.currentPosition
import app.navi.hasLocationPermission
import kotlinx.coroutines.launch

private data class Turn(val msg: ChatMessage, val links: List<AssistantLink> = emptyList(), val error: Boolean = false)

/** Conversation survives leaving the screen (e.g. opening a board from an answer) until the app closes. */
private object AssistantChat {
    val turns = mutableStateListOf<Turn>()
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)
@Composable
fun AssistantScreen(nav: Nav) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val turns = AssistantChat.turns
    var input by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val list = rememberLazyListState()
    val lang = LocalConfiguration.current.locales[0].language.let { if (it == "de") "de" else "en" }
    val failed = stringResource(R.string.ask_failed)

    fun send(text: String) {
        val q = text.trim().take(600)
        if (q.isEmpty() || busy) return
        input = ""
        turns += Turn(ChatMessage("user", q))
        busy = true
        scope.launch {
            val pos = if (hasLocationPermission(ctx)) currentPosition(ctx) else null
            val history = turns.filterNot { it.error }.map { it.msg }
            try {
                val r = Api.ask(history, lang, pos?.latitude, pos?.longitude)
                turns += Turn(ChatMessage("assistant", r.answer), r.links)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                turns += Turn(ChatMessage("assistant", e.message ?: failed), error = true)
            } finally {
                busy = false
            }
        }
    }
    LaunchedEffect(turns.size, busy) { if (turns.isNotEmpty()) list.animateScrollToItem(turns.size + if (busy) 1 else 0) }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = nav::back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
                title = {
                    Column {
                        Text(stringResource(R.string.ask_title))
                        Text(stringResource(R.string.ask_subtitle), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
            )
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(top = pad.calculateTopPadding()).imePadding().navigationBarsPadding()) {
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = list, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier.size(96.dp).clip(MaterialShapes.Cookie7Sided.toShape()).background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Filled.AutoAwesome, null, Modifier.size(44.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer) }
                        Spacer(Modifier.size(12.dp))
                        Text(stringResource(R.string.ask_intro), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (turns.isEmpty()) {
                            Spacer(Modifier.size(16.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(R.string.ask_s1, R.string.ask_s2, R.string.ask_s3).forEach { id ->
                                    val s = stringResource(id)
                                    SuggestionChip(onClick = { send(s) }, label = { Text(s) })
                                }
                            }
                        }
                    }
                }
                itemsIndexed(turns) { _, t -> Bubble(t, nav) }
                if (busy) item {
                    Row(Modifier.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        LoadingIndicator(Modifier.size(40.dp))
                        Spacer(Modifier.size(8.dp))
                        Text(stringResource(R.string.ask_thinking), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it.take(600) },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(stringResource(R.string.ask_hint)) },
                    shape = RoundedCornerShape(28.dp),
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { send(input) }),
                )
                Spacer(Modifier.size(8.dp))
                FilledIconButton(onClick = { send(input) }, enabled = input.isNotBlank() && !busy, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.AutoMirrored.Filled.Send, stringResource(R.string.ask_send))
                }
            }
            Text(
                stringResource(R.string.ask_disclaimer),
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 8.dp),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Bubble(t: Turn, nav: Nav) {
    val mine = t.msg.role == "user"
    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
        Text(
            t.msg.content,
            Modifier
                .widthIn(max = 320.dp)
                .clip(if (mine) RoundedCornerShape(24.dp, 24.dp, 6.dp, 24.dp) else RoundedCornerShape(24.dp, 24.dp, 24.dp, 6.dp))
                .background(
                    when {
                        mine -> MaterialTheme.colorScheme.primaryContainer
                        t.error -> MaterialTheme.colorScheme.errorContainer
                        else -> MaterialTheme.colorScheme.surfaceContainerHigh
                    },
                )
                .padding(horizontal = 16.dp, vertical = 12.dp),
            color = when {
                mine -> MaterialTheme.colorScheme.onPrimaryContainer
                t.error -> MaterialTheme.colorScheme.onErrorContainer
                else -> MaterialTheme.colorScheme.onSurface
            },
            style = MaterialTheme.typography.bodyLarge,
        )
        if (t.links.isNotEmpty()) FlowRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            t.links.distinctBy { it.kind + it.stopId + it.from + it.to }.forEach { l ->
                when (l.kind) {
                    "board" -> AssistChip(
                        onClick = { l.stopId?.let { nav.board(Location(it, "STOP", l.label, lat = 0.0, lon = 0.0)) } },
                        label = { Text(stringResource(R.string.ask_open_board, l.label)) },
                        leadingIcon = { Icon(Icons.Filled.DepartureBoard, null, Modifier.size(18.dp)) },
                    )
                    "plan" -> AssistChip(
                        onClick = {
                            val (fromName, toName) = l.label.split(" → ").let { it.getOrElse(0) { "?" } to it.getOrElse(1) { "?" } }
                            val f = l.from ?: return@AssistChip
                            val to = l.to ?: return@AssistChip
                            JourneyForm.from = Location(f, if (f.contains(',')) "GPS" else "STOP", fromName, lat = 0.0, lon = 0.0)
                            JourneyForm.to = Location(to, if (to.contains(',')) "PLACE" else "STOP", toName, lat = 0.0, lon = 0.0)
                            nav.results()
                        },
                        label = { Text(stringResource(R.string.ask_open_plan)) },
                        leadingIcon = { Icon(Icons.Filled.Route, null, Modifier.size(18.dp)) },
                    )
                }
            }
        }
    }
}
