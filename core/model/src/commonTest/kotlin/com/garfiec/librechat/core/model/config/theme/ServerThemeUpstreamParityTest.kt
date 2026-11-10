package com.garfiec.librechat.core.model.config.theme

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Resolves themes that between them exercise every fallback rule and compares the result with
 * upstream's own `resolveTheme`. [EXPECTED] holds, per theme and mode, every role whose resolved
 * colour differs from the stock palette, as `resolveTheme` returned it from the
 * `librechat-data-provider` build in the v0.8.9-rc1 API image (upstream 8d3da9bb).
 */
class ServerThemeUpstreamParityTest {
    @Test
    fun resolvesLikeUpstream() {
        THEMES.forEach { (name, json) ->
            val read = readServerTheme(Json.parseToJsonElement(json))
            val definition = assertIs<ServerThemeRead.Theme>(read, name).definition
            listOf(false, true).forEach { dark ->
                val stock = ThemeRole.entries.associateWith { if (dark) it.libreChatDark else it.libreChatLight }
                val changed = definition.resolve(dark)
                    .filter { (role, rgb) -> stock[role] != rgb }
                    .entries.associate { (role, rgb) -> role.token to triplet(rgb) }
                val mode = if (dark) "dark" else "light"
                assertEquals(EXPECTED.getValue(name).getValue(mode), changed, "$name $mode")
            }
        }
    }

    private fun triplet(rgb: Int) = "${rgb shr 16 and 0xFF} ${rgb shr 8 and 0xFF} ${rgb and 0xFF}"

    private companion object {
        val THEMES: Map<String, String> = mapOf(
            "acme" to """
                {
                  "version": 1,
                  "name": "acme",
                  "modes": {
                    "light": {
                      "colors": {
                        "rgb-surface-primary": "250 246 238",
                        "rgb-surface-secondary": "240 232 218",
                        "rgb-surface-sidebar": "236 226 208",
                        "rgb-surface-chat": "250 246 238",
                        "rgb-surface-user-message": "214 236 228",
                        "rgb-surface-composer": "255 252 246",
                        "rgb-surface-code": "232 224 210",
                        "rgb-text-primary": "40 32 24",
                        "rgb-text-secondary": "96 84 70",
                        "rgb-accent-primary": "13 128 110",
                        "rgb-surface-submit": "13 128 110",
                        "rgb-surface-submit-hover": "10 104 90",
                        "rgb-link": "13 110 150",
                        "rgb-border-light": "222 208 186",
                        "rgb-border-medium": "204 188 162"
                      }
                    },
                    "dark": {
                      "colors": {
                        "rgb-surface-primary": "18 24 44",
                        "rgb-surface-secondary": "28 36 62",
                        "rgb-surface-sidebar": "14 19 36",
                        "rgb-surface-chat": "18 24 44",
                        "rgb-surface-user-message": "44 56 96",
                        "rgb-surface-composer": "28 36 62",
                        "rgb-surface-code": "10 14 28",
                        "rgb-text-primary": "236 240 255",
                        "rgb-text-secondary": "170 180 210",
                        "rgb-accent-primary": "255 122 89",
                        "rgb-surface-submit": "255 122 89",
                        "rgb-surface-submit-hover": "235 100 70",
                        "rgb-link": "255 160 130",
                        "rgb-border-light": "44 54 86",
                        "rgb-border-medium": "60 72 110"
                      }
                    }
                  }
                }
            """,
            "sources" to """
                {
                  "version": 1,
                  "name": "sources",
                  "modes": {
                    "light": {
                      "colors": {
                        "rgb-surface-primary-alt": "37 71 113",
                        "rgb-presentation": "74 142 226",
                        "rgb-surface-chat": "111 213 83",
                        "rgb-surface-hover": "148 28 196",
                        "rgb-text-primary": "185 99 53",
                        "rgb-text-tertiary": "222 170 166",
                        "rgb-border-medium": "3 241 23",
                        "rgb-surface-active-alt": "40 56 136",
                        "rgb-surface-hover-alt": "77 127 249",
                        "rgb-surface-secondary": "114 198 106",
                        "rgb-surface-tertiary": "151 13 219",
                        "rgb-border-xheavy": "188 84 76",
                        "rgb-surface-primary": "225 155 189",
                        "rgb-border-light": "6 226 46",
                        "rgb-border-heavy": "43 41 159",
                        "rgb-status-error-subtle": "80 112 16",
                        "rgb-status-error-border": "117 183 129",
                        "rgb-series-1": "154 254 242",
                        "rgb-series-2": "191 69 99",
                        "rgb-series-4": "228 140 212",
                        "rgb-series-5": "9 211 69",
                        "rgb-series-6": "46 26 182",
                        "rgb-series-7": "83 97 39",
                        "rgb-text-secondary": "120 168 152",
                        "rgb-surface-dialog": "157 239 9",
                        "rgb-ring-primary": "194 54 122",
                        "rgb-surface-inverted": "231 125 235",
                        "rgb-surface-inverted-hover": "12 196 92",
                        "rgb-status-success-strong": "49 11 205"
                      }
                    },
                    "dark": {
                      "colors": {
                        "rgb-surface-primary-alt": "86 82 62",
                        "rgb-presentation": "123 153 175",
                        "rgb-surface-chat": "160 224 32",
                        "rgb-surface-hover": "197 39 145",
                        "rgb-text-primary": "234 110 2",
                        "rgb-text-tertiary": "15 181 115",
                        "rgb-border-medium": "52 252 228",
                        "rgb-surface-active-alt": "89 67 85",
                        "rgb-surface-hover-alt": "126 138 198",
                        "rgb-surface-secondary": "163 209 55",
                        "rgb-surface-tertiary": "200 24 168",
                        "rgb-border-xheavy": "237 95 25",
                        "rgb-surface-primary": "18 166 138",
                        "rgb-border-light": "55 237 251",
                        "rgb-border-heavy": "92 52 108",
                        "rgb-status-error-subtle": "129 123 221",
                        "rgb-status-error-border": "166 194 78",
                        "rgb-series-1": "203 9 191",
                        "rgb-series-2": "240 80 48",
                        "rgb-series-4": "21 151 161",
                        "rgb-series-5": "58 222 18",
                        "rgb-series-6": "95 37 131",
                        "rgb-series-7": "132 108 244",
                        "rgb-text-secondary": "169 179 101",
                        "rgb-surface-dialog": "206 250 214",
                        "rgb-ring-primary": "243 65 71",
                        "rgb-surface-inverted": "24 136 184",
                        "rgb-surface-inverted-hover": "61 207 41",
                        "rgb-status-success-strong": "98 22 154"
                      }
                    }
                  }
                }
            """,
            "sparse" to """
                {
                  "version": 1,
                  "name": "sparse",
                  "modes": {
                    "light": {
                      "colors": {
                        "rgb-text-primary": "10 20 30",
                        "rgb-focus-control": "40 50 60",
                        "rgb-surface-sidebar": "70 80 90",
                        "rgb-border-medium": "100 110 120",
                        "rgb-link": "1 2 3"
                      }
                    },
                    "dark": {
                      "colors": {
                        "rgb-text-primary": "10 20 30",
                        "rgb-focus-control": "40 50 60",
                        "rgb-surface-sidebar": "70 80 90",
                        "rgb-border-medium": "100 110 120"
                      }
                    }
                  }
                }
            """,
            "legacy" to """
                {
                  "version": 1,
                  "name": "legacy",
                  "modes": {
                    "light": {
                      "colors": {
                        "rgb-category-icon": "9 8 7",
                        "rgb-text-on-status": "6 5 4",
                        "rgb-series-3": "3 3 3",
                        "rgb-future-role": "1 1 1"
                      }
                    },
                    "dark": {
                      "colors": {
                        "rgb-category-icon": "9 8 7",
                        "rgb-category-icon-4": "7 7 7",
                        "rgb-surface-hover": "20 20 20",
                        "rgb-surface-composer-hover": "30 30 30"
                      }
                    }
                  }
                }
            """,
            "lightonly" to """
                {
                  "version": 1,
                  "name": "lightonly",
                  "modes": {
                    "light": {
                      "colors": {
                        "rgb-surface-chat": "200 200 255"
                      }
                    }
                  }
                }
            """,
        )

        val EXPECTED: Map<String, Map<String, Map<String, String>>> = mapOf(
            "acme" to mapOf(
                "light" to mapOf(
                    "rgb-accent-primary" to "13 128 110",
                    "rgb-avatar-placeholder" to "240 232 218",
                    "rgb-avatar-text" to "40 32 24",
                    "rgb-badge-label" to "40 32 24",
                    "rgb-border-control" to "222 208 186",
                    "rgb-border-field-focus" to "40 32 24",
                    "rgb-border-light" to "222 208 186",
                    "rgb-border-medium" to "204 188 162",
                    "rgb-border-menu" to "222 208 186",
                    "rgb-chart-widget-stroke" to "222 208 186",
                    "rgb-chart-widget-surface" to "250 246 238",
                    "rgb-checkbox-fill" to "250 246 238",
                    "rgb-dialog-title" to "40 32 24",
                    "rgb-drawer-edge" to "236 226 208",
                    "rgb-field-fill" to "250 246 238",
                    "rgb-field-text" to "40 32 24",
                    "rgb-focus-control" to "40 32 24",
                    "rgb-link" to "13 110 150",
                    "rgb-link-prose" to "13 110 150",
                    "rgb-prose-bullet" to "204 188 162",
                    "rgb-prose-quote-bar" to "204 188 162",
                    "rgb-shimmer-base" to "40 32 24",
                    "rgb-status-verified" to "2 133 94",
                    "rgb-surface-card" to "240 232 218",
                    "rgb-surface-chat" to "250 246 238",
                    "rgb-surface-code" to "232 224 210",
                    "rgb-surface-code-body" to "250 246 238",
                    "rgb-surface-composer" to "255 252 246",
                    "rgb-surface-popover" to "250 246 238",
                    "rgb-surface-primary" to "250 246 238",
                    "rgb-surface-search" to "240 232 218",
                    "rgb-surface-secondary" to "240 232 218",
                    "rgb-surface-sidebar" to "236 226 208",
                    "rgb-surface-submit" to "13 128 110",
                    "rgb-surface-submit-hover" to "10 104 90",
                    "rgb-surface-tooltip" to "250 246 238",
                    "rgb-surface-user-message" to "214 236 228",
                    "rgb-switch-thumb" to "250 246 238",
                    "rgb-table-header-text" to "96 84 70",
                    "rgb-text-primary" to "40 32 24",
                    "rgb-text-secondary" to "96 84 70",
                    "rgb-text-tooltip" to "40 32 24",
                ),
                "dark" to mapOf(
                    "rgb-accent-primary" to "255 122 89",
                    "rgb-avatar-text" to "236 240 255",
                    "rgb-badge-label" to "236 240 255",
                    "rgb-border-control" to "44 54 86",
                    "rgb-border-field-focus" to "236 240 255",
                    "rgb-border-light" to "44 54 86",
                    "rgb-border-medium" to "60 72 110",
                    "rgb-border-menu" to "44 54 86",
                    "rgb-chart-widget-stroke" to "44 54 86",
                    "rgb-chart-widget-surface" to "18 24 44",
                    "rgb-checkbox-fill" to "18 24 44",
                    "rgb-dialog-title" to "236 240 255",
                    "rgb-field-fill" to "18 24 44",
                    "rgb-field-text" to "236 240 255",
                    "rgb-focus-control" to "236 240 255",
                    "rgb-link" to "255 160 130",
                    "rgb-link-prose" to "255 160 130",
                    "rgb-prose-bullet" to "60 72 110",
                    "rgb-prose-quote-bar" to "60 72 110",
                    "rgb-shimmer-base" to "236 240 255",
                    "rgb-status-verified" to "8 135 89",
                    "rgb-surface-card" to "28 36 62",
                    "rgb-surface-chat" to "18 24 44",
                    "rgb-surface-code" to "10 14 28",
                    "rgb-surface-composer" to "28 36 62",
                    "rgb-surface-popover" to "28 36 62",
                    "rgb-surface-primary" to "18 24 44",
                    "rgb-surface-search" to "28 36 62",
                    "rgb-surface-secondary" to "28 36 62",
                    "rgb-surface-sidebar" to "14 19 36",
                    "rgb-surface-submit" to "255 122 89",
                    "rgb-surface-submit-hover" to "235 100 70",
                    "rgb-surface-tooltip" to "18 24 44",
                    "rgb-surface-user-message" to "44 56 96",
                    "rgb-switch-thumb" to "18 24 44",
                    "rgb-table-header-text" to "170 180 210",
                    "rgb-text-primary" to "236 240 255",
                    "rgb-text-secondary" to "170 180 210",
                    "rgb-text-tooltip" to "236 240 255",
                ),
            ),
            "sources" to mapOf(
                "light" to mapOf(
                    "rgb-alert-error-border" to "117 183 129",
                    "rgb-alert-error-fill" to "80 112 16",
                    "rgb-avatar-placeholder" to "114 198 106",
                    "rgb-avatar-text" to "185 99 53",
                    "rgb-badge-label" to "185 99 53",
                    "rgb-border-control" to "6 226 46",
                    "rgb-border-field-focus" to "185 99 53",
                    "rgb-border-heavy" to "43 41 159",
                    "rgb-border-light" to "6 226 46",
                    "rgb-border-medium" to "3 241 23",
                    "rgb-border-menu" to "6 226 46",
                    "rgb-border-xheavy" to "188 84 76",
                    "rgb-button-primary" to "231 125 235",
                    "rgb-button-primary-hover" to "12 196 92",
                    "rgb-category-icon-1" to "154 254 242",
                    "rgb-category-icon-2" to "191 69 99",
                    "rgb-category-icon-4" to "228 140 212",
                    "rgb-category-icon-5" to "9 211 69",
                    "rgb-category-icon-6" to "46 26 182",
                    "rgb-category-icon-7" to "83 97 39",
                    "rgb-chart-widget-stroke" to "6 226 46",
                    "rgb-chart-widget-surface" to "225 155 189",
                    "rgb-checkbox-fill" to "225 155 189",
                    "rgb-dialog-title" to "185 99 53",
                    "rgb-drawer-edge" to "37 71 113",
                    "rgb-field-fill" to "225 155 189",
                    "rgb-field-text" to "185 99 53",
                    "rgb-focus-control" to "185 99 53",
                    "rgb-focus-outline" to "194 54 122",
                    "rgb-focus-subtle" to "43 41 159",
                    "rgb-page-canvas" to "37 71 113",
                    "rgb-presentation" to "74 142 226",
                    "rgb-prose-bullet" to "3 241 23",
                    "rgb-prose-quote-bar" to "3 241 23",
                    "rgb-ring-primary" to "194 54 122",
                    "rgb-series-1" to "154 254 242",
                    "rgb-series-2" to "191 69 99",
                    "rgb-series-4" to "228 140 212",
                    "rgb-series-5" to "9 211 69",
                    "rgb-series-6" to "46 26 182",
                    "rgb-series-7" to "83 97 39",
                    "rgb-series-8" to "120 168 152",
                    "rgb-shimmer-base" to "185 99 53",
                    "rgb-status-error-border" to "117 183 129",
                    "rgb-status-error-subtle" to "80 112 16",
                    "rgb-status-success-strong" to "49 11 205",
                    "rgb-status-verified" to "49 11 205",
                    "rgb-surface-active-alt" to "40 56 136",
                    "rgb-surface-canvas" to "37 71 113",
                    "rgb-surface-card" to "114 198 106",
                    "rgb-surface-card-hover" to "151 13 219",
                    "rgb-surface-chat" to "111 213 83",
                    "rgb-surface-code" to "37 71 113",
                    "rgb-surface-code-body" to "111 213 83",
                    "rgb-surface-code-inline" to "40 56 136",
                    "rgb-surface-composer" to "111 213 83",
                    "rgb-surface-composer-hover" to "148 28 196",
                    "rgb-surface-dialog" to "157 239 9",
                    "rgb-surface-hover" to "148 28 196",
                    "rgb-surface-hover-alt" to "77 127 249",
                    "rgb-surface-inverted" to "231 125 235",
                    "rgb-surface-inverted-hover" to "12 196 92",
                    "rgb-surface-inverted-pressed" to "12 196 92",
                    "rgb-surface-menu" to "74 142 226",
                    "rgb-surface-nav-hover" to "40 56 136",
                    "rgb-surface-nav-selected" to "40 56 136",
                    "rgb-surface-popover" to "225 155 189",
                    "rgb-surface-pressed" to "148 28 196",
                    "rgb-surface-primary" to "225 155 189",
                    "rgb-surface-primary-alt" to "37 71 113",
                    "rgb-surface-search" to "114 198 106",
                    "rgb-surface-secondary" to "114 198 106",
                    "rgb-surface-sidebar" to "37 71 113",
                    "rgb-surface-tab-selected" to "151 13 219",
                    "rgb-surface-tertiary" to "151 13 219",
                    "rgb-surface-tooltip" to "225 155 189",
                    "rgb-surface-user-message" to "151 13 219",
                    "rgb-switch-thumb" to "225 155 189",
                    "rgb-table-header-fill" to "157 239 9",
                    "rgb-table-header-text" to "120 168 152",
                    "rgb-text-muted" to "222 170 166",
                    "rgb-text-primary" to "185 99 53",
                    "rgb-text-secondary" to "120 168 152",
                    "rgb-text-tertiary" to "222 170 166",
                    "rgb-text-tooltip" to "185 99 53",
                ),
                "dark" to mapOf(
                    "rgb-alert-error-border" to "166 194 78",
                    "rgb-alert-error-fill" to "129 123 221",
                    "rgb-avatar-placeholder" to "200 24 168",
                    "rgb-avatar-text" to "234 110 2",
                    "rgb-badge-label" to "234 110 2",
                    "rgb-border-control" to "55 237 251",
                    "rgb-border-field-focus" to "234 110 2",
                    "rgb-border-heavy" to "92 52 108",
                    "rgb-border-light" to "55 237 251",
                    "rgb-border-medium" to "52 252 228",
                    "rgb-border-menu" to "55 237 251",
                    "rgb-border-xheavy" to "237 95 25",
                    "rgb-button-primary" to "24 136 184",
                    "rgb-button-primary-hover" to "61 207 41",
                    "rgb-category-icon-1" to "203 9 191",
                    "rgb-category-icon-2" to "240 80 48",
                    "rgb-category-icon-4" to "21 151 161",
                    "rgb-category-icon-5" to "58 222 18",
                    "rgb-category-icon-6" to "95 37 131",
                    "rgb-category-icon-7" to "132 108 244",
                    "rgb-chart-widget-stroke" to "55 237 251",
                    "rgb-chart-widget-surface" to "18 166 138",
                    "rgb-checkbox-fill" to "18 166 138",
                    "rgb-dialog-title" to "234 110 2",
                    "rgb-drawer-edge" to "237 95 25",
                    "rgb-field-fill" to "18 166 138",
                    "rgb-field-text" to "234 110 2",
                    "rgb-focus-control" to "234 110 2",
                    "rgb-focus-outline" to "243 65 71",
                    "rgb-focus-subtle" to "92 52 108",
                    "rgb-link-prose" to "234 110 2",
                    "rgb-page-canvas" to "86 82 62",
                    "rgb-presentation" to "123 153 175",
                    "rgb-prose-bullet" to "52 252 228",
                    "rgb-prose-quote-bar" to "52 252 228",
                    "rgb-ring-primary" to "243 65 71",
                    "rgb-series-1" to "203 9 191",
                    "rgb-series-2" to "240 80 48",
                    "rgb-series-4" to "21 151 161",
                    "rgb-series-5" to "58 222 18",
                    "rgb-series-6" to "95 37 131",
                    "rgb-series-7" to "132 108 244",
                    "rgb-series-8" to "169 179 101",
                    "rgb-shimmer-base" to "234 110 2",
                    "rgb-status-error-border" to "166 194 78",
                    "rgb-status-error-subtle" to "129 123 221",
                    "rgb-status-success-strong" to "98 22 154",
                    "rgb-status-verified" to "98 22 154",
                    "rgb-surface-active-alt" to "89 67 85",
                    "rgb-surface-canvas" to "86 82 62",
                    "rgb-surface-card" to "163 209 55",
                    "rgb-surface-card-hover" to "200 24 168",
                    "rgb-surface-chat" to "160 224 32",
                    "rgb-surface-code" to "123 153 175",
                    "rgb-surface-code-body" to "86 82 62",
                    "rgb-surface-code-inline" to "126 138 198",
                    "rgb-surface-composer" to "160 224 32",
                    "rgb-surface-composer-hover" to "197 39 145",
                    "rgb-surface-dialog" to "206 250 214",
                    "rgb-surface-hover" to "197 39 145",
                    "rgb-surface-hover-alt" to "126 138 198",
                    "rgb-surface-inverted" to "24 136 184",
                    "rgb-surface-inverted-hover" to "61 207 41",
                    "rgb-surface-inverted-pressed" to "61 207 41",
                    "rgb-surface-menu" to "123 153 175",
                    "rgb-surface-nav-hover" to "89 67 85",
                    "rgb-surface-nav-selected" to "89 67 85",
                    "rgb-surface-popover" to "163 209 55",
                    "rgb-surface-pressed" to "197 39 145",
                    "rgb-surface-primary" to "18 166 138",
                    "rgb-surface-primary-alt" to "86 82 62",
                    "rgb-surface-search" to "163 209 55",
                    "rgb-surface-secondary" to "163 209 55",
                    "rgb-surface-sidebar" to "86 82 62",
                    "rgb-surface-tab-selected" to "200 24 168",
                    "rgb-surface-tertiary" to "200 24 168",
                    "rgb-surface-tooltip" to "18 166 138",
                    "rgb-surface-user-message" to "200 24 168",
                    "rgb-switch-thumb" to "18 166 138",
                    "rgb-table-header-fill" to "206 250 214",
                    "rgb-table-header-text" to "169 179 101",
                    "rgb-text-muted" to "15 181 115",
                    "rgb-text-primary" to "234 110 2",
                    "rgb-text-secondary" to "169 179 101",
                    "rgb-text-tertiary" to "15 181 115",
                    "rgb-text-tooltip" to "234 110 2",
                ),
            ),
            "sparse" to mapOf(
                "light" to mapOf(
                    "rgb-avatar-text" to "10 20 30",
                    "rgb-badge-label" to "10 20 30",
                    "rgb-border-control" to "100 110 120",
                    "rgb-border-field-focus" to "40 50 60",
                    "rgb-border-medium" to "100 110 120",
                    "rgb-dialog-title" to "10 20 30",
                    "rgb-drawer-edge" to "70 80 90",
                    "rgb-field-text" to "10 20 30",
                    "rgb-focus-control" to "40 50 60",
                    "rgb-link" to "1 2 3",
                    "rgb-link-prose" to "1 2 3",
                    "rgb-prose-bullet" to "100 110 120",
                    "rgb-prose-quote-bar" to "100 110 120",
                    "rgb-shimmer-base" to "10 20 30",
                    "rgb-surface-sidebar" to "70 80 90",
                    "rgb-text-primary" to "10 20 30",
                    "rgb-text-tooltip" to "10 20 30",
                ),
                "dark" to mapOf(
                    "rgb-avatar-text" to "10 20 30",
                    "rgb-badge-label" to "10 20 30",
                    "rgb-border-control" to "100 110 120",
                    "rgb-border-field-focus" to "40 50 60",
                    "rgb-border-medium" to "100 110 120",
                    "rgb-dialog-title" to "10 20 30",
                    "rgb-field-text" to "10 20 30",
                    "rgb-focus-control" to "40 50 60",
                    "rgb-link-prose" to "10 20 30",
                    "rgb-prose-bullet" to "100 110 120",
                    "rgb-prose-quote-bar" to "100 110 120",
                    "rgb-shimmer-base" to "10 20 30",
                    "rgb-surface-sidebar" to "70 80 90",
                    "rgb-text-primary" to "10 20 30",
                    "rgb-text-tooltip" to "10 20 30",
                ),
            ),
            "legacy" to mapOf(
                "light" to mapOf(
                    "rgb-category-icon-4" to "9 8 7",
                    "rgb-series-3" to "3 3 3",
                    "rgb-series-8" to "66 66 66",
                    "rgb-status-verified" to "2 133 94",
                    "rgb-text-on-status" to "6 5 4",
                ),
                "dark" to mapOf(
                    "rgb-category-icon-4" to "7 7 7",
                    "rgb-surface-composer-hover" to "30 30 30",
                    "rgb-surface-hover" to "20 20 20",
                    "rgb-surface-pressed" to "20 20 20",
                ),
            ),
            "lightonly" to mapOf(
                "light" to mapOf(
                    "rgb-surface-chat" to "200 200 255",
                    "rgb-surface-code-body" to "200 200 255",
                    "rgb-surface-composer" to "200 200 255",
                ),
                "dark" to emptyMap(),
            ),
        )
    }
}
