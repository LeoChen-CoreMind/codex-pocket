package com.garfiec.librechat.core.common.di

import android.app.Application
import android.content.Context
import org.junit.Test
import org.koin.test.verify.verify

class CommonModuleVerificationTest {
    @Test
    fun verifyCommonModule() {
        commonModule.verify(
            extraTypes = listOf(
                Context::class,
                Application::class,
            ),
        )
    }
}
