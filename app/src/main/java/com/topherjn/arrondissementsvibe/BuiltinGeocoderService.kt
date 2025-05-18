package com.topherjn.arrondissementsvibe.location

import android.content.Context
import android.location.Address
import android.location.Geocoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.Locale

class BuiltinGeocoderService(private val context: Context) : GeocodingService {
    override fun getAddressFromCoordinates(latitude: Double, longitude: Double): Flow<Address?> = flow {
        val geocoder = Geocoder(context, Locale.getDefault())
        try {
            val addresses = withContext(Dispatchers.IO) {
                geocoder.getFromLocation(latitude, longitude, 1)
            }
            emit(addresses?.firstOrNull())
        } catch (e: IOException) {
            emit(null) // Or handle the error more specifically
            e.printStackTrace()
        } catch (e: IllegalArgumentException) {
            emit(null) // Or handle the error more specifically
            e.printStackTrace()
        }
    }
}