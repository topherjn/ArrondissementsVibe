package com.topherjn.arrondissementsvibe.location

import android.location.Address
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class GoogleGeocoderService : GeocodingService {
    override fun getAddressFromCoordinates(latitude: Double, longitude: Double): Flow<Address?> = flowOf(null) // Placeholder
}