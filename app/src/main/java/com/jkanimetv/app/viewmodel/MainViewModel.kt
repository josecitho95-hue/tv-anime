package com.jkanimetv.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jkanimetv.app.data.*
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

data class HomeUiState(
    val sections: List<AnimeSection> = emptyList(),
    val topAnime: List<Anime> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

data class SearchUiState(
    val results: List<Anime> = emptyList(),
    val isLoading: Boolean = false,
    val query: String = "",
    val error: String? = null
)

data class DetailUiState(
    val anime: Anime? = null,
    val episodes: List<Episode> = emptyList(),
    val isLoading: Boolean = false,
    val isFavorite: Boolean = false,
    val error: String? = null
)

data class VideoUrlState(
    val url: String? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val animeUrl: String = "",
    val episode: Int = 0
)

data class ScheduleUiState(
    val days: List<Schedule> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

data class DirectoryFilter(
    val genre: String = "",
    val type: String = "",
    val status: String = ""
)

@OptIn(FlowPreview::class)
class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = JKAnimeRepository(app)
    private val db = AppDatabase.getInstance(app)
    private val historyDao = db.watchHistoryDao()
    private val favDao = db.favoriteDao()
    private val searchDao = db.searchHistoryDao()

    // Used by SettingsScreen to clear the OkHttp cache.
    fun repository(): JKAnimeRepository = repo

    private val _home = MutableStateFlow(HomeUiState(isLoading = true))
    val home: StateFlow<HomeUiState> = _home

    private val _search = MutableStateFlow(SearchUiState())
    val search: StateFlow<SearchUiState> = _search

    private val _detail = MutableStateFlow(DetailUiState())
    val detail: StateFlow<DetailUiState> = _detail

    private val _videoUrl = MutableStateFlow(VideoUrlState())
    val videoUrl: StateFlow<VideoUrlState> = _videoUrl

    private val _schedule = MutableStateFlow(ScheduleUiState())
    val schedule: StateFlow<ScheduleUiState> = _schedule

    private val _history = MutableStateFlow<List<WatchHistory>>(emptyList())
    val history: StateFlow<List<WatchHistory>> = _history

    // Map episodeNumber -> WatchHistory for the currently opened anime detail.
    // Powers the watched-indicator overlay on EpisodeButton.
    private val _episodeProgress = MutableStateFlow<Map<Int, WatchHistory>>(emptyMap())
    val episodeProgress: StateFlow<Map<Int, WatchHistory>> = _episodeProgress

    private val _favorites = MutableStateFlow<List<Favorite>>(emptyList())
    val favorites: StateFlow<List<Favorite>> = _favorites

    // C3: recent searches (max 10, newest first).
    private val _searchHistory = MutableStateFlow<List<String>>(emptyList())
    val searchHistory: StateFlow<List<String>> = _searchHistory

    // C4: scroll memory. Stored as raw pixel offsets — survives navigation
    // (process-survival is out of scope; SavedStateHandle could add that).
    private val _homeScroll = MutableStateFlow(0)
    val homeScroll: StateFlow<Int> = _homeScroll
    fun setHomeScroll(value: Int) { _homeScroll.value = value }

    private val _browseScrollIndex = MutableStateFlow(0)
    val browseScrollIndex: StateFlow<Int> = _browseScrollIndex
    private val _browseScrollOffset = MutableStateFlow(0)
    val browseScrollOffset: StateFlow<Int> = _browseScrollOffset
    fun setBrowseScroll(index: Int, offset: Int) {
        _browseScrollIndex.value = index
        _browseScrollOffset.value = offset
    }

    private val _directory = MutableStateFlow<List<Anime>>(emptyList())
    val directory: StateFlow<List<Anime>> = _directory

    private val _directoryLoading = MutableStateFlow(false)
    val directoryLoading: StateFlow<Boolean> = _directoryLoading

    private val _directoryEnd = MutableStateFlow(false)
    val directoryEnd: StateFlow<Boolean> = _directoryEnd

    private val _directoryFilter = MutableStateFlow(DirectoryFilter())
    val directoryFilter: StateFlow<DirectoryFilter> = _directoryFilter

    private var directoryJob: Job? = null

    private val _currentAnimeUrl = MutableStateFlow("")
    val currentAnimeUrl: StateFlow<String> = _currentAnimeUrl

    private var detailJob: Job? = null

    // A3: search debounce. The UI writes the raw query into this flow on every
    // keystroke; we coalesce bursts (350 ms) before hitting the network. A
    // separate `searchJob` lets us cancel an in-flight request when a newer
    // query comes in.
    private val _searchQuery = MutableStateFlow("")
    private var searchJob: Job? = null

    fun selectAnime(url: String) {
        _currentAnimeUrl.value = url
        _detail.value = DetailUiState(isLoading = true)
        _videoUrl.value = VideoUrlState()
    }

    init {
        loadHome()
        loadHistory()
        loadFavorites()
        loadSearchHistory()
        _searchQuery
            .debounce(350L)
            .distinctUntilChanged()
            .onEach { performSearch(it) }
            .launchIn(viewModelScope)
    }

    fun loadHome() {
        viewModelScope.launch {
            _home.value = HomeUiState(isLoading = true)
            runCatching {
                coroutineScope {
                    val sectionsDeferred = async { repo.getRecentAnime() }
                    val topDeferred = async { repo.getTopAnime() }
                    val sections = sectionsDeferred.await()
                    val top = topDeferred.await()
                    _home.value = HomeUiState(sections = sections, topAnime = top)
                }
            }.onFailure {
                _home.value = HomeUiState(error = it.message ?: "Error desconocido")
            }
        }
    }

    // C3: force-refresh Home by evicting the HTTP cache so the next GETs go
    // out to the network. Cheaper alternative would be a fine-grained eviction
    // by URL — but the cache is small (20 MB) and a full wipe lets users also
    // reset Top/Schedule/Directory in one go.
    fun refreshHome() {
        repo.clearHttpCache()
        loadHome()
    }

    fun loadSchedule() {
        if (_schedule.value.isLoading) return
        viewModelScope.launch {
            _schedule.value = ScheduleUiState(isLoading = true)
            runCatching {
                val days = repo.getSchedule()
                _schedule.value = ScheduleUiState(days = days)
            }.onFailure {
                _schedule.value = ScheduleUiState(error = it.message ?: "Error desconocido")
            }
        }
    }

    // Public entry point — the SearchScreen calls this on every keystroke and
    // also on IME-action submit. We update both the UI's visible query field
    // and the debounced flow that triggers the network call.
    fun search(query: String) {
        _search.value = _search.value.copy(query = query)
        _searchQuery.value = query
    }

    private fun performSearch(query: String) {
        searchJob?.cancel()
        if (query.isBlank()) {
            _search.value = SearchUiState()
            return
        }
        searchJob = viewModelScope.launch {
            _search.value = SearchUiState(query = query, isLoading = true)
            runCatching {
                val results = repo.search(query)
                _search.value = SearchUiState(query = query, results = results)
                // Only persist queries that actually matched something — avoids
                // littering history with typos and 0-hit prefixes.
                if (results.isNotEmpty()) {
                    searchDao.upsert(SearchHistoryItem(query = query.trim()))
                    loadSearchHistory()
                }
            }.onFailure {
                _search.value = SearchUiState(query = query, error = it.message)
            }
        }
    }

    fun deleteSearchHistoryItem(query: String) {
        viewModelScope.launch {
            searchDao.delete(query)
            loadSearchHistory()
        }
    }

    private fun loadSearchHistory() {
        viewModelScope.launch {
            _searchHistory.value = searchDao.recent().map { it.query }
        }
    }

    fun loadDetail(animeUrl: String) {
        detailJob?.cancel()
        _episodeProgress.value = emptyMap()
        detailJob = viewModelScope.launch {
            _detail.value = DetailUiState(isLoading = true)
            runCatching {
                val anime = repo.getAnimeDetail(animeUrl)
                val episodes = repo.getEpisodeList(animeUrl)
                val isFav = favDao.isFavorite(animeUrl) > 0
                val warning = if (anime != null && episodes.isEmpty())
                    "No se pudieron cargar los episodios. Revisa tu conexión o intenta más tarde."
                else null
                _detail.value = DetailUiState(
                    anime = anime,
                    episodes = episodes,
                    isFavorite = isFav,
                    error = warning
                )
                refreshEpisodeProgress(animeUrl)
                // C2: snap the notification watermark up to the current latest
                // episode whenever the user actually views the detail page.
                // Prevents the next worker tick from flooding notifications
                // for series the user already knows about.
                if (isFav && episodes.isNotEmpty()) {
                    val latest = episodes.maxOf { it.number }
                    favDao.updateLastSeenEpisode(animeUrl, latest)
                }
            }.onFailure {
                _detail.value = DetailUiState(error = it.message ?: "Error cargando anime")
            }
        }
    }

    // Refresh the per-episode progress map for the given anime. Called on
    // detail load and whenever we re-enter the detail screen (e.g. after
    // returning from the player) so the watched indicators stay in sync.
    fun refreshEpisodeProgress(animeUrl: String) {
        viewModelScope.launch {
            _episodeProgress.value = historyDao.getForAnime(animeUrl).associateBy { it.episodeNumber }
        }
    }

    fun loadVideoUrl(animeUrl: String, episode: Int) {
        viewModelScope.launch {
            _videoUrl.value = VideoUrlState(isLoading = true, animeUrl = animeUrl, episode = episode)
            runCatching {
                val url = repo.getVideoUrl(animeUrl, episode)
                _videoUrl.value = VideoUrlState(
                    url = url, animeUrl = animeUrl, episode = episode,
                    error = if (url == null) "No se pudo obtener el enlace de video" else null
                )
            }.onFailure {
                _videoUrl.value = VideoUrlState(error = it.message, animeUrl = animeUrl, episode = episode)
            }
        }
    }

    fun clearVideoUrl() { _videoUrl.value = VideoUrlState() }

    fun setDirectoryFilter(genre: String = "", type: String = "", status: String = "") {
        _directoryFilter.value = DirectoryFilter(genre, type, status)
        _directory.value = emptyList()
        _directoryEnd.value = false
        // Filter change = different result set, so the saved scroll position
        // is no longer meaningful. Otherwise selecting a new genre lands the
        // user in the middle of a (smaller) grid.
        setBrowseScroll(0, 0)
        loadDirectory(1)
    }

    fun loadDirectory(page: Int = 1) {
        if (_directoryLoading.value) return
        if (page > 1 && _directoryEnd.value) return
        val f = _directoryFilter.value
        directoryJob?.cancel()
        directoryJob = viewModelScope.launch {
            _directoryLoading.value = true
            runCatching {
                val items = repo.getDirectory(page, f.genre, f.type, f.status)
                if (page == 1) _directory.value = items
                else _directory.value = _directory.value + items
                if (items.isEmpty()) _directoryEnd.value = true
            }
            _directoryLoading.value = false
        }
    }

    fun toggleFavorite(anime: Anime) {
        viewModelScope.launch {
            val isFav = favDao.isFavorite(anime.slug) > 0
            if (isFav) favDao.delete(anime.slug)
            else favDao.insert(Favorite(slug = anime.slug, title = anime.title, coverUrl = anime.coverUrl))
            _detail.value = _detail.value.copy(isFavorite = !isFav)
            loadFavorites()
        }
    }

    // D1: change the collection (status) of a favorite. Inserts the favorite
    // first if it doesn't exist, so the user can pick a status directly without
    // having to ♥ the anime first.
    fun setFavoriteStatus(anime: Anime, status: String) {
        viewModelScope.launch {
            val existing = favDao.get(anime.slug)
            if (existing == null) {
                favDao.insert(
                    Favorite(
                        slug = anime.slug, title = anime.title,
                        coverUrl = anime.coverUrl, status = status
                    )
                )
                _detail.value = _detail.value.copy(isFavorite = true)
            } else {
                favDao.updateStatus(anime.slug, status)
            }
            loadFavorites()
        }
    }

    // Exposed for the detail screen so the status dropdown can pre-select the
    // current value. null = not a favorite yet.
    suspend fun getFavoriteStatus(slug: String): String? = favDao.get(slug)?.status

    fun saveProgress(slug: String, title: String, cover: String, episode: Int, posMs: Long, durMs: Long) {
        viewModelScope.launch {
            val item = WatchHistory(
                key = "${slug}_$episode", animeSlug = slug, animeTitle = title, animeCover = cover,
                episodeNumber = episode, positionMs = posMs, durationMs = durMs
            )
            historyDao.upsert(item)
            _episodeProgress.value = _episodeProgress.value + (episode to item)
            loadHistory()
        }
    }

    suspend fun getProgress(slug: String, episode: Int): WatchHistory? =
        historyDao.get("${slug}_$episode")

    private fun loadHistory() {
        viewModelScope.launch { _history.value = historyDao.getAll() }
    }

    private fun loadFavorites() {
        viewModelScope.launch { _favorites.value = favDao.getAll() }
    }
}
