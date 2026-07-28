package com.garfiec.librechat.core.data.repository

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.model.Banner

/** Fetches server-configured banners for display in the app. */
interface BannerRepository {
    suspend fun getBanners(): Result<List<Banner>>
}
