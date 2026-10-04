package com.tcnunes.szokert.ui.search

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tcnunes.szokert.SzokertApp
import com.tcnunes.szokert.core.CombinedResponse
import com.tcnunes.szokert.core.cleanQuery
import com.tcnunes.szokert.core.searchBoth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn

data class SearchUi(val query: String, val response: CombinedResponse?, val error: String? = null)

/** The search box and its results, shared by every screen that starts a search (activity-scoped). */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class SearchViewModel(app: Application) : AndroidViewModel(app) {
    private val data = (app as SzokertApp).data

    /** What is in the search box. */
    val query = MutableStateFlow("")

    /** Bumped when a search should run straight away (a tapped word) rather than after typing pauses. */
    private val immediate = MutableStateFlow(0)

    val ui: StateFlow<SearchUi> = combine(
        query.debounce { if (it.isEmpty()) 0 else 180 },
        data.state.map { it.installed?.source }.distinctUntilChanged(),
        immediate,
    ) { q, source, _ -> q to source }
        .mapLatest { (q, source) ->
            val clean = cleanQuery(q)
            if (clean.isEmpty() || source == null) return@mapLatest SearchUi(q, null)
            runCatching { SearchUi(q, source.searchBoth(clean)) }
                .getOrElse { SearchUi(q, null, "Couldn't search the dictionary (${it.message}).") }
        }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.Eagerly, SearchUi("", null))

    /** Look up a word from elsewhere (a sentence, a token, a Try link): replaces the query. */
    fun lookUp(word: String) {
        query.value = word
        immediate.value++
    }
}
