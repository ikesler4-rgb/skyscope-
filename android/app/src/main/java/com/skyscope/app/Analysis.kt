package com.skyscope.app

import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class Severity { GOOD, INFO, WARN, BAD }

enum class Level(val label: String) {
    EXCELLENT("Excellent"),
    GOOD("Good"),
    FAIR("Fair"),
    POOR("Poor"),
    NONE("No signal"),
}

data class Rec(val severity: Severity, val title: String, val detail: String)

data class Assessment(
    val score: Int,
    val level: Level,
    val headline: String,
    val status: String,
    val statusSeverity: Severity,
    val tracked: Int,
    val used: Int,
    val top4: Float?,
    val accuracy: Float?,
    val constellationsUsed: Int,
    val recommendations: List<Rec>,
)

private val QUADRANT_NAMES = listOf("north", "east", "south", "west")

/** 0 = north, 1 = east, 2 = south, 3 = west. */
private fun quadrantOf(azimuth: Float): Int {
    val a = ((azimuth % 360f) + 360f + 45f) % 360f
    return (a / 90f).toInt().coerceIn(0, 3)
}

fun constellationName(type: Int): String = when (type) {
    1 -> "GPS"
    2 -> "SBAS"
    3 -> "GLONASS"
    4 -> "QZSS"
    5 -> "BeiDou"
    6 -> "Galileo"
    7 -> "NavIC"
    else -> "Other"
}

fun constellationShort(type: Int): String = when (type) {
    1 -> "GPS"
    2 -> "SBAS"
    3 -> "GLO"
    4 -> "QZS"
    5 -> "BDS"
    6 -> "GAL"
    7 -> "NAV"
    else -> "?"
}

/** Turns raw satellite data into a score, a verdict and practical advice. */
fun analyze(s: GnssSnapshot, now: Long): Assessment {
    val heard = s.sats.filter { it.cn0 > 0f }
    val tracked = heard.size
    val used = s.sats.count { it.usedInFix }
    val top4 = heard.map { it.cn0 }.sortedDescending().take(4)
        .takeIf { it.isNotEmpty() }?.average()?.toFloat()
    val lastFix = s.lastFixAt
    val fixFresh = lastFix != null && now - lastFix < 5_000L
    val accuracy = if (fixFresh) s.accuracyM else null
    val sinceStart = now - s.startedAt
    val constellationsUsed = s.sats.filter { it.usedInFix }.map { it.constellation }.distinct().size

    // ---- Score (0-100) ----
    val cn0Score = top4?.let { ((it - 20f) / 25f).coerceIn(0f, 1f) } ?: 0f
    val usedScore = (used / 12f).coerceIn(0f, 1f)
    val accScore = accuracy?.let {
        (1f - (ln(max(it, 1f)) - ln(3f)) / (ln(50f) - ln(3f))).coerceIn(0f, 1f)
    }
    val raw = if (accScore != null) {
        0.45f * cn0Score + 0.30f * usedScore + 0.25f * accScore
    } else {
        (0.45f * cn0Score + 0.30f * usedScore) / 0.75f
    }
    var score = if (tracked == 0) 0 else (raw * 100f).roundToInt()
    if (!fixFresh) score = min(score, 45)

    val level = when {
        tracked == 0 -> Level.NONE
        score >= 80 -> Level.EXCELLENT
        score >= 60 -> Level.GOOD
        score >= 35 -> Level.FAIR
        else -> Level.POOR
    }

    val headline = when (level) {
        Level.EXCELLENT -> "Clear sky view. Positioning here is as good as a phone gets."
        Level.GOOD -> "Solid signal. Reliable for navigation and tracking."
        Level.FAIR -> "Usable, but expect your position to drift a bit."
        Level.POOR -> "Weak reception. Your position may be off by tens of meters."
        Level.NONE -> when {
            !s.gpsEnabled -> "Location is turned off."
            sinceStart < 45_000L -> "Listening for satellites…"
            else -> "No satellites heard. You're probably indoors."
        }
    }

    val (status, statusSeverity) = when {
        !s.gpsEnabled -> "Location off" to Severity.BAD
        fixFresh -> "Live fix" to Severity.GOOD
        tracked > 0 -> "Searching" to Severity.WARN
        else -> "No signal" to Severity.BAD
    }

    // ---- Recommendations ----
    val recs = mutableListOf<Rec>()

    if (!s.gpsEnabled) {
        recs += Rec(
            Severity.BAD, "Turn on Location",
            "The phone can't listen to satellites while Location is off. Use the button above to open settings.",
        )
    } else if (tracked == 0) {
        recs += if (sinceStart < 45_000L) {
            Rec(
                Severity.INFO, "Hold still for up to a minute",
                "The phone is listening for satellites. Stay outdoors with a clear view of the sky.",
            )
        } else {
            Rec(
                Severity.BAD, "Go outside",
                "No satellites are reaching the phone. Roofs, walls and vehicles block these signals almost completely.",
            )
        }
    } else {
        val strongest = top4 ?: 0f
        val weakEverywhere = strongest < 28f

        if (weakEverywhere) {
            recs += Rec(
                Severity.BAD, "Signals are weak in every direction",
                "You're probably indoors, in a vehicle, or under dense cover. Move into the open, away from roofs and trees.",
            )
        } else {
            // Which parts of the sky are blocked?
            val strong = IntArray(4)
            val weak = IntArray(4)
            val heardQ = IntArray(4)
            s.sats.filter { it.elevation in 5f..65f }.forEach {
                val q = quadrantOf(it.azimuth)
                if (it.cn0 >= 30f) strong[q]++
                if (it.cn0 < 25f) weak[q]++
                if (it.cn0 > 0f) heardQ[q]++
            }
            val strongTotal = strong.sum()
            val blocked = (0..3).filter { q ->
                strong[q] == 0 && (weak[q] >= 2 || (heardQ[q] == 0 && strongTotal >= 6))
            }

            if (blocked.size in 1..2 && strongTotal >= 3) {
                val names = blocked.map { QUADRANT_NAMES[it] }
                val where = names.joinToString(" and ")
                val move = if (blocked.size == 1) {
                    "Move a few meters toward the ${QUADRANT_NAMES[(blocked[0] + 2) % 4]}, away from whatever is on that side."
                } else {
                    "Look for a spot with open sky toward the $where."
                }
                recs += Rec(
                    Severity.WARN, "Sky blocked to the $where",
                    "Satellites on that side are faint or missing, likely a building, hill or trees. $move",
                )
            } else if (blocked.size >= 3) {
                recs += Rec(
                    Severity.WARN, "Most of the sky is blocked",
                    "Only a narrow patch of sky is visible, like a street between tall buildings. A wider open area will help.",
                )
            }

            val lowGood = heard.count { it.elevation in 5f..25f && it.cn0 >= 25f }
            val highGood = heard.count { it.elevation > 45f && it.cn0 >= 30f }
            if (lowGood <= 1 && highGood >= 3 && blocked.isEmpty()) {
                recs += Rec(
                    Severity.WARN, "Low horizon is blocked",
                    "Satellites overhead are clear but ones near the horizon are missing. That's typical of streets, courtyards and forests. Higher ground or a wide-open space gives you more satellites.",
                )
            }

            if (accuracy != null && accuracy > 15f && strongest >= 36f && used >= 8) {
                recs += Rec(
                    Severity.WARN, "Strong signals, shaky position",
                    "This usually means signals are bouncing off glass or metal. Step away from tall buildings and parked cars.",
                )
            }
        }

        if (!fixFresh && tracked >= 4) {
            recs += Rec(
                Severity.INFO, "Locking on…",
                if (sinceStart < 60_000L) {
                    "Hearing $tracked satellites. A first fix usually takes 10 to 60 seconds. Keep the phone still."
                } else {
                    "Satellites are heard but there's no fix yet. Weak signals make locking slow, so try a more open spot."
                },
            )
        }

        if ((level == Level.FAIR || level == Level.POOR) && !weakEverywhere) {
            recs += Rec(
                Severity.INFO, "Hold the phone right",
                "Hold it flat, screen up, at chest height or higher. Your hand and body absorb more signal than you'd expect.",
            )
        }

        val hasProblems = recs.any { it.severity == Severity.WARN || it.severity == Severity.BAD }
        if (level == Level.EXCELLENT || (level == Level.GOOD && !hasProblems)) {
            val acc = accuracy?.let { " Estimated accuracy ±${it.roundToInt()} m." } ?: ""
            recs += Rec(
                Severity.GOOD,
                if (level == Level.EXCELLENT) "Great spot" else "Good spot",
                "$used satellites from $constellationsUsed systems are in your fix.$acc",
            )
        }
    }

    if (recs.isEmpty()) {
        recs += Rec(
            Severity.INFO, "Keep measuring",
            "Walk slowly and watch the score to find the best spot nearby.",
        )
    }

    return Assessment(
        score = score,
        level = level,
        headline = headline,
        status = status,
        statusSeverity = statusSeverity,
        tracked = tracked,
        used = used,
        top4 = top4,
        accuracy = accuracy,
        constellationsUsed = constellationsUsed,
        recommendations = recs,
    )
}
