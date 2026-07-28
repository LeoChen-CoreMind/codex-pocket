package com.garfiec.librechat.core.data.repository

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.common.result.safeApiCall
import com.garfiec.librechat.core.model.Balance
import com.garfiec.librechat.core.network.api.BalanceApi

class BalanceRepositoryImpl(
    private val balanceApi: BalanceApi,
) : BalanceRepository {

    override suspend fun getBalance(): Result<Balance> = safeApiCall {
        balanceApi.getBalance()
    }
}
