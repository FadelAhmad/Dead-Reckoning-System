package com.example.deadreckoningsystem.ml

import android.content.Context
import com.example.deadreckoningsystem.model.ImuData
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlin.math.expm1

/**
 * TFLite Model Wrapper for `imu_speed_prior_transformer.tflite`.
 * Evaluates rolling windows of 6-axis IMU features to predict forward vehicle speed (m/s)
 * and optional learned measurement covariance (R_speed).
 */
class SpeedPriorInference(context: Context, modelAssetPath: String = "imu_speed_prior_transformer.tflite") {

    private var interpreter: Interpreter? = null
    private val windowSize = 10 // Rolling IMU window size
    private val featureSize = 6 // [acc_fwd, acc_lat, acc_vert, gyro_yaw, gyro_pitch, gyro_roll]

    private val imuWindow = ArrayDeque<ImuData>()

    init {
        try {
            val modelBuffer = loadModelFile(context, modelAssetPath)
            val options = Interpreter.Options().apply {
                setNumThreads(2)
            }
            interpreter = Interpreter(modelBuffer, options)
        } catch (_: Exception) {
            interpreter = null
        }
    }

    private fun loadModelFile(context: Context, assetPath: String): ByteBuffer {
        val fileDescriptor = context.assets.openFd(assetPath)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    /**
     * Appends an IMU sample to the rolling window buffer.
     */
    fun pushImuSample(imuData: ImuData) {
        imuWindow.addLast(imuData)
        if (imuWindow.size > windowSize) {
            imuWindow.removeFirst()
        }
    }

    /**
     * Predicts vehicle forward speed in m/s and estimated measurement variance R_speed.
     * @return Pair(speedMps, rSpeed)
     */
    fun predictSpeedMps(): Pair<Float, Float> {
        val interp = interpreter ?: return Pair(fallbackSpeedFromWindow(), 64.0f) // 8.0^2 fallback
        if (imuWindow.size < windowSize) {
            return Pair(fallbackSpeedFromWindow(), 64.0f)
        }

        try {
            // Shape: [1, windowSize, featureSize] or [1, windowSize * featureSize]
            val inputBuffer = ByteBuffer.allocateDirect(1 * windowSize * featureSize * 4).apply {
                order(ByteOrder.nativeOrder())
            }

            for (sample in imuWindow) {
                inputBuffer.putFloat(sample.accelX)
                inputBuffer.putFloat(sample.accelY)
                inputBuffer.putFloat(sample.accelZ)
                inputBuffer.putFloat(sample.gyroZ) // gyro_yaw
                inputBuffer.putFloat(sample.gyroY) // gyro_pitch
                inputBuffer.putFloat(sample.gyroX) // gyro_roll
            }
            inputBuffer.rewind()

            val outputBuffer = Array(1) { FloatArray(2) } // [predicted_speed_kmh, r_speed_log_val]
            interp.run(inputBuffer, outputBuffer)

            val predKmh = outputBuffer[0][0].coerceAtLeast(0.0f)
            val predMps = predKmh / 3.6f

            val rSpeedVal = if (outputBuffer[0].size > 1) {
                val logR = outputBuffer[0][1]
                val valR = expm1(logR.toDouble()).toFloat()
                (valR * valR).coerceIn(0.1f, 100.0f)
            } else {
                64.0f // 8.0^2 fallback
            }

            return Pair(predMps, rSpeedVal)
        } catch (_: Exception) {
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
