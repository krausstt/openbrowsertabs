package io.github.krausstt.openbrowsertabs

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import io.github.krausstt.openbrowsertabs.core.Clustering
import io.github.krausstt.openbrowsertabs.core.Headline
import io.github.krausstt.openbrowsertabs.data.LinkEntity
import io.github.krausstt.openbrowsertabs.enrich.EnrichmentWorker
import io.github.krausstt.openbrowsertabs.ui.BrowseScreen
import io.github.krausstt.openbrowsertabs.ui.InboxScreen
import io.github.krausstt.openbrowsertabs.ui.MonogramTile
import io.github.krausstt.openbrowsertabs.ui.NavIcon
import io.github.krausstt.openbrowsertabs.ui.OpenTabsTheme
import io.github.krausstt.openbrowsertabs.ui.ReviewScreen
import io.github.krausstt.openbrowsertabs.ui.SearchScreen
import io.github.krausstt.openbrowsertabs.ui.SpacesScreen
import io.github.krausstt.openbrowsertabs.ui.TagChip

class MainActivity : ComponentActivity() {

    private val route = mutableStateOf<Route>(Route.None)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EnrichmentWorker.ensureChannel(this)
        route.value = routeFrom(intent)
        setContent {
            OpenTabsTheme {
                LinksScreen(route = route.value, onRouteHandled = { route.value = Route.None })
            }
        }
    }

    // singleTask: a deep link while the app is already open arrives here
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        route.value = routeFrom(intent)
    }

    private fun routeFrom(intent: Intent?): Route {
        if (intent == null) return Route.None
        intent.data?.takeIf { it.scheme == "openbrowsertabs" && it.host == "link" }
            ?.lastPathSegment?.toLongOrNull()
            ?.let { return Route.OpenLink(it) }
        if (intent.action == Intent.ACTION_PROCESS_TEXT) {
            val text = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
                ?: intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT_READONLY)?.toString()
            if (!text.isNullOrBlank()) return Route.AttachSummary(text)
        }
        return Route.None
    }
}

/** Where an incoming intent wants to land. */
sealed interface Route {
    data object None : Route
    data class OpenLink(val id: Long) : Route
    data class AttachSummary(val text: String) : Route
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LinksScreen(
    vm: LinksViewModel = viewModel(),
    route: Route = Route.None,
    onRouteHandled: () -> Unit = {},
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var showImport by remember { mutableStateOf(false) }
    var showExport by remember { mutableStateOf(false) }
    var selectedLink by remember { mutableStateOf<LinkEntity?>(null) }
    val scope = rememberCoroutineScope()

    // deep link / shared-text routing
    LaunchedEffect(route) {
        when (route) {
            is Route.OpenLink -> {
                vm.linkById(route.id)?.let { selectedLink = it }
                onRouteHandled()
            }
            is Route.AttachSummary -> {
                vm.setPendingSummary(route.text)
                onRouteHandled()
            }
            Route.None -> Unit
        }
    }

    // enrichment results arrive as notifications — ask once on Android 13+
    val notifPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // pick up links saved via the share sheet while the app was backgrounded
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) vm.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            vm.consumeMessage()
        }
    }

    val openLink: (LinkEntity) -> Unit = { selectedLink = it }
    val jumpToTag: (String) -> Unit = { tag ->
        vm.toggleTag(tag)
        vm.setTab(Tab.BROWSE)
    }

    // a running session takes over the whole screen: one entry, no list,
    // no navigation bar competing for attention
    state.session?.let { session ->
        ReviewScreen(
            session = session,
            curatedTotal = state.curatedTotal,
            onCommit = vm::commitCurrent,
            onSkip = vm::skipCurrent,
            onArchive = vm::archiveCurrent,
            onOpenUrl = { url ->
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            },
            onEnd = vm::endSession,
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (state.tab) {
                            Tab.BROWSE -> "Alle Links"
                            Tab.SPACES -> "Open Tabs"
                            Tab.SEARCH -> "Suche"
                            Tab.INBOX -> "Braucht Aufmerksamkeit"
                        },
                        fontWeight = FontWeight.Bold,
                    )
                },
                actions = {
                    TextButton(onClick = { showExport = true }) { Text("Export") }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = state.tab == Tab.SPACES,
                    onClick = { vm.setTab(Tab.SPACES) },
                    icon = { NavIcon("🗂", state.tab == Tab.SPACES) },
                    label = { Text("Spaces") },
                )
                NavigationBarItem(
                    selected = state.tab == Tab.BROWSE,
                    onClick = { vm.setTab(Tab.BROWSE) },
                    icon = { NavIcon("📚", state.tab == Tab.BROWSE) },
                    label = { Text("Alle") },
                )
                NavigationBarItem(
                    selected = state.tab == Tab.SEARCH,
                    onClick = { vm.setTab(Tab.SEARCH) },
                    icon = { NavIcon("🔍", state.tab == Tab.SEARCH) },
                    label = { Text("Suche") },
                )
                NavigationBarItem(
                    selected = state.tab == Tab.INBOX,
                    onClick = { vm.setTab(Tab.INBOX) },
                    icon = {
                        BadgedBox(
                            badge = {
                                if (state.inboxCount > 0) Badge { Text("${state.inboxCount}") }
                            },
                        ) { NavIcon("📥", state.tab == Tab.INBOX) }
                    },
                    label = { Text("Inbox") },
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showImport = true }) {
                Icon(Icons.Default.Add, contentDescription = "Links hinzufügen")
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when (state.tab) {
                Tab.SPACES -> SpacesScreen(
                    state = state,
                    onOpenSpace = vm::openSpace,
                    onOpenLink = openLink,
                    onToggleTag = jumpToTag,
                    onAttention = { mode ->
                        vm.setAttentionMode(mode)
                        vm.setTab(Tab.INBOX)
                    },
                    onTogglePin = vm::togglePin,
                )
                Tab.BROWSE -> BrowseScreen(
                    state = state,
                    onOpen = openLink,
                    onToggleTag = vm::toggleTag,
                    onClearTags = vm::clearTags,
                    onStatus = vm::setStatusFilter,
                )
                Tab.SEARCH -> SearchScreen(
                    state = state,
                    onQuery = vm::setQuery,
                    onOpen = openLink,
                    onToggleTag = jumpToTag,
                )
                Tab.INBOX -> InboxScreen(
                    state = state,
                    onMode = vm::setAttentionMode,
                    onOpen = openLink,
                    onToggleTag = jumpToTag,
                    onStartSession = vm::startSession,
                )
            }
        }
    }

    if (showImport) {
        ImportDialog(
            onDismiss = { showImport = false },
            onImport = { text ->
                showImport = false
                vm.addFromText(text)
            },
        )
    }

    if (showExport) {
        ExportDialog(
            onDismiss = { showExport = false },
            onPick = { kind ->
                showExport = false
                scope.launch {
                    val file = vm.runExport(kind)
                    if (file == null) {
                        vm.report("Nichts zu exportieren")
                    } else {
                        vm.report("Gespeichert: ${file.name}")
                        shareExport(context, file)
                    }
                }
            },
        )
    }

    state.pendingSummary?.let { text ->
        AttachSummaryDialog(
            text = text,
            loadCandidates = { vm.recentLinks() },
            onDismiss = { vm.setPendingSummary(null) },
            onPick = { target ->
                vm.setSummary(target, text)
                vm.setPendingSummary(null)
            },
        )
    }

    selectedLink?.let { link ->
        var related by remember(link.id) { mutableStateOf<List<LinkEntity>>(emptyList()) }
        LaunchedEffect(link.id) { related = vm.relatedFor(link) }
        // re-read from state so tag edits are reflected without reopening
        val fresh = state.links.firstOrNull { it.id == link.id }
            ?: state.spaceLinks.firstOrNull { it.id == link.id }
            ?: state.attentionLinks.firstOrNull { it.id == link.id }
            ?: link
        LinkDetailDialog(
            link = fresh,
            related = related,
            onRelatedClick = { selectedLink = it },
            onDismiss = { selectedLink = null },
            onAddTag = { vm.addUserTag(fresh, it) },
            onRemoveTag = { vm.removeUserTag(fresh, it) },
            onSaveSummary = { vm.setSummary(fresh, it) },
            onArchive = {
                vm.archive(fresh.id)
                selectedLink = null
            },
            onRestore = {
                vm.restore(fresh.id)
                selectedLink = null
            },
            onDelete = {
                vm.delete(fresh.id)
                selectedLink = null
            },
            onOpen = {
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(fresh.canonicalUrl)))
                }
                selectedLink = null
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LinkDetailDialog(
    link: LinkEntity,
    related: List<LinkEntity>,
    onRelatedClick: (LinkEntity) -> Unit,
    onDismiss: () -> Unit,
    onAddTag: (String) -> Unit,
    onRemoveTag: (String) -> Unit,
    onSaveSummary: (String?) -> Unit,
    onArchive: () -> Unit,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
    onOpen: () -> Unit,
) {
    var newTag by remember { mutableStateOf("") }
    var editingSummary by remember(link.id) { mutableStateOf(false) }
    var summaryDraft by remember(link.id) { mutableStateOf(link.userSummary.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MonogramTile(link.host, size = 38)
                Text(
                    text = link.label?.let { "🔍 $it" }
                        ?: link.title?.let { Headline.shortHeadline(it, link.canonicalUrl) }
                        ?: link.host,
                    modifier = Modifier.padding(start = 10.dp),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                // a hand-written summary replaces the scraped text entirely —
                // it is the better source and the reason the field exists
                if (editingSummary) {
                    OutlinedTextField(
                        value = summaryDraft,
                        onValueChange = { summaryDraft = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Eigene Zusammenfassung") },
                        minLines = 4,
                        maxLines = 12,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = {
                            onSaveSummary(summaryDraft)
                            editingSummary = false
                        }) { Text("Speichern") }
                        TextButton(onClick = {
                            summaryDraft = link.userSummary.orEmpty()
                            editingSummary = false
                        }) { Text("Abbrechen") }
                        if (!link.userSummary.isNullOrBlank()) {
                            TextButton(onClick = {
                                onSaveSummary(null)
                                summaryDraft = ""
                                editingSummary = false
                            }) { Text("Löschen") }
                        }
                    }
                } else if (!link.userSummary.isNullOrBlank()) {
                    Text(
                        "Deine Zusammenfassung",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(link.userSummary, style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = { editingSummary = true }) { Text("Bearbeiten") }
                } else {
                    link.description?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 6)
                    }
                    TextButton(onClick = { editingSummary = true }) {
                        Text("+ Zusammenfassung hinzufügen")
                    }
                }

                Text(
                    "Tags",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    link.allTags.forEach { tag ->
                        // hand-added tags are highlighted and removable;
                        // derived ones are shown but not editable here
                        TagChip(
                            tag = tag,
                            selected = tag in link.userTags,
                            onClick = { if (tag in link.userTags) onRemoveTag(tag) },
                        )
                    }
                    if (link.allTags.isEmpty()) {
                        Text(
                            "noch keine",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newTag,
                        onValueChange = { newTag = it },
                        modifier = Modifier.fillMaxWidth(0.72f),
                        placeholder = { Text("Tag hinzufügen") },
                        singleLine = true,
                    )
                    TextButton(
                        onClick = {
                            onAddTag(newTag)
                            newTag = ""
                        },
                        enabled = newTag.isNotBlank(),
                    ) { Text("Hinzufügen") }
                }

                if (related.isNotEmpty()) {
                    Text(
                        "Hängt zusammen mit",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    related.forEach { r ->
                        Text(
                            text = "🔗 " + (r.label ?: r.title ?: r.host),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 2,
                            modifier = Modifier.clickable { onRelatedClick(r) },
                        )
                    }
                }

                Text(
                    link.canonicalUrl,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (link.status == "open") {
                        TextButton(onClick = onArchive) { Text("Archivieren") }
                    } else {
                        TextButton(onClick = onRestore) { Text("Zurückholen") }
                        TextButton(onClick = onDelete) { Text("Löschen") }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onOpen) { Text("Öffnen") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Schließen") } },
    )
}

/**
 * Text selected in another app (an LLM answer, a note) arrives here via
 * ACTION_PROCESS_TEXT; pick which saved link it summarises.
 */
@Composable
private fun AttachSummaryDialog(
    text: String,
    loadCandidates: suspend () -> List<LinkEntity>,
    onDismiss: () -> Unit,
    onPick: (LinkEntity) -> Unit,
) {
    var candidates by remember { mutableStateOf<List<LinkEntity>>(emptyList()) }
    var filter by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { candidates = loadCandidates() }

    val shown = remember(candidates, filter) {
        if (filter.isBlank()) candidates
        else candidates.filter {
            (it.title.orEmpty() + " " + it.host).contains(filter, ignoreCase = true)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Zusammenfassung zuordnen") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text.take(160).let { if (text.length > 160) "$it …" else it },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = filter,
                    onValueChange = { filter = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Link suchen …") },
                    singleLine = true,
                )
                LazyColumn(
                    modifier = Modifier.heightIn(max = 320.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(shown, key = { it.id }) { candidate ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick(candidate) }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            MonogramTile(candidate.host, size = 32)
                            Column(modifier = Modifier.padding(start = 10.dp)) {
                                Text(
                                    candidate.title?.let {
                                        Headline.shortHeadline(it, candidate.canonicalUrl)
                                    } ?: candidate.host,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                )
                                Text(
                                    candidate.host,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

@Composable
private fun ExportDialog(onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val options = listOf(
        "notebooklm" to ("NotebookLM-Bundle" to
            "Eine Textdatei pro Cluster, nur URLs, max. ${Clustering.MAX_MEMBERS} pro Datei"),
        "markdown" to ("Digest als Markdown" to "Alle Cluster mit Titeln und Notizen"),
        "pdf" to ("Digest als PDF" to "Dasselbe, druck- und teilbar"),
        "interchange" to ("Cloud-Interchange (JSONL)" to
            "Rohdaten für den Batch-Job: Embeddings und Clustering"),
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Exportieren") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                options.forEach { (kind, texts) ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(kind) }
                            .padding(vertical = 10.dp),
                    ) {
                        Text(texts.first, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            texts.second,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

/** Hand the finished export to the share sheet via the app's FileProvider. */
private fun shareExport(context: android.content.Context, file: java.io.File) {
    runCatching {
        // a directory export (NotebookLM bundle) has nothing single to share
        if (file.isDirectory) return
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", file,
        )
        val mime = when (file.extension) {
            "pdf" -> "application/pdf"
            "md" -> "text/markdown"
            else -> "text/plain"
        }
        context.startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = mime
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                },
                "Export teilen",
            ),
        )
    }
}

@Composable
private fun ImportDialog(onDismiss: () -> Unit, onImport: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Links importieren") },
        text = {
            Column {
                Text("Text mit einer oder mehreren URLs einfügen — Duplikate werden als Sichtung gezählt.")
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    minLines = 4,
                    maxLines = 10,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onImport(text) }, enabled = text.isNotBlank()) {
                Text("Importieren")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        },
    )
}
