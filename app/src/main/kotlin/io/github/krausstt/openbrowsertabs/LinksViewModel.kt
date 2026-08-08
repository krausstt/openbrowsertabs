package io.github.krausstt.openbrowsertabs

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.krausstt.openbrowsertabs.core.LinkParser
import io.github.krausstt.openbrowsertabs.core.LinkResolution
import io.github.krausstt.openbrowsertabs.core.TextSimilarity
import io.github.krausstt.openbrowsertabs.data.LinkEntity
import io.github.krausstt.openbrowsertabs.data.LinkStore
import io.github.krausstt.openbrowsertabs.data.Space
import io.github.krausstt.openbrowsertabs.enrich.EnrichmentWorker
import kotlinx.coroutines.Dispatchers
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
    val attentionLinks: List<LinkEntity> = emptyList(),
    val attentionMode: String = "inbox",     // inbox | untagged
    val message: String? = null,
)

class LinksViewModel(application: Application) : AndroidViewModel(application) {

    private val store = LinkStore(application)
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
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
                    inbox = store.inbox(),
                    untagged = store.untagged(),
                    spaceLinks = s.openSpace?.let { store.linksInSpace(it) } ?: emptyList(),
                )
            }
            _state.value = _state.value.copy(
                links = snapshot.links,
                categoryCounts = snapshot.categoryCounts,
                tagCounts = snapshot.tagCounts,
                spaces = snapshot.spaces,
                spaceCounts = snapshot.spaceCounts,
                inboxCount = snapshot.inbox.size,
                untaggedCount = snapshot.untagged.size,
                spaceLinks = snapshot.spaceLinks,
                attentionLinks =
                    if (_state.value.attentionMode == "untagged") snapshot.untagged
                    else snapshot.inbox,
            )
        }
    }

    private data class Snapshot(
        val links: List<LinkEntity>,
        val categoryCounts: Map<String, Int>,
        val tagCounts: Map<String, Int>,
        val spaces: List<Space>,
        val spaceCounts: Map<Long, Int>,
        val inbox: List<LinkEntity>,
        val untagged: List<LinkEntity>,
        val spaceLinks: List<LinkEntity>,
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

    fun setAttentionMode(mode: String) {
        _state.value = _state.value.copy(attentionMode = mode)
        refresh()
    }

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
