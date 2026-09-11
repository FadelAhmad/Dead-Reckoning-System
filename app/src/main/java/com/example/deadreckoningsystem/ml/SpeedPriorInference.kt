package com.example.deadreckoningsystem.ml

import android.content.Context
import android.util.Log
import com.example.deadreckoningsystem.model.ImuData
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

/**
 * Standalone Offline TFLite Model Wrapper for `imu_speed_prior_transformer.tflite`.
 * Evaluates rolling windows of 6-axis IMU features to predict forward vehicle speed (m/s)
 * using an on-device 1D-CNN + Transformer Attention architecture.
 *
 * Model Input Shape:  [1, 30, 6] (Batch=1, TimeSteps=30, Channels=6)
 * Model Output Shape: [1, 1] (Predicted forward speed in km/h)
 */
class SpeedPriorInference(context: Context, modelAssetPath: String = "imu_speed_prior_transformer.tflite") {

    companion object {
        private const val TAG = "SpeedPriorInference"
        const val WINDOW_SIZE = 30 // Rolling IMU window size (matches model input shape [1, 30, 6])
        const val FEATURE_SIZE = 6 // [acc_fwd, acc_lat, acc_vert, gyro_yaw, gyro_pitch, gyro_roll]
        private const val DEFAULT_R_SPEED = 16.0f // (4.0 m/s)^2 measurement variance
    }

    private var interpreter: Interpreter? = null
    private val imuWindow = ArrayDeque<ImuData>()

    init {
        try {
            val modelBuffer = loadModelFile(context, modelAssetPath)
            val options = Interpreter.Options().apply {
                setNumThreads(2)
            }
            interpreter = Interpreter(modelBuffer, options)
            Log.i(TAG, "Successfully loaded standalone TFLite model: $modelAssetPath")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize standalone TFLite interpreter", e)
            interpreter = null
        }
    }

    private fun loadModelFile(context: Context, assetPath: String): ByteBuffer {
        val fileDescriptor = context.assets.openFd(assetPath)
        FileInputStream(fileDescriptor.fileDescriptor).use { inputStream ->
            val fileChannel = inputStream.channel
            val startOffset = fileDescriptor.startOffset
            val declaredLength = fileDescriptor.declaredLength
            return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
        }
    }

    /**
     * Appends an IMU sample to the rolling window buffer.
     */
    fun pushImuSample(imuData: ImuData) {
        imuWindow.addLast(imuData)
        if (imuWindow.size > WINDOW_SIZE) {
            imuWindow.removeFirst()
        }
    }

    /**
     * Predicts vehicle forward speed in m/s and estimated measurement variance R_speed.
     * @return Pair(speedMps, rSpeedVariance)
     */
    fun predictSpeedMps(): Pair<Float, Float> {
        val interp = interpreter ?: return Pair(fallbackSpeedFromWindow(), 64.0f)
        if (imuWindow.size < WINDOW_SIZE) {
            return Pair(fallbackSpeedFromWindow(), 64.0f)
        }

        try {
            // 1 batch * 30 timesteps * 6 features * 4 bytes/float = 720 bytes
            val inputBuffer = ByteBuffer.allocateDirect(1 * WINDOW_SIZE * FEATURE_SIZE * 4).apply {
                order(ByteOrder.nativeOrder())
            }

            for (sample in imuWindow) {
                inputBuffer.putFloat(sample.accelX) // acc_forward
                inputBuffer.putFloat(sample.accelY) // acc_lateral
                inputBuffer.putFloat(sample.accelZ) // acc_vertical
                inputBuffer.putFloat(sample.gyroZ)  // gyro_yaw
                inputBuffer.putFloat(sample.gyroY)  // gyro_pitch
                inputBuffer.putFloat(sample.gyroX)  // gyro_roll
            }
            inputBuffer.rewind()

            // Output shape is [1, 1]: predicted speed in km/h
            val outputBuffer = Array(1) { FloatArray(1) }
            interp.run(inputBuffer, outputBuffer)

            val predKmh = outputBuffer[0][0].coerceAtLeast(0.0f)
            val predMps = predKmh / 3.6f

            return Pair(predMps, DEFAULT_R_SPEED)
        } catch (e: Exception) {
            Log.w(TAG, "TFLite model inference error, using window fallback", e)
            return Pair(fallbackSpeedFromWindow(), 64.0f)
        }
    }

    private fun fallbackSpeedFromWindow(): Float {
        if (imuWindow.isEmpty()) return 0.0f
        val latest = imuWindow.last()
        val accMag = Math.sqrt((latest.accelX * latest.accelX + latest.accelY * latest.accelY).toDouble()).toFloat()
        return if (accMag < 0.15f) 0.0f else (accMag * 1.2f).coerceAtMost(25.0f)
    }

    fun close() {
        interpreter?.close()
        interpreter = null
    }
}
