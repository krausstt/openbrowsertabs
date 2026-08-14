package io.github.krausstt.openbrowsertabs.data

import android.database.sqlite.SQLiteDatabase

/**
 * Property-graph schema shared by the local (share-time) and the cloud
 * (batch) enrichment paths.
 *
 * Fixed columns cannot describe "every human-readable website": a recipe has
 * servings, a model card has a parameter count, a local notice has a valid-
 * until date. New attribute kinds and new relation kinds are therefore
 * *data*, not migrations — see docs/design/2026-08-09-graph-schema.md.
 *
 * The load-bearing idea is provenance: every attribute and every edge records
 * which source asserted it. Sources coexist rather than overwrite, so a cloud
 * run is idempotent and can never destroy hand-made curation.
 */
object GraphSchema {

    /** Precedence when several sources assert the same key. Highest wins. */
    val SOURCE_PRECEDENCE = listOf("user", "cloud_batch", "local_llm", "local_rules")

    const val SOURCE_USER = "user"
    const val SOURCE_CLOUD = "cloud_batch"
    const val SOURCE_RULES = "local_rules"

    fun create(db: SQLiteDatabase, now: Long) {
        db.execSQL(
            """
            CREATE TABLE entities (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                kind TEXT NOT NULL,
                canonical_key TEXT NOT NULL,
                label TEXT,
                link_id INTEGER,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                UNIQUE(kind, canonical_key)
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX idx_entities_link ON entities(link_id)")
        db.execSQL("CREATE INDEX idx_entities_kind ON entities(kind)")

        // three typed value columns instead of one text column: keeps
        // "published before 2024" and "under 30 minutes" as SQL comparisons
        db.execSQL(
            """
            CREATE TABLE entity_attrs (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                entity_id INTEGER NOT NULL,
                key TEXT NOT NULL,
                value_text TEXT,
                value_num REAL,
                value_time INTEGER,
                source TEXT NOT NULL,
                confidence REAL NOT NULL DEFAULT 1.0,
                observed_at INTEGER NOT NULL,
                UNIQUE(entity_id, key, source)
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX idx_attrs_entity ON entity_attrs(entity_id)")
        db.execSQL("CREATE INDEX idx_attrs_key ON entity_attrs(key)")

        db.execSQL(
            """
            CREATE TABLE edges (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                src_id INTEGER NOT NULL,
                dst_id INTEGER NOT NULL,
                type TEXT NOT NULL,
                weight REAL NOT NULL DEFAULT 1.0,
                directed INTEGER NOT NULL DEFAULT 0,
                evidence TEXT,
                source TEXT NOT NULL,
                confidence REAL NOT NULL DEFAULT 1.0,
                created_at INTEGER NOT NULL,
                UNIQUE(src_id, dst_id, type, source)
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX idx_edges_src ON edges(src_id)")
        db.execSQL("CREATE INDEX idx_edges_dst ON edges(dst_id)")
        db.execSQL("CREATE INDEX idx_edges_type ON edges(type)")

        // the gazetteer as data, so the cloud batch and the user can extend it
        db.execSQL(
            """
            CREATE TABLE vocab (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                kind TEXT NOT NULL,
                term TEXT NOT NULL,
                canonical TEXT NOT NULL,
                source TEXT NOT NULL,
                hits INTEGER NOT NULL DEFAULT 0,
                added_at INTEGER NOT NULL,
                UNIQUE(kind, term)
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX idx_vocab_kind ON vocab(kind)")

        db.execSQL(
            """
            CREATE TABLE clusters (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                label TEXT NOT NULL,
                method TEXT NOT NULL,
                run_id TEXT,
                size INTEGER NOT NULL DEFAULT 0,
                created_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE cluster_members (
                cluster_id INTEGER NOT NULL,
                entity_id INTEGER NOT NULL,
                score REAL NOT NULL DEFAULT 1.0,
                PRIMARY KEY (cluster_id, entity_id)
            )
            """.trimIndent(),
        )

        seedVocab(db, now)
    }

    /**
     * Starting vocabulary. Deliberately small and broad — this is a seed, not
     * a taxonomy. The batch run and hand-made tags grow it from here, which is
     * how recipe or local-news vocabulary appears without anyone writing it.
     */
    private fun seedVocab(db: SQLiteDatabase, now: Long) {
        val seed = listOf(
            // technical standards / formats
            "standard" to listOf(
                "mcp", "model context protocol", "onnx", "rag", "gguf", "webxr",
                "zigbee", "mqtt", "matter", "wireguard", "opengl", "vulkan",
                "quantization", "matryoshka", "lora", "diffusion", "transformer",
            ),
            // recurring content shapes, useful across all site kinds
            "form" to listOf(
                "tutorial", "review", "benchmark", "changelog", "rezept",
                "recipe", "anleitung", "vergleich", "roadmap", "interview",
            ),
            // everyday domains — proof that this is not an AI-only vocabulary
            "topic" to listOf(
                "vegan", "meal-prep", "sous-vide", "3d-druck", "lasercutter",
                "photovoltaik", "wärmepumpe", "fahrrad", "wandern", "mietrecht",
            ),
        )
        val stmt = "INSERT OR IGNORE INTO vocab (kind, term, canonical, source, added_at) " +
            "VALUES (?, ?, ?, ?, ?)"
        seed.forEach { (kind, terms) ->
            terms.forEach { term ->
                db.execSQL(
                    stmt,
                    arrayOf<Any>(kind, term, term.replace(' ', '_').replace('-', '_'),
                        SOURCE_RULES, now),
                )
            }
        }
    }

    /**
     * Every existing link becomes a graph node, and the associations already
     * computed by the TF-IDF layer become `similar_to` edges attributed to
     * local_rules — so the graph starts populated instead of empty and the
     * cloud run has something to improve on rather than replace.
     */
    fun backfillFromLinks(db: SQLiteDatabase, now: Long) {
        db.execSQL(
            """
            INSERT OR IGNORE INTO entities (kind, canonical_key, label, link_id, created_at, updated_at)
            SELECT 'link', canonical_url, COALESCE(title, label, host), id, first_seen_at, last_seen_at
            FROM links
            """.trimIndent(),
        )
        // saved-at as a first-class, queryable attribute
        db.execSQL(
            """
            INSERT OR IGNORE INTO entity_attrs (entity_id, key, value_time, source, confidence, observed_at)
            SELECT e.id, 'saved_at', l.first_seen_at, ?, 1.0, ?
            FROM entities e JOIN links l ON l.id = e.link_id
            """.trimIndent(),
            arrayOf<Any>(SOURCE_RULES, now),
        )
        db.execSQL(
            """
            INSERT OR IGNORE INTO entity_attrs (entity_id, key, value_text, source, confidence, observed_at)
            SELECT e.id, 'host', l.host, ?, 1.0, ?
            FROM entities e JOIN links l ON l.id = e.link_id
            """.trimIndent(),
            arrayOf<Any>(SOURCE_RULES, now),
        )
    }
}
