package com.netraze.app.data.location

data class DeviceLocationFix(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Double,
    val capturedAt: Long
)

/**
 * Indicates that location permission was denied or is unavailable.
 */
class LocationPermissionException(message: String) : Exception(message)

/**
 * Indicates that device location services are disabled.
 */
class LocationServicesDisabledException(message: String) : Exception(message)

/**
 * Indicates that no usable location provider (GPS/Network) is available.
 */
class LocationProviderUnavailableException(message: String) : Exception(message)

/**
 * Indicates a failure to obtain an actual location fix from the hardware.
 */
class LocationFixUnavailableException(message: String) : Exception(message)

interface LocationProvider {
    /**
     * Attempts to acquire a single current device location fix.
     * Throws specific exceptions on failure.
     */
    suspend fun getCurrentLocation(): DeviceLocationFix
}
