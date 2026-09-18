package com.example
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

class SampleProvider : MainAPI() { 
    override var mainUrl = "https://www.hdfilmcehennemi.nl/"
    override var name = "Benim Eklentim"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    // Ana sayfada görünecek içerikleri listeleme fonksiyonu
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        val items = ArrayList<SearchResponse>()
        
        // Örnek bir film kartı ekleme
        items.add(MovieSearchResponse(
            name = "Örnek Film",
            url = "https://www.hdfilmcehennemi.nl/",
            apiName = this.name,
            type = TvType.Movie,
            posterUrl = "https://gorsel-linki.com"
        ))
        
        return HomePageResponse(arrayListOf(HomePageList("Öne Çıkanlar", items)), hasNext = false)
    }
}
