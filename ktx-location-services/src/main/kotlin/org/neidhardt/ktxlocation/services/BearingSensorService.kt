/*
 * SPDX-FileCopyrightText: 2021 Eric Neidhardt
 * SPDX-License-Identifier: MIT
 */
package org.neidhardt.ktxlocation.services

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import android.view.WindowManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import org.neidhardt.ktxlocation.exceptions.MissingSensor
import org.neidhardt.ktxlocation.model.Bearing


/**
 * BearingSensorService is a wrapper getting the bearing from different available sensors.
 * It supports getting updates from different sources.
 *
 * @param context android context required to obtain sensor service
 * @constructor creates a new instance that does nothing on start
 */
class BearingSensorService(context: Context) {

	private val sensorManager by lazy { context.getSystemService(Context.SENSOR_SERVICE) as SensorManager }
	private val windowManager by lazy { context.getSystemService(Context.WINDOW_SERVICE) as WindowManager }

	/**
	 * lastKnownBearing returns the last bearing which was calculated by any call for bearing updates.
	 */
	var lastKnownBearing: Float? = null

	/**
	 * getBearingUpdatesFromRotation returns Flowable for continuous bearing updates.
	 * It uses the rotation sensor which should provide the best results.
	 * Some devices may not have all the required sensors (like gyro sensor).
	 *
	 * If one of the required sensors is not available, [MissingSensor] is emitted.
	 * A bearing of 0.0 or 360.0 means magnetic north.
	 *
	 * @return bearing updates in degree
	 */
	fun getBearingUpdatesFromRotation(): Flow<Bearing> {
		return channelFlow {
			val rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
				?: error(MissingSensor("Rotation sensor could not be accessed"))

			var sensorAccuracy: Int = SensorManager.SENSOR_STATUS_NO_CONTACT

			val rotationSensorCallback = object : SensorEventListener {
				override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
					sensorAccuracy = accuracy
				}

				override fun onSensorChanged(event: SensorEvent?) {
					if (event == null) {
						return
					}

					if (event.sensor?.type != Sensor.TYPE_GAME_ROTATION_VECTOR
							&& event.sensor?.type != Sensor.TYPE_ROTATION_VECTOR) {
						return
					}

					val rotationMatrix = FloatArray(9)
					val sensorReading = event.values

					SensorManager.getRotationMatrixFromVector(rotationMatrix, sensorReading)

					val worldAxisX: Int
					val worldAxisY: Int
					when (windowManager.defaultDisplay.rotation) {
						Surface.ROTATION_90 -> {
							worldAxisX = SensorManager.AXIS_Z
							worldAxisY = SensorManager.AXIS_MINUS_X
						}
						Surface.ROTATION_180 -> {
							worldAxisX = SensorManager.AXIS_MINUS_X
							worldAxisY = SensorManager.AXIS_MINUS_Z
						}
						Surface.ROTATION_270 -> {
							worldAxisX = SensorManager.AXIS_MINUS_Z
							worldAxisY = SensorManager.AXIS_X
						}
						Surface.ROTATION_0 -> {
							worldAxisX = SensorManager.AXIS_X
							worldAxisY = SensorManager.AXIS_Z
						}
						else -> {
							worldAxisX = SensorManager.AXIS_X
							worldAxisY = SensorManager.AXIS_Z
						}
					}
					val adjustedRotationMatrix = FloatArray(9)
					SensorManager.remapCoordinateSystem(
							rotationMatrix, worldAxisX,
							worldAxisY, adjustedRotationMatrix
					)

					// azimuth/pitch/roll
					val orientation = FloatArray(3)

					SensorManager.getOrientation(adjustedRotationMatrix, orientation)
					val azimuth = (Math.toDegrees(orientation[0].toDouble()).toFloat() + 360f) % 360f

					lastKnownBearing = azimuth
					trySend(Bearing(azimuth, sensorAccuracy))
				}
			}

			// start receiving updates
			sensorManager.registerListener(
					rotationSensorCallback,
					rotationSensor,
					SensorManager.SENSOR_DELAY_NORMAL
			)

			// unregister from sensors after unsubscribe
			awaitClose { sensorManager.unregisterListener(rotationSensorCallback) }
		}
	}


	/**
	 * getBearingUpdatesFromMagneticAndAccelerometer returns Flowable for continuous bearing updates.
	 * It uses the accelerometer and the magnetic field sensor.
	 * If one of the required sensors is not available, [MissingSensor] is emitted.
	 * A bearing of 0.0 or 360.0 means magnetic north.
	 *
	 * @return bearing updates in degree
	 */
	fun getBearingUpdatesFromMagneticAndAccelerometer(): Flow<Bearing> {
		return channelFlow {
			// both sensor are required to calculate bearing
			val magneticSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
			val accelerationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

			// arrays to store sensor reading, used to calculate bearing
			val accelerometerReading = FloatArray(3)
			val magnetometerReading = FloatArray(3)
			var isAccelerometerReadingAvailable = false
			var isMagnetometerReadingAvailable = false

			// check if sensor is available
			if (magneticSensor == null) {
				error(MissingSensor("Rotation sensor could not be accessed"))
			}
			if (accelerationSensor == null) {
				error(MissingSensor("Acceleration sensor could not be accessed"))
			}

			var sensorAccuracy: Int = SensorManager.SENSOR_STATUS_NO_CONTACT

			// get updates from sensors
			val accelerationSensorsCallback = object : SensorEventListener {
				override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
					sensorAccuracy = accuracy
				}

				override fun onSensorChanged(event: SensorEvent?) {
					// store sensor reading
					if (event != null) {
						System.arraycopy(event.values, 0, accelerometerReading, 0, accelerometerReading.size)
						isAccelerometerReadingAvailable = true
					} else {
						error(MissingSensor("Received acceleration sensor event is null"))
					}

					// if both reading are available, calculate bearing
					if (isAccelerometerReadingAvailable && isMagnetometerReadingAvailable) {
						val azimuth = calculateAzimuthDegree(accelerometerReading, magnetometerReading)
						lastKnownBearing = azimuth
						trySend(Bearing(azimuth, sensorAccuracy))
					}
				}
			}
			val rotationSensorUpdateCallback = object : SensorEventListener {
				override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
					sensorAccuracy = accuracy
				}

				override fun onSensorChanged(event: SensorEvent?) {
					// store sensor reading
					if (event != null) {
						System.arraycopy(event.values, 0, magnetometerReading, 0, magnetometerReading.size)
						isMagnetometerReadingAvailable = true
					} else {
						error(Throwable("Received rotation sensor event is null"))
					}

					// if both reading are available, calculate bearing
					if (isAccelerometerReadingAvailable && isMagnetometerReadingAvailable) {
						val azimuth = calculateAzimuthDegree(accelerometerReading, magnetometerReading)
						lastKnownBearing = azimuth
						trySend(Bearing(azimuth, sensorAccuracy))
					}
				}
			}

			// start receiving updates
			sensorManager.registerListener(
					accelerationSensorsCallback,
					accelerationSensor,
					SensorManager.SENSOR_DELAY_GAME
			)

			// start receiving updates
			sensorManager.registerListener(
					rotationSensorUpdateCallback,
					magneticSensor,
					SensorManager.SENSOR_DELAY_GAME
			)

			// unregister from sensors after unsubscribe
			awaitClose {
				sensorManager.unregisterListener(accelerationSensorsCallback)
				sensorManager.unregisterListener(rotationSensorUpdateCallback)
			}
		}
	}

	private fun calculateAzimuthDegree(
			accelerometerReading: FloatArray,
			magnetometerReading: FloatArray
	): Float {
		val rotationMatrix = FloatArray(9)
		SensorManager.getRotationMatrix(rotationMatrix, null, accelerometerReading, magnetometerReading)

		val orientation = FloatArray(3)
		SensorManager.getOrientation(rotationMatrix, orientation)
		val azimuthInRadians = orientation[0]
		return (Math.toDegrees(azimuthInRadians.toDouble()) + 360).toFloat() % 360 // azimuth in degree
	}
}