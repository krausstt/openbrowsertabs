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
import androidx.compose.foundation.layout.padding
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
import io.github.krausstt.openbrowsertabs.core.Headline
import io.github.krausstt.openbrowsertabs.data.LinkEntity
import io.github.krausstt.openbrowsertabs.enrich.EnrichmentWorker
import io.github.krausstt.openbrowsertabs.ui.BrowseScreen
import io.github.krausstt.openbrowsertabs.ui.InboxScreen
import io.github.krausstt.openbrowsertabs.ui.MonogramTile
import io.github.krausstt.openbrowsertabs.ui.NavIcon
import io.github.krausstt.openbrowsertabs.ui.OpenTabsTheme
import io.github.krausstt.openbrowsertabs.ui.SearchScreen
import io.github.krausstt.openbrowsertabs.ui.SpacesScreen
import io.github.krausstt.openbrowsertabs.ui.TagChip

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EnrichmentWorker.ensureChannel(this)
        setContent {
            OpenTabsTheme {
                LinksScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LinksScreen(vm: LinksViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var showImport by remember { mutableStateOf(false) }
    var selectedLink by remember { mutableStateOf<LinkEntity?>(null) }

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
    onArchive: () -> Unit,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
    onOpen: () -> Unit,
) {
    var newTag by remember { mutableStateOf("") }
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
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                link.description?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 6)
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
