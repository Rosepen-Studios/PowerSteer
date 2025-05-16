package com.rosepen.powersteer

import android.content.Context
import android.content.pm.ActivityInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.forEachGesture
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rosepen.powersteer.ui.theme.PSTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import kotlin.math.max
import kotlin.math.min
import androidx.compose.ui.geometry.Offset

class MainActivity : ComponentActivity(), SensorEventListener {
    // Analog values for throttle and brake (0..32767)
    private var accelerateValue by mutableStateOf(0)
    private var brakeValue by mutableStateOf(0)

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var magnetometer: Sensor? = null

    private val gravity = FloatArray(3)
    private val geomagnetic = FloatArray(3)

    private var orientationAngles by mutableStateOf(FloatArray(3))
    private var isSending by mutableStateOf(false)
    private var statusText by mutableStateOf("Status: Not connected")

    private var sendJob: Job? = null

    private val targetIP = "192.168.1.189"
    private val targetPort = 5005

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

        setContent {
            PSTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SteeringUI(
                        roll = Math.toDegrees(orientationAngles[1].toDouble()).toInt(),
                        status = statusText,
                        isSending = isSending,
                        onToggle = { toggleSending() },
                        onAccelerateChanged = { accelerateValue = it },
                        onBrakeChanged = { brakeValue = it },
                        onRestart = { sendRestartSignal() }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        accelerometer?.also { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        magnetometer?.also { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
        stopSending()
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> System.arraycopy(event.values, 0, gravity, 0, event.values.size)
            Sensor.TYPE_MAGNETIC_FIELD -> System.arraycopy(event.values, 0, geomagnetic, 0, event.values.size)
        }
        val rotationMatrix = FloatArray(9)
        val success = SensorManager.getRotationMatrix(rotationMatrix, null, gravity, geomagnetic)
        if (success) {
            val orientation = FloatArray(3)
            SensorManager.getOrientation(rotationMatrix, orientation)
            orientationAngles = orientation
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun toggleSending() {
        if (isSending) stopSending() else startSending()
    }

    private fun startSending() {
        isSending = true
        statusText = "Status: Sending data..."
        sendJob = CoroutineScope(Dispatchers.IO).launch {
            val socket = DatagramSocket()
            val address = InetAddress.getByName(targetIP)
            try {
                while (isActive) {
                    val roll = Math.toDegrees(orientationAngles[1].toDouble()).toInt()
                    val message = "$roll,$accelerateValue,$brakeValue"
                    val data = message.toByteArray()
                    socket.send(DatagramPacket(data, data.size, address, targetPort))
                    delay(50L)
                }
            } catch (e: Exception) {
                Log.e("UDP", "Sending failed: ${e.message}")
            } finally {
                socket.close()
            }
        }
    }

    private fun stopSending() {
        isSending = false
        statusText = "Status: Not connected"
        sendJob?.cancel()
    }

    private fun sendRestartSignal() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val socket = DatagramSocket()
                val address = InetAddress.getByName(targetIP)
                val message = "RESTART"
                socket.send(DatagramPacket(message.toByteArray(), message.length, address, targetPort))
                socket.close()
            } catch (e: Exception) {
                Log.e("UDP", "Restart signal failed: ${e.message}")
            }
        }
    }
}

fun mapYPositionToVJoyValue(y: Float, height: Float): Int {
    val clampedY = max(0f, min(y, height))
    val normalized = 1f - (clampedY / height)
    return (normalized * 32767).toInt()
}

@Composable
fun SteeringUI(
    roll: Int,
    status: String,
    isSending: Boolean,
    onToggle: () -> Unit,
    onAccelerateChanged: (Int) -> Unit,
    onBrakeChanged: (Int) -> Unit,
    onRestart: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .background(if (isSending) Color.Green else Color.Black)
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = status.uppercase(),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            // --- BRAKE BUTTON ---
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(1f)
                    .background(Color.DarkGray)
                    .pointerInput(Unit) {
                        forEachGesture {
                            awaitPointerEventScope {
                                // Wait for at least one pointer to press down
                                val down = awaitFirstDown()
                                // Calculate the initial value based on position
                                val height = size.height.toFloat()
                                val pressedAmount = (1f - (down.position.y / height)).coerceIn(0f, 1f)
                                onBrakeChanged((pressedAmount * 32767).toInt())

                                // Now handle dragging
                                drag(down.id) { change ->
                                    // Update value based on current finger position
                                    val newPressedAmount = (1f - (change.position.y / height)).coerceIn(0f, 1f)
                                    onBrakeChanged((newPressedAmount * 32767).toInt())

                                    // Consume the change so it doesn't propagate
                                    if (change.positionChange() != Offset.Zero) change.consume()
                                }

                                // Reset when drag ends
                                onBrakeChanged(0)
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Brake",
                    fontSize = 36.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }

            // --- CENTER (ROLL BAR + BUTTONS) ---
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(1f)
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                val maxBarHeight = 150f
                val leftBarLength = max(0f, min(maxBarHeight, -roll.toFloat() * 3f))
                val rightBarLength = max(0f, min(maxBarHeight, roll.toFloat() * 3f))

                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(10.dp)
                                    .fillMaxHeight()
                                    .padding(bottom = (150 - rightBarLength).dp)
                                    .background(Color.Red)
                            )
                            Box(
                                modifier = Modifier
                                    .width(10.dp)
                                    .fillMaxHeight()
                                    .padding(bottom = (150 - leftBarLength).dp)
                                    .background(Color.Red)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // --- RESTART BUTTON ---
                    Button(
                        onClick = onRestart,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                        shape = CircleShape,
                        modifier = Modifier.size(50.dp)
                    ) {
                        Text(
                            text = "⟲",
                            fontSize = 24.sp,
                            color = Color.Black,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // --- TOGGLE SENDING BUTTON ---
                    Button(
                        onClick = onToggle,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isSending) Color.Red else Color.Yellow
                        ),
                        shape = CircleShape,
                        modifier = Modifier.size(50.dp)
                    ) {
                        Text(
                            text = if (isSending) "✕" else "●",
                            fontSize = 24.sp,
                            color = Color.Black,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // --- ACCELERATE BUTTON ---
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(1f)
                    .background(Color.DarkGray)
                    .pointerInput(Unit) {
                        forEachGesture {
                            awaitPointerEventScope {
                                // Wait for at least one pointer to press down
                                val down = awaitFirstDown()
                                // Calculate the initial value based on position
                                val height = size.height.toFloat()
                                val pressedAmount = (1f - (down.position.y / height)).coerceIn(0f, 1f)
                                onAccelerateChanged((pressedAmount * 32767).toInt())

                                // Now handle dragging
                                drag(down.id) { change ->
                                    // Update value based on current finger position
                                    val newPressedAmount = (1f - (change.position.y / height)).coerceIn(0f, 1f)
                                    onAccelerateChanged((newPressedAmount * 32767).toInt())

                                    // Consume the change so it doesn't propagate
                                    if (change.positionChange() != Offset.Zero) change.consume()
                                }

                                // Reset when drag ends
                                onAccelerateChanged(0)
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Accelerate",
                    fontSize = 36.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}