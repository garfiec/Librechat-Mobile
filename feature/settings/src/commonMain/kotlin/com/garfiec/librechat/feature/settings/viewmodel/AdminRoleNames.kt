package com.garfiec.librechat.feature.settings.viewmodel

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.RoleRepository

/** The server's roles, custom ones included, or null to keep [FALLBACK_ROLE_NAMES]. */
internal suspend fun RoleRepository.pickerRoleNames(): List<String>? =
    (listRoleNames() as? Result.Success)?.data?.takeIf { it.isNotEmpty() }
