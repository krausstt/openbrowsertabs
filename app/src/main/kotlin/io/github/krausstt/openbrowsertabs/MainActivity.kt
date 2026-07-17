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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import io.github.krausstt.openbrowsertabs.data.LinkEntity
import io.github.krausstt.openbrowsertabs.enrich.EnrichmentWorker

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EnrichmentWorker.ensureChannel(this)
        setContent {
            MaterialTheme {
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("OpenBrowserTabs") },
                actions = {
                    IconButton(onClick = { showImport = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Links importieren")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp),
        ) {
            OutlinedTextField(
                value = state.query,
                onValueChange = vm::setQuery,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("URL, Titel oder Suchbegriff filtern …") },
                singleLine = true,
            )

            Row(
                modifier = Modifier.padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf("open" to "Offen", "archived" to "Archiv", "all" to "Alle").forEach { (key, name) ->
                    FilterChip(
                        selected = state.statusFilter == key,
                        onClick = { vm.setStatusFilter(key) },
                        label = { Text(name) },
                    )
                }
            }

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.categoryCounts.entries.sortedByDescending { it.value }.toList()) { (cat, n) ->
                    FilterChip(
                        selected = state.categoryFilter == cat,
                        onClick = { vm.setCategoryFilter(if (state.categoryFilter == cat) null else cat) },
                        label = { Text("${CATEGORY_NAMES[cat] ?: cat} $n") },
                    )
                }
            }

            Text(
                text = "${state.links.size} Links",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(vertical = 6.dp),
            )

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(state.links, key = { it.id }) { link ->
                    LinkRow(
                        link = link,
                        onOpen = { selectedLink = link },
                        onArchive = { vm.archive(link.id) },
                        onRestore = { vm.restore(link.id) },
                        onDelete = { vm.delete(link.id) },
                    )
                }
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
        LinkDetailDialog(
            link = link,
            related = related,
            onRelatedClick = { selectedLink = it },
            onDismiss = { selectedLink = null },
            onOpen = {
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link.canonicalUrl)))
                }
                selectedLink = null
            },
        )
    }
}

@Composable
private fun LinkDetailDialog(
    link: LinkEntity,
    related: List<LinkEntity>,
    onRelatedClick: (LinkEntity) -> Unit,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(link.label?.let { "🔍 $it" } ?: link.title ?: link.host)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                link.description?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium)
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
                val meta = buildList {
                    add(CATEGORY_NAMES[link.category] ?: link.category)
                    link.siteName?.let { add(it) } ?: add(link.host)
                    link.publishedAt?.take(10)?.let { add(it) }
                    if (link.nSightings > 1) add("${link.nSightings}× gesehen")
                    add(enrichmentLabel(link.enrichmentState))
                }
                Text(
                    meta.joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    link.canonicalUrl,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onOpen) { Text("Öffnen") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Schließen") }
        },
    )
}

private fun enrichmentLabel(state: String): String = when (state) {
    "done" -> "angereichert ✓"
    "unfetchable" -> "Seite nicht abrufbar"
    else -> "Anreicherung ausstehend"
}

@Composable
private fun LinkRow(
    link: LinkEntity,
    onOpen: () -> Unit,
    onArchive: () -> Unit,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            val headline = link.label?.let { "🔍 $it" }
                ?: link.title
                ?: (link.host + shortPath(link.canonicalUrl))
            Text(
                text = headline,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
            )
            val meta = buildList {
                add(CATEGORY_NAMES[link.category] ?: link.category)
                add(link.host)
                if (link.nSightings > 1) add("${link.nSightings}× gesehen")
                // quiet by default: only surface non-final enrichment states
                if (link.enrichmentState == "pending") add("⏳")
                if (link.enrichmentState == "unfetchable") add("⚠ nicht abrufbar")
            }
            Text(
                text = meta.joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (link.status == "open") {
            IconButton(onClick = onArchive) {
                Icon(Icons.Default.Done, contentDescription = "Archivieren")
            }
        } else {
            IconButton(onClick = onRestore) {
                Icon(Icons.Default.Refresh, contentDescription = "Wiederherstellen")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Endgültig löschen")
            }
        }
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

private fun shortPath(url: String): String {
    val path = url.removePrefix("https://").substringAfter('/', "")
    if (path.isEmpty()) return ""
    val decoded = runCatching { java.net.URLDecoder.decode(path, Charsets.UTF_8) }.getOrDefault(path)
    return "/" + decoded.take(60)
}
