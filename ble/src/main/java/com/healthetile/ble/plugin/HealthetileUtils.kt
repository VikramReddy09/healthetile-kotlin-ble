package com.healthetile.ble.plugin

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.util.Locale
import java.util.UUID

@SuppressLint("MissingPermission")
internal object HealthetileUtils {

    val batteryServiceUUID = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb")
    val batteryLevelUUID = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb")
    val tempServiceUUID = UUID.fromString("2a3b580a-7263-4249-a647-e0af6f5966ab")
    val tempSendUUID = UUID.fromString("2a3b580b-7263-4249-a647-e0af6f5966ab")
    val tempReceiveUUID = UUID.fromString("2a3b580c-7263-4249-a647-e0af6f5966ab")


    fun getBatteryLevel(bluetoothGatt: BluetoothGatt) {

        val batteryCharacteristic =
            findCharacteristics(bluetoothGatt, batteryServiceUUID, batteryLevelUUID)

        val result = bluetoothGatt.readCharacteristic(batteryCharacteristic)

        if (!result) {
            Log.e("Bluetooth", "Failed to initiate reading battery level")
        }
    }

     fun setDateToDevice(
        bluetoothGatt: BluetoothGatt
    ) {
        Handler(Looper.getMainLooper()).postDelayed({

            val dataTimePrefix = byteArrayOf(0x05, 0x01, 0x00, 0x04)

            val currentTimeInSeconds = System.currentTimeMillis() / 1000

            val timeBytes = ByteArray(4)
            for (i in 0..3) {
                timeBytes[3 - i] = ((currentTimeInSeconds shr (8 * i)) and 0xFF).toByte()
            }
            val dataToSend = dataTimePrefix + timeBytes

            val dataCharacteristic = findCharacteristics(bluetoothGatt, tempServiceUUID,
                tempSendUUID)

            if(dataCharacteristic != null){
                val writeSuccess = writeCharacteristic(bluetoothGatt, dataCharacteristic, dataToSend)

                if (writeSuccess) {
                    Log.d("Bluetooth", "Date set successful!")
                } else {
                    Log.e("Bluetooth", "Date set failed!")
                }
            }

        }, 1000)
    }

    fun onlineData(bluetoothGatt: BluetoothGatt, onResult: (Boolean) -> Unit) {
        Log.d("Bluetooth", "Starting onlineData process...")

        val characteristic = findCharacteristics(
            bluetoothGatt, tempServiceUUID, tempSendUUID
        ) ?: run {
            Log.e("Bluetooth", "Failed to find send characteristic")
            onResult(false)
            return
        }

        val dataTime = "01 09 00 01 01\n"
        val dataTimeBytes = convertWriteToByteArray(dataTime)

        Log.d("Bluetooth", "Writing first characteristic for online data")

        writeCharacteristicWithRetry(
            bluetoothGatt,
            characteristic,
            dataTimeBytes,
            delayMillis = 1000
        ) { firstWriteSuccess ->
            if (!firstWriteSuccess) {
                Log.e("Bluetooth", "First write failed during onlineData")
                onResult(false)
                return@writeCharacteristicWithRetry
            }

            Log.d("Bluetooth", "First write succeeded, writing second characteristic")

            val data3 = "01 0b\n"
            val data4 = convertWriteToByteArray(data3)

            writeCharacteristicWithRetry(
                bluetoothGatt,
                characteristic,
                data4,
                delayMillis = 1000
            ) { secondWriteSuccess ->
                if (!secondWriteSuccess) {
                    Log.e("Bluetooth", "Second write failed during onlineData")
                    onResult(false)
                    return@writeCharacteristicWithRetry
                }

                Log.d("Bluetooth", "Second write succeeded, enabling notification")

                val receiveCharacteristic = findCharacteristics(
                    bluetoothGatt, tempServiceUUID, tempReceiveUUID
                ) ?: run {
                    Log.e("Bluetooth", "Failed to find receive characteristic")
                    onResult(false)
                    return@writeCharacteristicWithRetry
                }

                val notificationEnabled = enableNotificationForCharacteristic(bluetoothGatt, receiveCharacteristic)
                if (!notificationEnabled) {
                    Log.e("BluetoothGattCallback", "Failed to enable notification for tempReceiveUUID")
                    onResult(false)
                } else {
                    Log.d("BluetoothGattCallback", "Notification enabled for tempReceiveUUID")
                    onResult(true)
                }
            }
        }
    }

    fun offlineData(bluetoothGatt: BluetoothGatt, onResult: (Boolean) -> Unit) {
        Log.d("Bluetooth", "Starting offlineData process...")

        // Find the characteristic for sending data
        val characteristic = findCharacteristics(
            bluetoothGatt, tempServiceUUID, tempSendUUID
        ) ?: run {
            Log.e("Bluetooth", "Failed to find send characteristic")
            onResult(false)
            return
        }

        // Disable notifications for the characteristic before writing
        val isDisabled = disableNotificationForCharacteristic(bluetoothGatt, characteristic)

        if (isDisabled) {
            Log.d("Bluetooth", "Notifications disabled successfully for characteristic: ${characteristic.uuid}")
        } else {
            Log.e("Bluetooth", "Failed to disable notifications for characteristic: ${characteristic.uuid}")
            onResult(false)
            return
        }

        // Prepare data to send
        val data3 = "04 01\n"
        val data4 = convertWriteToByteArray(data3)

        Log.d("Bluetooth", "Writing characteristic for offline data")

        // Write to the characteristic
        writeCharacteristicWithRetry(
            bluetoothGatt,
            characteristic,
            data4,
            delayMillis = 2000
        ) { secondSuccess ->
            if (secondSuccess) {
                Log.d("Bluetooth", "Characteristic write succeeded, enabling notification")

                // Find the receive characteristic to enable notifications
                val receiveCharacteristic = findCharacteristics(
                    bluetoothGatt, tempServiceUUID, tempReceiveUUID
                ) ?: run {
                    Log.e("Bluetooth", "Failed to find receive characteristic")
                    onResult(false)
                    return@writeCharacteristicWithRetry
                }

                // Enable notifications for the receive characteristic
                val notificationEnabled = enableNotificationForCharacteristic(bluetoothGatt, receiveCharacteristic)
                if (!notificationEnabled) {
                    Log.e("BluetoothGattCallback", "Failed to enable notification for tempReceiveUUID")
                    onResult(false)
                } else {
                    Log.d("BluetoothGattCallback", "Notification enabled for tempReceiveUUID")
                    onResult(true)  // Indicate success
                }
            } else {
                Log.e("Bluetooth", "Failed to write characteristic during offlineData")
                onResult(false)
            }
        }
    }


    fun stoppingData(bluetoothGatt: BluetoothGatt, onResult: (Boolean) -> Unit) {
        Log.d("Bluetooth", "Starting stoppingData process...")

        val characteristic = findCharacteristics(
            bluetoothGatt, tempServiceUUID, tempSendUUID
        ) ?: run {
            Log.e("Bluetooth", "Failed to find send characteristic")
            onResult(false)
            return
        }

        Handler(Looper.getMainLooper()).postDelayed({
            Log.d("Bluetooth", "Writing command to stop data acquisition")

            val command = "01 12\n"
            val convertedCommand = convertWriteToByteArray(command)

            val response = writeCharacteristic(bluetoothGatt, characteristic, convertedCommand)
            if (response) {
                Log.d("Bluetooth", "Stopping data acquisition succeeded")
                onResult(true)
            } else {
                Log.e("Bluetooth", "Failed to stop data acquisition")
                onResult(false)
            }
        }, 1000)
    }


     fun sendHealthetileDataBroadcast(context: Context, sensorDataList: List<SensorData>) {
        val intent = Intent("com.healthetile.HEALTHETILE_DATA")
        intent.putParcelableArrayListExtra("healthetile_sensor_data_list", ArrayList(sensorDataList))
        context.sendBroadcast(intent) // Send the broadcast
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun onDataRead(
        context: Context,
        uuid: UUID,
        value: ByteArray,
        callback: DataAcquisitionCallback
    ) {
        if (uuid == tempReceiveUUID) {
            CoroutineScope(Dispatchers.Default).launch {
                try {
                    val encodedReceived = value.joinToString(" ") {
                        "0x${it.toUByte().toString(16).padStart(2, '0').toUpperCase()}"
                    }

                    val dataList = encodedReceived.split(" ")

                    when {
                        dataList.size > 42 && dataList[0] == "0x01" && dataList[1] == "0x90" -> {
                            withTimeout(200) {
                                val baseSensorData = baseDataHandler(value)
                                sendHealthetileDataBroadcast(context, baseSensorData)
                                callback.onDataAcquisitionStatusChanged(true)
                            }
                        }

                        dataList.size > 42 && dataList[0] == "0x01" && dataList[1] == "0x91" -> {
                            withTimeout(200) {
                                val signalSensorData = signalDataHandler(value)
                                sendHealthetileDataBroadcast(context, signalSensorData)
                                callback.onDataAcquisitionStatusChanged(true)
                            }
                        }

                        dataList[0] == "0x01" && dataList[1] == "0x99" && dataList.size == 6 -> {
                            callback.onDataAcquisitionStatusChanged(false)
                        }

                        else -> {
                            callback.onDataAcquisitionStatusChanged(false)
                        }
                    }
                } catch (e: TimeoutCancellationException) {
                    Log.e("Bluetooth", "Timeout: Data acquisition took too long")
                    callback.onDataAcquisitionStatusChanged(false)
                } catch (e: Exception) {
                    e.printStackTrace()
                    Log.e("Bluetooth", "Error processing received data: ${e.message}")
                    callback.onDataAcquisitionStatusChanged(false)
                }
            }
        }
    }





    private fun writeCharacteristicWithRetry(
        bluetoothGatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        payload: ByteArray,
        delayMillis: Long,
        retries: Int = 3,
        onWriteComplete: (Boolean) -> Unit
    ) {
        var attempt = 0
        fun attemptWrite() {
            if (attempt < retries) {
                Handler(Looper.getMainLooper()).postDelayed({
                    val writeSuccess = writeCharacteristic(bluetoothGatt, characteristic, payload)
                    if (writeSuccess) {
                        onWriteComplete(true)
                    } else {
                        attempt++
                        attemptWrite()
                    }
                }, delayMillis)
            } else {
                onWriteComplete(false)
            }
        }
        attemptWrite()
    }


    private fun writeCharacteristic(
        bluetoothGatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        payload: ByteArray
    ): Boolean {
        val writeType = when {
            characteristic.isWritable() -> BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            characteristic.isWritableWithoutResponse() -> BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            else -> error("Characteristic ${characteristic.uuid} cannot be written to")
        }

        var writeSuccessful: Boolean

        bluetoothGatt.let { gatt ->
            characteristic.writeType = writeType
            characteristic.value = payload

            writeSuccessful = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeCharacteristic(characteristic)
            } else {
                gatt.legacyCharacteristicWrite(characteristic, payload, writeType)
            }
        }
        return writeSuccessful
    }
    private fun disableNotificationForCharacteristic(
        bluetoothGatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic
    ): Boolean {
        val cccUUID = UUID.fromString("00002902-0000-1000-8000-00805F9B34FB")

        // Retrieve the CCC descriptor
        val cccDescriptor = characteristic.getDescriptor(cccUUID) ?: run {
            Log.e("ConnectionManager", "${characteristic.uuid} doesn't contain the CCC descriptor!")
            return false
        }


        val currentValue = cccDescriptor.value
        val isNotifiable = characteristic.isNotifiable() && currentValue?.contentEquals(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) == true

        // If already disabled, return true
        if (!isNotifiable) {
            Log.d("ConnectionManager", "Notifications are already disabled for ${characteristic.uuid}")
            return true
        }

        // Prepare the payload to disable notifications
        val payload = BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE

        // Set the characteristic notification to false
        if (!bluetoothGatt.setCharacteristicNotification(characteristic, false)) {
            Log.e("ConnectionManager", "setCharacteristicNotification failed for ${characteristic.uuid}")
            return false
        }

        // Write the descriptor to disable notifications
        return writeDescriptorWithRetry(bluetoothGatt, cccDescriptor, payload)
    }


    private fun enableNotificationForCharacteristic(
        bluetoothGatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic
    ): Boolean {
        val cccUUID = UUID.fromString("00002902-0000-1000-8000-00805F9B34FB")

        // Retrieve the CCC descriptor
        val cccDescriptor = characteristic.getDescriptor(cccUUID) ?: run {
            Log.e("ConnectionManager", "${characteristic.uuid} doesn't contain the CCC descriptor!")
            return false
        }


        val currentValue = cccDescriptor.value
        val isNotifiable = characteristic.isNotifiable() && currentValue.contentEquals(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
        val isIndicatable = characteristic.isIndicatable() && currentValue.contentEquals(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE)


        if (isNotifiable || isIndicatable) {
            Log.d("ConnectionManager", "Notifications/Indications already enabled for ${characteristic.uuid}")
            return true
        }


        val payload = when {
            characteristic.isIndicatable() -> BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
            characteristic.isNotifiable() -> BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            else -> return false
        }

        if (!bluetoothGatt.setCharacteristicNotification(characteristic, true)) {
            Log.e("ConnectionManager", "setCharacteristicNotification failed for ${characteristic.uuid}")
            return false
        }

        // Retry descriptor write logic
        val result = writeDescriptorWithRetry(bluetoothGatt, cccDescriptor, payload)
        return result
    }

    private fun writeDescriptorWithRetry(
        bluetoothGatt: BluetoothGatt,
        descriptor: BluetoothGattDescriptor,
        payload: ByteArray,
        attempts: Int = 3,
        delayMillis: Long = 2000
    ): Boolean {
        for (attempt in 1..attempts) {
            val result = writeDescriptor(bluetoothGatt, descriptor, payload)
            Log.d("Bluetooth", "Attempt $attempt to write descriptor with result: $result")

            if (result) {
                Log.d("Bluetooth", "Descriptor write succeeded on attempt $attempt for ${descriptor.uuid}")
                return true
            } else {
                Log.e("Bluetooth", "Descriptor write failed on attempt $attempt for ${descriptor.uuid}")
                if (attempt < attempts) {
                    Log.d("Bluetooth", "Retrying in $delayMillis ms...")
                    Thread.sleep(delayMillis)
                }
            }
        }

        Log.e("Bluetooth", "Descriptor write failed after $attempts attempts for ${descriptor.uuid}")
        return false
    }
    private fun writeDescriptor(
        bluetoothGatt: BluetoothGatt,
        descriptor: BluetoothGattDescriptor,
        payload: ByteArray
    ): Boolean {
         bluetoothGatt.let { gatt ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                try {
                    val result = gatt.writeDescriptor(descriptor, payload)
                    Log.d("Bluetooth", "Attempting to write descriptor: ${descriptor.uuid}, result: $result")
                    return result == BluetoothGatt.GATT_SUCCESS
                } catch (e: Exception) {
                    Log.e("Bluetooth", "Error while writing descriptor: ${e.message}", e)
                    return false
                }
            } else {
                   return gatt.legacyDescriptorWrite(descriptor, payload)
            }
        }
    }




    @TargetApi(Build.VERSION_CODES.S)
    @Suppress("DEPRECATION")
    private fun BluetoothGatt.legacyDescriptorWrite(
        descriptor: BluetoothGattDescriptor,
        value: ByteArray
    ): Boolean {
        descriptor.value = value
        val result = writeDescriptor(descriptor)
        Log.d("Bluetooth", "Legacy descriptor write result for ${descriptor.uuid}: $result")
        return result
    }

    @TargetApi(Build.VERSION_CODES.S)
    @Suppress("DEPRECATION")
    private fun BluetoothGatt.legacyCharacteristicWrite(
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray,
        writeType: Int
    ): Boolean {
        characteristic.writeType = writeType
        characteristic.value = value
        val result = writeCharacteristic(characteristic)

        Log.d("Bluetooth", "Legacy characteristic write result for ${characteristic.uuid}: $result")
        return result
    }


    private fun findCharacteristics(
        bluetoothGatt: BluetoothGatt, serviceUUID: UUID, characteristicsUUID: UUID
    ): BluetoothGattCharacteristic? {
        return bluetoothGatt.services.find { service ->
            service.uuid == serviceUUID
        }?.characteristics?.find { characteristic ->
            characteristic.uuid == characteristicsUUID
        }
    }



    const val CCCD_DESCRIPTOR_UUID = "00002902-0000-1000-8000-00805F9B34FB"

    fun BluetoothGatt.printGattTable() {
        if (services.isEmpty()) {
            Log.d("BluetoothGatt","No service and characteristic available, call discoverServices() first?")
            return
        }
        services.forEach { service ->
            val characteristicsTable = service.characteristics.joinToString(
                separator = "\n|--",
                prefix = "|--"
            ) { char ->
                var description = "${char.uuid}: ${char.printProperties()}"
                if (char.descriptors.isNotEmpty()) {
                    description += "\n" + char.descriptors.joinToString(
                        separator = "\n|------",
                        prefix = "|------"
                    ) { descriptor ->
                        "${descriptor.uuid}: ${descriptor.printProperties()}"
                    }
                }
                description
            }
            Log.d("BluetoothGatt","Service ${service.uuid}\nCharacteristics:\n$characteristicsTable")
        }
    }
    fun BluetoothGattCharacteristic.printProperties(): String = mutableListOf<String>().apply {
        if (isReadable()) add("READABLE")
        if (isWritable()) add("WRITABLE")
        if (isWritableWithoutResponse()) add("WRITABLE WITHOUT RESPONSE")
        if (isIndicatable()) add("INDICATABLE")
        if (isNotifiable()) add("NOTIFIABLE")
        if (isEmpty()) add("EMPTY")
    }.joinToString()

    fun BluetoothGattCharacteristic.isReadable(): Boolean =
        containsProperty(BluetoothGattCharacteristic.PROPERTY_READ)

    fun BluetoothGattCharacteristic.isWritable(): Boolean =
        containsProperty(BluetoothGattCharacteristic.PROPERTY_WRITE)

    fun BluetoothGattCharacteristic.isWritableWithoutResponse(): Boolean =
        containsProperty(BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)

    fun BluetoothGattCharacteristic.isIndicatable(): Boolean =
        containsProperty(BluetoothGattCharacteristic.PROPERTY_INDICATE)

    fun BluetoothGattCharacteristic.isNotifiable(): Boolean =
        containsProperty(BluetoothGattCharacteristic.PROPERTY_NOTIFY)

    fun BluetoothGattCharacteristic.containsProperty(property: Int): Boolean =
        properties and property != 0

    fun BluetoothGattDescriptor.printProperties(): String = mutableListOf<String>().apply {
        if (isReadable()) add("READABLE")
        if (isWritable()) add("WRITABLE")
        if (isEmpty()) add("EMPTY")
    }.joinToString()

    fun BluetoothGattDescriptor.isReadable(): Boolean =
        containsPermission(BluetoothGattDescriptor.PERMISSION_READ)

    fun BluetoothGattDescriptor.isWritable(): Boolean =
        containsPermission(BluetoothGattDescriptor.PERMISSION_WRITE)

    fun BluetoothGattDescriptor.containsPermission(permission: Int): Boolean =
        permissions and permission != 0

    fun BluetoothGattDescriptor.isCccd() =
        uuid.toString().uppercase(Locale.US) == CCCD_DESCRIPTOR_UUID.uppercase(Locale.US)

    fun ByteArray.toHexString(): String =
        joinToString(separator = " ", prefix = "0x") { String.format("%02X", it) }

}

