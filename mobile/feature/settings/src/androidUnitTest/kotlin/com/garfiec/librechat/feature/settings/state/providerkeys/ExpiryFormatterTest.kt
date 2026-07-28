package com.garfiec.librechat.feature.settings.state.providerkeys

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * Tests for [formatRelativeExpiry]. The formatter is hermetic — `now` is injected — so we can
 * exercise the boundary cases without touching the system clock.
 *
 * Output contract (matches the docstring on `formatRelativeExpiry`):
 * - sub-minute → `"<1m"`
 * - sub-hour   → `"Nm"` (zero-pads not added)
 * - sub-day    → `"Nh"`
 * - past       → `"0m"`
 */
class ExpiryFormatterTest {

    private val now: Instant = Instant.parse("2026-05-01T00:00:00Z")

    @Test
    fun formats_sub_minute_as_lt_1m() {
        val expiresAt = now + 30.seconds
        assertThat(formatRelativeExpiry(expiresAt, now)).isEqualTo("<1m")
    }

    @Test
    fun formats_minutes_under_an_hour() {
        val expiresAt = now + 42.minutes
        assertThat(formatRelativeExpiry(expiresAt, now)).isEqualTo("42m")
    }

    @Test
    fun formats_one_minute_boundary_as_1m() {
        val expiresAt = now + 1.minutes
        assertThat(formatRelativeExpiry(expiresAt, now)).isEqualTo("1m")
    }

    @Test
    fun formats_hours_under_a_day() {
        val expiresAt = now + 11.hours
        assertThat(formatRelativeExpiry(expiresAt, now)).isEqualTo("11h")
    }

    @Test
    fun formats_one_hour_boundary_as_1h() {
        val expiresAt = now + 1.hours
        assertThat(formatRelativeExpiry(expiresAt, now)).isEqualTo("1h")
    }

    @Test
    fun formats_days() {
        val expiresAt = now + 3.days
        assertThat(formatRelativeExpiry(expiresAt, now)).isEqualTo("3d")
    }

    @Test
    fun formats_30_day_max_preset_as_30d() {
        val expiresAt = now + 30.days
        assertThat(formatRelativeExpiry(expiresAt, now)).isEqualTo("30d")
    }

    @Test
    fun formats_past_as_zero_minutes() {
        // KeyState.Expired covers this in normal flow; defensive default for the formatter.
        val expiresAt = now - 5.minutes
        assertThat(formatRelativeExpiry(expiresAt, now)).isEqualTo("0m")
    }

    @Test
    fun formats_zero_duration_as_zero_minutes() {
        assertThat(formatRelativeExpiry(now, now)).isEqualTo("0m")
    }

    @Test
    fun never_emits_raw_iso_substring() {
        // Regression: the prior bug substituted `Instant.toString()` directly, producing
        // `2026-05-01T00:06:38.286Z`-style output. Format must not contain ISO markers.
        val expiresAt = now + 11.hours
        val out = formatRelativeExpiry(expiresAt, now)
        assertThat(out).doesNotContain("T")
        assertThat(out).doesNotContain("Z")
        assertThat(out).doesNotContain(":")
    }
}
