package io.github.krausstt.openbrowsertabs.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import io.github.krausstt.openbrowsertabs.core.ParsedLink
import io.github.krausstt.openbrowsertabs.core.Reactions

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
    val userTags: List<String>,  // hand-added, never overwritten by enrichment
    val userSummary: String?,    // written by hand, outranks the scraped text
    val imageUrl: String?,       // captured for a later thumbnail tier
    val wordCount: Int,
    val reaction: String?,       // one-tap stance from the save moment, see core.Reactions
    val userNote: String?,       // the one word of context asked for on save
    val contextAt: Long?,        // when the human last said something about it
) {
    /** Whether the human ever said anything about this entry at all. */
    val hasContext: Boolean
        get() = reaction != null || !userNote.isNullOrBlank() || !userSummary.isNullOrBlank()

    /** Auto topics plus hand-added tags, de-duplicated, for display + filtering. */
    val allTags: List<String>
        get() = (topics.filter { it != "untagged" } + userTags).distinct()
}

/**
 * A Space is a *saved filter*, not a folder: it matches links by tag or
 * category instead of owning them. Items therefore flow into spaces as they
 * get enriched, with no membership table to maintain and no orphans when a
 * link is deleted. The trade-off (no "pin this one item here regardless")
 * is documented in docs/design/2026-07-30-mobile-ui.md.
 */
data class Space(
    val id: Long,
    val name: String,
    val icon: String,
    val matchTags: List<String>,
    val matchCategories: List<String>,
    val pinned: Boolean,
    val position: Int,
)

/**
 * Plain-SQLite persistence. Deliberately no Room/KSP in the MVP to keep the
 * build free of codegen; the API surface is small enough to swap the
 * implementation later (Room KMP or SQLDelight for the iPad port).
 */
class LinkStore(context: Context) :
    SQLiteOpenHelper(context.applicationContext, "links.db", null, 7) {

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
        migrateToV4(db)
        migrateToV5(db)
        migrateToV6(db)
        migrateToV7(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) migrateToV2(db)
        if (oldVersion < 3) migrateToV3(db)
        if (oldVersion < 4) migrateToV4(db)
        if (oldVersion < 5) migrateToV5(db)
        if (oldVersion < 6) migrateToV6(db)
        if (oldVersion < 7) migrateToV7(db)
    }

    /** The save-moment fields. Both are the human's own words and are never
     *  written by enrichment — see docs/design/2026-09-01-save-moment.md. */
    private fun migrateToV7(db: SQLiteDatabase) {
        db.execSQL("ALTER TABLE links ADD COLUMN reaction TEXT")
        db.execSQL("ALTER TABLE links ADD COLUMN user_note TEXT")
        // when the human last said something, so "done today" is a real
        // measurement and not last_seen_at standing in for it
        db.execSQL("ALTER TABLE links ADD COLUMN context_at INTEGER")
    }

    private fun migrateToV6(db: SQLiteDatabase) {
        val now = System.currentTimeMillis()
        GraphSchema.create(db, now)
        GraphSchema.backfillFromLinks(db, now)
    }

    private fun migrateToV5(db: SQLiteDatabase) {
        db.execSQL("ALTER TABLE links ADD COLUMN user_summary TEXT")
    }

    private fun migrateToV4(db: SQLiteDatabase) {
        db.execSQL("ALTER TABLE links ADD COLUMN user_tags TEXT")
        db.execSQL("ALTER TABLE links ADD COLUMN image_url TEXT")
        db.execSQL("ALTER TABLE links ADD COLUMN word_count INTEGER NOT NULL DEFAULT 0")
        db.execSQL(
            """
            CREATE TABLE spaces (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                icon TEXT NOT NULL DEFAULT '📁',
                match_tags TEXT NOT NULL DEFAULT '',
                match_categories TEXT NOT NULL DEFAULT '',
                pinned INTEGER NOT NULL DEFAULT 0,
                position INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent(),
        )
        // seeded from the goal profile in the design doc, so the first launch
        // already shows something meaningful instead of an empty shelf
        // explicit <Any>: mixed String/Int literals would otherwise infer an
        // intersection type, which execSQL's reified bindArgs rejects
        val seed = listOf<Array<Any>>(
            arrayOf("KI & Agenten", "🤖", "llm_agents", "", 1, 0),
            arrayOf("Home Lab", "🏠", "embedded_iot,coding_devops", "", 0, 1),
            arrayOf("Audio & Video", "🎧", "audio_music", "video", 0, 2),
            arrayOf("Hardware", "🖥", "hardware", "", 0, 3),
            arrayOf("Lesen", "📚", "", "article,blog,paper", 0, 4),
            arrayOf("Kaufen", "🛒", "", "shopping", 0, 5),
        )
        seed.forEach { row ->
            db.execSQL(
                "INSERT INTO spaces (name, icon, match_tags, match_categories, pinned, position)" +
                    " VALUES (?, ?, ?, ?, ?, ?)",
                row,
            )
        }
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
            "site_name, published_at, enrichment_state, enriched_at, related_ids, " +
            "user_tags, image_url, word_count, user_summary, reaction, user_note, context_at"

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

    /**
     * id + text material for the similarity corpus (content capped in SQL).
     * Deliberately excludes `description`: site-wide boilerplate descriptions
     * (e.g. HuggingFace's identical tagline on every model page) would
     * otherwise make unrelated pages look "related" purely by sharing that
     * fixed marketing text.
     */
    fun similarityDocs(): List<Pair<Long, String>> =
        readableDatabase.rawQuery(
            "SELECT id, COALESCE(title,'') || ' ' || COALESCE(label,'') || ' ' || " +
                "COALESCE(topics,'') || ' ' || COALESCE(user_summary,'') || ' ' || " +
                "COALESCE(substr(content,1,1500),'') FROM links",
            null,
        ).use { c ->
            generateSequence { if (c.moveToNext()) c.getLong(0) to c.getString(1) else null }.toList()
        }

    /**
     * How many OTHER rows share this exact description string. A count >= 2
     * means the text is a fixed site-wide default (boilerplate), not a
     * genuine per-page summary — used to keep such text out of notifications
     * and the "one-liner" without needing a per-host blocklist.
     */
    fun countSharedDescription(description: String, excludingId: Long): Int =
        readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM links WHERE description = ? AND id != ?",
            arrayOf(description, excludingId.toString()),
        ).use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }

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
        topics: List<String>? = null,
        imageUrl: String? = null,
        wordCount: Int? = null,
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
            if (topics != null) put("topics", topics.joinToString(","))
            if (imageUrl != null) put("image_url", imageUrl)
            if (wordCount != null) put("word_count", wordCount)
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

    // ------------------------------------------------------------- spaces
    fun spaces(): List<Space> =
        readableDatabase.rawQuery(
            "SELECT id, name, icon, match_tags, match_categories, pinned, position " +
                "FROM spaces ORDER BY pinned DESC, position ASC",
            null,
        ).use { c ->
            generateSequence { if (c.moveToNext()) c.toSpace() else null }.toList()
        }

    /** The saved filter as SQL: any tag hit OR any category hit.
     *  Null when the space matches nothing at all. */
    private fun spaceWhere(space: Space, status: String?): Pair<String, Array<String>>? {
        val where = StringBuilder("1=1")
        val args = ArrayList<String>()
        if (status != null) {
            where.append(" AND status = ?"); args.add(status)
        }
        val clauses = ArrayList<String>()
        space.matchTags.forEach {
            // ',' padding prevents "audio" from matching "audio_music"
            clauses.add("(',' || topics || ',' || COALESCE(user_tags,'') || ',') LIKE ?")
            args.add("%,$it,%")
        }
        space.matchCategories.forEach {
            clauses.add("category = ?"); args.add(it)
        }
        if (clauses.isEmpty()) return null
        where.append(" AND (").append(clauses.joinToString(" OR ")).append(")")
        return where.toString() to args.toTypedArray()
    }

    /** Links matching a space's saved filter: any tag hit OR any category hit. */
    fun linksInSpace(space: Space, status: String? = "open"): List<LinkEntity> {
        val (where, args) = spaceWhere(space, status) ?: return emptyList()
        return readableDatabase.rawQuery(
            "SELECT $entityColumns FROM links WHERE $where ORDER BY last_seen_at DESC",
            args,
        ).use { c -> generateSequence { if (c.moveToNext()) c.toEntity() else null }.toList() }
    }

    /** COUNT rather than `linksInSpace(...).size`: this runs once per space on
     *  every refresh, and a refresh happens on every keystroke in the search
     *  field — building full entities only to discard them is the difference
     *  between a count and a full table read per space. */
    fun spaceCount(space: Space, status: String? = "open"): Int {
        val (where, args) = spaceWhere(space, status) ?: return 0
        return readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM links WHERE $where", args,
        ).use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }
    }

    fun setSpacePinned(id: Long, pinned: Boolean) {
        val values = ContentValues().apply { put("pinned", if (pinned) 1 else 0) }
        writableDatabase.update("spaces", values, "id = ?", arrayOf(id.toString()))
    }

    fun createSpace(name: String, icon: String, tags: List<String>, categories: List<String>): Long {
        val values = ContentValues().apply {
            put("name", name)
            put("icon", icon)
            put("match_tags", tags.joinToString(","))
            put("match_categories", categories.joinToString(","))
            put("pinned", 0)
            put("position", 99)
        }
        return writableDatabase.insert("spaces", null, values)
    }

    fun deleteSpace(id: Long) {
        writableDatabase.delete("spaces", "id = ?", arrayOf(id.toString()))
    }

    // --------------------------------------------------------------- tags
    /** Tag -> count over open links, auto topics and hand-added tags combined. */
    fun tagCounts(status: String? = "open"): Map<String, Int> {
        val (where, args) =
            if (status != null) "WHERE status = ?" to arrayOf(status) else "" to emptyArray<String>()
        val counts = LinkedHashMap<String, Int>()
        readableDatabase.rawQuery(
            "SELECT topics, user_tags FROM links $where", args,
        ).use { c ->
            while (c.moveToNext()) {
                val tags = (c.getString(0) ?: "").split(",") + (c.getString(1) ?: "").split(",")
                tags.map { it.trim() }
                    .filter { it.isNotEmpty() && it != "untagged" }
                    .distinct()
                    .forEach { counts[it] = (counts[it] ?: 0) + 1 }
            }
        }
        return counts.entries.sortedByDescending { it.value }.associate { it.key to it.value }
    }

    /**
     * Links a review session should offer: untagged first (biggest gain per
     * action), then the ones that failed enrichment and need a human summary.
     * Search queries are skipped — their label already says everything.
     */
    fun reviewQueue(limit: Int): List<LinkEntity> =
        readableDatabase.rawQuery(
            "SELECT $entityColumns FROM links WHERE status = 'open' " +
                "AND category != 'search_query' " +
                "AND (user_tags IS NULL OR user_tags = '') " +
                "AND (user_summary IS NULL OR user_summary = '') " +
                "ORDER BY CASE WHEN topics = '' OR topics = 'untagged' THEN 0 ELSE 1 END, " +
                "CASE WHEN enrichment_state = 'unfetchable' THEN 0 ELSE 1 END, " +
                "last_seen_at DESC LIMIT ?",
            arrayOf(limit.toString()),
        ).use { c -> generateSequence { if (c.moveToNext()) c.toEntity() else null }.toList() }

    /** How many links carry a hand-made tag or summary — only ever grows. */
    fun curatedCount(): Int =
        readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM links WHERE " +
                "(user_tags IS NOT NULL AND user_tags != '') OR " +
                "(user_summary IS NOT NULL AND user_summary != '') OR " +
                "(user_note IS NOT NULL AND user_note != '') OR " +
                "reaction IS NOT NULL",
            null,
        ).use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }

    /** Open links sharing at least one of these tags — the honest reward
     *  signal after tagging: how much this entry just connected to. */
    fun countSharingAnyTag(tags: List<String>, excludingId: Long): Int {
        if (tags.isEmpty()) return 0
        val clauses = tags.joinToString(" OR ") {
            "(',' || topics || ',' || COALESCE(user_tags,'') || ',') LIKE ?"
        }
        val args = tags.map { "%,$it,%" } + excludingId.toString()
        return readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM links WHERE status = 'open' AND ($clauses) AND id != ?",
            args.toTypedArray(),
        ).use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }
    }

    /** Extracted article text — held apart from [entityColumns] because it is
     *  large and only the export and embedding paths need it. */
    fun contentOf(id: Long): String? =
        readableDatabase.rawQuery(
            "SELECT content FROM links WHERE id = ?", arrayOf(id.toString()),
        ).use { c -> if (c.moveToFirst() && !c.isNull(0)) c.getString(0) else null }

    fun setUserSummary(id: Long, summary: String?) {
        val values = ContentValues().apply {
            if (summary.isNullOrBlank()) putNull("user_summary") else put("user_summary", summary)
        }
        writableDatabase.update("links", values, "id = ?", arrayOf(id.toString()))
    }

    /** Recent links, for attaching a summary shared in from another app. */
    fun recent(limit: Int = 40): List<LinkEntity> =
        readableDatabase.rawQuery(
            "SELECT $entityColumns FROM links ORDER BY last_seen_at DESC LIMIT ?",
            arrayOf(limit.toString()),
        ).use { c -> generateSequence { if (c.moveToNext()) c.toEntity() else null }.toList() }

    fun setUserTags(id: Long, tags: List<String>) {
        val values = ContentValues().apply { put("user_tags", tags.joinToString(",")) }
        writableDatabase.update("links", values, "id = ?", arrayOf(id.toString()))
    }

    // --------------------------------------------------- needs attention
    /** Open links with no usable tag at all — the "untagged" bucket. */
    fun untagged(): List<LinkEntity> =
        readableDatabase.rawQuery(
            "SELECT $entityColumns FROM links WHERE status = 'open' " +
                "AND (topics = '' OR topics = 'untagged') " +
                "AND (user_tags IS NULL OR user_tags = '') " +
                "ORDER BY last_seen_at DESC",
            null,
        ).use { c -> generateSequence { if (c.moveToNext()) c.toEntity() else null }.toList() }

    /** Counts only — the list forms were loaded on every refresh purely to
     *  call `.size` on several hundred rows. */
    fun untaggedCount(): Int =
        readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM links WHERE status = 'open' " +
                "AND (topics = '' OR topics = 'untagged') " +
                "AND (user_tags IS NULL OR user_tags = '')",
            null,
        ).use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }

    fun inboxCount(): Int =
        readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM links WHERE status = 'open' AND enrichment_state != 'done'",
            null,
        ).use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }

    /** Open links still waiting for (or failed at) enrichment. */
    fun inbox(): List<LinkEntity> =
        readableDatabase.rawQuery(
            "SELECT $entityColumns FROM links WHERE status = 'open' " +
                "AND enrichment_state != 'done' ORDER BY last_seen_at DESC",
            null,
        ).use { c -> generateSequence { if (c.moveToNext()) c.toEntity() else null }.toList() }

    // ------------------------------------------------- the save moment
    /**
     * Store the one-tap stance. Written by the share overlay and by the
     * inbox stack; enrichment never touches it, so a cloud run can neither
     * overwrite nor invent one.
     */
    fun setReaction(id: Long, reaction: String?, now: Long = System.currentTimeMillis()) {
        val clean = Reactions.sanitize(reaction)
        val values = ContentValues().apply {
            if (clean == null) putNull("reaction") else put("reaction", clean)
            if (clean != null) put("context_at", now)
        }
        writableDatabase.update("links", values, "id = ?", arrayOf(id.toString()))
    }

    fun setUserNote(id: Long, note: String?, now: Long = System.currentTimeMillis()) {
        val clean = Reactions.normalizeNote(note)
        val values = ContentValues().apply {
            if (clean == null) putNull("user_note") else put("user_note", clean)
            if (clean != null) put("context_at", now)
        }
        writableDatabase.update("links", values, "id = ?", arrayOf(id.toString()))
    }

    /**
     * Candidates for the inbox stack: open, not a bare search query, and
     * still missing either context or tags. Ranking happens in
     * [io.github.krausstt.openbrowsertabs.core.InboxBatch] so it is testable
     * without a device.
     */
    fun batchCandidates(limit: Int = 400): List<LinkEntity> =
        readableDatabase.rawQuery(
            "SELECT $entityColumns FROM links WHERE status = 'open' " +
                "AND category != 'search_query' " +
                "AND (reaction IS NULL OR user_note IS NULL " +
                "     OR topics = '' OR topics = 'untagged') " +
                // the LIMIT runs before InboxBatch gets to rank, so the SQL has
                // to agree with it about what matters most: an entry you
                // reacted to but never tagged is the batch's top priority and
                // must not fall off the end of the window as it ages
                "ORDER BY CASE WHEN (reaction IS NOT NULL " +
                "                    OR (user_note IS NOT NULL AND user_note != '')) " +
                "              AND (topics = '' OR topics = 'untagged') " +
                "              AND (user_tags IS NULL OR user_tags = '') " +
                "         THEN 0 ELSE 1 END, last_seen_at DESC LIMIT ?",
            arrayOf(limit.toString()),
        ).use { c -> generateSequence { if (c.moveToNext()) c.toEntity() else null }.toList() }

    private val withoutContextWhere =
        "status = 'open' AND category != 'search_query' " +
            "AND reaction IS NULL " +
            "AND (user_note IS NULL OR user_note = '') " +
            "AND (user_summary IS NULL OR user_summary = '')"

    /** Open links the human has never said anything about, newest first. */
    fun withoutContext(limit: Int = 300): List<LinkEntity> =
        readableDatabase.rawQuery(
            "SELECT $entityColumns FROM links WHERE $withoutContextWhere " +
                "ORDER BY last_seen_at DESC LIMIT ?",
            arrayOf(limit.toString()),
        ).use { c -> generateSequence { if (c.moveToNext()) c.toEntity() else null }.toList() }

    /** Open links the human has never said anything about. */
    fun withoutContextCount(): Int =
        readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM links WHERE $withoutContextWhere", null,
        ).use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }

    /** Entries touched at the save moment since [since] — the honest
     *  "you did this" number, which cannot grow while you sleep. */
    fun contextGivenSince(since: Long): Int =
        readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM links WHERE context_at >= ?",
            arrayOf(since.toString()),
        ).use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }

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
        userTags = getStringOrNullAt("user_tags")
            ?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList(),
        imageUrl = getStringOrNullAt("image_url"),
        wordCount = getInt(getColumnIndexOrThrow("word_count")),
        userSummary = getStringOrNullAt("user_summary"),
        reaction = getStringOrNullAt("reaction"),
        userNote = getStringOrNullAt("user_note"),
        contextAt = if (isNull(getColumnIndexOrThrow("context_at"))) null
        else getLong(getColumnIndexOrThrow("context_at")),
    )

    private fun Cursor.toSpace() = Space(
        id = getLong(0),
        name = getString(1),
        icon = getString(2),
        matchTags = getString(3).split(",").map { it.trim() }.filter { it.isNotEmpty() },
        matchCategories = getString(4).split(",").map { it.trim() }.filter { it.isNotEmpty() },
        pinned = getInt(5) == 1,
        position = getInt(6),
    )

    private fun Cursor.getStringOrNullAt(column: String): String? {
        val idx = getColumnIndexOrThrow(column)
        return if (isNull(idx)) null else getString(idx)
    }
}
