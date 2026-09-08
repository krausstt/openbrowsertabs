package io.github.krausstt.openbrowsertabs

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import io.github.krausstt.openbrowsertabs.core.Headline
import io.github.krausstt.openbrowsertabs.core.LinkParser
import io.github.krausstt.openbrowsertabs.core.LinkResolution
import io.github.krausstt.openbrowsertabs.data.LinkEntity
import io.github.krausstt.openbrowsertabs.data.LinkStore
import io.github.krausstt.openbrowsertabs.enrich.EnrichmentWorker
import io.github.krausstt.openbrowsertabs.ui.OpenTabsTheme
import io.github.krausstt.openbrowsertabs.ui.SaveSheet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Share-sheet target.
 *
 * The order of operations is the design: **store first, ask second.** A
 * single shared link is written to the database before any UI exists, and
 * only then does the save sheet appear to ask why it was kept. The question
 * can therefore never cost a save — closing the sheet, backing out or
 * killing the app all leave the entry exactly as it was before this screen
 * existed, minus the context.
 *
 * Bulk shares (a URL-list export, several tabs at once) skip the sheet: one
 * reaction cannot mean anything about forty links, and asking forty times is
 * the opposite of low-threshold. Those drain through the background queue as
 * before and surface later in the inbox stack.
 */
class ShareReceiverActivity : ComponentActivity() {

    // guard against accidentally shared huge files
    private val maxFileChars = 5_000_000

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val subject = intent?.getStringExtra(Intent.EXTRA_SUBJECT)

        lifecycleScope.launch(Dispatchers.IO) {
            val text = intent?.getStringExtra(Intent.EXTRA_TEXT)
                ?.takeIf { it.isNotBlank() }
                ?: readSharedTextFile()
            val links = LinkParser.parse(text).map { LinkResolution.resolveIfShortened(it) }

            if (links.isEmpty()) {
                withContext(Dispatchers.Main) {
                    toastAndFinish("Kein Link im geteilten Inhalt gefunden")
                }
                return@launch
            }

            val store = LinkStore(this@ShareReceiverActivity)
            // the share-sheet subject is the page title when a single link is shared
            val title = if (links.size == 1) subject?.takeIf { it.isNotBlank() } else null
            var added = 0
            links.forEach { if (store.upsertSighting(it, title)) added++ }

            // context-on-sight: enrich a single share right away (with result
            // notification); bulk shares drain in the background queue
            val single: LinkEntity? =
                if (links.size == 1) store.byCanonicalUrl(links[0].canonicalUrl) else null
            if (single != null) {
                EnrichmentWorker.enqueueForLink(this@ShareReceiverActivity, single.id)
            } else {
                EnrichmentWorker.enqueueDrain(this@ShareReceiverActivity)
            }

            withContext(Dispatchers.Main) {
                if (single == null) {
                    toastAndFinish(
                        "${links.size} Links verarbeitet, $added neu, " +
                            "${links.size - added} als Sichtung gezählt",
                    )
                } else {
                    showSheet(store, single)
                }
            }
        }
    }

    private fun showSheet(store: LinkStore, link: LinkEntity) {
        setContent {
            OpenTabsTheme {
                SaveSheet(
                    host = link.host,
                    title = Headline.best(link.title, link.canonicalUrl, link.host),
                    sightings = link.nSightings,
                    onReact = { id -> persist(store) { it.setReaction(link.id, id) } },
                    onNote = { note -> persist(store) { it.setUserNote(link.id, note) } },
                    onDismiss = { finish() },
                )
            }
        }
    }

    /**
     * Write, then leave immediately.
     *
     * Deliberately *not* on [lifecycleScope]: this activity is `noHistory`
     * and finishes in the same breath, which cancels that scope and would
     * race the write away. [writeScope] outlives the activity so the tap is
     * never silently lost — the whole promise of the sheet.
     */
    private fun persist(store: LinkStore, block: (LinkStore) -> Unit) {
        writeScope.launch { block(store) }
        Toast.makeText(this, "Kontext gemerkt", Toast.LENGTH_SHORT).show()
        finish()
    }

    private companion object {
        val writeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    private fun toastAndFinish(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        finish()
    }

    /** Read a shared text file (EXTRA_STREAM content URI), e.g. a URL-list export. */
    private fun readSharedTextFile(): String {
        val uri: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }
        if (uri == null) return ""
        return runCatching {
            contentResolver.openInputStream(uri)?.use { input ->
                val text = input.bufferedReader().readText()
                if (text.length > maxFileChars) text.take(maxFileChars) else text
            }.orEmpty()
        }.getOrDefault("")
    }
}
