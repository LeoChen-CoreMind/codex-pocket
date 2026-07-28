package com.garfiec.librechat.core.data.util

import com.garfiec.librechat.core.data.repository.RoleRepository
import com.garfiec.librechat.core.model.permissions.UserRolePermissions
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

class PermissionGate(private val roleRepository: RoleRepository) {
    /**
     * Awaits the role to load (up to [ROLE_LOAD_TIMEOUT_MS]) and returns it.
     * Null return = timeout or fetch-failure; callers should treat it as permissive.
     */
    suspend fun awaitRole(): UserRolePermissions? =
        withTimeoutOrNull(ROLE_LOAD_TIMEOUT_MS) {
            roleRepository.userPermissions.filterNotNull().first()
        }

    companion object {
        const val ROLE_LOAD_TIMEOUT_MS = 5_000L
    }
}
