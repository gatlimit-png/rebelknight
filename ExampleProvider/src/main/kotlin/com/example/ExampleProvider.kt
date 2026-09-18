package com.example

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.Jsoup

class ExampleProvider : MainAPI() { 
    override var mainUrl = "https://hdfilmcehennemi.nl"
    override var name = "Hdfilmcehennemi"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    // 1. ANA SAYFA KAZIMA
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        val response = app.get(mainUrl).text
        val document = Jsoup.parse(response)
        val items = ArrayList<SearchResponse>()
        
        document.select("div.poster-populer, div.film-karti, div.movie-item").forEach { element ->
            val title = element.select("h2, h3, .film-adi, img").attr("alt").ifEmpty { element.select("strong, .title").text() }
            val videoUrl = element.select("a").attr("href")
            val poster = element.select("img").attr("data-src").ifEmpty { element.select("img").attr("src") }

            if (title.isNotEmpty() && videoUrl.isNotEmpty()) {
                items.add(newMovieSearchResponse(title.trim(), fixUrl(videoUrl), TvType.Movie) {
                    this.posterUrl = fixUrl(poster)
                })
            }
        }
        return newHomePageResponse("Yeni Eklenenler", items, hasNext = false)
    }

    // 2. DETAY SAYFASI VE BÖLÜM YÜKLEME
    override suspend fun load(url: String): LoadResponse? {
        val response = app.get(url).text
        val document = Jsoup.parse(response)

        val title = document.select("h1, .film-adi").text().trim()
        val description = document.select(".ozet, p.description, .storyline").text().trim()
        val poster = document.select("div.poster img, .cover img").attr("src")

        val isSeries = url.contains("/dizi/") || document.select(".season-list, .bolumler").isNotEmpty()

        return if (isSeries) {
            val episodesList = ArrayList<Episode>()
            document.select(".bolumler a, .episode-item").forEach { element ->
                val epUrl = element.attr("href")
                val epName = element.text().trim()
                episodesList.add(newEpisode(fixUrl(epUrl)) {
                    this.name = epName
                })
            }
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodesList) {
                this.posterUrl = fixUrl(poster)
                this.plot = description
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = fixUrl(poster)
                this.plot = description
            }
        }
    }

    // 3. VİDEO KAYNAKLARINI ÇÖZME
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val response = app.get(data).text
        val document = Jsoup.parse(response)

        document.select("iframe").forEach { element ->
            var playerUrl = element.attr("src")
            if (playerUrl.startsWith("//")) {
                playerUrl = "https:$playerUrl"
            }

            if (playerUrl.isNotEmpty() && playerUrl.contains("vidmoly")) {
                // Adlandırılmış parametre uyuşmazlığını aşmak için callback'e doğrudan nesne ataması yapıyoruz.
                // Bu yapı builder fonksiyonu yerine doğrudan sınıf üretecini kullanarak esneklik sağlar.
                callback.invoke(
                    ExtractorLink(
                        source = "Vidmoly",
                        name = "Vidmoly",
                        url = playerUrl,
                        referer = data,
                        quality = Qualities.Unknown.value
                    )
                )
            }
        }
        return true
    }
}
