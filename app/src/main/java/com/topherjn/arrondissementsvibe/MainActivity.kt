package com.topherjn.arrondissementsvibe

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.topherjn.arrondissementsvibe.ui.theme.ArrondissementsVibeTheme

class MainActivity : ComponentActivity() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationPermissionRequest: ActivityResultLauncher<String>
    private val locationState = mutableStateOf<Pair<Double?, Double?>?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        locationPermissionRequest = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if (isGranted) {
                getLocation()
            } else {
                locationState.value = Pair(null, null) // Indicate permission denied
                println("Location permission denied.")
                // Optionally, show a UI message.
            }
        }

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            getLocation()
        } else {
            locationPermissionRequest.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        enableEdgeToEdge()
        setContent {
            ArrondissementsVibeTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    LocationDisplay(
                        location = locationState.value,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }

    private fun getLocation() {
        try {
            fusedLocationClient.lastLocation
                .addOnSuccessListener { location ->
                    if (location != null) {
                        locationState.value = Pair(location.latitude, location.longitude)
                        println("Latitude: ${location.latitude}, Longitude: ${location.longitude}")
                    } else {
                        locationState.value = Pair(null, null)
                        println("Last known location was null.")
                    }
                }
                .addOnFailureListener { e ->
                    locationState.value = Pair(null, null)
                    println("Failed to get last location: ${e.localizedMessage}")
                }
        } catch (securityException: SecurityException) {
            locationState.value = Pair(null, null)
            println("Security exception while getting location: $securityException")
        }
    }
}

@Composable
fun LocationDisplay(location: Pair<Double?, Double?>?, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Location:",
            style = MaterialTheme.typography.headlineMedium
        )
        if (location != null) {
            val latitude = location.first
            val longitude = location.second
            if (latitude != null && longitude != null) {
                Text(text = "Latitude: $latitude")
                Text(text = "Longitude: $longitude")
            } else {
                Text(text = "Could not retrieve location.")
            }
        } else {
            Text(text = "Waiting for location...")
        }
    }
}

@Preview(showBackground = true)
@Composable
fun LocationDisplayPreview() {
    ArrondissementsVibeTheme {
        LocationDisplay(location = Pair(48.8566, 2.3522)) // Example Paris coordinates
    }
}