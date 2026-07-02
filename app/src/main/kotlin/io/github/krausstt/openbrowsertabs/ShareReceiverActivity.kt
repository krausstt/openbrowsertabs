package io.github.krausstt.openbrowsertabs

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import io.github.krausstt.openbrowsertabs.core.LinkParser
import io.github.krausstt.openbrowsertabs.data.LinkStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Invisible share-sheet target: saves the shared link(s) immediately and
 * finishes. Accepts both plain shared text (EXTRA_TEXT, e.g. a browser tab)
 * and shared text files (EXTRA_STREAM, e.g. a numbered URL-list export).
 * Enrichment happens later in the background (Phase 2).
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
            val links = LinkParser.parse(text)

            if (links.isEmpty()) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@ShareReceiverActivity,
                        "Kein Link im geteilten Inhalt gefunden",
                        Toast.LENGTH_SHORT,
                    ).show()
                    finish()
                }
                return@launch
            }

            val store = LinkStore(this@ShareReceiverActivity)
            // the share-sheet subject is the page title when a single link is shared
            val title = if (links.size == 1) subject?.takeIf { it.isNotBlank() } else null
            var added = 0
            links.forEach { if (store.upsertSighting(it, title)) added++ }

            withContext(Dispatchers.Main) {
                val msg = when {
                    links.size == 1 && added == 1 -> "Gespeichert: ${links[0].host}"
                    links.size == 1 -> "Schon bekannt — Sichtung gezählt: ${links[0].host}"
                    else -> "${links.size} Links verarbeitet, $added neu, " +
                        "${links.size - added} als Sichtung gezählt"
                }
                Toast.makeText(this@ShareReceiverActivity, msg, Toast.LENGTH_LONG).show()
                finish()
            }
        }
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
