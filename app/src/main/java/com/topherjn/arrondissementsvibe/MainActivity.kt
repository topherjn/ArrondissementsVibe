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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator // Import for progress indicator
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
import com.google.android.gms.location.LocationRequest
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
    private var isLoading by mutableStateOf(false) // NEW: State to track loading
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
                arrondissementState = null
                isLoading = false // NEW: Set to false if permission denied
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
                        arrondissement = arrondissementState,
                        isLoading = isLoading, // NEW: Pass loading state
                        onRefreshLocation = { getLocation() },
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
                                    isLoading = false // NEW: Set to false after geocoding
                                    println("Postal Code (from Flow): ${address?.postalCode}, " +
                                            "Arrondissement: $arrondissementState")
                                }
                        } else {
                            // If location becomes null or invalid, ensure loading is off
                            isLoading = false
                            postalCodeState = null
                            arrondissementState = null
                        }
                    }
                }
            }
        }
    }

    private fun getLocation() {
        isLoading = true // NEW: Set to true when starting location request
        locationState.value = null // Clear previous location data immediately
        postalCodeState = null
        arrondissementState = null

        try {
            fusedLocationClient.lastLocation
                .addOnSuccessListener { location ->
                    val currentTime = System.currentTimeMillis()
                    val locationAge = if (location != null) currentTime - location.time else Long.MAX_VALUE

                    if (location != null && locationAge < 30000) {
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
            postalCodeState = "Security Exception"
            arrondissementState = null
            isLoading = false // NEW: Set to false on exception
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
            isLoading = false // NEW: Set to false if no permission for updates
        }
    }

    private val locationRequest = LocationRequest.Builder(1000L)
        .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
        .setWaitForAccurateLocation(false)
        .setMinUpdateIntervalMillis(500L)
        .setMaxUpdateDelayMillis(2000L)
        .build()

    private val locationCallback = object : com.google.android.gms.location.LocationCallback() {
        override fun onLocationResult(locationResult: com.google.android.gms.location.LocationResult) {
            locationResult.lastLocation?.let { location ->
                locationState.value = Pair(location.latitude, location.longitude)
                println("Fresh Location: Lat=${location.latitude}, Lon=${location.longitude}")
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
    isLoading: Boolean, // NEW: Accept loading state
    onRefreshLocation: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (isLoading) { // NEW: Display loading indicator
            CircularProgressIndicator(modifier = Modifier.padding(16.dp))
            Text(
                text = "Locating...",
                style = MaterialTheme.typography.headlineMedium
            )
        } else if (arrondissement != null) {
            Text(
                text = "Arrondissement",
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                text = "$arrondissement",
                style = MaterialTheme.typography.displayLarge
            )
        } else {
            // Display error/status messages only when not loading
            Text(
                text = "Not in Paris?",
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
        Button(
            onClick = onRefreshLocation,
            enabled = !isLoading // NEW: Disable button while loading
        ) {
            Text("Refresh Location")
        }
    }
}

@Preview(showBackground = true)
@Composable
fun LocationDisplayPreview() {
    ArrondissementsVibeTheme {
        // Ensure this call provides the lambda as the last argument
        LocationDisplay(
            location = Pair(48.8566, 2.3522),
            postalCode = "75001",
            arrondissement = 1,
            isLoading = false, // Set to false for preview
            onRefreshLocation = { /* This is the empty lambda */ } // Explicitly pass the lambda
        )
    }
}