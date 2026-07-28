package com.garfiec.librechat.core.data.util

import com.garfiec.librechat.core.data.repository.RoleRepository

/**
 * Loads the authenticated user's role permissions when a session starts. Without this,
 * permission gates stay permissive-null across app restarts until a gated screen
 * happens to trigger a user fetch.
 */
class RoleFetchSessionTask(
    private val roleRepository: RoleRepository,
) : SessionTask {
    override suspend fun run() {
        roleRepository.fetchUserRole()
    }
}
