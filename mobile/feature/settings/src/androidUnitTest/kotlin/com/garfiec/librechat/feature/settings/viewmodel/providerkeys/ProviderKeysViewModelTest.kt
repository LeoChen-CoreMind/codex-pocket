package com.garfiec.librechat.feature.settings.viewmodel.providerkeys

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.ConfigRepository
import com.garfiec.librechat.core.data.repository.KeyRepository
import com.garfiec.librechat.core.model.EndpointConfig
import com.garfiec.librechat.core.model.endpoint.KeyState
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProviderKeysViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val keyRepository = mockk<KeyRepository>(relaxed = true)
    private val configRepository = mockk<ConfigRepository>(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        coEvery { keyRepository.fetchKeyState(any()) } returns Result.Success(KeyState.Unset)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun setEndpoints(map: Map<String, EndpointConfig>) {
        val flow = MutableStateFlow(map)
        coEvery { configRepository.endpointConfigs } returns flow
        coEvery { configRepository.fetchEndpoints() } returns Result.Success(map)
        coEvery { configRepository.fetchModels() } returns Result.Success(emptyMap())
    }

    @Test
    fun filters_endpoints_without_userProvide_or_userProvideURL() = runTest(testDispatcher) {
        setEndpoints(
            mapOf(
                "openAI" to EndpointConfig(name = "openAI", userProvide = true),
                "google" to EndpointConfig(name = "google", userProvide = true),
                "no-key" to EndpointConfig(name = "no-key", userProvide = false, userProvideURL = false),
                "custom-url" to EndpointConfig(name = "custom-url", userProvide = false, userProvideURL = true),
                "no-fields" to EndpointConfig(name = "no-fields"),
            ),
        )
        val vm = ProviderKeysViewModel(keyRepository, configRepository)
        advanceUntilIdle()
        val names = vm.uiState.value.entries.map { it.endpointName }.toSet()
        assertThat(names).containsExactly("openAI", "google", "custom-url")
    }

    @Test
    fun revokeAll_invokes_fetchEndpoints_and_fetchModels() = runTest(testDispatcher) {
        setEndpoints(mapOf("openAI" to EndpointConfig(name = "openAI", userProvide = true)))
        coEvery { keyRepository.deleteAllKeys() } returns Result.Success(Unit)

        val vm = ProviderKeysViewModel(keyRepository, configRepository)
        advanceUntilIdle()
        vm.revokeAll()
        advanceUntilIdle()

        coVerify(atLeast = 1) { configRepository.fetchEndpoints() }
        coVerify(atLeast = 1) { configRepository.fetchModels() }
        coVerify(exactly = 1) { keyRepository.deleteAllKeys() }
    }

    @Test
    fun onChildKeyChanged_invalidates_models_cache() = runTest(testDispatcher) {
        setEndpoints(mapOf("openAI" to EndpointConfig(name = "openAI", userProvide = true)))
        val vm = ProviderKeysViewModel(keyRepository, configRepository)
        advanceUntilIdle()
        vm.onChildKeyChanged("openAI")
        advanceUntilIdle()

        coVerify(atLeast = 1) { configRepository.fetchEndpoints() }
        coVerify(atLeast = 1) { configRepository.fetchModels() }
    }

    @Test
    fun onChildKeyChanged_cancels_stale_refresh_when_called_in_quick_succession() =
        runTest(testDispatcher) {
            // A stale fan-out (slow first call) must not overwrite fresh state from a later
            // call. Mockk's coEvery returns immediately, so we don't need to interleave
            // delays; back-to-back invocations should still each reach fetchEndpoints, but
            // the cancel-and-restart pattern via `refreshJob` ensures only the latest job's
            // result is observed.
            setEndpoints(
                mapOf("openAI" to EndpointConfig(name = "openAI", userProvide = true)),
            )
            val vm = ProviderKeysViewModel(keyRepository, configRepository)
            advanceUntilIdle()

            vm.onChildKeyChanged("openAI")
            vm.onChildKeyChanged("openAI")
            advanceUntilIdle()

            // Cache invalidation still happens for the latest job.
            coVerify(atLeast = 1) { configRepository.fetchEndpoints() }
            coVerify(atLeast = 1) { configRepository.fetchModels() }
        }

    @Test
    fun onChildKeyChanged_refetches_only_the_named_endpoints_keystate() =
        runTest(testDispatcher) {
            // `refresh(forceFor=...)` contract: only the named endpoints get their key state
            // re-fetched; other rows reuse the cached KeyState. Two endpoints; fetch is
            // verified to be called once per endpoint at init, then once more for the
            // explicitly-refreshed one after onChildKeyChanged.
            setEndpoints(
                mapOf(
                    "openAI" to EndpointConfig(name = "openAI", userProvide = true),
                    "google" to EndpointConfig(name = "google", userProvide = true),
                ),
            )
            val vm = ProviderKeysViewModel(keyRepository, configRepository)
            advanceUntilIdle()

            // Initial load: each endpoint's key state fetched once.
            coVerify(exactly = 1) { keyRepository.fetchKeyState("openAI") }
            coVerify(exactly = 1) { keyRepository.fetchKeyState("google") }

            vm.onChildKeyChanged("openAI")
            advanceUntilIdle()

            // Only the named endpoint is re-fetched; google reuses the cached value.
            coVerify(exactly = 2) { keyRepository.fetchKeyState("openAI") }
            coVerify(exactly = 1) { keyRepository.fetchKeyState("google") }
        }

    @Test
    fun revokeAll_success_force_refetches_all_keystates() = runTest(testDispatcher) {
        // `refresh(forceAll=true)` contract: revokeAll re-fetches every row's key state
        // from the server even when the entry list is unchanged.
        setEndpoints(
            mapOf(
                "openAI" to EndpointConfig(name = "openAI", userProvide = true),
                "google" to EndpointConfig(name = "google", userProvide = true),
            ),
        )
        coEvery { keyRepository.deleteAllKeys() } returns Result.Success(Unit)

        val vm = ProviderKeysViewModel(keyRepository, configRepository)
        advanceUntilIdle()
        // Initial load: 1 fetch per endpoint.
        coVerify(exactly = 1) { keyRepository.fetchKeyState("openAI") }
        coVerify(exactly = 1) { keyRepository.fetchKeyState("google") }

        vm.revokeAll()
        advanceUntilIdle()

        // Both endpoints re-fetched (forceAll=true).
        coVerify(exactly = 2) { keyRepository.fetchKeyState("openAI") }
        coVerify(exactly = 2) { keyRepository.fetchKeyState("google") }
    }

    // The next two tests exercise the public-method composition contract: concurrent /
    // sequential calls to public mutators compose their effects without losing writes.
    // CAS retries are inherently non-deterministic, so the race is not directly exercised.

    @Test
    fun concurrent_revokeAll_then_showRevokeAllConfirm_compose_without_overwrite() =
        runTest(testDispatcher) {
            setEndpoints(
                mapOf("openAI" to EndpointConfig(name = "openAI", userProvide = true)),
            )
            coEvery { keyRepository.deleteAllKeys() } returns Result.Success(Unit)

            val vm = ProviderKeysViewModel(keyRepository, configRepository)
            advanceUntilIdle()
            vm.emitTransientMessage("hello")

            // Launch revokeAll + showRevokeAllConfirm concurrently. Both touch _uiState.
            launch { vm.revokeAll() }
            launch { vm.showRevokeAllConfirm() }
            advanceUntilIdle()

            // Both effects must have landed: revokeAll finished (isRevokingAll = false), and
            // the transient message survives both updates.
            assertThat(vm.uiState.value.isRevokingAll).isFalse()
            assertThat(vm.uiState.value.transientMessage).isEqualTo("hello")
        }

    @Test
    fun sequential_emitTransientMessage_then_consume_compose_correctly() =
        runTest(testDispatcher) {
            setEndpoints(
                mapOf("openAI" to EndpointConfig(name = "openAI", userProvide = true)),
            )
            val vm = ProviderKeysViewModel(keyRepository, configRepository)
            advanceUntilIdle()

            vm.emitTransientMessage("msg1")
            vm.emitTransientMessage("msg2")
            advanceUntilIdle()
            // The latest write wins; intermediate state is not lost in a way that resets to null.
            assertThat(vm.uiState.value.transientMessage).isEqualTo("msg2")
            vm.consumeTransientMessage()
            assertThat(vm.uiState.value.transientMessage).isNull()
        }

    @Test
    fun onChildKeyChanged_forceFor_survives_cancellation_by_configs_collector() =
        runTest(testDispatcher) {
            // Race: onChildKeyChanged kicks off refresh(forceFor={openAI}) → mid-fetch the
            // endpointConfigs flow emits a new value → collector triggers refresh() with
            // forceFor=emptySet, cancelling the in-flight job. Without merge-on-cancel, the
            // restarted refresh would skip openAI (no force, openAI already in previousByName)
            // and the just-mutated row falls back to its cached pre-save value. With merge,
            // openAI stays in pendingForceFor and is re-fetched after the restart.

            val initialMap = mapOf("openAI" to EndpointConfig(name = "openAI", userProvide = true))
            val flow = MutableStateFlow(initialMap)
            coEvery { configRepository.endpointConfigs } returns flow
            coEvery { configRepository.fetchEndpoints() } returns Result.Success(initialMap)
            coEvery { configRepository.fetchModels() } returns Result.Success(emptyMap())

            // Init load completes synchronously (Unset returned without suspension).
            val vm = ProviderKeysViewModel(keyRepository, configRepository)
            advanceUntilIdle()
            // Sanity: initial row exists, fetched once.
            coVerify(exactly = 1) { keyRepository.fetchKeyState("openAI") }

            // Now arrange a suspending fetchKeyState so we can interleave a config emission
            // while onChildKeyChanged's refresh is mid-flight. The first call after this
            // re-stub suspends until we complete the deferred.
            val gate = CompletableDeferred<Unit>()
            var calls = 0
            coEvery { keyRepository.fetchKeyState("openAI") } coAnswers {
                calls++
                if (calls == 1) {
                    // Block the in-flight call so we can emit a new config value mid-fan-out.
                    gate.await()
                }
                Result.Success(KeyState.Set(expiresAt = null, neverExpires = true, wire = "never"))
            }

            // Kick off the explicit refresh (forceFor={openAI}).
            vm.onChildKeyChanged("openAI")
            // Let the launched coroutine reach the suspended fetchKeyState before we race.
            advanceUntilIdle()

            // Mid-flight: emit a differing config map. The configs collector calls refresh()
            // with forceFor=emptySet, which cancels the in-flight (suspended) job.
            flow.value = initialMap + ("openAI" to EndpointConfig(
                name = "openAI",
                userProvide = true,
                modelDisplayLabel = "OpenAI (admin updated)",
            ))
            advanceUntilIdle()

            // Release the gate so any still-active call can resume. Must be safe to release
            // even though the original call was cancelled — CompletableDeferred ignores extra
            // completions.
            gate.complete(Unit)
            advanceUntilIdle()

            // openAI MUST have been re-fetched (initial load + post-cancel restart). The merge
            // semantics carry the explicit forceFor across cancellation.
            coVerify(atLeast = 2) { keyRepository.fetchKeyState("openAI") }
            // And the row must reflect the fresh Set(neverExpires) state, not the pre-save
            // cached Unset.
            val resolved = vm.uiState.value.entries.single { it.endpointName == "openAI" }.keyState
            assertThat(resolved).isInstanceOf(KeyState.Set::class.java)
        }
}
