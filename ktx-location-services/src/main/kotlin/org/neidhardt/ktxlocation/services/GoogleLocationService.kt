/*
 * SPDX-FileCopyrightText: 2021 Eric Neidhardt
 * SPDX-License-Identifier: MIT
 */
package org.neidhardt.ktxlocation.services

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.HandlerThread
import com.google.android.gms.location.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.channelFlow
import org.neidhardt.ktxlocation.exceptions.MissingPermissionCoarseLocation
import org.neidhardt.ktxlocation.exceptions.MissingPermissionFineLocation


/**
 * [GoogleLocationService] is a wrapper for FusedLocationProviderClient to use flows.
 *
 * @property context android context required for FusedLocationProviderClient, should be Application context.
 * @constructor creates a new instance that does nothing on start
 */
class GoogleLocationService(private val context: Context) {

	private val client = LocationServices.getFusedLocationProviderClient(context)

	/**
	 * [lastKnowLocation] returns the last location which was obtained from this repository.
	 * This location may be null, if no location was obtained before.
	 */
	var lastKnowLocation: Location? = null

	/**
	 * [getLastKnowLocationFromDevice] returns the last location obtained by any google
	 * location service on this device. It doest not retrieve a new location.
	 * To get a new location, it is usually better to call: getLocationUpdates(...) and use the
	 * first received location.
	 *
	 * It does not check if google play services are present on the device.
	 * It does check if booth permission [Manifest.permission.ACCESS_FINE_LOCATION]
	 * and [Manifest.permission.ACCESS_COARSE_LOCATION] are granted.
	 * If permission is missing, it emits error of either [MissingPermissionFineLocation]
	 * or [MissingPermissionCoarseLocation].
	 *
	 * @return single location update
	 */
	@SuppressLint("MissingPermission")
	fun getLastKnowLocationFromDevice(): Flow<Location> {
		return callbackFlow {
			if (!isRequiredPermissionGranted(context)) {
				error(getErrorForMissingPermission(context))
			} else {
				// request last know location from client with async task
				val asyncLocationTask = client.lastLocation
				asyncLocationTask.addOnSuccessListener { location ->
					if (location != null) {
						lastKnowLocation = location
						trySend(location)
					} else {
						error(Throwable("Received location is null"))
					}
				}
				asyncLocationTask.addOnFailureListener {
					error(it)
				}
			}
		}
	}

	/**
	 * [getLocationUpdates] returns Flowable for continuous location updates.
	 * It does not check if google play services are present on the device.
	 * It does check if booth permission [Manifest.permission.ACCESS_FINE_LOCATION] and [Manifest.permission.ACCESS_COARSE_LOCATION]
	 * are granted. If permission is missing, it emits error of either [MissingPermissionFineLocation] or [MissingPermissionCoarseLocation].
	 *
	 * @param locationRequest to set update interval, precision, power usage, etc.
	 * @return location updates
	 */
	@SuppressLint("MissingPermission")
	fun getLocationUpdates(locationRequest: LocationRequest): Flow<Location> {
		return channelFlow {
			// callback for location updates
			val locationUpdateCallback = object : LocationCallback() {
				override fun onLocationResult(locationResult: LocationResult) {
					locationResult.lastLocation?.let {
						lastKnowLocation = it
						trySend(it)
					}
				}
			}
			// star receiving updates
			if (!isRequiredPermissionGranted(context)) {
				error(getErrorForMissingPermission(context))
			} else {
				// create looper and request location updates
				val handlerThread = HandlerThread("GoogleLocationService.getLocationUpdates")
					.apply { start() }
				client.requestLocationUpdates(locationRequest, locationUpdateCallback, handlerThread.looper)
			}
			// stop client, if observer unsubscribe
			awaitClose {
				client.removeLocationUpdates(locationUpdateCallback)
			}
		}
	}
}

object LocationRequests {

	fun precise(updateRateMillis: Long): LocationRequest {
		return LocationRequest.create().apply {
			interval = updateRateMillis
			priority = Priority.PRIORITY_HIGH_ACCURACY
		}
	}

	fun balanced(updateRateMillis: Long): LocationRequest {
		return LocationRequest.create().apply {
			interval = updateRateMillis
			priority = Priority.PRIORITY_BALANCED_POWER_ACCURACY
		}
	}

	fun cheap(updateRateMillis: Long): LocationRequest {
		return LocationRequest.create().apply {
			interval = updateRateMillis
			priority = Priority.PRIORITY_LOW_POWER
		}
	}
}