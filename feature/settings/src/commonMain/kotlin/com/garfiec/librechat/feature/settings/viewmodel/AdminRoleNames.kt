package com.garfiec.librechat.feature.settings.viewmodel

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.RoleRepository

/**
 * The role picker's list when the server's own can't be had: upstream SystemRoles, as web's
 * `useRoleSelector` falls back to when `GET /api/admin/roles` fails or lists nothing (a server
 * before v0.8.8, or an admin without READ_ROLES). Registered in scripts/mirrors.json as
 * `admin-role-picker-fallback`.
 */
internal val FALLBACK_ROLE_NAMES = listOf("USER", "ADMIN")

/** The server's roles, custom ones included, or null to keep [FALLBACK_ROLE_NAMES]. */
internal suspend fun RoleRepository.pickerRoleNames(): List<String>? =
    (listRoleNames() as? Result.Success)?.data?.takeIf { it.isNotEmpty() }
