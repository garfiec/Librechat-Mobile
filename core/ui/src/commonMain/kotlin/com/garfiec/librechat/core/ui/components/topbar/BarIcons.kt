package com.garfiec.librechat.core.ui.components.topbar

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Compare
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DataUsage
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.SaveAs
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Timeline

/**
 * Every icon a top bar may show, paired with its SF Symbol. Adding an icon here is the only way to
 * put it on a bar, and [all] is what the symbol tests walk — an entry missing from [all] escapes them.
 */
object BarIcons {
    val Back = BarIcon(Icons.AutoMirrored.Filled.ArrowBack, "chevron.backward")
    val Menu = BarIcon(Icons.Default.Menu, "line.3.horizontal")
    val Close = BarIcon(Icons.Default.Close, "xmark")
    val More = BarIcon(Icons.Default.MoreVert, "ellipsis")
    val Search = BarIcon(Icons.Default.Search, "magnifyingglass")
    val Visible = BarIcon(Icons.Default.Visibility, "eye")
    val Hidden = BarIcon(Icons.Default.VisibilityOff, "eye.slash")
    val Media = BarIcon(Icons.Outlined.PhotoLibrary, "photo.on.rectangle")
    val LoadPreset = BarIcon(Icons.Outlined.FileOpen, "doc")
    val SavePreset = BarIcon(Icons.Outlined.SaveAs, "square.and.arrow.down")
    val Prompts = BarIcon(Icons.Outlined.AutoAwesome, "sparkles")
    val Compare = BarIcon(Icons.Outlined.Compare, "rectangle.split.2x1")
    val Trace = BarIcon(Icons.Outlined.Timeline, "point.3.connected.trianglepath.dotted")
    val ContextUsage = BarIcon(Icons.Outlined.DataUsage, "chart.pie")
    val Share = BarIcon(Icons.Outlined.Share, "square.and.arrow.up")
    val Edit = BarIcon(Icons.Outlined.Edit, "pencil")
    val Duplicate = BarIcon(Icons.Outlined.ContentCopy, "doc.on.doc")
    val Archive = BarIcon(Icons.Outlined.Archive, "archivebox")
    val Delete = BarIcon(Icons.Outlined.DeleteOutline, "trash")

    // Filled variants: screens that used the filled Material glyph keep it, so Material mode is unchanged.
    val EditFilled = BarIcon(Icons.Default.Edit, "square.and.pencil")
    val DuplicateFilled = BarIcon(Icons.Default.ContentCopy, "plus.square.on.square")
    val DeleteFilled = BarIcon(Icons.Default.Delete, "trash.fill")
    val ShareFilled = BarIcon(Icons.Default.Share, "square.and.arrow.up.fill")

    val History = BarIcon(Icons.Default.History, "clock.arrow.circlepath")
    val Sort = BarIcon(Icons.AutoMirrored.Filled.Sort, "arrow.up.arrow.down")
    val NewFolder = BarIcon(Icons.Default.CreateNewFolder, "folder.badge.plus")
    val Tools = BarIcon(Icons.Default.Build, "wrench.and.screwdriver")
    val Import = BarIcon(Icons.Default.FileUpload, "square.and.arrow.down.on.square")
    val SelectAll = BarIcon(Icons.Default.SelectAll, "checklist")
    val GridView = BarIcon(Icons.Default.GridView, "square.grid.2x2")
    val ListView = BarIcon(Icons.AutoMirrored.Filled.ViewList, "list.bullet")
    val Filter = BarIcon(Icons.Default.FilterList, "line.3.horizontal.decrease.circle")
    val Source = BarIcon(Icons.Default.Code, "chevron.left.forwardslash.chevron.right")
    val Rendered = BarIcon(Icons.AutoMirrored.Filled.Notes, "text.alignleft")

    val all: List<BarIcon> = listOf(
        Back, Menu, Close, More, Search, Visible, Hidden, Media, LoadPreset, SavePreset, Prompts,
        Compare, Trace, ContextUsage, Share, Edit, Duplicate, Archive, Delete,
        EditFilled, DuplicateFilled, DeleteFilled, ShareFilled, History, Sort, NewFolder, Tools, Import,
        SelectAll, GridView, ListView, Filter, Source, Rendered,
    )
}
