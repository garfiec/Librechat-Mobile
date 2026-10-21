package com.garfiec.librechat.core.ui.components.share

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.model.AccessRole
import com.garfiec.librechat.core.model.AccessRoleIds
import com.garfiec.librechat.core.model.Principal
import com.garfiec.librechat.core.model.PrincipalType
import com.garfiec.librechat.core.model.acl.AclEntry
import com.garfiec.librechat.core.model.acl.AclError
import com.garfiec.librechat.core.model.acl.AclSearch
import com.garfiec.librechat.core.model.acl.AclState
import com.garfiec.librechat.core.model.share.ShareAudience
import com.garfiec.librechat.core.model.ui.UiStyle
import com.garfiec.librechat.core.ui.components.AdaptiveSheetSurface
import com.garfiec.librechat.core.ui.components.acl.AclSharingActions
import com.garfiec.librechat.core.ui.glass.GlassSheetHost
import com.garfiec.librechat.core.ui.preview.PreviewNarrowScreens
import com.garfiec.librechat.core.ui.theme.LibreChatTheme

// The share sheet's Manage access page at narrow widths and large fonts: the grants with the
// owner locked, a search with hits, a search with none, a refused change, loading and a failed
// read. Material renders light and Liquid Glass dark.

private val owner = Principal(
    type = PrincipalType.USER,
    id = "me",
    name = "Maximiliane Oberhausen-Wittgenstein",
    email = "maximiliane@example-company-intranet.com",
    accessRoleId = AccessRoleIds.SHARED_LINK_OWNER,
)
private val viewer = Principal(
    type = PrincipalType.USER,
    id = "u2",
    name = "Bea Lindqvist",
    email = "bea@example.com",
    accessRoleId = AccessRoleIds.SHARED_LINK_VIEWER,
)
private val group = Principal(
    type = PrincipalType.GROUP,
    id = "g1",
    name = "Design and Research",
    accessRoleId = AccessRoleIds.SHARED_LINK_VIEWER,
)

private val ready = AclState.Ready(
    entries = listOf(AclEntry(owner, locked = true), AclEntry(viewer, locked = false), AclEntry(group, locked = false)),
    isPublic = true,
    canSharePublic = true,
    canSearch = true,
    roles = listOf(AccessRole(accessRoleId = AccessRoleIds.SHARED_LINK_VIEWER)),
    grantRoleId = AccessRoleIds.SHARED_LINK_VIEWER,
)

private val searching = ready.copy(
    entries = listOf(AclEntry(owner, locked = true)),
    isPublic = false,
    search = AclSearch(
        query = "de",
        results = listOf(
            group.copy(accessRoleId = null),
            Principal(type = PrincipalType.USER, id = "u3", name = "Dee Okafor", email = "dee.okafor@example.com"),
        ),
    ),
)

private val noMatches = ready.copy(
    entries = listOf(AclEntry(owner, locked = true)),
    search = AclSearch(query = "zzz"),
)

/** No SHARE_PUBLIC and no people picker: only the list, with a refused change on it. */
private val refused = ready.copy(
    canSharePublic = false,
    canSearch = false,
    error = AclError.OWNER_PROTECTED,
)

@PreviewNarrowScreens
@Composable
private fun AccessReadyMaterialPreview() = AccessPreview(UiStyle.MATERIAL, ready)

@PreviewNarrowScreens
@Composable
private fun AccessReadyGlassPreview() = AccessPreview(UiStyle.LIQUID_GLASS, ready, ShareAudience.SIGNED_IN)

@PreviewNarrowScreens
@Composable
private fun AccessSearchMaterialPreview() = AccessPreview(UiStyle.MATERIAL, searching)

@PreviewNarrowScreens
@Composable
private fun AccessSearchGlassPreview() = AccessPreview(UiStyle.LIQUID_GLASS, searching)

@PreviewNarrowScreens
@Composable
private fun AccessNoMatchesMaterialPreview() = AccessPreview(UiStyle.MATERIAL, noMatches)

@PreviewNarrowScreens
@Composable
private fun AccessNoMatchesGlassPreview() = AccessPreview(UiStyle.LIQUID_GLASS, noMatches)

@PreviewNarrowScreens
@Composable
private fun AccessRefusedMaterialPreview() = AccessPreview(UiStyle.MATERIAL, refused)

@PreviewNarrowScreens
@Composable
private fun AccessRefusedGlassPreview() = AccessPreview(UiStyle.LIQUID_GLASS, refused)

@PreviewNarrowScreens
@Composable
private fun AccessLoadingMaterialPreview() = AccessPreview(UiStyle.MATERIAL, AclState.Loading)

@PreviewNarrowScreens
@Composable
private fun AccessLoadingGlassPreview() = AccessPreview(UiStyle.LIQUID_GLASS, AclState.Loading)

@PreviewNarrowScreens
@Composable
private fun AccessFailedMaterialPreview() = AccessPreview(UiStyle.MATERIAL, AclState.Failed(AclError.NETWORK))

@PreviewNarrowScreens
@Composable
private fun AccessFailedGlassPreview() = AccessPreview(UiStyle.LIQUID_GLASS, AclState.Failed(AclError.GENERIC))

@Composable
private fun AccessPreview(style: UiStyle, access: AclState, audience: ShareAudience = ShareAudience.ANYONE) {
    LibreChatTheme(darkTheme = style == UiStyle.LIQUID_GLASS, uiStyle = style) {
        GlassSheetHost(Modifier.fillMaxWidth().height(SCREEN_HEIGHT.dp)) {
            Box(
                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.BottomCenter,
            ) {
                AdaptiveSheetSurface(Modifier.fillMaxWidth()) {
                    ManageAccessPage(
                        audience = audience,
                        access = access,
                        actions = AclSharingActions.None,
                        onBack = {},
                    )
                }
            }
        }
    }
}
