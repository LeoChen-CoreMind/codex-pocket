package com.garfiec.librechat.core.common.network

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Contract tests for ConnectivityObserver interface.
 * Uses a fake implementation to verify Flow behavior.
 */
class ConnectivityObserverContractTest {

    private class FakeConnectivityObserver(initiallyConnected: Boolean = true) : ConnectivityObserver {
        private val _isConnected = MutableStateFlow(initiallyConnected)
        override val isConnected: Flow<Boolean> = _isConnected

        fun setConnected(connected: Boolean) {
            _isConnected.value = connected
        }
    }

    @Test
    fun `emits initial connected state`() = runTest {
        val observer = FakeConnectivityObserver(initiallyConnected = true)
        observer.isConnected.test {
            assertThat(awaitItem()).isTrue()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `emits initial disconnected state`() = runTest {
        val observer = FakeConnectivityObserver(initiallyConnected = false)
        observer.isConnected.test {
            assertThat(awaitItem()).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `emits false when connection is lost`() = runTest {
        val observer = FakeConnectivityObserver(initiallyConnected = true)
        observer.isConnected.test {
            assertThat(awaitItem()).isTrue()
            observer.setConnected(false)
            assertThat(awaitItem()).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `emits true when connection is restored`() = runTest {
        val observer = FakeConnectivityObserver(initiallyConnected = false)
        observer.isConnected.test {
            assertThat(awaitItem()).isFalse()
            observer.setConnected(true)
            assertThat(awaitItem()).isTrue()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `deduplicates consecutive identical states`() = runTest {
        val observer = FakeConnectivityObserver(initiallyConnected = true)
        observer.isConnected.test {
            assertThat(awaitItem()).isTrue()
            // Setting same value again should not emit
            observer.setConnected(true)
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `tracks multiple state transitions`() = runTest {
        val observer = FakeConnectivityObserver(initiallyConnected = true)
        observer.isConnected.test {
            assertThat(awaitItem()).isTrue()
            observer.setConnected(false)
            assertThat(awaitItem()).isFalse()
            observer.setConnected(true)
            assertThat(awaitItem()).isTrue()
            observer.setConnected(false)
            assertThat(awaitItem()).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
    }
}
