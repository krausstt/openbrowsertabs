package io.github.krausstt.openbrowsertabs

import android.content.Intent
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
 * finishes. Enrichment happens later in the background (Phase 2).
 */
class ShareReceiverActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val text = intent?.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
        val subject = intent?.getStringExtra(Intent.EXTRA_SUBJECT)
        val links = LinkParser.parse(text)

        if (links.isEmpty()) {
            Toast.makeText(this, "Kein Link im geteilten Text gefunden", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            val store = LinkStore(this@ShareReceiverActivity)
            // the share-sheet subject is the page title when a single link is shared
            val title = if (links.size == 1) subject?.takeIf { it.isNotBlank() } else null
            var added = 0
            links.forEach { if (store.upsertSighting(it, title)) added++ }
            withContext(Dispatchers.Main) {
                val msg = when {
                    links.size == 1 && added == 1 -> "Gespeichert: ${links[0].host}"
                    links.size == 1 -> "Schon bekannt — Sichtung gezählt: ${links[0].host}"
                    else -> "${links.size} Links verarbeitet, $added neu"
                }
                Toast.makeText(this@ShareReceiverActivity, msg, Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }
}
