package com.meshwalkie.location

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.meshwalkie.core.HeadingFilter

/**
 * My heading = direction the phone faces, from TYPE_ROTATION_VECTOR
 * (fused magnetometer + accelerometer + gyro), low-pass filtered (Task 9)
 * so the arrow glides instead of vibrating.
 */
class HeadingSource(context: Context) : SensorEventListener {

    private val sensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val sensor: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val filter = HeadingFilter(alpha = 0.15f)
    private val rotationMatrix = FloatArray(9)
    private val orientation = FloatArray(3)
    private var onHeading: (Float) -> Unit = {}

    fun start(onHeading: (Float) -> Unit) {
        this.onHeading = onHeading
        sensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    fun stop() = sensorManager.unregisterListener(this)

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ROTATION_VECTOR) return
        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
        SensorManager.getOrientation(rotationMatrix, orientation)
        val azimuthDeg = Math.toDegrees(orientation[0].toDouble()).toFloat()
        onHeading(filter.update(((azimuthDeg % 360f) + 360f) % 360f))
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
