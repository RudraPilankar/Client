package com.client.helpers

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.util.Log


class FlashlightHelper(private val context: Context) {
    private val cameraManager: CameraManager by lazy {
        context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    }

    fun isFlashAvailable(): Boolean {
        val pm = context.packageManager
        return pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)
    }

    fun turnOnFlashlight() {
        try {
            val cameraId = getBackCameraId() ?: return
            cameraManager.setTorchMode(cameraId, true)
        } catch (e: CameraAccessException) {
            // Handle the exception (e.g., log the error)
            Log.e("FlashlightHelper", "Error turning on flashlight", e)
        }
    }

    fun turnOffFlashlight() {
        try {
            val cameraId = getBackCameraId() ?: return
            cameraManager.setTorchMode(cameraId, false)
        } catch (e: CameraAccessException) {
            // Handle the exception (e.g., log the error)
            Log.e("FlashlightHelper", "Error turning off flashlight", e)
        }
    }

    private fun getBackCameraId(): String? {
        try {
            val cameraIdList = cameraManager.cameraIdList
            for (cameraId in cameraIdList) {
                val characteristics = cameraManager.getCameraCharacteristics(cameraId)
                val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
                if (facing == CameraCharacteristics.LENS_FACING_BACK) {
                    return cameraId
                }
            }
        } catch (e: Exception) {  // Catch a broader exception here
            Log.e("FlashlightHelper", "Error getting camera ID", e)
        }
        return null
    }
}
