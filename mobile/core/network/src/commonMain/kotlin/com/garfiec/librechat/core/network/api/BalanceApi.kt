package com.garfiec.librechat.core.network.api

import com.garfiec.librechat.core.model.Balance
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.path

class BalanceApi constructor(
    private val client: HttpClient,
) {
    suspend fun getBalance(): Balance =
        client.get {
            url { path("api/balance") }
        }.body()
}
