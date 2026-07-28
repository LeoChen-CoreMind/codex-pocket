package com.garfiec.librechat.shared.navigation

import co.touchlab.kermit.Logger
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.BannerRepository
import com.garfiec.librechat.core.model.Banner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Instant

class BannerStateHolder(
    private val bannerRepository: BannerRepository,
    private val scope: CoroutineScope,
) {

    private val _banners = MutableStateFlow<List<Banner>>(emptyList())
    val banners: StateFlow<List<Banner>> = _banners.asStateFlow()

    private val _dismissedBannerIds = MutableStateFlow<Set<String>>(emptySet())
    val dismissedBannerIds: StateFlow<Set<String>> = _dismissedBannerIds.asStateFlow()

    fun fetchBanners() {
        scope.launch {
            try {
                val result = bannerRepository.getBanners()
                if (result is Result.Success) {
                    val now = Clock.System.now()
                    _banners.value = result.data.filter { banner ->
                        val from = banner.displayFrom?.let {
                            runCatching { Instant.parse(it) }
                                .onFailure { e -> Logger.w(e) { "Failed to parse banner displayFrom: $it" } }
                                .getOrNull()
                        }
                        val to = banner.displayTo?.let {
                            runCatching { Instant.parse(it) }
                                .onFailure { e -> Logger.w(e) { "Failed to parse banner displayTo: $it" } }
                                .getOrNull()
                        }
                        val afterStart = from == null || now >= from
                        val beforeEnd = to == null || now < to
                        afterStart && beforeEnd
                    }
                }
            } catch (e: Exception) {
                Logger.w(e) { "Failed to fetch banners" }
            }
        }
    }

    fun dismissBanner(bannerId: String) {
        _dismissedBannerIds.update { it + bannerId }
    }
}
