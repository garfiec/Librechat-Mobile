package com.garfiec.librechat.core.model.server

/**
 * Why a header a user typed can't be trusted verbatim.
 *
 * Rejection is deliberately loud rather than silent-sanitising: a gateway credential that is *almost*
 * right fails as an opaque redirect to a login page, so the user has to be told at entry time.
 */
enum class HeaderRejection {
    /** Blank, or contains a character outside the RFC 7230 `token` set. */
    InvalidName,

    /** Contains a character outside printable US-ASCII (plus HTAB). */
    InvalidValue,

    /** A header the transport or the app itself owns; see `CustomHeaderRules.RESERVED_NAMES` in :core:network. */
    ReservedName,
}
