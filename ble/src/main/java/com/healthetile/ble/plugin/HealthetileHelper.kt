package com.healthetile.ble.plugin

import android.Manifest
import android.annotation.SuppressLint
import android.os.Build
import android.os.Parcel
import android.os.Parcelable
import androidx.annotation.RequiresApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.pow
import kotlin.math.sqrt

@SuppressLint("SuspiciousIndentation")
@RequiresApi(Build.VERSION_CODES.O)
fun baseDataHandler(data: ByteArray): List<SensorData> {

    val sensorDataList = mutableListOf<SensorData>()

    var baseIndex = 6

    val dataTime = (byteArrayToInt(data.sliceArray(baseIndex + 2..baseIndex + 5))).toLong()

    val temperature = tempConversion(byteArrayToInt(data.sliceArray(baseIndex + 6..baseIndex + 9))).toFloat()
    val hr = byteArrayToShort(data.sliceArray(baseIndex + 10..baseIndex + 11))
    val hrConfidence = data[baseIndex + 12].toInt()
    val rrInterBeatInterval = byteArrayToShort(data.sliceArray(baseIndex + 13..baseIndex + 14))
    val rrConfidence = data[baseIndex + 15].toInt()
    val spo2 = byteArrayToShort(data.sliceArray(baseIndex + 16..baseIndex + 17))
    val spo2Confidence = data[baseIndex + 18].toInt()
    val spo2Estimated = byteArrayToShort(data.sliceArray(baseIndex + 19..baseIndex + 20))
    val spo2CalPercentage = data[baseIndex + 21].toInt()
    val spo2LowSignQualityFlag = ((data[baseIndex + 22].toInt() shr 7) and 0x01)
    val spo2MotionFlag = ((data[baseIndex + 22].toInt() shr 6) and 0x01)
    val spo2LowPiFlag = ((data[baseIndex + 22].toInt() shr 5) and 0x01)
    val spo2UnreliableRFlag = ((data[baseIndex + 22].toInt() shr 4) and 0x01)
    val spo2State = (data[baseIndex + 22].toInt() and 0x03)
    val skinContactState = (data[baseIndex + 23].toInt() and 0x02)
    val activityClass = (data[baseIndex + 23].toInt() shr 4)
    val walkSteps = byteArrayToInt(data.sliceArray(baseIndex + 24..baseIndex + 27))
    val runSteps = byteArrayToInt(data.sliceArray(baseIndex + 28..baseIndex + 31))
    val calories = byteArrayToInt(data.sliceArray(baseIndex + 32..baseIndex + 35))
    val cadence = byteArrayToInt(data.sliceArray(baseIndex + 36..baseIndex + 39))
    val event = ((data[baseIndex + 23].toInt() shr 2) and 0x03)
    val grnCount = ((data[baseIndex + 40].toUByte().toInt() shl 16) or
            (data[baseIndex + 41].toUByte().toInt() shl 8) or data[baseIndex + 42].toUByte().toInt())
    val irCount = ((data[baseIndex + 43].toUByte().toInt() shl 16) or
            (data[baseIndex + 44].toUByte().toInt() shl 8) or data[baseIndex + 45].toUByte().toInt())
    val redCount = ((data[baseIndex + 46].toUByte().toInt() shl 16) or
            (data[baseIndex + 47].toUByte().toInt() shl 8) or data[baseIndex + 48].toUByte().toInt())
    val grn2Count = ((data[baseIndex + 49].toUByte().toInt() shl 16) or
            (data[baseIndex + 50].toUByte().toInt() shl 8) or data[baseIndex + 51].toUByte().toInt())
    val accX1 = ((data[baseIndex + 52].toUByte().toInt() shl 8) or data[baseIndex + 53].toUByte().toInt())
    val accX = accConversion(accX1)
    val accY1 = ((data[baseIndex + 54].toUByte().toInt() shl 8) or data[baseIndex + 55].toUByte().toInt())
    val accY = accConversion(accY1)
    val accZ1 = ((data[baseIndex + 56].toUByte().toInt() shl 8) or data[baseIndex + 57].toUByte().toInt())
    val accZ = accConversion(accZ1)

    val gsr = ((data[baseIndex + 58].toUByte().toInt() shl 8) or data[baseIndex + 59].toUByte().toInt())

    var updatesEda = -110.0 * gsr / (11.0 * gsr - 435200.0)
    updatesEda = updatesEda.coerceIn(0.0, 50.0)

    val updatedAcc = sqrt(accX * accX + accY * accY + accZ * accZ)

    sensorDataList.add(
        SensorData(
            timeStamp = dataTime,
            temperature = temperature,
            hr = hr,
            hrConfidence = hrConfidence,
            rrInterBeatInterval = rrInterBeatInterval,
            rrConfidence = rrConfidence,
            spo2 = spo2,
            spo2Confidence = spo2Confidence,
            spo2Estimated = spo2Estimated,
            spo2CalPercentage = spo2CalPercentage,
            spo2LowSignQualityFlag = spo2LowSignQualityFlag,
            spo2MotionFlag = spo2MotionFlag,
            spo2LowPiFlag = spo2LowPiFlag,
            spo2UnreliableRFlag = spo2UnreliableRFlag,
            spo2State = spo2State,
            skinContactState = skinContactState,
            activityClass = activityClass,
            walkSteps = walkSteps,
            runSteps = runSteps,
            calories = calories,
            cadence = cadence,
            event = event,
            grnCount = grnCount,
            irCount = irCount,
            redCount = redCount,
            grn2Count = grn2Count,
            accX = accX,
            accY = accY,
            accZ = accZ,
            gsr = gsr,
            updatedEda = updatesEda,
            updatedAcc = updatedAcc
        )
    )

    baseIndex += 60


    for (i in 0 until 6) {
        val grnCountBase = ((data[baseIndex + 0].toUByte().toInt() shl 16) or
                (data[baseIndex + 1].toUByte().toInt() shl 8) or data[baseIndex + 2].toUByte().toInt())
        val irCountBase = ((data[baseIndex + 3].toUByte().toInt() shl 16) or
                (data[baseIndex + 4].toUByte().toInt() shl 8) or data[baseIndex + 5].toUByte().toInt())
        val redCountBase = ((data[baseIndex + 6].toUByte().toInt() shl 16) or
                (data[baseIndex + 7].toUByte().toInt() shl 8) or data[baseIndex + 8].toUByte().toInt())
        val grn2CountBase = ((data[baseIndex + 9].toUByte().toInt() shl 16) or
                (data[baseIndex + 10].toUByte().toInt() shl 8) or data[baseIndex + 11].toUByte().toInt())

        val accX1Base = ((data[baseIndex + 12].toUByte().toInt() shl 8) or data[baseIndex + 13].toUByte().toInt())
        val accXBase = accConversion(accX1Base)
        val accY1Base = ((data[baseIndex + 14].toUByte().toInt() shl 8) or data[baseIndex + 15].toUByte().toInt())
        val accYBase = accConversion(accY1Base)
        val accZ1Base = ((data[baseIndex + 16].toUByte().toInt() shl 8) or data[baseIndex + 17].toUByte().toInt())
        val accZBase = accConversion(accZ1Base)

        val gsrBase = ((data[baseIndex + 18].toUByte().toInt() shl 8) or data[baseIndex + 19].toUByte().toInt())

        var updatesEdaBase = -110.0 * gsrBase / (11.0 * gsrBase - 435200.0)
        updatesEdaBase = updatesEdaBase.coerceIn(0.0, 50.0)

        val updatedAccBase = sqrt(accX * accX + accY * accY + accZ * accZ)

        sensorDataList.add(
            SensorData(
                timeStamp = 0,
                temperature = 0.0f,
                hr = 0,
                hrConfidence = 0,
                rrInterBeatInterval = 0,
                rrConfidence = 0,
                spo2 = 0,
                spo2Confidence = 0,
                spo2Estimated = 0,
                spo2CalPercentage = 0,
                spo2LowSignQualityFlag = 0,
                spo2MotionFlag = 0,
                spo2LowPiFlag = 0,
                spo2UnreliableRFlag = 0,
                spo2State = 0,
                skinContactState = 0,
                activityClass = 0,
                walkSteps = 0,
                runSteps = 0,
                calories = 0,
                cadence = 0,
                event = 0,
                grnCount = grnCountBase,
                irCount = irCountBase,
                redCount = redCountBase,
                grn2Count = grn2CountBase,
                accX = accXBase,
                accY = accYBase,
                accZ = accZBase,
                gsr = gsrBase,
                updatedEda = updatesEdaBase,
                updatedAcc = updatedAccBase
            )
        )

        baseIndex += 20
    }

    return sensorDataList
}


@RequiresApi(Build.VERSION_CODES.O)
fun signalDataHandler(data: ByteArray): List<SensorData> {
    val sensorSignalDataList = mutableListOf<SensorData>()

    var newBaseIndex = 6

    for (i in 0 until 9) {
        val grnCount = ((data[newBaseIndex + 0].toUByte().toInt() shl 16) or
                (data[newBaseIndex + 1].toUByte().toInt() shl 8) or
                data[newBaseIndex + 2].toUByte().toInt())
        val irCount = ((data[newBaseIndex + 3].toUByte().toInt() shl 16) or
                (data[newBaseIndex + 4].toUByte().toInt() shl 8) or
                data[newBaseIndex + 5].toUByte().toInt())
        val redCount = ((data[newBaseIndex + 6].toUByte().toInt() shl 16) or
                (data[newBaseIndex + 7].toUByte().toInt() shl 8) or
                data[newBaseIndex + 8].toUByte().toInt())
        val grn2Count = ((data[newBaseIndex + 9].toUByte().toInt() shl 16) or
                (data[newBaseIndex + 10].toUByte().toInt() shl 8) or
                data[newBaseIndex + 11].toUByte().toInt())
        val accX1 = ((data[newBaseIndex + 12].toUByte().toInt() shl 8) or
                data[newBaseIndex + 13].toUByte().toInt())
        val accX = accConversion(accX1)
        val accY1 = ((data[newBaseIndex + 14].toUByte().toInt() shl 8) or
                data[newBaseIndex + 15].toUByte().toInt())
        val accY = accConversion(accY1)
        val accZ1 = ((data[newBaseIndex + 16].toUByte().toInt() shl 8) or
                data[newBaseIndex + 17].toUByte().toInt())
        val accZ = accConversion(accZ1)
        val gsr = ((data[newBaseIndex + 18].toUByte().toInt() shl 8) or
                data[newBaseIndex + 19].toUByte().toInt())

        var updatesEda = -110.0 * gsr / (11.0 * gsr - 435200.0)


        updatesEda = updatesEda.coerceIn(0.0, 50.0)

        val updatedAcc = sqrt(accX.pow(2) + accY.pow(2) + accZ.pow(2))

        val sensorData = SensorData(
            timeStamp = 0,
            temperature = 0.0f,
            hr = 0,
            hrConfidence = 0,
            rrInterBeatInterval = 0,
            rrConfidence = 0,
            spo2 = 0,
            spo2Confidence = 0,
            spo2Estimated = 0,
            spo2CalPercentage = 0,
            spo2LowSignQualityFlag = 0,
            spo2MotionFlag = 0,
            spo2LowPiFlag = 0,
            spo2UnreliableRFlag = 0,
            spo2State = 0,
            skinContactState = 0,
            activityClass = 0,
            walkSteps = 0,
            runSteps = 0,
            calories = 0,
            cadence = 0,
            event = 0,
            grnCount = grnCount,
            irCount = irCount,
            redCount = redCount,
            grn2Count = grn2Count,
            accX = accX,
            accY = accY,
            accZ = accZ,
            gsr = gsr,
            updatedEda = updatesEda,
            updatedAcc = updatedAcc
        )


        sensorSignalDataList.add(sensorData)


        newBaseIndex += 20
    }

    return sensorSignalDataList
}






fun convertWriteToByteArray(data: String): ByteArray {
    val dataBytes = mutableListOf<Int>()
    var i = 0
    val byte1 = IntArray(2)

    data.forEach { a ->
        when (a) {
            ' ', '\n' -> {
                i = 0
                val byte = byte1[0] * 16 + byte1[1]
                dataBytes.add(byte)
            }
            in '0'..'9' -> {
                byte1[i] = a - '0'
                i++
            }
            in 'a'..'f' -> {
                byte1[i] = a - 'a' + 10
                i++
            }
            in 'A'..'F' -> {
                byte1[i] = a - 'A' + 10
                i++
            }
        }
    }

    return ByteArray(dataBytes.size) { dataBytes[it].toByte() }
}

private fun byteArrayToInt(bytes: ByteArray): Int {
    val buffer = ByteBuffer.wrap(bytes)
    buffer.order(ByteOrder.BIG_ENDIAN)
    return buffer.int
}

private fun byteArrayToShort(bytes: ByteArray): Int {
    val buffer = ByteBuffer.wrap(bytes)
    buffer.order(ByteOrder.BIG_ENDIAN)
    return buffer.short.toInt()
}

private fun tempConversion(n: Int): Double {

    val exponentRaw = (n and 2139095040) shr 23
    val mantissa = n and 8388607


    val signMulti = 1


    val exponent = exponentRaw - 127
    var mantMult = 1.0

    for (b in 22 downTo 0) {
        if ((mantissa and (1 shl b)) != 0) {
            mantMult += 1.0 / (1 shl (23 - b))
        }
    }


    return signMulti * (2.0.pow(exponent)) * mantMult
}

private fun accConversion(acc: Int): Double {

    var adjustedAcc = acc
    if (acc and (1 shl (16 - 1)) != 0) {
        adjustedAcc = acc - (1 shl 16)
    }
    return adjustedAcc * 0.001
}



data class SensorData(
    val timeStamp: Long,
    val temperature: Float,
    val gsr: Int,
    val grnCount: Int,
    val grn2Count: Int,
    val irCount: Int,
    val redCount: Int,
    val accX: Double,
    val accY: Double,
    val accZ: Double,
    val hr: Int,
    val hrConfidence: Int,
    val rrInterBeatInterval: Int,
    val rrConfidence: Int,
    val spo2: Int,
    val spo2Confidence: Int,
    val spo2Estimated: Int,
    val spo2CalPercentage: Int,
    val spo2LowSignQualityFlag: Int,
    val spo2MotionFlag: Int,
    val spo2LowPiFlag: Int,
    val spo2UnreliableRFlag: Int,
    val spo2State: Int,
    val skinContactState: Int,
    val walkSteps: Int,
    val runSteps: Int,
    val calories: Int,
    val cadence: Int,
    val event: Int,
    val activityClass: Int,
    val updatedEda: Double,
    val updatedAcc: Double
) : Parcelable {
    constructor(parcel: Parcel) : this(
        parcel.readLong(),
        parcel.readFloat(),
        parcel.readInt(),
        parcel.readInt(),
        parcel.readInt(),
        parcel.readInt(),
        parcel.readInt(),
        parcel.readDouble(),
        parcel.readDouble(),
        parcel.readDouble(),
        parcel.readInt(),
        parcel.readInt(),
        parcel.readInt(),
        parcel.readInt(),
        parcel.readInt(),
        parcel.readInt(),
        parcel.readInt(),
        parcel.readInt(),
        parcel.readInt(),
        parcel.readInt(),
        parcel.readInt(),
        parcel.readInt(),
        parcel.readInt(),
        parcel.readInt(),
        parcel.readInt(),
        parcel.readInt(),
        parcel.readInt(),
        parcel.readInt(),
        parcel.readInt(),
        parcel.readInt(),
        parcel.readDouble(),
        parcel.readDouble()  // Fix: Added the missing updatedAcc field here
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeLong(timeStamp)
        parcel.writeFloat(temperature)
        parcel.writeInt(gsr)
        parcel.writeInt(grnCount)
        parcel.writeInt(grn2Count)
        parcel.writeInt(irCount)
        parcel.writeInt(redCount)
        parcel.writeDouble(accX)
        parcel.writeDouble(accY)
        parcel.writeDouble(accZ)
        parcel.writeInt(hr)
        parcel.writeInt(hrConfidence)
        parcel.writeInt(rrInterBeatInterval)
        parcel.writeInt(rrConfidence)
        parcel.writeInt(spo2)
        parcel.writeInt(spo2Confidence)
        parcel.writeInt(spo2Estimated)
        parcel.writeInt(spo2CalPercentage)
        parcel.writeInt(spo2LowSignQualityFlag)
        parcel.writeInt(spo2MotionFlag)
        parcel.writeInt(spo2LowPiFlag)
        parcel.writeInt(spo2UnreliableRFlag)
        parcel.writeInt(spo2State)
        parcel.writeInt(skinContactState)
        parcel.writeInt(walkSteps)
        parcel.writeInt(runSteps)
        parcel.writeInt(calories)
        parcel.writeInt(cadence)
        parcel.writeInt(event)
        parcel.writeInt(activityClass)
        parcel.writeDouble(updatedEda)
        parcel.writeDouble(updatedAcc)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<SensorData> {
        override fun createFromParcel(parcel: Parcel): SensorData {
            return SensorData(parcel)
        }

        override fun newArray(size: Int): Array<SensorData?> {
            return arrayOfNulls(size)
        }
    }
}





object PermissionUtils {

    val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        listOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.BLUETOOTH_ADVERTISE,
        )
    } else {
        listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
        )
    }

}
