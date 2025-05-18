package com.topherjn.arrondissementsvibe.location

import android.location.Address
import kotlinx.coroutines.flow.Flow

interface GeocodingService {
    fun getAddressFromCoordinates(latitude: Double, longitude: Double): Flow<Address?>
}