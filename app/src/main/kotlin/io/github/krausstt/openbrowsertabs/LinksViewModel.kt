package io.github.krausstt.openbrowsertabs

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.krausstt.openbrowsertabs.core.Clustering
import io.github.krausstt.openbrowsertabs.core.InboxBatch
import io.github.krausstt.openbrowsertabs.core.LinkParser
import io.github.krausstt.openbrowsertabs.core.LinkResolution
import io.github.krausstt.openbrowsertabs.core.TextSimilarity
import io.github.krausstt.openbrowsertabs.data.LinkEntity
import io.github.krausstt.openbrowsertabs.data.LinkStore
import io.github.krausstt.openbrowsertabs.data.GraphStore
import io.github.krausstt.openbrowsertabs.data.Space
import io.github.krausstt.openbrowsertabs.export.Exporter
import io.github.krausstt.openbrowsertabs.enrich.EnrichmentWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Tab { BROWSE, SPACES, SEARCH, INBOX }

data class UiState(
    val tab: Tab = Tab.SPACES,
    val links: List<LinkEntity> = emptyList(),
    val query: String = "",
    val statusFilter: String = "open",       // open | archived | all
    val categoryFilter: String? = null,
    val categoryCounts: Map<String, Int> = emptyMap(),
    val tagCounts: Map<String, Int> = emptyMap(),
    val activeTags: Set<String> = emptySet(),
    val spaces: List<Space> = emptyList(),
    val spaceCounts: Map<Long, Int> = emptyMap(),
    val openSpace: Space? = null,
    val spaceLinks: List<LinkEntity> = emptyList(),
    val inboxCount: Int = 0,
    val untaggedCount: Int = 0,
    val attentionLinks: List<LinkEntity> = emptyList(),   // only loaded on request
    val stack: List<LinkEntity> = emptyList(),   // the finishable handful
    val stackSize: Int = 0,                  // size it was dealt at, for progress
    val withoutContextCount: Int = 0,        // the honest total, kept below the fold
    val contextToday: Int = 0,               // what *you* did today; resets, never accrues
    val showingRest: Boolean = false,
    val pendingSummary: String? = null,      // text shared in, awaiting a target
    val session: ReviewSession? = null,
    val curatedTotal: Int = 0,
    val message: String? = null,
)

/**
 * A short, finite curation run. Deliberately session-scoped and not
 * persisted: there is no daily streak to break and nothing to lose by not
 * playing tomorrow. The score only counts what was actually gained.
 */
data class ReviewSession(
    val queue: List<LinkEntity>,
    val index: Int = 0,
    val target: Int,
    val done: Int = 0,
    val connectionsMade: Int = 0,
    val skipped: Int = 0,
    val suggestions: List<String> = emptyList(),
    val lastReward: String? = null,
    val finished: Boolean = false,
) {
    val current: LinkEntity? get() = queue.getOrNull(index)
    val progress: Float get() = if (target == 0) 0f else (done.toFloat() / target).coerceIn(0f, 1f)
}

class LinksViewModel(application: Application) : AndroidViewModel(application) {

    private val store = LinkStore(application)
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        refresh()
    }

    /**
     * Re-read everything the screens show.
     *
     * Only one may be in flight: every keystroke in the search field calls
     * this, and two overlapping reads finish in whatever order the database
     * returns them — an older query's results would then overwrite a newer
     * one's. Cancelling the previous run makes the last call win, which is
     * the only ordering a user can predict.
     */
    private var refreshJob: Job? = null

    fun refresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            val s = _state.value
            val status = if (s.statusFilter == "all") null else s.statusFilter
            val snapshot = withContext(Dispatchers.IO) {
                val all = store.query(status, s.categoryFilter, s.query)
                val filtered =
                    if (s.activeTags.isEmpty()) all
                    else all.filter { link -> s.activeTags.all { it in link.allTags } }
                val spaces = store.spaces()
                Snapshot(
                    links = filtered,
                    categoryCounts = store.categoryCounts(status),
                    tagCounts = store.tagCounts(status),
                    spaces = spaces,
                    spaceCounts = spaces.associate { it.id to store.spaceCount(it) },
                    inbox = store.inboxCount(),
                    untagged = store.untaggedCount(),
                    spaceLinks = s.openSpace?.let { store.linksInSpace(it) } ?: emptyList(),
                    curated = store.curatedCount(),
                    stack = if (s.stackSize == 0) dealStack()
                    else store.byIds(s.stack.map { it.id }),
                    // 300 rows are only read when the list is actually open
                    rest = if (s.showingRest) store.withoutContext() else emptyList(),
                    withoutContext = store.withoutContextCount(),
                    contextToday = store.contextGivenSince(startOfToday()),
                )
            }
            _state.value = _state.value.copy(
                links = snapshot.links,
                categoryCounts = snapshot.categoryCounts,
                tagCounts = snapshot.tagCounts,
                spaces = snapshot.spaces,
                spaceCounts = snapshot.spaceCounts,
                inboxCount = snapshot.inbox,
                untaggedCount = snapshot.untagged,
                spaceLinks = snapshot.spaceLinks,
                attentionLinks = snapshot.rest,
                curatedTotal = snapshot.curated,
                stack = reconcileStack(snapshot.stack),
                stackSize = if (_state.value.stackSize == 0) snapshot.stack.size
                else _state.value.stackSize,
                withoutContextCount = snapshot.withoutContext,
                contextToday = snapshot.contextToday,
            )
        }
    }

    private data class Snapshot(
        val links: List<LinkEntity>,
        val categoryCounts: Map<String, Int>,
        val tagCounts: Map<String, Int>,
        val spaces: List<Space>,
        val spaceCounts: Map<Long, Int>,
        val inbox: Int,
        val untagged: Int,
        val spaceLinks: List<LinkEntity>,
        val curated: Int,
        val stack: List<LinkEntity>,
        val rest: List<LinkEntity>,
        val withoutContext: Int,
        val contextToday: Int,
    )

    fun setTab(tab: Tab) {
        _state.value = _state.value.copy(tab = tab)
        refresh()
    }

    fun setQuery(q: String) {
        _state.value = _state.value.copy(query = q)
        refresh()
    }

    fun setStatusFilter(status: String) {
        _state.value = _state.value.copy(statusFilter = status, categoryFilter = null)
        refresh()
    }

    fun setCategoryFilter(category: String?) {
        _state.value = _state.value.copy(categoryFilter = category)
        refresh()
    }

    /** Tags combine with AND: each one narrows the set further. */
    fun toggleTag(tag: String) {
        val active = _state.value.activeTags.toMutableSet()
        if (!active.remove(tag)) active.add(tag)
        _state.value = _state.value.copy(activeTags = active)
        refresh()
    }

    fun clearTags() {
        _state.value = _state.value.copy(activeTags = emptySet())
        refresh()
    }

    fun openSpace(space: Space?) {
        _state.value = _state.value.copy(openSpace = space)
        refresh()
    }

    fun togglePin(space: Space) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { store.setSpacePinned(space.id, !space.pinned) }
            refresh()
        }
    }

    fun createSpaceFromActiveTags(name: String) {
        val tags = _state.value.activeTags.toList()
        if (tags.isEmpty() || name.isBlank()) return
        viewModelScope.launch {
            withContext(Dispatchers.IO) { store.createSpace(name.trim(), "📁", tags, emptyList()) }
            _state.value = _state.value.copy(message = "Space „$name“ angelegt")
            refresh()
        }
    }

    fun deleteSpace(space: Space) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { store.deleteSpace(space.id) }
            _state.value = _state.value.copy(openSpace = null, message = "Space gelöscht")
            refresh()
        }
    }

    // ------------------------------------------------------- the stack
    /**
     * Deal a handful out of the backlog.
     *
     * Called on first load and on request — never after a reaction. A stack
     * that silently refills is the backlog again with extra steps: the point
     * is that it can reach zero and stay there until you ask for more.
     */
    private fun dealStack(): List<LinkEntity> {
        val candidates = store.batchCandidates()
        val chosen = InboxBatch.pick(
            candidates.map {
                InboxBatch.Item(
                    id = it.id,
                    host = it.host,
                    savedAt = it.lastSeenAt,
                    sightings = it.nSightings,
                    hasContext = it.hasContext,
                    hasTags = it.allTags.isNotEmpty(),
                )
            },
        )
        val byId = candidates.associateBy { it.id }
        return chosen.mapNotNull { byId[it] }
    }

    /**
     * Fold a stack read before the database round-trip back into the taps
     * that landed during it.
     *
     * [refresh] captures the stack ids up front, so an answer given while it
     * was running is missing from what it brings back — and re-adding that
     * card would undo the one thing the screen promises: you answer, it goes
     * away. The live state wins; the snapshot only refreshes the rows.
     */
    private fun reconcileStack(fromSnapshot: List<LinkEntity>): List<LinkEntity> {
        val current = _state.value
        if (current.stackSize == 0) return fromSnapshot
        val live = current.stack.mapTo(mutableSetOf()) { it.id }
        return fromSnapshot.filter { it.id in live }
    }

    private fun startOfToday(): Long {
        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    /**
     * One tap on a stack card. The entry is removed from the stack right
     * away rather than on the next refresh — the reward for answering has to
     * be that the thing goes away, immediately and visibly.
     */
    fun react(id: Long, reactionId: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { store.setReaction(id, reactionId) }
            _state.value = _state.value.copy(
                stack = _state.value.stack.filterNot { it.id == id },
                contextToday = _state.value.contextToday + 1,
            )
            refreshCounts()
        }
    }

    /** The typed word, from the detail view or a later revisit. */
    fun setNote(id: Long, note: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { store.setUserNote(id, note) }
            _state.value = _state.value.copy(message = "Kontext gemerkt")
            refreshCounts()
        }
    }

    fun archiveFromStack(id: Long) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { store.setStatus(id, "archived") }
            _state.value = _state.value.copy(stack = _state.value.stack.filterNot { it.id == id })
            refreshCounts()
        }
    }

    /** Deal the next handful. Only ever on request — nothing refills itself. */
    fun nextStack() {
        viewModelScope.launch {
            val fresh = withContext(Dispatchers.IO) { dealStack() }
            _state.value = _state.value.copy(stack = fresh, stackSize = fresh.size)
        }
    }

    fun toggleRest() {
        _state.value = _state.value.copy(showingRest = !_state.value.showingRest)
        refresh()
    }

    /** Counts only — cheap enough to run after every tap. */
    private fun refreshCounts() {
        viewModelScope.launch {
            val counts = withContext(Dispatchers.IO) {
                Triple(store.withoutContextCount(), store.curatedCount(), store.inboxCount())
            }
            _state.value = _state.value.copy(
                withoutContextCount = counts.first,
                curatedTotal = counts.second,
                inboxCount = counts.third,
            )
        }
    }

    // ------------------------------------------------------- review session
    fun startSession(target: Int) {
        viewModelScope.launch {
            val queue = withContext(Dispatchers.IO) { store.reviewQueue(target * 2) }
            if (queue.isEmpty()) {
                _state.value = _state.value.copy(message = "Nichts offen — alles kuratiert.")
                return@launch
            }
            val session = ReviewSession(queue = queue, target = minOf(target, queue.size))
            _state.value = _state.value.copy(session = session)
            loadSuggestions()
        }
    }

    fun endSession() {
        _state.value = _state.value.copy(session = null)
        refresh()
    }

    /** Tag candidates for the current entry, cheapest-decision first. */
    private fun loadSuggestions() {
        val link = _state.value.session?.current ?: return
        viewModelScope.launch {
            val suggestions = withContext(Dispatchers.IO) {
                val fromRelated = store.byIds(link.relatedIds).flatMap { it.allTags }
                val global = store.tagCounts().keys
                // tags of neighbours first: they are usually the right answer
                (fromRelated + link.topics.filter { it != "untagged" } + global)
                    .distinct()
                    .filterNot { it in link.allTags }
                    .take(8)
            }
            _state.value = _state.value.copy(
                session = _state.value.session?.copy(suggestions = suggestions),
            )
        }
    }

    /** Commit the current entry and advance; the reward is what it connected to. */
    fun commitCurrent(tags: List<String>, summary: String?) {
        val session = _state.value.session ?: return
        val link = session.current ?: return
        viewModelScope.launch {
            val connections = withContext(Dispatchers.IO) {
                if (tags.isNotEmpty()) store.setUserTags(link.id, (link.userTags + tags).distinct())
                if (!summary.isNullOrBlank()) store.setUserSummary(link.id, summary.trim())
                store.countSharingAnyTag(tags, link.id)
            }
            val done = session.done + 1
            val reward = when {
                connections >= 20 -> "🎉 verbindet sich mit $connections Einträgen"
                connections > 0 -> "🔗 verbindet sich mit $connections Einträgen"
                !summary.isNullOrBlank() -> "📝 Zusammenfassung gesichert"
                else -> "✓ gespeichert"
            }
            _state.value = _state.value.copy(
                session = session.copy(
                    index = session.index + 1,
                    done = done,
                    connectionsMade = session.connectionsMade + connections,
                    lastReward = reward,
                    finished = done >= session.target || session.index + 1 >= session.queue.size,
                ),
                curatedTotal = withContext(Dispatchers.IO) { store.curatedCount() },
            )
            loadSuggestions()
        }
    }

    fun skipCurrent() {
        val session = _state.value.session ?: return
        _state.value = _state.value.copy(
            session = session.copy(
                index = session.index + 1,
                skipped = session.skipped + 1,
                lastReward = null,
                finished = session.index + 1 >= session.queue.size,
            ),
        )
        loadSuggestions()
    }

    fun archiveCurrent() {
        val session = _state.value.session ?: return
        val link = session.current ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) { store.setStatus(link.id, "archived") }
            _state.value = _state.value.copy(
                session = session.copy(
                    index = session.index + 1,
                    lastReward = "🗄 archiviert",
                    finished = session.index + 1 >= session.queue.size,
                ),
            )
            loadSuggestions()
        }
    }

    // ------------------------------------------------------------ exports
    /**
     * Cluster the open collection with the local tag-based grouping and write
     * every export flavour. Returns the directory holding the results.
     */
    suspend fun runExport(kind: String): java.io.File? = withContext(Dispatchers.IO) {
        val links = store.query("open", null, "")
        if (links.isEmpty()) return@withContext null
        val exporter = Exporter(getApplication())
        val clusters = Clustering.byTag(
            links.map { Clustering.Item(it.id, it.allTags, it.category) },
            labelFor = exporter::labelFor,
        )
        // persist the grouping so the UI and a later cloud run share one view
        val graph = GraphStore(store.writableDatabase)
        graph.replaceClusters(
            runId = System.currentTimeMillis().toString(),
            method = "local_tags",
            clusters = clusters.mapNotNull { cluster ->
                val ids = cluster.members.mapNotNull { graph.entityIdForLink(it) }
                if (ids.isEmpty()) null else cluster.label to ids
            },
        )
        when (kind) {
            "notebooklm" -> exporter.writeNotebookLmBundle(links, clusters)
            "markdown" -> exporter.writeDigestMarkdown(links, clusters)
            "pdf" -> exporter.writeDigestPdf(links, clusters)
            "interchange" -> exporter.writeInterchange(links) { store.contentOf(it) }
            else -> null
        }
    }

    fun report(message: String) {
        _state.value = _state.value.copy(message = message)
    }

    /** Hand-written summary; also feeds the similarity corpus. */
    fun setSummary(link: LinkEntity, summary: String?) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { store.setUserSummary(link.id, summary?.trim()) }
            _state.value = _state.value.copy(
                message = if (summary.isNullOrBlank()) "Zusammenfassung entfernt"
                else "Zusammenfassung gespeichert",
            )
            refresh()
        }
    }

    /** Text shared in from another app, waiting to be attached to a link. */
    fun setPendingSummary(text: String?) {
        _state.value = _state.value.copy(pendingSummary = text)
    }

    suspend fun recentLinks(): List<LinkEntity> =
        withContext(Dispatchers.IO) { store.recent() }

    suspend fun linkById(id: Long): LinkEntity? =
        withContext(Dispatchers.IO) { store.byId(id) }

    fun addUserTag(link: LinkEntity, tag: String) {
        val clean = tag.trim().removePrefix("#").lowercase().replace(' ', '_')
        if (clean.isEmpty()) return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                store.setUserTags(link.id, (link.userTags + clean).distinct())
            }
            refresh()
        }
    }

    fun removeUserTag(link: LinkEntity, tag: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { store.setUserTags(link.id, link.userTags - tag) }
            refresh()
        }
    }

    /** Bulk import from pasted text; reports via snackbar message. */
    fun addFromText(text: String) {
        viewModelScope.launch {
            val (added, seenAgain) = withContext(Dispatchers.IO) {
                val links = LinkParser.parse(text).map { LinkResolution.resolveIfShortened(it) }
                var a = 0
                var u = 0
                links.forEach { if (store.upsertSighting(it, title = null)) a++ else u++ }
                a to u
            }
            if (added > 0) EnrichmentWorker.enqueueDrain(getApplication())
            _state.value = _state.value.copy(
                message = when {
                    added + seenAgain == 0 -> "Keine Links im Text gefunden"
                    seenAgain == 0 -> "$added neu gespeichert — Anreicherung läuft im Hintergrund"
                    else -> "$added neu, $seenAgain bereits bekannt (Sichtung gezählt)"
                },
            )
            refresh()
        }
    }

    fun archive(id: Long) = setStatusAnd(id, "archived")

    fun restore(id: Long) = setStatusAnd(id, "open")

    fun delete(id: Long) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { store.delete(id) }
            refresh()
        }
    }

    fun consumeMessage() {
        _state.value = _state.value.copy(message = null)
    }

    /** Related links for the detail view; computes lazily for rows enriched
     *  before the association layer existed. */
    suspend fun relatedFor(link: LinkEntity): List<LinkEntity> =
        withContext(Dispatchers.IO) {
            if (link.relatedIds.isEmpty() && link.enrichmentState == "done") {
                val docs = store.similarityDocs()
                    .map { (id, text) -> TextSimilarity.Doc(id, text) }
                val related = TextSimilarity.topRelated(docs, link.id, k = 3).map { it.id }
                store.updateRelated(link.id, related)
                store.byIds(related)
            } else {
                store.byIds(link.relatedIds)
            }
        }

    private fun setStatusAnd(id: Long, status: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { store.setStatus(id, status) }
            refresh()
        }
    }
}
