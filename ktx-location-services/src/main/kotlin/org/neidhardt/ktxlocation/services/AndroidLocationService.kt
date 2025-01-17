/*
 * SPDX-FileCopyrightText: 2021 Eric Neidhardt
 * SPDX-License-Identifier: MIT
 */
package org.neidhardt.ktxlocation.services

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Context.LOCATION_SERVICE
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.HandlerThread
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import org.neidhardt.ktxlocation.exceptions.MissingPermissionCoarseLocation
import org.neidhardt.ktxlocation.exceptions.MissingPermissionFineLocation
import org.neidhardt.ktxlocation.exceptions.ProviderDisabled

/**
 * [AndroidLocationService] is a wrapper for stock android location manager to use flows.
 *
 * @property context android context, should be Application context.
 * @constructor creates a new instance that does nothing on start
 */
class AndroidLocationService(private val context: Context) {

	private val locationManager = context.getSystemService(LOCATION_SERVICE) as LocationManager

	/**
	 * [lastKnowLocation] returns the last location which was obtained from this repository.
	 * This location may be null, if no location was obtained before.
	 */
	var lastKnowLocation: Location? = null

	/**
	 * [getLocation] returns a single location from the android location manager, by using
	 * the GPS_PROVIDER.
	 */
	suspend fun getLocation(): Location {
		return getLocationUpdates(0, 0f, LocationManager.GPS_PROVIDER)
			.onEach { lastKnowLocation = it }
			.first()
	}

	/**
	 * [getLocationUpdates] returns Flow for continuous location updates.
	 * It does check if booth permission [Manifest.permission.ACCESS_FINE_LOCATION] and [Manifest.permission.ACCESS_COARSE_LOCATION]
	 * are granted. If permission is missing, it emits error of either [MissingPermissionFineLocation] or [MissingPermissionCoarseLocation].
	 *
	 * @param updateIntervalMs minimum time interval between location updates, in milliseconds
	 * @param minDistance minimum distance between location updates, in meters
	 * @param provider to use, for example LocationManager.GPS_PROVIDER
	 * @return location updates
	 */
	@SuppressLint("MissingPermission")
	fun getLocationUpdates(
		updateIntervalMs: Long,
		minDistance: Float,
		provider: String
	): Flow<Location> {
		return channelFlow{
			// callback for location updates
			val locationUpdateCallback = object : LocationListener {
				override fun onLocationChanged(location: Location) {
					lastKnowLocation = location
					trySend(location)
				}
				override fun onProviderDisabled(provider: String) {
					error(ProviderDisabled("Provider $provider was disabled"))
				}
				override fun onProviderEnabled(provider: String) {
				}
			}
			// star receiving updates
			if (!isRequiredPermissionGranted(context)) {
				error(getErrorForMissingPermission(context))
			} else {
				// create looper and request location updates
				val handlerThread = HandlerThread("AndroidLocationService.getLocationUpdates")
					.apply { start() }
				try {
					locationManager.requestLocationUpdates(
							provider,
							updateIntervalMs,
							minDistance,
							locationUpdateCallback,
							handlerThread.looper
					)
				} catch (e: Exception) {
					error(e)
				}
			}
			// stop client, if observer unsubscribe
			awaitClose {
				locationManager.removeUpdates(locationUpdateCallback)
			}
		}
	}
}
