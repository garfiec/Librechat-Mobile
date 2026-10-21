package com.garfiec.librechat.core.ui.components.share

import androidx.compose.runtime.Composable
import com.garfiec.librechat.core.model.share.FilesOption
import com.garfiec.librechat.core.model.share.ShareAudience
import com.garfiec.librechat.core.model.share.ShareSheetBusy
import com.garfiec.librechat.core.model.share.ShareSheetError
import com.garfiec.librechat.core.model.share.ShareSheetState
import com.garfiec.librechat.core.model.ui.UiStyle
import com.garfiec.librechat.core.ui.preview.PreviewNarrowScreens

private val noLink = ShareSheetState.NoLink(
    canCreate = true,
    filesOption = FilesOption.NEW_LINK,
    audience = ShareAudience.ANYONE,
)

@PreviewNarrowScreens
@Composable
private fun NoLinkMaterialPreview() = SheetPreview(UiStyle.MATERIAL, noLink)

@PreviewNarrowScreens
@Composable
private fun NoLinkGlassPreview() = SheetPreview(UiStyle.LIQUID_GLASS, noLink)

/** A role without SHARED_LINKS.SHARE_PUBLIC: the new link gets no public grant. */
@PreviewNarrowScreens
@Composable
private fun NoLinkPeopleAddedMaterialPreview() =
    SheetPreview(UiStyle.MATERIAL, noLink.copy(audience = ShareAudience.PEOPLE_ADDED))

@PreviewNarrowScreens
@Composable
private fun NoLinkPeopleAddedGlassPreview() =
    SheetPreview(UiStyle.LIQUID_GLASS, noLink.copy(audience = ShareAudience.PEOPLE_ADDED))

@PreviewNarrowScreens
@Composable
private fun NoLinkCreatingMaterialPreview() = SheetPreview(UiStyle.MATERIAL, creating())

@PreviewNarrowScreens
@Composable
private fun NoLinkCreatingGlassPreview() = SheetPreview(UiStyle.LIQUID_GLASS, creating())

@PreviewNarrowScreens
@Composable
private fun CannotCreateMaterialPreview() = SheetPreview(UiStyle.MATERIAL, cannotCreate())

@PreviewNarrowScreens
@Composable
private fun CannotCreateGlassPreview() = SheetPreview(UiStyle.LIQUID_GLASS, cannotCreate())

@PreviewNarrowScreens
@Composable
private fun LoadingMaterialPreview() = SheetPreview(UiStyle.MATERIAL, ShareSheetState.Loading)

@PreviewNarrowScreens
@Composable
private fun LoadingGlassPreview() = SheetPreview(UiStyle.LIQUID_GLASS, ShareSheetState.Loading)

@PreviewNarrowScreens
@Composable
private fun FailedMaterialPreview() = SheetPreview(UiStyle.MATERIAL, ShareSheetState.Failed(ShareSheetError.Network))

@PreviewNarrowScreens
@Composable
private fun FailedGlassPreview() = SheetPreview(UiStyle.LIQUID_GLASS, ShareSheetState.Failed(ShareSheetError.Network))

private fun creating() = noLink.copy(busy = ShareSheetBusy.CREATING)

private fun cannotCreate() = noLink.copy(canCreate = false, audience = ShareAudience.SIGNED_IN)
