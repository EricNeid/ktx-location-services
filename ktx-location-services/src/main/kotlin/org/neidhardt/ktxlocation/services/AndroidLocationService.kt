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
import android.os.Bundle
import android.os.HandlerThread
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
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
	 * [getLocationUpdates] returns Flow for continuous location updates.
	 * It does not check if google play services are present on the device.
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
				override fun onLocationChanged(location: Location?) {
					location?.let {
						lastKnowLocation = it
						trySend(it)
					}
				}
				override fun onProviderDisabled(provider: String?) {
					error(ProviderDisabled("Provider ${provider ?: "null"} was disabled"))
				}
				@Deprecated("Deprecated in Java")
				override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
				}
				override fun onProviderEnabled(provider: String?) {
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
