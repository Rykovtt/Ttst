package com.kartoteka.app.data

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

/** Адрес ↔ координаты через системный геокодер телефона. */
object Geo {
    fun available() = Geocoder.isPresent()

    suspend fun locate(context: Context, address: String): Pair<Double, Double>? {
        if (address.isBlank() || !available()) return null
        val geocoder = Geocoder(context, Locale("ru"))
        val result: Address? = withTimeoutOrNull(10_000) {
            if (Build.VERSION.SDK_INT >= 33) {
                suspendCancellableCoroutine { cont ->
                    geocoder.getFromLocationName(address, 1, object : Geocoder.GeocodeListener {
                        override fun onGeocode(list: MutableList<Address>) { cont.resume(list.firstOrNull()) }
                        override fun onError(errorMessage: String?) { cont.resume(null) }
                    })
                }
            } else withContext(Dispatchers.IO) {
                @Suppress("DEPRECATION")
                runCatching { geocoder.getFromLocationName(address, 1)?.firstOrNull() }.getOrNull()
            }
        }
        return result?.let { it.latitude to it.longitude }
    }

    suspend fun addressOf(context: Context, lat: Double, lng: Double): String? {
        if (!available()) return null
        val geocoder = Geocoder(context, Locale("ru"))
        val result: Address? = withTimeoutOrNull(10_000) {
            if (Build.VERSION.SDK_INT >= 33) {
                suspendCancellableCoroutine { cont ->
                    geocoder.getFromLocation(lat, lng, 1, object : Geocoder.GeocodeListener {
                        override fun onGeocode(list: MutableList<Address>) { cont.resume(list.firstOrNull()) }
                        override fun onError(errorMessage: String?) { cont.resume(null) }
                    })
                }
            } else withContext(Dispatchers.IO) {
                @Suppress("DEPRECATION")
                runCatching { geocoder.getFromLocation(lat, lng, 1)?.firstOrNull() }.getOrNull()
            }
        }
        return result?.getAddressLine(0)
    }

    /** Находит координаты для всех адресов, у которых их ещё нет. */
    suspend fun fillMissing(context: Context, repo: Repository) {
        repo.placesWithoutCoords().forEach { place ->
            locate(context, place.address)?.let { (lat, lng) -> repo.setPlaceCoords(place.id, lat, lng) }
        }
    }
}
