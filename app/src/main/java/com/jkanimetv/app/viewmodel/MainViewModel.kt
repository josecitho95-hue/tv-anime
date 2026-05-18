package com.jkanimetv.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jkanimetv.app.data.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = JKAnimeRepository()
    private val db = AppDatabase.getInstance(app)
    private val historyDao = db.watchHistoryDao()
    private val favDao = db.favoriteDao()

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

    private val _favorites = MutableStateFlow<List<Favorite>>(emptyList())
    val favorites: StateFlow<List<Favorite>> = _favorites

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

    fun selectAnime(url: String) {
        _currentAnimeUrl.value = url
        _detail.value = DetailUiState(isLoading = true)
        _videoUrl.value = VideoUrlState()
    }

    init {
        loadHome()
        loadHistory()
        loadFavorites()
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

    fun search(query: String) {
        if (query.isBlank()) { _search.value = SearchUiState(); return }
        viewModelScope.launch {
            _search.value = SearchUiState(query = query, isLoading = true)
            runCatching {
                val results = repo.search(query)
                _search.value = SearchUiState(query = query, results = results)
            }.onFailure {
                _search.value = SearchUiState(query = query, error = it.message)
            }
        }
    }

    fun loadDetail(animeUrl: String) {
        detailJob?.cancel()
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
            }.onFailure {
                _detail.value = DetailUiState(error = it.message ?: "Error cargando anime")
            }
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

    fun saveProgress(slug: String, title: String, cover: String, episode: Int, posMs: Long, durMs: Long) {
        viewModelScope.launch {
            historyDao.upsert(WatchHistory(
                key = "${slug}_$episode", animeSlug = slug, animeTitle = title, animeCover = cover,
                episodeNumber = episode, positionMs = posMs, durationMs = durMs
            ))
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
