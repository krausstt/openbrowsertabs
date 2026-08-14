package io.github.krausstt.openbrowsertabs.export

import android.content.Context
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import io.github.krausstt.openbrowsertabs.CATEGORY_NAMES
import io.github.krausstt.openbrowsertabs.TOPIC_NAMES
import io.github.krausstt.openbrowsertabs.core.Clustering
import io.github.krausstt.openbrowsertabs.core.Headline
import io.github.krausstt.openbrowsertabs.data.LinkEntity
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * File exports. Everything lands in the app's own external files directory,
 * so nothing leaves the device unless the user shares it deliberately.
 */
class Exporter(private val context: Context) {

    private val stamp: String
        get() = SimpleDateFormat("yyyy-MM-dd-HHmm", Locale.GERMANY).format(Date())

    private fun outDir(name: String): File =
        File(context.getExternalFilesDir(null), name).apply { mkdirs() }

    // ------------------------------------------------------- NotebookLM
    /**
     * One plain-text file per cluster: nothing but URLs, one per line.
     *
     * NotebookLM ingests a pasted URL list directly, and its citation quality
     * degrades well before the nominal source cap, so clusters stay at or
     * below [Clustering.MAX_MEMBERS] rather than filling a notebook.
     */
    fun writeNotebookLmBundle(
        links: List<LinkEntity>,
        clusters: List<Clustering.Cluster>,
    ): File {
        val byId = links.associateBy { it.id }
        val dir = outDir("notebooklm/$stamp")
        clusters.forEachIndexed { index, cluster ->
            val urls = cluster.members.mapNotNull { byId[it]?.canonicalUrl }
            if (urls.isEmpty()) return@forEachIndexed
            val safe = cluster.label.replace(Regex("[^\\p{L}\\p{N}]+"), "-").trim('-').lowercase()
            File(dir, "%02d-%s.txt".format(index + 1, safe.ifBlank { "cluster" }))
                .writeText(urls.joinToString("\n") + "\n")
        }
        File(dir, "README.txt").writeText(
            buildString {
                appendLine("OpenBrowserTabs — NotebookLM-Export vom $stamp")
                appendLine()
                appendLine("Eine Datei pro Themencluster, jeweils nur URLs (eine pro Zeile).")
                appendLine("In NotebookLM: neues Notebook pro Datei, Inhalt einfügen.")
                appendLine("Cluster sind auf ${Clustering.MAX_MEMBERS} Quellen begrenzt.")
                appendLine()
                clusters.forEach { appendLine("- ${it.label}: ${it.members.size} URLs") }
            },
        )
        return dir
    }

    // ------------------------------------------------------------ digest
    fun writeDigestMarkdown(
        links: List<LinkEntity>,
        clusters: List<Clustering.Cluster>,
        title: String = "Wochen-Digest",
    ): File {
        val byId = links.associateBy { it.id }
        val file = File(outDir("digest"), "digest-$stamp.md")
        file.writeText(
            buildString {
                appendLine("# $title")
                appendLine()
                appendLine("_${links.size} Einträge · ${clusters.size} Cluster · ${stamp}_")
                appendLine()
                clusters.forEach { cluster ->
                    appendLine("## ${cluster.label}")
                    appendLine()
                    cluster.members.mapNotNull { byId[it] }.forEach { link ->
                        val name = link.title?.let { Headline.shortHeadline(it, link.canonicalUrl) }
                            ?: link.host
                        appendLine("- [$name](${link.canonicalUrl}) · ${link.host}")
                        val note = link.userSummary ?: link.description
                        if (!note.isNullOrBlank()) {
                            appendLine("  > ${note.replace("\n", " ").take(220)}")
                        }
                    }
                    appendLine()
                }
            },
        )
        return file
    }

    /**
     * PDF via the platform's own PdfDocument — no dependency, no rendering
     * engine, and it works offline. Layout is deliberately plain text flow.
     */
    fun writeDigestPdf(
        links: List<LinkEntity>,
        clusters: List<Clustering.Cluster>,
        title: String = "Wochen-Digest",
    ): File {
        val byId = links.associateBy { it.id }
        val pageWidth = 595   // A4 at 72 dpi
        val pageHeight = 842
        val margin = 42f
        val doc = PdfDocument()

        val h1 = Paint().apply { textSize = 20f; isFakeBoldText = true }
        val h2 = Paint().apply { textSize = 14f; isFakeBoldText = true }
        val body = Paint().apply { textSize = 10f }
        val meta = Paint().apply { textSize = 8.5f; setARGB(255, 110, 110, 110) }

        var pageNumber = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
        var canvas = page.canvas
        var y = margin + 10f

        fun newPage() {
            doc.finishPage(page)
            pageNumber++
            page = doc.startPage(
                PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create(),
            )
            canvas = page.canvas
            y = margin
        }

        fun line(text: String, paint: Paint, indent: Float = 0f, gap: Float = 4f) {
            val maxWidth = pageWidth - 2 * margin - indent
            // manual wrapping keeps this independent of StaticLayout/TextView
            var remaining = text
            while (remaining.isNotEmpty()) {
                val count = paint.breakText(remaining, true, maxWidth, null)
                val cut = if (count >= remaining.length) remaining.length else
                    remaining.lastIndexOf(' ', count).takeIf { it > 0 } ?: count
                if (y > pageHeight - margin) newPage()
                canvas.drawText(remaining.substring(0, cut).trim(), margin + indent, y, paint)
                y += paint.textSize + gap
                remaining = remaining.substring(cut).trimStart()
            }
        }

        line(title, h1, gap = 8f)
        line("${links.size} Einträge · ${clusters.size} Cluster · $stamp", meta, gap = 12f)

        clusters.forEach { cluster ->
            if (y > pageHeight - margin - 60) newPage()
            y += 6f
            line(cluster.label, h2, gap = 6f)
            cluster.members.mapNotNull { byId[it] }.forEach { link ->
                val name = link.title?.let { Headline.shortHeadline(it, link.canonicalUrl) }
                    ?: link.host
                line("• $name", body, indent = 6f, gap = 2f)
                line(link.canonicalUrl, meta, indent = 14f, gap = 2f)
                val note = link.userSummary ?: link.description
                if (!note.isNullOrBlank()) {
                    line(note.replace("\n", " ").take(220), meta, indent = 14f)
                }
            }
        }

        doc.finishPage(page)
        val file = File(outDir("digest"), "digest-$stamp.pdf")
        file.outputStream().use { doc.writeTo(it) }
        doc.close()
        return file
    }

    // ------------------------------------------------- cloud interchange
    /**
     * JSONL for the batch pipeline, matching
     * docs/design/2026-08-09-graph-schema.md. One line per record so the
     * cloud side can stream it; ids only, never device identifiers.
     */
    fun writeInterchange(links: List<LinkEntity>, contentFor: (Long) -> String?): File {
        val file = File(outDir("interchange"), "export-$stamp.jsonl")
        file.bufferedWriter().use { out ->
            out.write(
                JSONObject()
                    .put("type", "meta")
                    .put("schema", 6)
                    .put("exported_at", System.currentTimeMillis())
                    .put("count", links.size)
                    .toString(),
            )
            out.newLine()
            links.forEach { link ->
                val node = JSONObject()
                    .put("type", "node")
                    .put("id", link.id)
                    .put("kind", "link")
                    .put("key", link.canonicalUrl)
                    .put("label", link.title ?: link.label ?: link.host)
                    .put("host", link.host)
                    .put("category", link.category)
                    .put("tags", JSONArray(link.allTags))
                    .put("saved_at", link.firstSeenAt)
                link.userSummary?.let { node.put("summary", it) }
                link.description?.let { node.put("description", it) }
                contentFor(link.id)?.take(8000)?.let { node.put("text", it) }
                out.write(node.toString())
                out.newLine()
            }
        }
        return file
    }

    /** Human-facing labels for cluster keys, shared by all export paths. */
    fun labelFor(key: String): String =
        TOPIC_NAMES[key] ?: CATEGORY_NAMES[key] ?: key
}
