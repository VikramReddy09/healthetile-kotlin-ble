package com.healthetile.ble.plugin

// Exception when the device does not support Bluetooth
class BluetoothNotSupportedException : Exception("This device doesn't support Bluetooth.")

// Exception when Bluetooth is turned off
class BluetoothOffException : Exception("Bluetooth is not enabled.")

// Exception when Bluetooth scan permissions are missing
class ScanPermissionException : Exception("Missing required permissions for Bluetooth scan.")

// Exception when Bluetooth scan fails with an error code
class ScanFailException(errorCode: Int) : Exception("Scan failed with error code: $errorCode")

// Exception when the Bluetooth device is not found during scanning
class BluetoothDeviceNotFoundException : Exception("Bluetooth device not found.")

// Exception for Bluetooth pairing failure
class BluetoothPairingException : Exception("Bluetooth pairing failed.")

// Exception for Bluetooth GATT errors with an optional cause
class BluetoothGattException(message: String, cause: Throwable? = null) : Exception(message, cause)

// Custom exception for connection errors with detailed message
class BluetoothConnectionException(
    message: String = "Bluetooth connection error",
    val deviceAddress: String? = null
) : Exception("$message for device $deviceAddress")


// Custom exception when context is not initialized
class ContextNotInitializedException : Exception("Context not initialized. Call Helathetile.initialize(context) first.")
