package com.skyscope.app

import android.annotation.SuppressLint
import android.content.Context
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** One satellite as reported by the phone's GNSS chip. */
data class Sat(
    val constellation: Int,
    val svid: Int,
    /** Carrier-to-noise density in dB-Hz. 0 means the phone expects it but can't hear it. */
    val cn0: Float,
    val elevation: Float,
    val azimuth: Float,
    val usedInFix: Boolean,
)

/** Everything the app knows right now. */
data class GnssSnapshot(
    val sats: List<Sat> = emptyList(),
    val accuracyM: Float? = null,
    val lat: Double? = null,
    val lon: Double? = null,
    val lastFixAt: Long? = null,
    val gpsEnabled: Boolean = true,
    val startedAt: Long = System.currentTimeMillis(),
    val timeToFirstFixMs: Int? = null,
)

/** Listens to the GNSS chip and exposes the latest snapshot as a StateFlow. */
class GnssTracker(context: Context) {

    private val locationManager =
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _state = MutableStateFlow(GnssSnapshot())
    val state: StateFlow<GnssSnapshot> = _state.asStateFlow()

    private var running = false

    private val statusCallback = object : GnssStatus.Callback() {
        override fun onSatelliteStatusChanged(status: GnssStatus) {
            val sats = List(status.satelliteCount) { i ->
                Sat(
                    constellation = status.getConstellationType(i),
                    svid = status.getSvid(i),
                    cn0 = status.getCn0DbHz(i),
                    elevation = status.getElevationDegrees(i),
                    azimuth = status.getAzimuthDegrees(i),
                    usedInFix = status.usedInFix(i),
                )
            }
            _state.update { it.copy(sats = sats) }
        }

        override fun onFirstFix(ttffMillis: Int) {
            _state.update { it.copy(timeToFirstFixMs = ttffMillis) }
        }
    }

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            _state.update {
                it.copy(
                    accuracyM = if (location.hasAccuracy()) location.accuracy else null,
                    lat = location.latitude,
                    lon = location.longitude,
                    lastFixAt = System.currentTimeMillis(),
                )
            }
        }

        override fun onProviderEnabled(provider: String) {
            _state.update { it.copy(gpsEnabled = true) }
        }

        override fun onProviderDisabled(provider: String) {
            _state.update { it.copy(gpsEnabled = false, lastFixAt = null) }
        }

        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
        }
    }

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    fun start() {
        if (running) return
        running = true
        _state.update {
            it.copy(
                startedAt = System.currentTimeMillis(),
                gpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER),
            )
        }
        locationManager.registerGnssStatusCallback(statusCallback, mainHandler)
        locationManager.requestLocationUpdates(
            LocationManager.GPS_PROVIDER,
            1000L,
            0f,
            locationListener,
            Looper.getMainLooper(),
        )
    }

    fun stop() {
        if (!running) return
        running = false
        locationManager.unregisterGnssStatusCallback(statusCallback)
        locationManager.removeUpdates(locationListener)
    }
}
