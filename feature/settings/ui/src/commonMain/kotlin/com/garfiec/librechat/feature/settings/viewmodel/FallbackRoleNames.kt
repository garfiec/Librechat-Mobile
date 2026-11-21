package com.garfiec.librechat.feature.settings.viewmodel

/**
 * The role picker's list when the server's own can't be had: upstream SystemRoles, as web's
 * `useRoleSelector` falls back to when `GET /api/admin/roles` fails or lists nothing (a server
 * before v0.8.8, or an admin without READ_ROLES). Registered in scripts/mirrors.json as
 * `admin-role-picker-fallback`.
 */
val FALLBACK_ROLE_NAMES = listOf("USER", "ADMIN")
