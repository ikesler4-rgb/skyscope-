package com.skyscope.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {

    private lateinit var tracker: GnssTracker
    private lateinit var trail: SignalTrail
    private var hasPermission by mutableStateOf(false)

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            hasPermission = result[Manifest.permission.ACCESS_FINE_LOCATION] == true
            if (hasPermission) tracker.start()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Keep the screen awake while measuring.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        tracker = GnssTracker(applicationContext)
        trail = SignalTrail(applicationContext)
        hasPermission = isFineLocationGranted()

        setContent {
            SkyScopeTheme {
                if (hasPermission) {
                    val snapshot by tracker.state.collectAsState()
                    MainScreen(
                        snapshot = snapshot,
                        trail = trail,
                        onOpenLocationSettings = {
                            startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                        },
                    )
                } else {
                    PermissionScreen(
                        onGrant = {
                            permissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION,
                                )
                            )
                        },
                        onOpenAppSettings = {
                            startActivity(
                                Intent(
                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.fromParts("package", packageName, null),
                                )
                            )
                        },
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        hasPermission = isFineLocationGranted()
        if (hasPermission) tracker.start()
    }

    override fun onStop() {
        super.onStop()
        tracker.stop()
    }

    private fun isFineLocationGranted(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
}
