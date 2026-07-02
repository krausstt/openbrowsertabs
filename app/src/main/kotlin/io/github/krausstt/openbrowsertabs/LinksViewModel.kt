package io.github.krausstt.openbrowsertabs

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.krausstt.openbrowsertabs.core.LinkParser
import io.github.krausstt.openbrowsertabs.core.LinkResolution
import io.github.krausstt.openbrowsertabs.data.LinkEntity
import io.github.krausstt.openbrowsertabs.data.LinkStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class UiState(
    val links: List<LinkEntity> = emptyList(),
    val query: String = "",
    val statusFilter: String = "open",       // open | archived | all
    val categoryFilter: String? = null,
    val categoryCounts: Map<String, Int> = emptyMap(),
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
            val (links, counts) = withContext(Dispatchers.IO) {
                store.query(status, s.categoryFilter, s.query) to store.categoryCounts(status)
            }
            _state.value = _state.value.copy(links = links, categoryCounts = counts)
        }
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

    /** Bulk import from pasted text; returns via snackbar message. */
    fun addFromText(text: String) {
        viewModelScope.launch {
            val (added, seenAgain) = withContext(Dispatchers.IO) {
                val links = LinkParser.parse(text).map { LinkResolution.resolveIfShortened(it) }
                var a = 0
                var u = 0
                links.forEach { if (store.upsertSighting(it, title = null)) a++ else u++ }
                a to u
            }
            _state.value = _state.value.copy(
                message = when {
                    added + seenAgain == 0 -> "Keine Links im Text gefunden"
                    seenAgain == 0 -> "$added neu gespeichert"
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

    private fun setStatusAnd(id: Long, status: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { store.setStatus(id, status) }
            refresh()
        }
    }
}
