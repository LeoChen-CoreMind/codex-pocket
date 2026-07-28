package com.garfiec.librechat.core.network.api

import com.garfiec.librechat.core.model.Banner
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.path

class BannerApi constructor(
    private val client: HttpClient,
) {
    suspend fun getBanners(): List<Banner> =
        client.get {
            url { path("api/banner") }
        }.body()
}
