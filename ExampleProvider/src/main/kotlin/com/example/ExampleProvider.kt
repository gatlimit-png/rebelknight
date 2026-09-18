package com.example

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.Jsoup

class SampleProvider : MainAPI() { 
    override var mainUrl = "https://www.hdfilmcehennemi.nl"
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
                items.add(MovieSearchResponse(
                    name = title.trim(),
                    url = fixUrl(videoUrl),
                    apiName = this.name,
                    type = TvType.Movie,
                    posterUrl = fixUrl(poster)
                ))
            }
        }
        return HomePageResponse(arrayListOf(HomePageList("Yeni Eklenenler", items)), hasNext = false)
    }

    // 2. DETAY SAYFASI VE BÖLÜM YÜKLEME (load)
    override suspend fun load(url: String): LoadResponse? {
        val response = app.get(url).text
        val document = Jsoup.parse(response)

        // Detay sayfasından başlık, açıklama ve poster verilerini ayıklıyoruz
        val title = document.select("h1, .film-adi").text().trim()
        val description = document.select(".ozet, p.description, .storyline").text().trim()
        val poster = document.select("div.poster img, .cover img").attr("src")

        // Eğer içerik dizi ise dizi yapısında, film ise film yapısında Cloudstream'e bildiriyoruz
        val isSeries = url.contains("/dizi/") || document.select(".season-list, .bolumler").isNotEmpty()

        return if (isSeries) {
            val episodesList = ArrayList<Episode>()
            // Sitedeki sezon ve bölüm elementlerini döngüye alıyoruz
            document.select(".bolumler a, .episode-item").forEach { element ->
                val epUrl = element.attr("href")
                val epName = element.text().trim()
                episodesList.add(Episode(
                    data = fixUrl(epUrl),
                    name = epName
                ))
            }
            TvSeriesLoadResponse(
                name = title,
                url = url,
                apiName = this.name,
                type = TvType.TvSeries,
                posterUrl = fixUrl(poster),
                plot = description,
                episodes = episodesList
            )
        } else {
            MovieLoadResponse(
                name = title,
                url = url,
                apiName = this.name,
                type = TvType.Movie,
                dataUrl = url, // Video linki çözülecek asıl kaynak sayfa URL'si
                posterUrl = fixUrl(poster),
                plot = description
            )
        }
    }

    // 3. VİDEO KAYNAKLARINI ÇÖZME (loadLinks)
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val response = app.get(data).text
        val document = Jsoup.parse(response)

        // Sitedeki iframe (video oynatıcı) elementlerini veya player alternatif linklerini buluyoruz
        // Hdfilmcehennemi genellikle Vidmoly veya Rapidrame iframe'leri kullanır
        document.select("iframe, .player-container iframe, #player-holder").forEach { element ->
            var playerUrl = element.attr("src")
            if (playerUrl.startsWith("//")) {
                playerUrl = "https:$playerUrl"
            }

            // Cloudstream'in yerleşik "loadExtractor" motoru, popüler video servislerini (Vidmoly vb.) otomatik çözer
            if (playerUrl.isNotEmpty() && (playerUrl.contains("vidmoly") || playerUrl.contains("rapidrame") || playerUrl.contains("player"))) {
                loadExtractor(playerUrl, data, subtitleCallback, callback)
            }
        }
        return true
    }
}
