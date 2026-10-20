package com.munawar.ai

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

/** Phone-tilt values (-1..1, smoothed) used to move layers for a real 3D parallax feel. Costs nothing when the app is hidden. */
object Parallax : SensorEventListener {
    @Volatile var tx = 0f
    @Volatile var ty = 0f
    private var manager: SensorManager? = null
    private var registered = false

    fun start(ctx: Context) {
        if (registered) return
        val m = ctx.applicationContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val s = m.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
        manager = m
        registered = m.registerListener(this, s, SensorManager.SENSOR_DELAY_UI)
    }

    fun stop() {
        if (registered) { manager?.unregisterListener(this); registered = false }
        tx = 0f; ty = 0f
    }

    override fun onSensorChanged(e: SensorEvent) {
        val nx = (-e.values[0] / 5f).coerceIn(-1f, 1f)
        val ny = ((e.values[1] - 7f) / 4f).coerceIn(-1f, 1f)
        tx += (nx - tx) * 0.12f
        ty += (ny - ty) * 0.12f
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
