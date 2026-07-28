package com.garfiec.librechat.core.model.permissions

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UserRolePermissionsTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun hasAccessReturnsTrueWhenPermissionExplicitlyGranted() {
        val role = UserRolePermissions(
            name = "ADMIN",
            permissions = mapOf("PROMPTS" to mapOf("USE" to true)),
        )
        assertTrue(role.hasAccess(PermissionType.PROMPTS, Permission.USE))
    }

    @Test
    fun hasAccessReturnsFalseWhenPermissionExplicitlyDenied() {
        val role = UserRolePermissions(
            name = "ADMIN",
            permissions = mapOf("PROMPTS" to mapOf("USE" to false)),
        )
        assertFalse(role.hasAccess(PermissionType.PROMPTS, Permission.USE))
    }

    @Test
    fun hasAccessReturnsTrueWhenPermissionTypeMissing() {
        val role = UserRolePermissions(
            name = "ADMIN",
            permissions = mapOf("PROMPTS" to mapOf("USE" to true)),
        )
        // AGENTS type not in map → permissive default
        assertTrue(role.hasAccess(PermissionType.AGENTS, Permission.USE))
    }

    @Test
    fun hasAccessReturnsTrueWhenActionMissingWithinKnownType() {
        val role = UserRolePermissions(
            name = "ADMIN",
            permissions = mapOf("PROMPTS" to mapOf("USE" to false)),
        )
        // CREATE action absent under PROMPTS → permissive default
        assertTrue(role.hasAccess(PermissionType.PROMPTS, Permission.CREATE))
    }

    @Test
    fun hasAccessReturnsTrueForEveryPermissionWhenPermissionsMapEmpty() {
        val role = UserRolePermissions(name = "ADMIN", permissions = emptyMap())
        assertTrue(role.hasAccess(PermissionType.PROMPTS, Permission.USE))
        assertTrue(role.hasAccess(PermissionType.AGENTS, Permission.CREATE))
        assertTrue(role.hasAccess(PermissionType.MCP_SERVERS, Permission.USE))
    }

    @Test
    fun deserializesLiveRolesAdminResponseFromV085Rc1() {
        // Captured 2026-04-24 from `GET /api/roles/ADMIN` on v0.8.5-rc1 test server
        // with mongo roles seeded from an earlier USE=false configuration.
        val decoded = json.decodeFromString(UserRolePermissions.serializer(), LIVE_ADMIN_ROLE_JSON)

        assertEquals("ADMIN", decoded.name)
        assertEquals("", decoded.description)

        // Spot-check a mix of denied + granted permissions from the live response.
        assertFalse(decoded.hasAccess(PermissionType.PROMPTS, Permission.USE))
        assertTrue(decoded.hasAccess(PermissionType.PROMPTS, Permission.CREATE))
        assertFalse(decoded.hasAccess(PermissionType.AGENTS, Permission.USE))
        assertFalse(decoded.hasAccess(PermissionType.MEMORIES, Permission.USE))
        assertTrue(decoded.hasAccess(PermissionType.MEMORIES, Permission.CREATE))
        assertFalse(decoded.hasAccess(PermissionType.MCP_SERVERS, Permission.USE))
        assertFalse(decoded.hasAccess(PermissionType.MARKETPLACE, Permission.USE))
        assertTrue(decoded.hasAccess(PermissionType.BOOKMARKS, Permission.USE))
        assertTrue(decoded.hasAccess(PermissionType.MULTI_CONVO, Permission.USE))
        assertTrue(decoded.hasAccess(PermissionType.TEMPORARY_CHAT, Permission.USE))
        assertTrue(decoded.hasAccess(PermissionType.RUN_CODE, Permission.USE))
        assertTrue(decoded.hasAccess(PermissionType.WEB_SEARCH, Permission.USE))
        assertTrue(decoded.hasAccess(PermissionType.FILE_SEARCH, Permission.USE))
        assertFalse(decoded.hasAccess(PermissionType.REMOTE_AGENTS, Permission.USE))
        assertFalse(decoded.hasAccess(PermissionType.REMOTE_AGENTS, Permission.CREATE))
    }

    @Test
    fun deserializesMinimalRolePayload() {
        val minimal = """{"name":"USER","permissions":{}}"""
        val decoded = json.decodeFromString(UserRolePermissions.serializer(), minimal)
        assertEquals("USER", decoded.name)
        assertTrue(decoded.permissions.isEmpty())
        assertTrue(decoded.hasAccess(PermissionType.PROMPTS, Permission.USE))
    }

    @Test
    fun ignoresUnknownTopLevelFieldsFromServer() {
        // Live payload includes `_id` and `__v` which aren't in our model; ensure it deserializes.
        val json = Json { ignoreUnknownKeys = true }
        val payload = """{"_id":"abc","name":"ADMIN","__v":0,"permissions":{"PROMPTS":{"USE":true}}}"""
        val decoded = json.decodeFromString(UserRolePermissions.serializer(), payload)
        assertEquals("ADMIN", decoded.name)
        assertTrue(decoded.hasAccess(PermissionType.PROMPTS, Permission.USE))
    }

    private companion object {
        // Verbatim response body from the running v0.8.5-rc1 test server, 2026-04-24.
        private val LIVE_ADMIN_ROLE_JSON = """
            {
                "_id": "69eab599e5c9757247ad5c21",
                "name": "ADMIN",
                "permissions": {
                    "BOOKMARKS": { "USE": true },
                    "PROMPTS": {
                        "USE": false,
                        "CREATE": true,
                        "SHARE": false,
                        "SHARE_PUBLIC": false
                    },
                    "MEMORIES": {
                        "USE": false,
                        "CREATE": true,
                        "UPDATE": true,
                        "READ": true,
                        "OPT_OUT": true
                    },
                    "AGENTS": {
                        "USE": false,
                        "CREATE": true,
                        "SHARE": false,
                        "SHARE_PUBLIC": false
                    },
                    "MULTI_CONVO": { "USE": true },
                    "TEMPORARY_CHAT": { "USE": true },
                    "RUN_CODE": { "USE": true },
                    "WEB_SEARCH": { "USE": true },
                    "PEOPLE_PICKER": {
                        "VIEW_USERS": true,
                        "VIEW_GROUPS": true,
                        "VIEW_ROLES": true
                    },
                    "MARKETPLACE": { "USE": false },
                    "FILE_SEARCH": { "USE": true },
                    "FILE_CITATIONS": { "USE": true },
                    "MCP_SERVERS": {
                        "USE": false,
                        "CREATE": true,
                        "SHARE": false,
                        "SHARE_PUBLIC": false
                    },
                    "REMOTE_AGENTS": {
                        "USE": false,
                        "CREATE": false,
                        "SHARE": false,
                        "SHARE_PUBLIC": false
                    }
                },
                "__v": 0,
                "description": ""
            }
        """.trimIndent()
    }
}
