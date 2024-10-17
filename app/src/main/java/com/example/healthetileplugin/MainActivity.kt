package com.example.healthetileplugin

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.healthetileplugin.ui.theme.HealthetilepluginTheme
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.healthetile.ble.plugin.BluetoothScanHandler
import com.healthetile.ble.plugin.ConnectionState
import com.healthetile.ble.plugin.Helathetile
import com.healthetile.ble.plugin.SensorData
import java.io.IOException


class MainActivity : ComponentActivity() {

    private lateinit var sensorDataReceiver: BroadcastReceiver
    var csvStringBuilder = StringBuilder()
    var sensorData by mutableStateOf<SensorData?>(null)

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Helathetile.initialize(applicationContext)

        sensorDataReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                intent?.let {
                    val sensorDataList: ArrayList<SensorData>? =
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            it.getParcelableArrayListExtra(
                                "healthetile_sensor_data_list",
                                SensorData::class.java
                            )
                        } else {
                            @Suppress("DEPRECATION")
                            it.getParcelableArrayListExtra("healthetile_sensor_data_list")
                        }
                    sensorDataList?.let { data ->
                        displaySensorData(data)
                    }
                    sensorDataList?.firstOrNull()?.let { firstData ->
                        if (firstData.timeStamp.toInt() != 0) {
                            sensorData = firstData
                        }
                    }

                }
            }
        }

        setContent {
            HealthetilepluginTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {

                    BluetoothScannerUI(this)

                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        Log.d("MainActivity", "onStart: Activity is starting.")
        val filter = IntentFilter("com.healthetile.HEALTHETILE_DATA")
        registerReceiver(sensorDataReceiver, filter)
    }

    override fun onResume() {
        super.onResume()
        Log.d("MainActivity", "onResume: Activity is resuming.")
    }

    override fun onPause() {
        super.onPause()
        Log.d("MainActivity", "onPause: Activity is pausing.")
    }

    override fun onStop() {
        super.onStop()
        Log.d("MainActivity", "onStop: Activity is stopping.")
        unregisterReceiver(sensorDataReceiver)
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("MainActivity", "onDestroy: Activity is being destroyed.")
        Helathetile.cleanup()
    }

    fun displaySensorData(sensorDataList: List<SensorData>) {
        if (sensorDataList.isEmpty()) {
            Log.d("MainActivity", "displaySensorData: No sensor data received to process.")
            return
        }
        sensorDataList.forEach { sensorData ->
            val csvLine = sensorDataToCsvString(sensorData)
            csvStringBuilder.append(csvLine).append("\n")
        }
    }

    fun initializeCsvStringBuilder() {
        clearCsvStringBuilder()
        csvStringBuilder.append("timeStamp,temperature,gsr,grnCount,grn2Count,irCount,redCount,accX,accY,accZ,hr,hrConfidence,rrInterBeatInterval,rrConfidence,spo2,spo2Confidence,spo2Estimated,spo2CalPercentage,spo2LowSignQualityFlag,spo2MotionFlag,spo2LowPiFlag,spo2UnreliableRFlag,spo2State,skinContactState,walkSteps,runSteps,calories,cadence,event,activityClass,updatedEda,updatedAcc\n")
    }

    fun clearCsvStringBuilder() {
        csvStringBuilder.clear()
    }
}

@RequiresApi(Build.VERSION_CODES.Q)
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun BluetoothScannerUI(mainActivity: MainActivity) {
    Column(
        modifier = Modifier.padding(15.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        BluetoothConnection()
        BatteryBox()
        EventDataBox(mainActivity)
        SensorDataBox(mainActivity)
        OnlineAcquireButton(mainActivity)
        OfflineAcquireButton(mainActivity)
    }
}


@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun BluetoothConnection() {

    var scanResult by remember { mutableStateOf("No device found yet.") }
    var deviceFound by remember { mutableStateOf<BluetoothDevice?>(null) }
    val permissionState =
        rememberMultiplePermissionsState(permissions = PermissionUtils.permissions)
    val connectionState by Helathetile.connectionState.collectAsState()

    var deviceName by remember { mutableStateOf("") }

    val handleScanResult: (String) -> Unit = { result ->
        scanResult = result
    }

    val handleScanDevice: (BluetoothDevice) -> Unit = { device ->
        deviceFound = device
    }

    val handlePermissionsRequest = {
        permissionState.launchMultiplePermissionRequest()
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .border(width = 1.dp, color = Color.Black, shape = RoundedCornerShape(5.dp))
    ) {
        Column(
            modifier = Modifier
                .padding(10.dp)
                .fillMaxWidth()
        ) {
            Text(
                text = "Healthetile BLE",
                color = Color.Black,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .padding(horizontal = 15.dp, vertical = 5.dp)
                    .align(Alignment.CenterHorizontally)
            )


            deviceFound?.let { device ->
                DeviceFoundRow(deviceFound = device)
            }


            if (connectionState is ConnectionState.Disconnected) {

                TextField(
                    value = deviceName,
                    onValueChange = { deviceName = it },
                    label = { Text("Enter Device Name") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                        .border(1.dp, Color.Gray, shape = RoundedCornerShape(4.dp)),
                    keyboardOptions = KeyboardOptions.Default.copy(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Done
                    ),

                    )


                BluetoothScanButton(
                    scanResult = handleScanResult,
                    scanDevice = handleScanDevice,
                    onPermissionsRequest = handlePermissionsRequest,
                    deviceName = deviceName
                )
            }
        }
    }
}


@Composable
fun EventDataBox(mainActivity: MainActivity) {
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val boxWidth = (screenWidth - 30.dp-20.dp) / 2  // Subtracting padding and dividing the screen width by two

    val event1Status = mainActivity.sensorData?.event == 1
    val event2Status = mainActivity.sensorData?.event == 2

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        EventBox("Event 1", event1Status, boxWidth)
        Spacer(modifier = Modifier.width(20.dp))
        EventBox("Event 2", event2Status, boxWidth)
    }
}

@Composable
fun EventBox(eventName: String, isActive: Boolean, boxWidth: Dp) {
    Box(
        modifier = Modifier
            .width(boxWidth)
            .border(1.dp, Color.Black, shape = RoundedCornerShape(5.dp))
            .padding(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            Text(
                text = eventName,
                color = Color.Black,
             //   fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.width(10.dp))
            EventIndicator(isActive = isActive)
        }
    }
}

@Composable
fun EventIndicator(isActive: Boolean) {
    Canvas(modifier = Modifier.size(10.dp)) {
        drawCircle(
            color = if (isActive) Color.Green else Color.Red
        )
    }
}



@Composable
fun SensorDataBox(mainActivity: MainActivity) {
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val boxWidth = (screenWidth - 30.dp - 20.dp) / 2

    val hrIcon = painterResource(id = R.drawable.ic_hr)
    val hrBgIcon = painterResource(id = R.drawable.ic_hr_bg)
    val accIcon = painterResource(id = R.drawable.ic_acc)
    val accBgIcon = painterResource(id = R.drawable.ic_acc_bg)


    val hrValue = mainActivity.sensorData?.hr?.toString() ?: "--"
    val accValue = mainActivity.sensorData?.updatedAcc?.let {
        String.format("%.2f", it)
    } ?: "--"


    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        SensorInfoBox(
            label = "HR",
            value = "$hrValue BPM",
            imagePainter = hrIcon,
            bgImagePainter = hrBgIcon,
            modifier = Modifier.width(boxWidth)
        )
        Spacer(modifier = Modifier.width(20.dp))
        SensorInfoBox(
            label = "ACC",
            value = "$accValue g",
            imagePainter = accIcon,
            bgImagePainter = accBgIcon,
            modifier = Modifier.width(boxWidth)
        )
    }
}

@Composable
fun SensorInfoBox(
    label: String,
    value: String,
    imagePainter: Painter,
    bgImagePainter: Painter,
    modifier: Modifier
) {
    Box(
        modifier = modifier
            .border(1.dp, Color.Black, shape = RoundedCornerShape(5.dp))
            .padding(10.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = imagePainter,
                    contentDescription = "Main Image",
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = label,
                    color = Color.Black,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = value,
                    color = Color.Black,
                    fontSize = 14.sp
                )
                Image(
                    painter = bgImagePainter,
                    contentDescription = "Background Image",
                    modifier = Modifier.size(40.dp)
                )
            }
        }
    }
}

@Composable
fun BatteryBox() {

    val batteryLevel by Helathetile.batteryLevel.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .border(shape = RoundedCornerShape(5.dp), width = 1.dp, color = Color.Black)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            horizontalArrangement = Arrangement.Absolute.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Battery Level",
                color = Color.Black,
                fontSize = 16.sp,
                modifier = Modifier.padding(10.dp)
            )
            Text(
                text = "-----------------> ",
                color = Color.Black,
                fontSize = 16.sp,
                modifier = Modifier.padding(10.dp)
            )
            Text(
                text = "${batteryLevel ?: 0}%",
                color = Color.Black,
                fontSize = 16.sp,
                modifier = Modifier.padding(10.dp)
            )
        }
    }
}


@OptIn(ExperimentalPermissionsApi::class)
@SuppressLint("MissingPermission")
@Composable
fun BluetoothScanButton(
    scanResult: (String) -> Unit,
    scanDevice: (BluetoothDevice) -> Unit,
    onPermissionsRequest: () -> Unit,
    deviceName: String,
) {
    val context = LocalContext.current
    var isLoading by remember { mutableStateOf(false) }
    val permissionState =
        rememberMultiplePermissionsState(permissions = PermissionUtils.permissions)

    Button(
        onClick = {
            try {
                if (permissionState.allPermissionsGranted) {
                    Helathetile.startScanning(
                        context = context,
                        deviceName = "We-Be e581",
                        callback = object : BluetoothScanHandler {
                            override fun onDeviceFound(device: BluetoothDevice) {
                                isLoading = false
                                scanResult("Device found: ${device.name}")
                                scanDevice(device)
                            }

                            override fun onError(error: Exception) {
                                isLoading = false
                                scanResult("Error: ${error.message}")
                            }

                            override fun onScanStarted() {
                                isLoading = true
                            }
                        }
                    )
                } else {
                    onPermissionsRequest()
                }
            } catch (e: Exception) {
                scanResult("Scanning failed: ${e.message}")
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp, horizontal = 5.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .padding(2.dp)
                        .size(20.dp),
                    color = Color.White,
                    strokeWidth = 1.dp
                )
            } else {
                Text(
                    text = "Scan for device",
                    color = Color.White
                )
            }
        }
    }
    if (permissionState.shouldShowRationale) {
        Text(text = "We need Bluetooth and Location permissions to scan for devices.")
    }
}


@SuppressLint("MissingPermission")
@Composable
fun DeviceFoundRow(deviceFound: BluetoothDevice?) {
    deviceFound?.let { device ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 15.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = device.name ?: "Unknown Device",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall
            )
            BluetoothButton(device)
        }
    }
}

@Composable
fun BluetoothButton(
    device: BluetoothDevice,
) {
    val connectionState by Helathetile.connectionState.collectAsState()

    val buttonText = when (connectionState) {
        is ConnectionState.Connected -> "Disconnect"
        is ConnectionState.Disconnected -> "Connect"
        is ConnectionState.Connecting -> "Connecting.."
        else -> ""
    }

    Button(
        onClick = {
            if (connectionState is ConnectionState.Disconnected) {
                Helathetile.connectBluetoothDevice(device)
            } else if (connectionState is ConnectionState.Connected) {
                Helathetile.disconnectDevice()
            }
        },
        modifier = Modifier.padding(top = 16.dp)
    ) {
        Text(text = buttonText)
    }
}

@RequiresApi(Build.VERSION_CODES.Q)
@Composable
fun OnlineAcquireButton(mainActivity: MainActivity) {
    val context = LocalContext.current
    var buttonText by remember { mutableStateOf("Start Online Acquire") }
    val isAcquiring by Helathetile.isOnlineAcquiring.collectAsState()
    val connectionState by Helathetile.connectionState.collectAsState()


    Button(
        onClick = {
            if (connectionState is ConnectionState.Connected) {
                if (!isAcquiring) {
                    buttonText = "Starting..."
                    Helathetile.acquireOnlineData { success ->
                        buttonText = if (success) {
                            mainActivity.initializeCsvStringBuilder()
                            "Stop Acquire"
                        } else {
                            "Start Online Acquire"
                        }
                    }
                } else {
                    buttonText = "Stopping..."
                    Helathetile.stopAcquiringData { success ->
                        if (success) {
                            val csvData = mainActivity.csvStringBuilder.toString()
                            if (csvData.isNotEmpty()) {
                                val savedSuccessfully = saveCsvToFile(context, csvData)
                                buttonText = "Start Acquire"
                                if (savedSuccessfully) {
                                    Log.d("MainActivity", "CSV file successfully saved.")
                                    mainActivity.clearCsvStringBuilder()

                                } else {
                                    Log.e("MainActivity", "CSV file error.")
                                }
                            } else {
                                Log.d("MainActivity", "No data to save.")
                                buttonText = "Start Acquire"
                            }
                        } else {
                            buttonText = "Stop online Acquire"
                        }
                    }
                }
            }
        },
        modifier = Modifier
            .padding(top = 16.dp)
            .fillMaxWidth()
    ) {
        Text(text = buttonText)
    }
}


@RequiresApi(Build.VERSION_CODES.Q)
@Composable
fun OfflineAcquireButton(mainActivity: MainActivity) {
    val context = LocalContext.current
    var buttonText by remember { mutableStateOf("Start Offline Acquire") }
    val isAcquiring by Helathetile.isOfflineAcquiring.collectAsState()
    val connectionState by Helathetile.connectionState.collectAsState()

    LaunchedEffect(isAcquiring) {
       if (isAcquiring) buttonText = "Offline Acquiring..." else {
           val csvData = mainActivity.csvStringBuilder.toString()
           if (csvData.isNotEmpty()) {
               val savedSuccessfully = saveCsvToFile(context, csvData)
               buttonText = "Start Acquire"
               if (savedSuccessfully) {
                   Log.d("MainActivity", "CSV file successfully saved.")
                   mainActivity.clearCsvStringBuilder()

               } else {
                   Log.e("MainActivity", "CSV file error.")
               }
           } else {
               Log.d("MainActivity", "No data to save.")
               buttonText = "Start Offline Acquire"
           }
       }
    }

    Button(
        onClick = {
            if (connectionState is ConnectionState.Connected) {
                buttonText = "Starting..."
                Helathetile.acquireOfflineData { success ->
                   if (success) {
                        mainActivity.initializeCsvStringBuilder()
                    } else {
                       buttonText =   "Start Online Acquire"
                    }
                }
            }
        },
        modifier = Modifier.padding(top = 16.dp)
    ) {
        Text(text = buttonText)
    }
}


@RequiresApi(Build.VERSION_CODES.Q)
fun saveCsvToFile(context: Context, csvData: String): Boolean {
    val fileName = "vitals_data_${System.currentTimeMillis()}.csv"
    val resolver = context.contentResolver
    val contentValues = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
        put(MediaStore.MediaColumns.MIME_TYPE, "text/csv")
        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
    }


    val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)

    return try {
        resolver.openOutputStream(uri ?: return false).use { outputStream ->
            outputStream?.write(csvData.toByteArray())
            Log.d("CSVFile", "CSV file saved: $uri")
        }
        true
    } catch (e: IOException) {
        Log.e("CSVFile", "Error writing CSV file: ${e.message}")
        if (uri != null) {
            resolver.delete(uri, null, null)
        }
        false
    }
}


private fun sensorDataToCsvString(sensorData: SensorData): String {
    return listOf(
        sensorData.timeStamp.toString(),
        sensorData.temperature.toString(),
        sensorData.gsr.toString(),
        sensorData.grnCount.toString(),
        sensorData.grn2Count.toString(),
        sensorData.irCount.toString(),
        sensorData.redCount.toString(),
        sensorData.accX.toString(),
        sensorData.accY.toString(),
        sensorData.accZ.toString(),
        sensorData.hr.toString(),
        sensorData.hrConfidence.toString(),
        sensorData.rrInterBeatInterval.toString(),
        sensorData.rrConfidence.toString(),
        sensorData.spo2.toString(),
        sensorData.spo2Confidence.toString(),
        sensorData.spo2Estimated.toString(),
        sensorData.spo2CalPercentage.toString(),
        sensorData.spo2LowSignQualityFlag.toString(),
        sensorData.spo2MotionFlag.toString(),
        sensorData.spo2LowPiFlag.toString(),
        sensorData.spo2UnreliableRFlag.toString(),
        sensorData.spo2State.toString(),
        sensorData.skinContactState.toString(),
        sensorData.walkSteps.toString(),
        sensorData.runSteps.toString(),
        sensorData.calories.toString(),
        sensorData.cadence.toString(),
        sensorData.event.toString(),
        sensorData.activityClass.toString(),
        sensorData.updatedEda.toString(),
        sensorData.updatedAcc.toString()
    ).joinToString(",")
}




