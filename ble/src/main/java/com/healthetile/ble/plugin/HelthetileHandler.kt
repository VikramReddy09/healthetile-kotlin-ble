package com.healthetile.ble.plugin

import android.bluetooth.BluetoothDevice


interface BluetoothScanHandler {
    fun onDeviceFound(device: BluetoothDevice)
    fun onError(error: Exception)
    fun onScanStarted()
}


interface ConnectionState{
    data object Connected: ConnectionState
    data object Disconnected: ConnectionState
    data object Connecting: ConnectionState
}

interface DataAcquisitionCallback {
    fun onDataAcquisitionStatusChanged(isAcquiring: Boolean)
}


sealed class Resource<out T : Any> {
    data class Success<out T : Any>(val data: T?) : Resource<T>()  // Represents a successful acquisition with optional data
    data class Error(val errorMessage: String) : Resource<Nothing>()  // Represents an error with an error message
    data class Loading<out T : Any>(val data: T? = null, val message: String? = null) : Resource<T>()  // Represents a loading state with optional data and message
    data class Initial<out T : Any>(val data: T? = null, val message: String? = null) : Resource<T>()  // Represents an initial state with optional data and message
    data class Stopping(val message: String? = null) : Resource<Nothing>()  // Represents a stopping state with an optional message
}





