package io.github.krausstt.openbrowsertabs.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase

/**
 * Reads and writes the property graph. All writers must name their source;
 * readers resolve conflicts by [GraphSchema.SOURCE_PRECEDENCE] instead of
 * letting the last writer win.
 */
class GraphStore(private val db: SQLiteDatabase) {

    data class Attr(val key: String, val text: String?, val num: Double?, val time: Long?,
                    val source: String, val confidence: Double)

    data class Edge(val srcId: Long, val dstId: Long, val type: String, val weight: Double,
                    val evidence: String?, val source: String)

    data class Cluster(val id: Long, val label: String, val size: Int)

    // ------------------------------------------------------------- entities
    fun entityIdForLink(linkId: Long): Long? =
        db.rawQuery("SELECT id FROM entities WHERE link_id = ?", arrayOf(linkId.toString()))
            .use { if (it.moveToFirst()) it.getLong(0) else null }

    fun upsertEntity(kind: String, key: String, label: String?, linkId: Long? = null,
                     now: Long = System.currentTimeMillis()): Long {
        db.execSQL(
            "INSERT OR IGNORE INTO entities (kind, canonical_key, label, link_id, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?)",
            arrayOf<Any?>(kind, key, label, linkId, now, now),
        )
        return db.rawQuery(
            "SELECT id FROM entities WHERE kind = ? AND canonical_key = ?", arrayOf(kind, key),
        ).use { if (it.moveToFirst()) it.getLong(0) else -1L }
    }

    // ---------------------------------------------------------- attributes
    fun putAttr(entityId: Long, key: String, source: String,
                text: String? = null, num: Double? = null, time: Long? = null,
                confidence: Double = 1.0, now: Long = System.currentTimeMillis()) {
        val values = ContentValues().apply {
            put("entity_id", entityId)
            put("key", key)
            put("source", source)
            put("confidence", confidence)
            put("observed_at", now)
            text?.let { put("value_text", it) }
            num?.let { put("value_num", it) }
            time?.let { put("value_time", it) }
        }
        // same source may correct itself; other sources keep their own row
        db.insertWithOnConflict("entity_attrs", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    /** Winning value per key for one entity, resolved by source precedence. */
    fun attrs(entityId: Long): Map<String, Attr> {
        val byKey = LinkedHashMap<String, Attr>()
        db.rawQuery(
            "SELECT key, value_text, value_num, value_time, source, confidence " +
                "FROM entity_attrs WHERE entity_id = ?",
            arrayOf(entityId.toString()),
        ).use { c ->
            while (c.moveToNext()) {
                val attr = Attr(
                    key = c.getString(0),
                    text = if (c.isNull(1)) null else c.getString(1),
                    num = if (c.isNull(2)) null else c.getDouble(2),
                    time = if (c.isNull(3)) null else c.getLong(3),
                    source = c.getString(4),
                    confidence = c.getDouble(5),
                )
                val incumbent = byKey[attr.key]
                if (incumbent == null || rank(attr.source) < rank(incumbent.source)) {
                    byKey[attr.key] = attr
                }
            }
        }
        return byKey
    }

    private fun rank(source: String): Int =
        GraphSchema.SOURCE_PRECEDENCE.indexOf(source).let { if (it < 0) Int.MAX_VALUE else it }

    // --------------------------------------------------------------- edges
    fun putEdge(srcId: Long, dstId: Long, type: String, source: String,
                weight: Double = 1.0, directed: Boolean = false, evidence: String? = null,
                confidence: Double = 1.0, now: Long = System.currentTimeMillis()) {
        if (srcId == dstId) return
        val values = ContentValues().apply {
            put("src_id", srcId)
            put("dst_id", dstId)
            put("type", type)
            put("weight", weight)
            put("directed", if (directed) 1 else 0)
            put("source", source)
            put("confidence", confidence)
            put("created_at", now)
            evidence?.let { put("evidence", it) }
        }
        db.insertWithOnConflict("edges", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun edgesOf(entityId: Long, limit: Int = 40): List<Edge> =
        db.rawQuery(
            "SELECT src_id, dst_id, type, weight, evidence, source FROM edges " +
                "WHERE src_id = ? OR dst_id = ? ORDER BY weight DESC LIMIT ?",
            arrayOf(entityId.toString(), entityId.toString(), limit.toString()),
        ).use { c ->
            generateSequence {
                if (c.moveToNext()) {
                    Edge(c.getLong(0), c.getLong(1), c.getString(2), c.getDouble(3),
                        if (c.isNull(4)) null else c.getString(4), c.getString(5))
                } else null
            }.toList()
        }

    fun edgeCount(): Int =
        db.rawQuery("SELECT COUNT(*) FROM edges", null)
            .use { if (it.moveToFirst()) it.getInt(0) else 0 }

    // --------------------------------------------------------------- vocab
    /** Terms of a kind, longest first so multi-word terms win over their parts. */
    fun vocabTerms(kind: String? = null): List<Pair<String, String>> {
        val (where, args) = if (kind != null) "WHERE kind = ?" to arrayOf(kind)
        else "" to emptyArray<String>()
        return db.rawQuery(
            "SELECT term, canonical FROM vocab $where ORDER BY LENGTH(term) DESC", args,
        ).use { c ->
            generateSequence { if (c.moveToNext()) c.getString(0) to c.getString(1) else null }
                .toList()
        }
    }

    /** Add a term if unknown. Hand-made tags enter the gazetteer this way. */
    fun learnTerm(kind: String, term: String, source: String,
                  now: Long = System.currentTimeMillis()) {
        val clean = term.trim().lowercase()
        if (clean.isEmpty()) return
        db.execSQL(
            "INSERT OR IGNORE INTO vocab (kind, term, canonical, source, added_at) VALUES (?, ?, ?, ?, ?)",
            arrayOf<Any>(kind, clean, clean.replace(' ', '_').replace('-', '_'), source, now),
        )
    }

    fun countVocabHit(term: String) {
        db.execSQL("UPDATE vocab SET hits = hits + 1 WHERE term = ?", arrayOf<Any>(term))
    }

    fun vocabSize(): Int =
        db.rawQuery("SELECT COUNT(*) FROM vocab", null)
            .use { if (it.moveToFirst()) it.getInt(0) else 0 }

    // ------------------------------------------------------------ clusters
    fun replaceClusters(runId: String, method: String,
                        clusters: List<Pair<String, List<Long>>>,
                        now: Long = System.currentTimeMillis()) {
        db.beginTransaction()
        try {
            db.execSQL("DELETE FROM cluster_members")
            db.execSQL("DELETE FROM clusters")
            clusters.forEach { (label, members) ->
                val values = ContentValues().apply {
                    put("label", label)
                    put("method", method)
                    put("run_id", runId)
                    put("size", members.size)
                    put("created_at", now)
                }
                val cid = db.insert("clusters", null, values)
                members.forEach { entityId ->
                    db.execSQL(
                        "INSERT OR REPLACE INTO cluster_members (cluster_id, entity_id, score) VALUES (?, ?, 1.0)",
                        arrayOf<Any>(cid, entityId),
                    )
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun clusters(): List<Cluster> =
        db.rawQuery("SELECT id, label, size FROM clusters ORDER BY size DESC", null)
            .use { c ->
                generateSequence {
                    if (c.moveToNext()) Cluster(c.getLong(0), c.getString(1), c.getInt(2)) else null
                }.toList()
            }

    /** Link rows belonging to a cluster, in save order. */
    fun clusterLinkIds(clusterId: Long): List<Long> =
        db.rawQuery(
            "SELECT e.link_id FROM cluster_members m JOIN entities e ON e.id = m.entity_id " +
                "WHERE m.cluster_id = ? AND e.link_id IS NOT NULL",
            arrayOf(clusterId.toString()),
        ).use { c ->
            generateSequence { if (c.moveToNext()) c.getLong(0) else null }.toList()
        }
}
