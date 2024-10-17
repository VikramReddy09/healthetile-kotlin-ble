package com.healthetile.ble.plugin

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.healthetile.ble.plugin.PermissionUtils.permissions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow


@SuppressLint("MissingPermission")
object Helathetile {

    private val _batteryLevel = MutableStateFlow(0)
    val batteryLevel: StateFlow<Int> get() = _batteryLevel

    private val _isOnlineAcquiring = MutableStateFlow(false)
    val isOnlineAcquiring: StateFlow<Boolean> get() = _isOnlineAcquiring.asStateFlow()

    private val _isOfflineAcquiring = MutableStateFlow(false)
    val isOfflineAcquiring: StateFlow<Boolean> get() = _isOfflineAcquiring.asStateFlow()

    private val _connectionStateFlow =
        MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)

    val connectionState: StateFlow<ConnectionState> get() = _connectionStateFlow

    fun initialize(context: Context) {
        appContext = context.applicationContext
        initializeBluetooth(appContext!!)
    }

    @SuppressLint("MissingPermission")
    fun startScanning(context: Context, deviceName: String, callback: BluetoothScanHandler) {
        try {

            if (!bluetoothAdapter!!.isEnabled) {
                throw BluetoothOffException()
            }

            if (!checkAllPermissionsGranted()) {
                throw ScanPermissionException()
            }

            val scanner = bluetoothAdapter!!.bluetoothLeScanner
            val mainHandler = Handler(context.mainLooper)

            if (isScanning) {
                stopScanning(scanner)
            }

            scanCallback = createScanCallback(deviceName, scanner, callback)

            isScanning = true
            callback.onScanStarted()
            scanner.startScan(scanCallback)


            mainHandler.postDelayed({
                if (isScanning) {
                    stopScanning(scanner)
                    callback.onError(BluetoothDeviceNotFoundException())
                }
            }, 10000)
        } catch (e: Exception) {
            Log.e("BluetoothScan", "Failed to start Bluetooth scan", e)
            callback.onError(e)
            isScanning = false
        }
    }

    @SuppressLint("MissingPermission")
    fun connectBluetoothDevice(
        bluetoothDevice: BluetoothDevice,
    ) {

        updateConnectionState(ConnectionState.Connecting)

        val context = requireAppContext()

        val bluetoothManager =
            context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager

        this.bluetoothAdapter = bluetoothManager.adapter

        if (bluetoothAdapter == null) {
            updateConnectionState(ConnectionState.Disconnected)
            throw BluetoothNotSupportedException()
        }

        if (!bluetoothAdapter!!.isEnabled) {
            updateConnectionState(ConnectionState.Disconnected)
            throw BluetoothOffException()
        }

        val pairedDevices = bluetoothAdapter!!.bondedDevices


        val pairedDevice = pairedDevices.find {
            it.address == bluetoothDevice.address
        }

        if (pairedDevice != null && pairedDevice.bondState == BluetoothDevice.BOND_BONDED) {
            Log.d(
                "Bluetooth",
                "Found paired device: ${pairedDevice.name} (${pairedDevice.address})"
            )

            healthetileGatt = pairedDevice.connectGatt(context, false, gattCallback)

        } else {

            val pairingInitiated = bluetoothDevice.createBond()

            if (!pairingInitiated) {
                Log.e("Bluetooth", "Pairing initiation failed.")
                updateConnectionState(ConnectionState.Disconnected)
                throw BluetoothPairingException()
            } else {
                registerPairingReceiver(context, bluetoothDevice)
            }
        }
    }

    fun disconnectDevice() {
        healthetileGatt?.let { gatt ->
            gatt.disconnect()
            gatt.close()
            healthetileGatt = null
            updateConnectionState(ConnectionState.Disconnected)
        } ?: run {
            Log.e("Bluetooth", "Cannot disconnect, GATT is null")
        }
    }

    fun acquireOnlineData(onResult: (Boolean) -> Unit) {
        if (healthetileGatt == null) {
            Log.e("Bluetooth", "BluetoothGatt is null, cannot acquire online data")
            onResult(false)
        } else {
            isOnlineClicked = true
            HealthetileUtils.onlineData(healthetileGatt!!) { success ->
                if (success) {
                    Log.d("Bluetooth", "acquire online data Successfully")
                    onResult(true)
                } else {
                    Log.e("Bluetooth", "Failed to acquire online data")
                    isOnlineClicked = false
                    onResult(false)
                }
            }
        }
    }

    fun acquireOfflineData(onResult: (Boolean) -> Unit) {
        if (healthetileGatt == null) {
            Log.e("Bluetooth", "BluetoothGatt is null, cannot acquire offline data")
            onResult(false)
        } else {
            isOfflineClicked = true
            HealthetileUtils.offlineData(healthetileGatt!!) { success ->
                if (success) {
                    Log.d("Bluetooth", "acquire offline data Successfully")
                    onResult(true)
                } else {
                    Log.e("Bluetooth", "Failed to acquire offline data")
                    isOfflineClicked = false
                    onResult(false)
                }
            }
        }
    }

    fun stopAcquiringData(onResult: (Boolean) -> Unit) {
        if (healthetileGatt == null) {
            Log.e("Bluetooth", "BluetoothGatt is null, cannot stop acquiring data")
            onResult(false)
        } else {
            HealthetileUtils.stoppingData(healthetileGatt!!) { success ->
                if (success) {
                    Log.d("Bluetooth", "stop acquiring data Successfully")
                    onResult(true)
                } else {
                    Log.e("Bluetooth", "Failed to stop acquiring data")
                    onResult(false)
                }
            }
        }
    }

    fun cleanup() {
        Log.d("Bluetooth", "Cleaning up resources...")

        healthetileGatt?.let { gatt ->
            gatt.disconnect()
            gatt.close()
            healthetileGatt = null
            Log.d("Bluetooth", "GATT connection closed.")
        }

        appContext?.let {
            unregisterBluetoothStateReceiver(it)
        }

        appContext?.let {
            unregisterPairingReceiver(it)
        }

        appContext = null

        isOnlineClicked = false
        isOfflineClicked = false


        Log.d("Bluetooth", "Resources cleaned up successfully.")
    }



    ///Private functions

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var healthetileGatt: BluetoothGatt? = null
    private var pairingReceiver: BroadcastReceiver? = null
    private var bluetoothStateReceiver: BroadcastReceiver? = null
    private var appContext: Context? = null
    private var _isOfflineClicked = false
    private var _isOnlineClicked = false
    private var isScanning = false
    private var connectedDevice:BluetoothDevice?= null
    private var _autoReconnect = false
    private var scanCallback: ScanCallback? = null

    var isOnlineClicked: Boolean
        get() = _isOnlineClicked
        private set(value) {
            _isOnlineClicked = value
        }


    var isOfflineClicked: Boolean
        get() = _isOfflineClicked
        private set(value) {
            _isOfflineClicked = value
        }


    var autoReconnect: Boolean
        get() = _autoReconnect
        set(value) {
            _autoReconnect = value
        }




    private fun updateConnectionState(state: ConnectionState) {
        _connectionStateFlow.value = state
    }

    private fun updateOnlineAcquisitionStatus(status: Boolean) {
        _isOnlineAcquiring.value = status
    }

    private fun updateOfflineAcquisitionStatus(status: Boolean) {
        _isOfflineAcquiring.value = status
    }

    private fun updateBatteryLevel(level: Int) {
        _batteryLevel.value = level
    }

    private fun checkContextInitialized() {
        if (appContext == null) {
            throw ContextNotInitializedException()
        }
    }

    private fun requireAppContext(): Context {
        return appContext ?: throw ContextNotInitializedException()
    }

    private fun initializeBluetooth(context: Context) {
        checkContextInitialized()
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            ?: return
        bluetoothAdapter = bluetoothManager.adapter
        if (bluetoothAdapter == null) {
            throw BluetoothNotSupportedException()
        }
        registerBluetoothStateReceiver(context)
    }

    private fun checkAllPermissionsGranted(): Boolean {
        permissions.forEach { permission ->
            if (ContextCompat.checkSelfPermission(
                    requireAppContext(),
                    permission
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return false
            }
        }
        return true
    }

    private fun registerBluetoothStateReceiver(context: Context) {
        unregisterBluetoothStateReceiver(context)
        bluetoothStateReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val action = intent.action
                if (BluetoothAdapter.ACTION_STATE_CHANGED == action) {
                    val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                    Log.d("BluetoothReceiver", "Bluetooth state changed: $state")
                    handleBluetoothStateChange(state)
                } else {
                    Log.d("BluetoothReceiver", "Received unrelated intent action: $action")
                }
            }
        }

        val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        context.registerReceiver(bluetoothStateReceiver, filter)

    }

    private fun unregisterBluetoothStateReceiver(context: Context) {
        if (bluetoothStateReceiver != null) {
            Log.d("BluetoothReceiver", "Attempting to unregister Bluetooth state receiver")
            try {
                context.unregisterReceiver(bluetoothStateReceiver)
                Log.d("BluetoothReceiver", "Bluetooth state receiver unregistered successfully")
            } catch (e: IllegalArgumentException) {
                Log.e("BluetoothReceiver", "Failed to unregister Bluetooth state receiver: Receiver was either already unregistered or it was never registered", e)
            } finally {
                bluetoothStateReceiver = null
            }
        } else {
            Log.d("BluetoothReceiver", "No Bluetooth state receiver to unregister")
        }
    }

    private fun unregisterPairingReceiver(context: Context) {
        pairingReceiver?.let {
            context.unregisterReceiver(it)
            pairingReceiver = null
        }
    }

    private fun registerPairingReceiver(
        context: Context,
        bluetoothDevice: BluetoothDevice,
    ) {
        pairingReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val bondState =
                    intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE)
                when (bondState) {

                    BluetoothDevice.BOND_BONDING -> {
                        Log.e("Bluetooth", "Pairing.")
                    }

                    BluetoothDevice.BOND_BONDED -> {
                        Log.d("Bluetooth", "Device successfully paired.")
                        healthetileGatt = bluetoothDevice.connectGatt(context, false, gattCallback)
                    }

                    BluetoothDevice.BOND_NONE -> {
                        Log.e("Bluetooth", "Pairing failed.")
                        updateConnectionState(ConnectionState.Disconnected)
                    }
                }
            }
        }

        context.registerReceiver(
            pairingReceiver,
            IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        )
    }

    private fun handleBluetoothStateChange(state: Int) {
        when (state) {
            BluetoothAdapter.STATE_ON -> {
                Log.d("Bluetooth", "Bluetooth turned ON")
            }

            BluetoothAdapter.STATE_OFF -> {
                Log.d("Bluetooth", "Bluetooth turned OFF")
            }

            BluetoothAdapter.STATE_CONNECTED -> {
                Log.d("Bluetooth", "Device connected")
            }

            BluetoothAdapter.STATE_DISCONNECTED -> {
                Log.d("Bluetooth", "Device disconnected")
            }

            else -> {
                Log.d("Bluetooth", "Unknown Bluetooth state: $state")
            }
        }
    }

    private fun createScanCallback(
        deviceName: String,
        scanner: BluetoothLeScanner,
        callback: BluetoothScanHandler
    ): ScanCallback {
        return object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                super.onScanResult(callbackType, result)
                if (result.device.name == deviceName) {
                    Log.i("BluetoothScan", "Desired device found: ${result.device.name}")
                    stopScanning(scanner)
                    callback.onDeviceFound(result.device)
                }
            }

            override fun onScanFailed(errorCode: Int) {
                super.onScanFailed(errorCode)
                Log.e("BluetoothScan", "Scan failed with error: $errorCode")
                stopScanning(scanner)
                callback.onError(ScanFailException(errorCode))
            }
        }
    }


    private fun stopScanning(scanner: BluetoothLeScanner) {
        if (isScanning && scanCallback != null) {
            scanner.stopScan(scanCallback)
            isScanning = false
            Log.i("BluetoothScan", "Scanning stopped successfully")
            scanCallback = null
        }
    }

    private fun handleDisconnection(gatt: BluetoothGatt?) {
        updateConnectionState(ConnectionState.Disconnected)
        gatt?.close()

        if (autoReconnect) {
            connectedDevice?.let { device ->
                Log.d("Bluetooth", "Auto-reconnect is enabled. Attempting to reconnect to: ${device.name}")
                connectBluetoothDevice(device)
            }
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            try {
                Log.d("Bluetooth", "onConnectionStateChange - Status: $status, NewState: $newState")
                when (newState) {
                    BluetoothGatt.STATE_CONNECTED -> {
                        Log.d("Bluetooth", "Device connected, discovering services...")
                        connectedDevice = gatt?.device
                        updateConnectionState(ConnectionState.Connected)
                        gatt?.discoverServices()
                    }

                    BluetoothGatt.STATE_DISCONNECTED -> {
                        Log.d("Bluetooth", "Device disconnected")
                        handleDisconnection(gatt)
                    }

                    else -> {
                        updateConnectionState(ConnectionState.Disconnected)
                        Log.e("Bluetooth", "Unknown connection state: $newState")
                    }
                }

                if (status != BluetoothGatt.GATT_SUCCESS) {
                    updateConnectionState(ConnectionState.Disconnected)
                    throw BluetoothGattException("Connection failed with status: $status")
                }

            } catch (e: Exception) {
                updateConnectionState(ConnectionState.Disconnected)
                Log.e("Bluetooth", "Error in onConnectionStateChange", e)
                throw BluetoothGattException("Error in onConnectionStateChange", e)
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            try {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    if(!autoReconnect){
                        HealthetileUtils.setDateToDevice(gatt)
                        HealthetileUtils.getBatteryLevel(gatt)
                    }
                    Log.d("Bluetooth", "Services discovered successfully")

                } else {
                    updateConnectionState(ConnectionState.Disconnected)
                    throw BluetoothGattException("Failed to discover services, status: $status")
                }
            } catch (e: Exception) {
                updateConnectionState(ConnectionState.Disconnected)
                Log.e("Bluetooth", "Error in onServicesDiscovered", e)
                throw BluetoothGattException("Error in onServicesDiscovered", e)
            }
        }

        override fun onPhyRead(gatt: BluetoothGatt?, txPhy: Int, rxPhy: Int, status: Int) {
            super.onPhyRead(gatt, txPhy, rxPhy, status)
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(
                    "BluetoothGattCallback",
                    "PHY Read successfully. TX PHY: $txPhy, RX PHY: $rxPhy"
                )
            } else {
                Log.e("BluetoothGattCallback", "Failed to read PHY with status: $status")
            }
        }

        override fun onPhyUpdate(gatt: BluetoothGatt?, txPhy: Int, rxPhy: Int, status: Int) {
            super.onPhyUpdate(gatt, txPhy, rxPhy, status)
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d("Bluetooth", "PHY updated successfully to TX: $txPhy Mbps, RX: $rxPhy Mbps")
            } else {
                Log.e("Bluetooth", "Failed to update PHY, status: $status")
            }
        }

        @SuppressLint("SuspiciousIndentation")
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            super.onCharacteristicRead(gatt, characteristic, status)
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (characteristic.uuid == HealthetileUtils.batteryLevelUUID) {
                    val batteryLevel =
                        characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0)
                    updateBatteryLevel(batteryLevel)
                    Log.d("Bluetooth", "Battery level: $batteryLevel%")
                } else {
                    Log.e("Bluetooth", "Unknown characteristic read: ${characteristic.uuid}")
                }
            } else {
                Log.e("Bluetooth", "Failed to read characteristic, status: $status")
            }
        }

        @Deprecated("Deprecated in Java")
        @RequiresApi(Build.VERSION_CODES.O)
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic
        ) {
            val dataHex = characteristic.value.joinToString(" ") { String.format("%02X", it) }
            Log.i(
                "BluetoothGattCallback",
                "Characteristic Changed: UUID=${characteristic.uuid}, Value=$dataHex"
            )

            when (val characteristicUUID = characteristic.uuid) {
                HealthetileUtils.batteryLevelUUID -> {
                    val batteryLevel = characteristic.value.firstOrNull()?.toInt() ?: -1
                    Log.i("BluetoothGattCallback", "Battery Level: $batteryLevel%")
                    updateBatteryLevel(batteryLevel)
                }

                HealthetileUtils.tempReceiveUUID -> {
                    val context = requireAppContext()
                    try {
                        HealthetileUtils.onDataRead(
                            context,
                            characteristic.uuid,
                            characteristic.value,
                            dataCallback
                        )
                    } catch (e: Exception) {
                        Log.e("BluetoothGattCallback", "Error in onDataRead: ${e.message}", e)
                    }
                }

                else -> {
                    Log.w(
                        "BluetoothGattCallback",
                        "Unknown characteristic UUID: $characteristicUUID"
                    )
                }
            }
        }
    }

    private val dataCallback = object : DataAcquisitionCallback {
        override fun onDataAcquisitionStatusChanged(isAcquiring: Boolean) {
            if(isOnlineClicked){
                updateOnlineAcquisitionStatus(isAcquiring)
            }
            if(isOfflineClicked){
                updateOfflineAcquisitionStatus(isAcquiring)
            }
        }
    }

}










