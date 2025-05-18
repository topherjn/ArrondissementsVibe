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
    private var arrondissementState by mutableStateOf<Int?>(null) // New state for arrondissement
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
                            geocodingService.getAddressFromCoordinates(currentLocation.first!!, currentLocation.second!!)
                                .collectLatest { address ->
                                    postalCodeState = address?.postalCode
                                    arrondissementState = address?.postalCode?.takeIf { it.startsWith("75") }?.takeLast(2)?.toIntOrNull() // Extract arrondissement
                                    println("Postal Code (from Flow): ${address?.postalCode}, Arrondissement: $arrondissementState")
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
                    if (location != null) {
                        locationState.value = Pair(location.latitude, location.longitude)
                        println("Latitude: ${location.latitude}, Longitude: ${location.longitude}")
                        // The getPostalCode/arrondissement logic is now in setContent
                    } else {
                        locationState.value = Pair(null, null)
                        postalCodeState = "Location Unavailable"
                        arrondissementState = null // Reset arrondissement
                        println("Last known location was null.")
                    }
                }
                .addOnFailureListener { e ->
                    locationState.value = Pair(null, null)
                    postalCodeState = "Location Error: ${e.localizedMessage}"
                    arrondissementState = null // Reset arrondissement
                    println("Failed to get last location: ${e.localizedMessage}")
                }
        } catch (securityException: SecurityException) {
            locationState.value = Pair(null, null)
            postalCodeState = "Security Exception"
            arrondissementState = null // Reset arrondissement
            println("Security exception while getting location: $securityException")
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
                text = "$arrondissement",
                style = MaterialTheme.typography.displayLarge // Use a large typography style
            )
            Text(
                text = "Arrondissement",
                style = MaterialTheme.typography.headlineSmall
            )
            if (location != null) {
                val latitude = location.first
                val longitude = location.second
                if (latitude != null && longitude != null) {
                    Text(text = "Lat: $latitude, Lon: $longitude", style = MaterialTheme.typography.bodySmall)
                }
            }
            if (postalCode != null) {
                Text(text = "Postal Code: $postalCode", style = MaterialTheme.typography.bodySmall)
            }
        } else {
            Text(
                text = "Locating...",
                style = MaterialTheme.typography.headlineMedium
            )
            if (location != null) {
                val latitude = location.first
                val longitude = location.second
                if (latitude != null && longitude != null) {
                    Text(text = "Lat: $latitude, Lon: $longitude", style = MaterialTheme.typography.bodySmall)
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