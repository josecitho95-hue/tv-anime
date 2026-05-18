package com.jkanimetv.app.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.security.KeyStore
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

class JKAnimeRepository {

    private val BASE  = "https://jkanime.net"
    private val BASE2 = "https://www.jkanime.net"  // used for AJAX endpoints

    // Replicates the original APK: minimal headers, single shared client.
    // No Referer, no Accept-Language, no X-Requested-With — jkanime serves a
    // different/empty body when those headers are present.
    private val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36"

    // Wraps the system trust manager but swallows OCSP / revocation-check
    // failures. Some emulators (and TVs that have been offline for a while)
    // reject Cloudflare's chain with "Response is unreliable: its validity
    // interval is out-of-date", which kills every request before it reaches
    // the server. The chain itself is still validated normally.
    private val lenientTrustManager: X509TrustManager = object : X509TrustManager {
        private val default: X509TrustManager = run {
            val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            tmf.init(null as KeyStore?)
            tmf.trustManagers.first { it is X509TrustManager } as X509TrustManager
        }
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) =
            default.checkClientTrusted(chain, authType)
        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
            try {
                default.checkServerTrusted(chain, authType)
            } catch (e: CertificateException) {
                val msgs = sequence {
                    var t: Throwable? = e
                    while (t != null) { yield(t.message ?: ""); t = t.cause }
                }.joinToString(" | ")
                val isRevocation = msgs.contains("OCSP", ignoreCase = true) ||
                    msgs.contains("revoc", ignoreCase = true) ||
                    msgs.contains("validity interval", ignoreCase = true) ||
                    msgs.contains("unreliable", ignoreCase = true)
                if (!isRevocation) throw e
                Log.w("JKAnimeRepo", "TLS revocation check skipped: $msgs")
            }
        }
        override fun getAcceptedIssuers(): Array<X509Certificate> = default.acceptedIssuers
    }

    private val sslSocketFactory = SSLContext.getInstance("TLS").apply {
        init(null, arrayOf<javax.net.ssl.TrustManager>(lenientTrustManager), java.security.SecureRandom())
    }.socketFactory

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .sslSocketFactory(sslSocketFactory, lenientTrustManager)
        .cookieJar(object : okhttp3.CookieJar {
            // Dedup by name+domain but send ALL cookies to ALL hosts
            // (jkanime / www.jkanime / jkdesa / jkplayer share a session).
            private val store = mutableMapOf<String, okhttp3.Cookie>()
            @Synchronized
            override fun saveFromResponse(url: okhttp3.HttpUrl, cookies: List<okhttp3.Cookie>) {
                cookies.forEach { c -> store["${c.name}|${c.domain}"] = c }
            }
            @Synchronized
            override fun loadForRequest(url: okhttp3.HttpUrl): List<okhttp3.Cookie> =
                store.values.toList()
        })
        .addInterceptor { chain ->
            val req = chain.request().newBuilder()
                .header("User-Agent", UA)
                .build()
            chain.proceed(req)
        }
        .build()

    // GET — adds browser-ish headers so Cloudflare doesn't gate the page.
    // The original APK only sends User-Agent and works on physical Android TVs,
    // but emulators get a stricter CF challenge; Accept-Language + Accept help.
    @Suppress("UNUSED_PARAMETER")
    private suspend fun get(url: String, referer: String = BASE): String = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder().url(url)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "es-ES,es;q=0.9,en;q=0.8")
                .get()
                .build()
            val resp = client.newCall(req).execute()
            val code = resp.code
            val body = resp.use { it.body?.string() ?: "" }
            Log.d("JKAnimeRepo", "GET $url -> $code (${body.length} bytes)")
            if (code !in 200..299) {
                Log.w("JKAnimeRepo", "GET non-2xx, first 200 chars: ${body.take(200)}")
            }
            body
        }.getOrElse {
            Log.e("JKAnimeRepo", "GET $url threw", it)
            ""
        }
    }

    // POST — kept minimal to match the original APK (jkanime's WAF rejects
    // requests that include Referer / X-Requested-With from off-browser clients).
    @Suppress("UNUSED_PARAMETER")
    private suspend fun post(url: String, params: Map<String, String>, referer: String = BASE): String =
        withContext(Dispatchers.IO) {
            runCatching {
                val body = FormBody.Builder().apply { params.forEach { (k, v) -> add(k, v) } }.build()
                val req = Request.Builder().url(url).post(body)
                    .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                    .build()
                val resp = client.newCall(req).execute()
                val code = resp.code
                val text = resp.use { it.body?.string() ?: "" }
                Log.d("JKAnimeRepo", "POST $url -> $code (${text.length} bytes)")
                if (code !in 200..299) {
                    Log.w("JKAnimeRepo", "POST non-2xx, first 200 chars: ${text.take(200)}")
                }
                text
            }.getOrElse {
                Log.e("JKAnimeRepo", "POST $url threw", it)
                ""
            }
        }

    // Unescape HTML entities — matches the original app's 8-step chain
    private fun unescape(s: String) = s
        .replace("&#039;", "'").replace("&quot;", "\"").replace("&amp;", "&")
        .replace("&lt;", "<").replace("&gt;", ">").replace("&nbsp;", " ")
        .replace("&#x27;", "'").replace("&#x2F;", "/")

    // ── HOME ─────────────────────────────────────────────────────────────────

    suspend fun getRecentAnime(): List<AnimeSection> = runCatching {
        val html = get("$BASE/")
        val sections = mutableListOf<AnimeSection>()

        // Extract the three sections exactly as the original app does
        val idxAnimes     = html.indexOf("id=\"animes\"")
        val idxDonghuas   = html.indexOf("id=\"donghuas\"", idxAnimes)
        val idxOvas       = html.indexOf("id=\"ovas\"", idxDonghuas)
        val idxEnd        = html.indexOf("<div class=\"section-title\">", idxOvas)

        if (idxAnimes == -1) {
            Log.w("JKAnimeRepo", "getRecentAnime: id=\"animes\" not found in ${html.length}-byte HTML; head=${html.take(200)}")
            return@runCatching emptyList()
        }

        val animeSection   = if (idxAnimes  != -1 && idxDonghuas != -1) html.substring(idxAnimes, idxDonghuas) else ""
        val donghuaSection = if (idxDonghuas != -1 && idxOvas     != -1) html.substring(idxDonghuas, idxOvas)  else ""
        val ovaSection     = if (idxOvas     != -1 && idxEnd      != -1) html.substring(idxOvas, idxEnd)       else ""

        if (animeSection.isNotBlank())   sections.add(AnimeSection("Programación de Anime",    extractAnimes(animeSection,   skipDate = false)))
        if (donghuaSection.isNotBlank()) sections.add(AnimeSection("Programación de Donghuas", extractAnimes(donghuaSection, skipDate = false)))
        if (ovaSection.isNotBlank())     sections.add(AnimeSection("Programación de Ovas",     extractAnimes(ovaSection,     skipDate = true)))

        sections
    }.getOrElse { emptyList() }

    // Replicates HomeFragment.ExtractorSerie()
    private fun extractAnimes(html: String, skipDate: Boolean): List<Anime> {
        val result = mutableListOf<Anime>()
        var pos = 0
        while (true) {
            // Find anime URL — search for jkanime.net link
            val urlStart = html.indexOf("https://jkanime.net/", pos)
            if (urlStart == -1) break
            val urlEnd = html.indexOf("\"", urlStart)
            if (urlEnd == -1) break
            val animeUrl = html.substring(urlStart, urlEnd)

            // Find large video thumbnail image
            var imageUrl = ""
            val imgStart = html.indexOf("https://cdn.jkdesa.com/assets/images/animes/video/image/", urlStart)
            if (imgStart != -1 && imgStart < urlStart + 2000) {
                val imgEnd = html.indexOf("\"", imgStart)
                if (imgEnd != -1) imageUrl = html.substring(imgStart, imgEnd)
            }

            // Fallback to small image
            if (imageUrl.isEmpty()) {
                val img2Start = html.indexOf("https://cdn.jkdesa.com/", urlStart)
                if (img2Start != -1 && img2Start < urlStart + 2000) {
                    val img2End = html.indexOf("\"", img2Start)
                    if (img2End != -1) imageUrl = html.substring(img2Start, img2End)
                }
            }

            // Find title
            val titleMarker = "<h5 class=\"strlimit card-title\">"
            val titleStart = html.indexOf(titleMarker, urlStart)
            if (titleStart == -1 || titleStart > urlStart + 3000) { pos = urlEnd + 1; continue }
            val titleEnd = html.indexOf("</h5></div>", titleStart + titleMarker.length)
            if (titleEnd == -1) { pos = urlEnd + 1; continue }
            var title = html.substring(titleStart + titleMarker.length, titleEnd)
            if (title.length > 70) title = title.substring(0, 67).trim() + "..."
            title = unescape(title)

            // Strip episode number to get anime page URL
            val lastSegment = animeUrl.trimEnd('/').substringAfterLast('/')
            val isEpisode = lastSegment.all { it.isDigit() }
            val animePageUrl = if (isEpisode) {
                animeUrl.trimEnd('/').substringBeforeLast('/') + "/"
            } else animeUrl

            val episodeSubtitle = if (isEpisode) "Episodio $lastSegment" else ""

            result.add(Anime(
                slug = animePageUrl,
                title = title,
                coverUrl = imageUrl,
                synopsis = episodeSubtitle
            ))

            pos = titleEnd + 1
        }
        return result
    }

    // ── ANIME DETAIL ─────────────────────────────────────────────────────────

    // Fetches anime detail page and returns metadata + episode count via AJAX
    // Replicates DetailFragment$jkanimeFetch$1.invoke2()
    suspend fun getAnimeDetail(animeUrl: String): Anime? = runCatching {
        val html = get(animeUrl)
        if (html.isEmpty()) return@runCatching null

        // Cover image — from: <div class="anime_pic pc" style="display: none;"><img src="https://cdn.jkdesa.com/assets/images/animes/image/
        val coverMarker = "<div class=\"anime_pic pc\" style=\"display: none;\"><img src=\"https://cdn.jkdesa.com/assets/images/animes/image/"
        val coverOffset = html.indexOf(coverMarker)
        val coverUrl = if (coverOffset != -1) {
            val start = coverOffset + coverMarker.length - "https://cdn.jkdesa.com/assets/images/animes/image/".length
            val end = html.indexOf("\"", start)
            if (end != -1) html.substring(start, end) else ""
        } else ""

        // Title — from: <div class="anime_info"> → <h3> ... </h3>
        val infoMarker = "<div class=\"anime_info\">"
        val infoIdx = html.indexOf(infoMarker)
        val h3Start = if (infoIdx != -1) html.indexOf("<h3>", infoIdx) else -1
        val title = if (h3Start != -1) {
            val h3End = html.indexOf("</h3>", h3Start + 4)
            if (h3End != -1) unescape(html.substring(h3Start + 4, h3End).trim()) else animeUrl.trimEnd('/').substringAfterLast('/')
        } else animeUrl.trimEnd('/').substringAfterLast('/')

        // Genre/subtitle — first <span> after </h3>
        val spanStart = if (h3Start != -1) html.indexOf("<span>", h3Start) else -1
        val genre = if (spanStart != -1) {
            val spanEnd = html.indexOf("</span>", spanStart)
            if (spanEnd != -1) unescape(html.substring(spanStart + 6, spanEnd).trim()) else ""
        } else ""

        // Synopsis — from: <p class="scroll"> ... </p>
        val synopsisMarker = "<p class=\"scroll\">"
        val synopsisStart = html.indexOf(synopsisMarker)
        val synopsis = if (synopsisStart != -1) {
            val end = html.indexOf("</p>", synopsisStart)
            if (end != -1) unescape(html.substring(synopsisStart + synopsisMarker.length, end).trim()) else ""
        } else ""

        // Status
        val status = if (html.indexOf(">En emision</div></li") != -1) "En emisión" else "Finalizado"

        Anime(
            slug = animeUrl,
            title = title,
            coverUrl = coverUrl,
            synopsis = synopsis,
            genre = genre,
            status = status
        )
    }.getOrNull()

    // Episode list — replicates the original APK's flow exactly:
    //  1. GET the anime page (already opened to fill detail).
    //  2. Find the literal "ajax/episodes/<id>/" path inside the page JS.
    //  3. Read csrf-token from <meta>.
    //  4. POST {_token} to https://jkanime.net/ajax/episodes/<id>/1/ — NOTE:
    //     the original APK used www.jkanime.net, but Cloudflare now 301-redirects
    //     www → bare host and OkHttp drops the body on a POST redirect. Posting
    //     directly to the bare host returns the JSON 200.
    //  5. Read "total":N from the JSON, applying the haveZero heuristic for
    //     animes whose first episode is 0 (the original ":0," detector — kept
    //     because the original app ships with it and it works on real animes).
    suspend fun getEpisodeList(animeUrl: String): List<Episode> = runCatching {
        val html = get(animeUrl)
        if (html.isEmpty()) {
            Log.w("JKAnimeRepo", "getEpisodeList: empty HTML for $animeUrl")
            return@runCatching emptyList()
        }
        val first = parseEpisodesFromHtml(html, animeUrl)
        if (first.isNotEmpty()) return@runCatching first

        // Sometimes the first GET returns a stale or CSRF-mismatched session
        // (cookie set in this response can't sign the immediate POST). Retry once
        // with a fresh GET so the POST uses cookies from a confirmed session.
        val freshHtml = get(animeUrl)
        val second = parseEpisodesFromHtml(freshHtml, animeUrl)
        if (second.isEmpty()) {
            Log.w("JKAnimeRepo", "getEpisodeList: empty result for $animeUrl " +
                "(hasAjax=${html.contains("ajax/episodes/")}, " +
                "hasCsrf=${html.contains("csrf-token")})")
        }
        second
    }.onFailure {
        Log.e("JKAnimeRepo", "getEpisodeList crashed for $animeUrl", it)
    }.getOrElse { emptyList() }

    private suspend fun parseEpisodesFromHtml(html: String, animeUrl: String): List<Episode> {
        if (html.isEmpty()) return emptyList()

        val ajaxIdx = html.indexOf("ajax/episodes/")
        if (ajaxIdx == -1) {
            Log.w("JKAnimeRepo", "parseEpisodesFromHtml: no ajax/episodes path for $animeUrl")
            return emptyList()
        }
        val ajaxEnd = html.indexOf("'", ajaxIdx)
        if (ajaxEnd == -1) return emptyList()
        // Keep trailing slash so URL becomes ".../ajax/episodes/<id>/1/" verbatim,
        // exactly as the original APK builds it.
        val ajaxPath = html.substring(ajaxIdx, ajaxEnd)

        val csrfMarker = "csrf-token\" content=\""
        val csrfIdx = html.indexOf(csrfMarker)
        val csrfToken = if (csrfIdx != -1) {
            val start = csrfIdx + csrfMarker.length
            val end = html.indexOf("\"", start)
            if (end != -1) html.substring(start, end) else ""
        } else ""
        if (csrfToken.isEmpty()) {
            Log.w("JKAnimeRepo", "parseEpisodesFromHtml: no CSRF token for $animeUrl")
            return emptyList()
        }

        val url = "$BASE/$ajaxPath" + "1/"
        val response = post(url, mapOf("_token" to csrfToken))
        if (response.isEmpty()) {
            Log.w("JKAnimeRepo", "parseEpisodesFromHtml: empty POST response from $url")
            return emptyList()
        }
        if (response.contains("CSRF token mismatch")) {
            Log.w("JKAnimeRepo", "parseEpisodesFromHtml: CSRF mismatch from $url")
            return emptyList()
        }

        // Original APK parser, line-for-line:
        //   haveZero = response.contains(":0,")
        //   total    = first integer after "\"total\":"  (default 1 on parse failure)
        //   adjusted = haveZero ? total-1 : total
        //   range    = haveZero ? 0..adjusted : 1..adjusted
        val haveZero = response.contains(":0,")
        val totalIdx = response.indexOf("\"total\":")
        val total = if (totalIdx != -1) {
            val start = totalIdx + 8
            val end = response.indexOf("}", start)
            if (end != -1) response.substring(start, end).trim().toIntOrNull() ?: 1 else 1
        } else 1

        if (total <= 0) {
            Log.w("JKAnimeRepo", "parseEpisodesFromHtml: total=$total from $url, response head=${response.take(120)}")
            return emptyList()
        }

        val startEp = if (haveZero) 0 else 1
        val endEp = if (haveZero) total - 1 else total
        return (startEp..endEp).map { Episode(animeSlug = animeUrl, number = it) }
    }

    // ── VIDEO LINK — the real 2-step jkplayer extraction ────────────────────

    // Step 1: Get episode page, extract jkplayer URL
    // Step 2: Fetch jkplayer, extract direct video URL
    // Replicates DetailFragment.parseEpisodes$lambda$4 and its inner lambdas
    suspend fun getVideoUrl(animeUrl: String, episode: Int): String? = runCatching {
        val episodeUrl = if (animeUrl.endsWith("/")) "$animeUrl$episode/" else "$animeUrl/$episode/"
        val html = get(episodeUrl, referer = animeUrl)
        if (html.isEmpty()) return@runCatching null

        // Step 1: find jkplayer URL
        val playerMarker = "https://jkanime.net/jkplayer/um?e="
        val playerIdx = html.indexOf(playerMarker)
        if (playerIdx == -1) return@runCatching null
        val playerEnd = html.indexOf("\"", playerIdx)
        if (playerEnd == -1) return@runCatching null
        val playerUrl = html.substring(playerIdx, playerEnd)

        // Step 2: fetch jkplayer, find video URL
        val playerHtml = get(playerUrl, referer = episodeUrl)
        if (playerHtml.isEmpty()) return@runCatching null

        val videoMarker = "url: 'https://"
        val searchIn = if (playerHtml.contains(videoMarker)) playerHtml else html
        val s = searchIn.indexOf(videoMarker)
        if (s == -1) return@runCatching null
        val start = s + 6  // skip "url: '"
        val end = searchIn.indexOf("'", start)
        if (end == -1) return@runCatching null
        searchIn.substring(start, end)
    }.getOrNull()

    // ── SEARCH ───────────────────────────────────────────────────────────────

    suspend fun search(query: String): List<Anime> = runCatching {
        val encoded = query.trim().replace(" ", "_")
        val html = get("$BASE/buscar/$encoded/")
        if (html.isEmpty()) return@runCatching emptyList()

        // Primary: NX anime__item containers (shared parser)
        val primary = parseAnimeItemList(html)
        if (primary.isNotEmpty()) return@runCatching primary

        // Fallback: scan jkanime.net URLs + cdn.jkdesa.com images + card-title
        val result = mutableListOf<Anime>()
        var pos = 0
        while (true) {
            val urlStart = html.indexOf("https://jkanime.net/", pos)
            if (urlStart == -1) break
            val urlEnd = html.indexOf("\"", urlStart)
            if (urlEnd == -1) break
            val animeUrl = html.substring(urlStart, urlEnd)
            if (animeUrl.contains("buscar") || animeUrl == "$BASE/") { pos = urlEnd + 1; continue }

            val imgIdx = html.indexOf("https://cdn.jkdesa.com/", urlStart)
            val imageUrl = if (imgIdx != -1 && imgIdx < urlStart + 2000) {
                val e = html.indexOf("\"", imgIdx); if (e != -1) html.substring(imgIdx, e) else ""
            } else ""

            val titleMarker = "<h5 class=\"strlimit card-title\">"
            val titleStart = html.indexOf(titleMarker, urlStart)
            if (titleStart == -1 || titleStart > urlStart + 3000) { pos = urlEnd + 1; continue }
            val titleEnd = html.indexOf("</h5></div>", titleStart + titleMarker.length)
            if (titleEnd == -1) { pos = urlEnd + 1; continue }
            val title = unescape(html.substring(titleStart + titleMarker.length, titleEnd).trim())

            result.add(Anime(slug = animeUrl, title = title, coverUrl = imageUrl))
            pos = titleEnd + 1
        }
        result.distinctBy { it.slug }
    }.getOrElse { emptyList() }

    // ── SCHEDULE / HORARIO ───────────────────────────────────────────────────

    suspend fun getSchedule(): List<Schedule> = runCatching {
        // NX normalizes "filtro" class (today's highlighted day) before splitting
        val raw = get("$BASE/horario")
        if (raw.isEmpty()) {
            Log.w("JKAnimeRepo", "getSchedule: empty HTML from /horario")
            return@runCatching emptyList()
        }
        if (!raw.contains("ti-calendar-clock")) {
            Log.w("JKAnimeRepo", "getSchedule: marker 'ti-calendar-clock' missing in ${raw.length}-byte HTML; head=${raw.take(200)}")
            return@runCatching emptyList()
        }
        val html = raw.replace("<div class='box semana filtro'>", "<div class='box semana'>")
        val days = listOf("Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo")
        days.mapNotNull { day ->
            val startMarker = "<h2><i class=\"ti ti-calendar-clock\"></i> $day</h2>"
            val startIdx = html.indexOf(startMarker)
            if (startIdx == -1) return@mapNotNull null
            val endIdx = html.indexOf("<div class='box semana", startIdx + startMarker.length)
            val section = if (endIdx != -1) html.substring(startIdx, endIdx) else html.substring(startIdx)
            val animes = extractHorarioAnimes(section)
            if (animes.isEmpty()) null else Schedule(dayName = day, animes = animes)
        }
    }.getOrElse { emptyList() }

    // Replicates TvShowFragment.ExtractorSerie() — different HTML structure from home page
    private fun extractHorarioAnimes(html: String): List<Anime> {
        val result = mutableListOf<Anime>()
        var pos = 0
        val urlMarker = "boxx\"><a href=\"https://jkanime.net/"
        while (true) {
            val urlIdx = html.indexOf(urlMarker, pos)
            if (urlIdx == -1) break
            val urlStart = urlIdx + 15  // skip: boxx"><a href="  (15 chars)
            val urlEnd = html.indexOf("\"><img title=", urlStart)
            if (urlEnd == -1) break
            val animeUrl = html.substring(urlStart, urlEnd)

            val imgMarker = "https://cdn.jkdesa.com/assets/images/animes/image/"
            val imgIdx = html.indexOf(imgMarker, urlEnd)
            val imageUrl = if (imgIdx != -1 && imgIdx < urlEnd + 3000) {
                val e = html.indexOf("\"", imgIdx); if (e != -1) html.substring(imgIdx, e) else ""
            } else ""

            val titleMarker = "<img title=\""
            val titleIdx = html.indexOf(titleMarker, urlEnd)
            if (titleIdx == -1 || titleIdx > urlEnd + 3000) { pos = urlEnd + 1; continue }
            val titleStart = titleIdx + titleMarker.length
            val titleEnd = html.indexOf("\"", titleStart)
            if (titleEnd == -1) { pos = urlEnd + 1; continue }
            var title = html.substring(titleStart, titleEnd)
            if (title.length > 70) title = title.substring(0, 67).trim() + "..."
            title = unescape(title)

            val epMarker = "<span>Último capítulo:"
            val epIdx = html.indexOf(epMarker, titleEnd)
            val episode = if (epIdx != -1 && epIdx < titleEnd + 3000) {
                val epStart = epIdx + epMarker.length
                val epEnd = html.indexOf("<", epStart)
                if (epEnd != -1) html.substring(epStart, epEnd).trim() else ""
            } else ""

            // Ensure we have the anime page URL (strip episode number if present)
            val lastSegment = animeUrl.trimEnd('/').substringAfterLast('/')
            val animePageUrl = if (lastSegment.all { it.isDigit() }) {
                animeUrl.trimEnd('/').substringBeforeLast('/') + "/"
            } else if (animeUrl.endsWith("/")) animeUrl else "$animeUrl/"

            result.add(Anime(
                slug = animePageUrl,
                title = title,
                coverUrl = imageUrl,
                synopsis = if (episode.isNotBlank()) "Ep: $episode" else ""
            ))
            pos = titleEnd + 1
        }
        return result
    }

    // ── TOP ANIME ────────────────────────────────────────────────────────────

    suspend fun getTopAnime(): List<Anime> = runCatching {
        val html = get("$BASE/top/")
        val result = mutableListOf<Anime>()
        var pos = 0
        while (true) {
            val urlStart = html.indexOf("https://jkanime.net/", pos)
            if (urlStart == -1) break
            val urlEnd = html.indexOf("\"", urlStart)
            if (urlEnd == -1) break
            val animeUrl = html.substring(urlStart, urlEnd)
            if (animeUrl.contains("/top") || animeUrl == "$BASE/") { pos = urlEnd + 1; continue }

            val imgIdx = html.indexOf("https://cdn.jkdesa.com/", urlStart)
            val imageUrl = if (imgIdx != -1 && imgIdx < urlStart + 2000) {
                val e = html.indexOf("\"", imgIdx); if (e != -1) html.substring(imgIdx, e) else ""
            } else ""

            val titleMarker = "<h5 class=\"strlimit card-title\">"
            val titleStart = html.indexOf(titleMarker, urlStart)
            if (titleStart == -1 || titleStart > urlStart + 3000) { pos = urlEnd + 1; continue }
            val titleEnd = html.indexOf("</h5></div>", titleStart + titleMarker.length)
            if (titleEnd == -1) { pos = urlEnd + 1; continue }
            val title = unescape(html.substring(titleStart + titleMarker.length, titleEnd).trim())

            result.add(Anime(slug = animeUrl, title = title, coverUrl = imageUrl))
            pos = titleEnd + 1
        }
        result.distinctBy { it.slug }
    }.getOrElse { emptyList() }

    // ── DIRECTORY ────────────────────────────────────────────────────────────

    // Directory listing. jkanime renders the cards client-side from a JSON blob
    // embedded as `var animes = {...}` in /directorio/1/. The path segment /1/ is
    // a constant listing id (NOT the page) — pagination is driven by ?p=N.
    suspend fun getDirectory(page: Int = 1, genre: String = "", type: String = "", status: String = ""): List<Anime> = runCatching {
        val params = mutableListOf("p=$page")
        if (genre.isNotBlank())  params.add("genero=$genre")
        if (type.isNotBlank())   params.add("tipo=$type")
        if (status.isNotBlank()) params.add("estado=$status")
        val url = "$BASE/directorio/1/?" + params.joinToString("&")
        parseDirectoryJson(get(url))
    }.onFailure {
        Log.e("JKAnimeRepo", "getDirectory failed", it)
    }.getOrElse { emptyList() }

    private fun parseDirectoryJson(html: String): List<Anime> {
        if (html.isEmpty()) return emptyList()
        val marker = "var animes = "
        val start = html.indexOf(marker)
        if (start == -1) {
            Log.w("JKAnimeRepo", "parseDirectoryJson: 'var animes =' missing in ${html.length}-byte response; head=${html.take(200)}")
            return emptyList()
        }
        val jsonStart = start + marker.length
        val jsonEnd = findMatchingBrace(html, jsonStart)
        if (jsonEnd == -1) {
            Log.w("JKAnimeRepo", "parseDirectoryJson: unbalanced JSON block at $jsonStart")
            return emptyList()
        }
        val json = html.substring(jsonStart, jsonEnd + 1)
        val root = org.json.JSONObject(json)
        val arr = root.optJSONArray("data") ?: return emptyList()
        val out = ArrayList<Anime>(arr.length())
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val rawUrl = obj.optString("url")
            if (rawUrl.isNullOrBlank()) continue
            out.add(
                Anime(
                    slug = if (rawUrl.endsWith("/")) rawUrl else "$rawUrl/",
                    title = unescape(obj.optString("title")),
                    coverUrl = obj.optString("image"),
                    synopsis = unescape(obj.optString("synopsis")).take(300),
                    genre = obj.optString("tipo"),
                    status = obj.optString("estado"),
                    type = obj.optString("type")
                )
            )
        }
        return out
    }

    // Walks forward from a position pointing AT the opening '{' (or just before
    // it) and returns the index of the matching closing '}', respecting JSON
    // string escapes so braces inside synopses don't throw off the count.
    // Returns -1 if no opening brace is found or the structure is unbalanced.
    private fun findMatchingBrace(s: String, from: Int): Int {
        var i = from
        while (i < s.length && s[i] != '{') i++
        if (i >= s.length) return -1
        var depth = 0
        var inString = false
        var escape = false
        while (i < s.length) {
            val c = s[i]
            if (inString) {
                when {
                    escape -> escape = false
                    c == '\\' -> escape = true
                    c == '"' -> inString = false
                }
            } else {
                when (c) {
                    '"' -> inString = true
                    '{' -> depth++
                    '}' -> { depth--; if (depth == 0) return i }
                }
            }
            i++
        }
        return -1
    }

    // Shared parser for listing pages that use the jkanime `anime__item` card layout
    // (search results, directory). Matches NX-style markers.
    private fun parseAnimeItemList(html: String): List<Anime> {
        val result = mutableListOf<Anime>()
        var pos = 0
        val containerMarker = "<div class=\"anime__item\">"
        while (true) {
            val containerIdx = html.indexOf(containerMarker, pos)
            if (containerIdx == -1) break

            val setBgMarker = "data-setbg=\""
            val setBgIdx = html.indexOf(setBgMarker, containerIdx)
            val imageUrl = if (setBgIdx != -1 && setBgIdx < containerIdx + 1000) {
                val s = setBgIdx + setBgMarker.length
                val e = html.indexOf("\"", s)
                if (e != -1) html.substring(s, e) else ""
            } else ""

            val hrefMarker = "href=\"https://jkanime.net/"
            val hrefIdx = html.indexOf(hrefMarker, containerIdx)
            if (hrefIdx == -1 || hrefIdx > containerIdx + 2000) { pos = containerIdx + containerMarker.length; continue }
            val urlStart = hrefIdx + 6
            val urlEnd = html.indexOf("\"", urlStart)
            if (urlEnd == -1) { pos = containerIdx + containerMarker.length; continue }
            val animeUrl = html.substring(urlStart, urlEnd)

            val h5Marker = "<h5>"
            val h5Idx = html.indexOf(h5Marker, containerIdx)
            if (h5Idx == -1 || h5Idx > containerIdx + 2000) { pos = containerIdx + containerMarker.length; continue }
            val titleEnd = html.indexOf("</h5>", h5Idx + h5Marker.length)
            if (titleEnd == -1) { pos = containerIdx + containerMarker.length; continue }
            val title = unescape(html.substring(h5Idx + h5Marker.length, titleEnd).trim())

            if (title.isNotBlank()) result.add(Anime(slug = animeUrl, title = title, coverUrl = imageUrl))
            pos = containerIdx + containerMarker.length
        }
        return result.distinctBy { it.slug }
    }
}

data class AnimeSection(val title: String, val animes: List<Anime>)
