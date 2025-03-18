package com.client.helpers

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.abs

object DeviceOrientationHelper {

    // Callback interface to pass the heading back
    interface HeadingCallback {
        fun onHeadingChanged(heading: Float)
    }

    // Callback interface to pass errors back (e.g., no sensor available)
    interface ErrorCallback {
        fun onError(error: String)
    }

    // Function to get device heading just once with error handling
    fun getDeviceHeading(
        context: Context,
        headingCallback: HeadingCallback?,
        errorCallback: ErrorCallback?
    ) {
        // Initialize the SensorManager
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

        // Get the rotation vector sensor
        val rotationVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

        // If the rotation vector sensor is available, use it
        if (rotationVectorSensor != null) {
            useRotationVector(sensorManager, rotationVectorSensor, headingCallback, errorCallback)
        }
        // Otherwise, use the accelerometer and magnetometer as a fallback
        else {
            useAccelerometerMagnetometer(sensorManager, headingCallback, errorCallback)
        }
    }


    private fun useRotationVector(
        sensorManager: SensorManager,
        rotationVectorSensor: Sensor,
        headingCallback: HeadingCallback?,
        errorCallback: ErrorCallback?
    ) {
        // Constants
        val ALPHA = 0.15f // Smoothing factor for low-pass filter

        // Filtered rotation vector values (size is now 5)
        var filteredRotationVector = FloatArray(5)
        var lastRotationVectorAccuracy = SensorManager.SENSOR_STATUS_UNRELIABLE

        // Sensor Event Listener to handle the sensor data
        val sensorEventListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                event?.let {
                    if (it.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
                        // Filter rotation vector data
                        for (i in 0 until 5) {
                            filteredRotationVector[i] = filter(it.values[i], filteredRotationVector.getOrElse(i) { 0f }, ALPHA)
                        }
                        lastRotationVectorAccuracy = it.accuracy
                        println("Rotation vector: ${filteredRotationVector[0]}, ${filteredRotationVector[1]}, ${filteredRotationVector[2]} accuracy: ${lastRotationVectorAccuracy}")

                        if (lastRotationVectorAccuracy >= SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM) {
                            // Calculate the heading (azimuth) from the rotation vector
                            val azimuth = calculateHeadingFromRotationVector(filteredRotationVector)

                            // Pass the heading value back to the callback
                            headingCallback?.onHeadingChanged(azimuth)

                            // After getting the first reading, unregister the listeners to stop further updates
                            sensorManager.unregisterListener(this)
                        }
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
                // We handle accuracy when we process sensor data
            }
        }

        // Register the sensor listener
        sensorManager.registerListener(sensorEventListener, rotationVectorSensor, SensorManager.SENSOR_DELAY_UI)
    }



    private fun useAccelerometerMagnetometer(
        sensorManager: SensorManager,
        headingCallback: HeadingCallback?,
        errorCallback: ErrorCallback?
    ) {
        // Get the accelerometer and magnetometer sensors
        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

        // Check if both sensors are available
        if (accelerometer == null || magnetometer == null) {
            errorCallback?.onError("Required sensors are not available on this device.")
            return
        }

        // Constants
        val ALPHA = 0.15f // Smoothing factor for low-pass filter
        val MAX_TIME_DIFFERENCE = 50_000_000L // Maximum time difference in nanoseconds to be considered as a single event

        // Filtered sensor values
        var filteredGravity = FloatArray(3)
        var filteredGeomagnetic = FloatArray(3)

        // Sensor values with timestamps
        var lastGravityEventTime : Long = 0
        var lastGeomagneticEventTime : Long = 0

        var lastGravityAccuracy = SensorManager.SENSOR_STATUS_UNRELIABLE
        var lastGeomagneticAccuracy = SensorManager.SENSOR_STATUS_UNRELIABLE

        // Arrays for rotation matrix and orientation
        val R = FloatArray(9)
        val I = FloatArray(9)
        val orientation = FloatArray(3)

        // Sensor Event Listener to handle the sensor data
        val sensorEventListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                event?.let {
                    // Update gravity and geomagnetic data based on sensor type
                    when (it.sensor.type) {
                        Sensor.TYPE_ACCELEROMETER -> {
                            // Filter accelerometer data
                            filteredGravity[0] = filter(it.values[0], filteredGravity[0], ALPHA)
                            filteredGravity[1] = filter(it.values[1], filteredGravity[1], ALPHA)
                            filteredGravity[2] = filter(it.values[2], filteredGravity[2], ALPHA)

                            lastGravityEventTime = it.timestamp
                            lastGravityAccuracy = it.accuracy

                            println("Gravity: ${filteredGravity[0]}, ${filteredGravity[1]}, ${filteredGravity[2]} accuracy: ${lastGravityAccuracy}")
                            println("Gravity Time: ${lastGravityEventTime}")
                        }
                        Sensor.TYPE_MAGNETIC_FIELD -> {
                            // Filter magnetometer data
                            filteredGeomagnetic[0] = filter(it.values[0], filteredGeomagnetic[0], ALPHA)
                            filteredGeomagnetic[1] = filter(it.values[1], filteredGeomagnetic[1], ALPHA)
                            filteredGeomagnetic[2] = filter(it.values[2], filteredGeomagnetic[2], ALPHA)

                            lastGeomagneticEventTime = it.timestamp
                            lastGeomagneticAccuracy = it.accuracy

                            println("Geomagnetic: ${filteredGeomagnetic[0]}, ${filteredGeomagnetic[1]}, ${filteredGeomagnetic[2]} accuracy: ${lastGeomagneticAccuracy}")
                            println("Geomagnetic Time: ${lastGeomagneticEventTime}")
                        }
                    }

                    // Check if we have valid sensor data to calculate the heading
                    if (lastGravityAccuracy >= SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM &&
                        lastGeomagneticAccuracy >= SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM &&
                        abs(lastGravityEventTime - lastGeomagneticEventTime) <= MAX_TIME_DIFFERENCE){
                        val success = SensorManager.getRotationMatrix(R, I, filteredGravity, filteredGeomagnetic)

                        if (success) {
                            SensorManager.getOrientation(R, orientation)

                            // Get the heading (azimuth) in radians, then convert to degrees
                            var azimuth = Math.toDegrees(orientation[0].toDouble()).toFloat()

                            // Normalize azimuth to be within 0 - 360 degrees
                            if (azimuth < 0) {
                                azimuth += 360
                            }

                            // Pass the heading value back to the callback
                            headingCallback?.onHeadingChanged(azimuth)
                        } else {
                            errorCallback?.onError("Failed to calculate the rotation matrix.")
                        }
                        // After getting the first reading, unregister the listeners to stop further updates
                        sensorManager.unregisterListener(this)
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
                // We handle accuracy when we process sensor data
            }
        }

        // Register the sensor listeners
        sensorManager.registerListener(sensorEventListener, accelerometer, SensorManager.SENSOR_DELAY_UI)
        sensorManager.registerListener(sensorEventListener, magnetometer, SensorManager.SENSOR_DELAY_UI)
    }

    // Function to calculate heading from the rotation vector
    private fun calculateHeadingFromRotationVector(rotationVector: FloatArray): Float {
        val rotationMatrix = FloatArray(9)
        SensorManager.getRotationMatrixFromVector(rotationMatrix, rotationVector)
        val orientation = FloatArray(3)
        SensorManager.getOrientation(rotationMatrix, orientation)

        var azimuth = Math.toDegrees(orientation[0].toDouble()).toFloat()

        // Normalize azimuth to be within 0 - 360 degrees
        if (azimuth < 0) {
            azimuth += 360
        }
        return azimuth
    }

    // Low-pass filter implementation
    private fun filter(current: Float, previous: Float, alpha: Float): Float {
        return (current * alpha) + (previous * (1 - alpha))
    }
}
