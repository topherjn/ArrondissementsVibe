package com.topherjn.arrondissementsvibe

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.topherjn.arrondissementsvibe.location.BuiltinGeocoderService
import com.topherjn.arrondissementsvibe.location.GeocodingService
import com.topherjn.arrondissementsvibe.ui.theme.ArrondissementsVibeTheme
import kotlinx.coroutines.flow.collectLatest

class MainActivity : ComponentActivity() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationPermissionRequest: ActivityResultLauncher<String>
    private val locationState = mutableStateOf<Pair<Double?, Double?>?>(null)
    private var postalCodeState by mutableStateOf<String?>(null)
    private var arrondissementState by mutableStateOf<Int?>(null)
    private lateinit var geocodingService: GeocodingService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        geocodingService = BuiltinGeocoderService(applicationContext)

        locationPermissionRequest = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if (isGranted) {
                getLocation()
            } else {
                locationState.value = Pair(null, null)
                postalCodeState = "Permission Denied"
                arrondissementState = null // Reset arrondissement
                println("Location permission denied.")
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
                        postalCode = postalCodeState,
                        arrondissement = arrondissementState, // Pass arrondissement state
                        modifier = Modifier.padding(innerPadding)
                    )

                    val currentLocation = locationState.value
                    LaunchedEffect(currentLocation?.first, currentLocation?.second) {
                        if (currentLocation?.first != null && currentLocation.second != null) {
                            geocodingService.getAddressFromCoordinates(currentLocation.first!!,
                                currentLocation.second!!)
                                .collectLatest { address ->
                                    postalCodeState = address?.postalCode
                                    arrondissementState = address?.postalCode?.takeIf {
                                        it.startsWith("75") }?.takeLast(2)?.toIntOrNull()
                                    println("Postal Code (from Flow): ${address?.postalCode}, " +
                                            "Arrondissement: $arrondissementState")
                                }
                        } else {
                            postalCodeState = null
                            arrondissementState = null
                        }
                    }
                }
            }
        }
    }

    private fun getLocation() {
        try {
            fusedLocationClient.lastLocation
                .addOnSuccessListener { location ->
                    // Check if location is recent enough (e.g., within 30 seconds) or not null
                    val currentTime = System.currentTimeMillis()
                    val locationAge = if (location != null) currentTime - location.time else Long.MAX_VALUE // Use Long.MAX_VALUE if location is null

                    if (location != null && locationAge < 30000) { // If location is less than 30 seconds old
                        locationState.value = Pair(location.latitude, location.longitude)
                        println("Using Last Known Location: Lat=${location.latitude}, Lon=${location.longitude}, Age=${locationAge/1000}s")
                    } else {
                        println("Last known location is null or stale, requesting current location.")
                        requestCurrentLocationUpdates()
                    }
                }
                .addOnFailureListener { e ->
                    println("Failed to get last known location: ${e.localizedMessage}, requesting current.")
                    requestCurrentLocationUpdates()
                }
        } catch (securityException: SecurityException) {
            println("Security exception while getting location: $securityException")
            postalCodeState = "Security Exception" // Update state in case of exception
            arrondissementState = null
        }
    }

    private fun requestCurrentLocationUpdates() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null)
            println("Requesting current location updates...")
        } else {
            println("Location permission not granted to request updates.")
            locationState.value = Pair(null, null)
            postalCodeState = "Permission Denied"
            arrondissementState = null
        }
    }

    // Inside MainActivity class, outside any function
    private val locationRequest = LocationRequest.Builder(1000L) // Request updates every 1 second
        .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY) // Prioritize high accuracy
        .setWaitForAccurateLocation(false) // Don't wait too long for perfect accuracy
        .setMinUpdateIntervalMillis(500L) // Minimum time between updates
        .setMaxUpdateDelayMillis(2000L) // Maximum time between updates
        .build()

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            locationResult.lastLocation?.let { location ->
                locationState.value = Pair(location.latitude, location.longitude)
                println("Fresh Location: Lat=${location.latitude}, Lon=${location.longitude}")
                // Stop updates once we get a fresh location
                fusedLocationClient.removeLocationUpdates(this)
            }
        }
    }
}

@Composable
fun LocationDisplay(
    location: Pair<Double?, Double?>?,
    postalCode: String?,
    arrondissement: Int?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally, // Center items horizontally
        verticalArrangement = Arrangement.Center // Center items vertically
    ) {
        if (arrondissement != null) {
            Text(
                text = "Arrondissement",
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                text = "$arrondissement",
                style = MaterialTheme.typography.displayLarge
            )
        } else {
            Text(
                text = "Locating...",
                style = MaterialTheme.typography.headlineMedium
            )
            if (location != null) {
                val latitude = location.first
                val longitude = location.second
                if (latitude != null && longitude != null) {
                    Text(text = "Lat: $latitude, Lon: $longitude",
                        style = MaterialTheme.typography.bodySmall)
                }
            }
            if (postalCode != null) {
                Text(text = "Postal Code: $postalCode", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun LocationDisplayPreview() {
    ArrondissementsVibeTheme {
        LocationDisplay(location = Pair(48.8566, 2.3522), postalCode = "75001", arrondissement = 1)
    }
}