package com.skyscope.app

import android.content.Context
import org.json.JSONArray
import kotlin.math.cos
import kotlin.math.hypot

/** One place the phone passed, with the satellite score measured there. */
data class TrailPoint(val lat: Double, val lon: Double, val score: Int, val t: Long)

/** The colored signal trail shown on the map, kept on the phone between launches. */
class SignalTrail(context: Context) {

    private val prefs = context.getSharedPreferences("skyscope", Context.MODE_PRIVATE)
    private val _points = load().toMutableList()
    val points: List<TrailPoint> get() = _points

    /** Adds a point unless it is a near-duplicate of the last one. Returns true when the trail changed. */
    fun add(p: TrailPoint): Boolean {
        val prev = _points.lastOrNull()
        if (prev != null && distanceM(prev, p) < 4.0 && p.t - prev.t < 15_000L) return false
        _points += p
        if (_points.size > MAX_POINTS) _points.subList(0, _points.size - MAX_POINTS).clear()
        save()
        return true
    }

    fun clear() {
        _points.clear()
        save()
    }

    private fun load(): List<TrailPoint> = try {
        val arr = JSONArray(prefs.getString(KEY, "[]"))
        List(arr.length()) { i ->
            val o = arr.getJSONArray(i)
            TrailPoint(o.getDouble(0), o.getDouble(1), o.getInt(2), o.getLong(3))
        }
    } catch (e: Exception) {
        emptyList()
    }

    private fun save() {
        val arr = JSONArray()
        _points.forEach { arr.put(JSONArray().put(it.lat).put(it.lon).put(it.score).put(it.t)) }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    private fun distanceM(a: TrailPoint, b: TrailPoint): Double {
        val mLat = 111_320.0
        val mLon = 111_320.0 * cos(Math.toRadians(a.lat))
        return hypot((a.lat - b.lat) * mLat, (a.lon - b.lon) * mLon)
    }

    private companion object {
        const val KEY = "trail.v1"
        const val MAX_POINTS = 4000
    }
}
