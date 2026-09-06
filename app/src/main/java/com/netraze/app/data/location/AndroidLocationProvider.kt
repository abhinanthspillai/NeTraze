package com.netraze.app.data.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import androidx.core.location.LocationManagerCompat
import androidx.core.os.CancellationSignal
import androidx.core.util.Consumer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class AndroidLocationProvider @Inject constructor(
    @ApplicationContext private val context: Context
) : LocationProvider {

    @SuppressLint("MissingPermission")
    override suspend fun getCurrentLocation(): DeviceLocationFix = suspendCancellableCoroutine { continuation ->
        // 1. Check permissions first
        val hasFine = PermissionChecker.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == PermissionChecker.PERMISSION_GRANTED
        val hasCoarse = PermissionChecker.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) == PermissionChecker.PERMISSION_GRANTED

        if (!hasFine && !hasCoarse) {
            continuation.resumeWithException(LocationPermissionException("Location permission is required for Location Survey."))
            return@suspendCancellableCoroutine
        }

        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        if (locationManager == null) {
            continuation.resumeWithException(LocationProviderUnavailableException("Device does not support location services."))
            return@suspendCancellableCoroutine
        }

        // 2. Check if location is enabled
        if (!LocationManagerCompat.isLocationEnabled(locationManager)) {
            continuation.resumeWithException(LocationServicesDisabledException("Location services are disabled. Enable location and try again."))
            return@suspendCancellableCoroutine
        }

        // 3. Determine best provider (GPS or Network)
        val provider = when {
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            else -> {
                continuation.resumeWithException(LocationProviderUnavailableException("No available location provider found (GPS/Network)."))
                return@suspendCancellableCoroutine
            }
        }

        // 4. Request current location
        LocationManagerCompat.getCurrentLocation(
            locationManager,
            provider,
            null as CancellationSignal?,
            ContextCompat.getMainExecutor(context),
            Consumer<Location> { location ->
                if (location != null) {
                continuation.resume(
                    DeviceLocationFix(
                        latitude = location.latitude,
                        longitude = location.longitude,
                        accuracyMeters = location.accuracy.toDouble(),
                        capturedAt = System.currentTimeMillis()
                    )
                )
            } else {
                continuation.resumeWithException(LocationFixUnavailableException("Unable to obtain a current location fix. Try again."))
            }
        })
    }
}
