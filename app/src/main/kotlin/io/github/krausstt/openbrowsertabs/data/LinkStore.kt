package io.github.krausstt.openbrowsertabs.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import io.github.krausstt.openbrowsertabs.core.ParsedLink

data class LinkEntity(
    val id: Long,
    val canonicalUrl: String,
    val originalUrl: String,
    val host: String,
    val title: String?,
    val label: String?,
    val category: String,
    val topics: List<String>,
    val status: String,          // open | archived
    val nSightings: Int,
    val firstSeenAt: Long,
    val lastSeenAt: Long,
    val description: String?,
    val siteName: String?,
    val publishedAt: String?,
    val enrichmentState: String, // pending | done | unfetchable
    val enrichedAt: Long?,
    val relatedIds: List<Long>,
)

/**
 * Plain-SQLite persistence. Deliberately no Room/KSP in the MVP to keep the
 * build free of codegen; the API surface is small enough to swap the
 * implementation later (Room KMP or SQLDelight for the iPad port).
 */
class LinkStore(context: Context) :
    SQLiteOpenHelper(context.applicationContext, "links.db", null, 3) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE links (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                canonical_url TEXT NOT NULL UNIQUE,
                original_url TEXT NOT NULL,
                host TEXT NOT NULL,
                title TEXT,
                label TEXT,
                category TEXT NOT NULL,
                topics TEXT NOT NULL DEFAULT '',
                status TEXT NOT NULL DEFAULT 'open',
                n_sightings INTEGER NOT NULL DEFAULT 1,
                first_seen_at INTEGER NOT NULL,
                last_seen_at INTEGER NOT NULL,
                pending_enrichment INTEGER NOT NULL DEFAULT 1
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX idx_links_status ON links(status)")
        db.execSQL("CREATE INDEX idx_links_category ON links(category)")
        migrateToV2(db)
        migrateToV3(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) migrateToV2(db)
        if (oldVersion < 3) migrateToV3(db)
    }

    private fun migrateToV3(db: SQLiteDatabase) {
        // comma-separated link ids, computed at enrichment time
        db.execSQL("ALTER TABLE links ADD COLUMN related_ids TEXT")
    }

    private fun migrateToV2(db: SQLiteDatabase) {
        db.execSQL("ALTER TABLE links ADD COLUMN description TEXT")
        db.execSQL("ALTER TABLE links ADD COLUMN content TEXT")
        db.execSQL("ALTER TABLE links ADD COLUMN site_name TEXT")
        db.execSQL("ALTER TABLE links ADD COLUMN published_at TEXT")
        db.execSQL("ALTER TABLE links ADD COLUMN enrichment_state TEXT NOT NULL DEFAULT 'pending'")
        db.execSQL("ALTER TABLE links ADD COLUMN enriched_at INTEGER")
        db.execSQL("CREATE INDEX idx_links_enrichment ON links(enrichment_state)")
    }

    // all columns except `content` — it can be 100KB per row and is only
    // needed for embeddings/digest jobs, never for list UI
    private val entityColumns =
        "id, canonical_url, original_url, host, title, label, category, topics, " +
            "status, n_sightings, first_seen_at, last_seen_at, description, " +
            "site_name, published_at, enrichment_state, enriched_at, related_ids"

    /**
     * Insert the link or, if the canonical URL is already known, record a new
     * sighting (bumps last_seen/n_sightings and re-opens archived links).
     * @return true if a new row was inserted, false if an existing one was updated.
     */
    fun upsertSighting(link: ParsedLink, title: String?, now: Long = System.currentTimeMillis()): Boolean {
        val db = writableDatabase
        val updated = db.rawQuery(
            "SELECT id FROM links WHERE canonical_url = ?",
            arrayOf(link.canonicalUrl),
        ).use { c: Cursor ->
            if (c.moveToFirst()) c.getLong(0) else null
        }
        if (updated != null) {
            val values = ContentValues().apply {
                put("last_seen_at", now)
                put("status", "open")
                if (title != null) put("title", title)
            }
            db.update("links", values, "id = ?", arrayOf(updated.toString()))
            db.execSQL("UPDATE links SET n_sightings = n_sightings + 1 WHERE id = ?", arrayOf(updated))
            return false
        }
        val values = ContentValues().apply {
            put("canonical_url", link.canonicalUrl)
            put("original_url", link.originalUrl)
            put("host", link.host)
            put("title", title)
            put("label", link.label)
            put("category", link.category)
            put("topics", link.topics.joinToString(","))
            put("status", "open")
            put("n_sightings", 1)
            put("first_seen_at", now)
            put("last_seen_at", now)
            put("pending_enrichment", 1)
        }
        db.insert("links", null, values)
        return true
    }

    fun query(status: String?, category: String?, search: String): List<LinkEntity> {
        val where = StringBuilder("1=1")
        val args = ArrayList<String>()
        if (status != null) {
            where.append(" AND status = ?"); args.add(status)
        }
        if (category != null) {
            where.append(" AND category = ?"); args.add(category)
        }
        if (search.isNotBlank()) {
            where.append(" AND (canonical_url LIKE ? OR title LIKE ? OR label LIKE ?)")
            val like = "%$search%"
            args.add(like); args.add(like); args.add(like)
        }
        return readableDatabase.rawQuery(
            "SELECT $entityColumns FROM links WHERE $where ORDER BY last_seen_at DESC",
            args.toTypedArray(),
        ).use { c -> generateSequence { if (c.moveToNext()) c.toEntity() else null }.toList() }
    }

    fun byIds(ids: List<Long>): List<LinkEntity> {
        if (ids.isEmpty()) return emptyList()
        val placeholders = ids.joinToString(",") { "?" }
        val rows = readableDatabase.rawQuery(
            "SELECT $entityColumns FROM links WHERE id IN ($placeholders)",
            ids.map { it.toString() }.toTypedArray(),
        ).use { c -> generateSequence { if (c.moveToNext()) c.toEntity() else null }.toList() }
        // preserve the ranking order of ids
        val byId = rows.associateBy { it.id }
        return ids.mapNotNull { byId[it] }
    }

    /** id + text material for the similarity corpus (content capped in SQL). */
    fun similarityDocs(): List<Pair<Long, String>> =
        readableDatabase.rawQuery(
            "SELECT id, COALESCE(title,'') || ' ' || COALESCE(label,'') || ' ' || " +
                "COALESCE(description,'') || ' ' || COALESCE(topics,'') || ' ' || " +
                "COALESCE(substr(content,1,1500),'') FROM links",
            null,
        ).use { c ->
            generateSequence { if (c.moveToNext()) c.getLong(0) to c.getString(1) else null }.toList()
        }

    fun updateRelated(id: Long, relatedIds: List<Long>) {
        val values = ContentValues().apply {
            put("related_ids", relatedIds.joinToString(","))
        }
        writableDatabase.update("links", values, "id = ?", arrayOf(id.toString()))
    }

    fun byCanonicalUrl(url: String): LinkEntity? =
        readableDatabase.rawQuery(
            "SELECT $entityColumns FROM links WHERE canonical_url = ?",
            arrayOf(url),
        ).use { c -> if (c.moveToFirst()) c.toEntity() else null }

    fun byId(id: Long): LinkEntity? =
        readableDatabase.rawQuery(
            "SELECT $entityColumns FROM links WHERE id = ?",
            arrayOf(id.toString()),
        ).use { c -> if (c.moveToFirst()) c.toEntity() else null }

    fun pendingEnrichment(limit: Int): List<LinkEntity> =
        readableDatabase.rawQuery(
            "SELECT $entityColumns FROM links WHERE enrichment_state = 'pending' " +
                "ORDER BY last_seen_at DESC LIMIT ?",
            arrayOf(limit.toString()),
        ).use { c -> generateSequence { if (c.moveToNext()) c.toEntity() else null }.toList() }

    fun pendingEnrichmentCount(): Int =
        readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM links WHERE enrichment_state = 'pending'", null,
        ).use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }

    fun applyEnrichment(
        id: Long,
        state: String,
        title: String? = null,
        description: String? = null,
        content: String? = null,
        siteName: String? = null,
        publishedAt: String? = null,
        now: Long = System.currentTimeMillis(),
    ) {
        val values = ContentValues().apply {
            put("enrichment_state", state)
            put("enriched_at", now)
            if (title != null) put("title", title)
            if (description != null) put("description", description)
            if (content != null) put("content", content)
            if (siteName != null) put("site_name", siteName)
            if (publishedAt != null) put("published_at", publishedAt)
        }
        writableDatabase.update("links", values, "id = ?", arrayOf(id.toString()))
    }

    fun categoryCounts(status: String?): Map<String, Int> {
        val (where, args) =
            if (status != null) "WHERE status = ?" to arrayOf(status) else "" to emptyArray<String>()
        return readableDatabase.rawQuery(
            "SELECT category, COUNT(*) FROM links $where GROUP BY category",
            args,
        ).use { c ->
            buildMap { while (c.moveToNext()) put(c.getString(0), c.getInt(1)) }
        }
    }

    fun setStatus(id: Long, status: String) {
        val values = ContentValues().apply { put("status", status) }
        writableDatabase.update("links", values, "id = ?", arrayOf(id.toString()))
    }

    fun delete(id: Long) {
        writableDatabase.delete("links", "id = ?", arrayOf(id.toString()))
    }

    private fun Cursor.toEntity() = LinkEntity(
        id = getLong(getColumnIndexOrThrow("id")),
        canonicalUrl = getString(getColumnIndexOrThrow("canonical_url")),
        originalUrl = getString(getColumnIndexOrThrow("original_url")),
        host = getString(getColumnIndexOrThrow("host")),
        title = getStringOrNullAt("title"),
        label = getStringOrNullAt("label"),
        category = getString(getColumnIndexOrThrow("category")),
        topics = getString(getColumnIndexOrThrow("topics")).split(",").filter { it.isNotEmpty() },
        status = getString(getColumnIndexOrThrow("status")),
        nSightings = getInt(getColumnIndexOrThrow("n_sightings")),
        firstSeenAt = getLong(getColumnIndexOrThrow("first_seen_at")),
        lastSeenAt = getLong(getColumnIndexOrThrow("last_seen_at")),
        description = getStringOrNullAt("description"),
        siteName = getStringOrNullAt("site_name"),
        publishedAt = getStringOrNullAt("published_at"),
        enrichmentState = getString(getColumnIndexOrThrow("enrichment_state")),
        enrichedAt = if (isNull(getColumnIndexOrThrow("enriched_at"))) null
        else getLong(getColumnIndexOrThrow("enriched_at")),
        relatedIds = getStringOrNullAt("related_ids")
            ?.split(",")?.mapNotNull { it.toLongOrNull() } ?: emptyList(),
    )

    private fun Cursor.getStringOrNullAt(column: String): String? {
        val idx = getColumnIndexOrThrow(column)
        return if (isNull(idx)) null else getString(idx)
    }
}
