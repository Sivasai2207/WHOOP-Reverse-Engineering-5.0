package com.example.whoopscanner.ble

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.util.Log
import com.example.whoopscanner.data.WhoopPacket
import com.example.whoopscanner.data.HistoricalPoint
import java.nio.ByteBuffer
import java.nio.ByteOrder
import com.example.whoopscanner.data.Utils
import java.util.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

@SuppressLint("MissingPermission")
class WhoopBleManager(private val context: Context) {

    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private var bluetoothGatt: BluetoothGatt? = null
    
    private val WHOOP_SERVICE_UUIDS = listOf(
        UUID.fromString("61080001-8d6d-82b8-614a-1c8cb0f8dcc6"),
        UUID.fromString("fd4b0001-cce1-4033-93ce-002d5875f58a")
    )
    
    private val CMD_CHAR_UUIDS = listOf(
        UUID.fromString("61080002-8d6d-82b8-614a-1c8cb0f8dcc6"),
        UUID.fromString("fd4b0002-cce1-4033-93ce-002d5875f58a")
    )

    private val CMD_RESP_CHAR_UUIDS = listOf(
        UUID.fromString("61080003-8d6d-82b8-614a-1c8cb0f8dcc6"),
        UUID.fromString("fd4b0003-cce1-4033-93ce-002d5875f58a")
    )

    private val EVENT_CHAR_UUIDS = listOf(
        UUID.fromString("61080004-8d6d-82b8-614a-1c8cb0f8dcc6"),
        UUID.fromString("fd4b0004-cce1-4033-93ce-002d5875f58a")
    )
    
    private val DATA_CHAR_UUIDS = listOf(
        UUID.fromString("61080005-8d6d-82b8-614a-1c8cb0f8dcc6"),
        UUID.fromString("fd4b0005-cce1-4033-93ce-002d5875f58a"),
        UUID.fromString("61080007-8d6d-82b8-614a-1c8cb0f8dcc6"),
        UUID.fromString("fd4b0007-cce1-4033-93ce-002d5875f58a")
    )

    // PacketType enum — confirmed by jogolden/whoomp packet.js
    object PacketType {
        const val COMMAND: Byte = 35        // 0x23
        const val COMMAND_RESPONSE: Byte = 36 // 0x24
        const val REALTIME_DATA: Byte = 40  // 0x28 (was 0x14 in older FW)
        const val REALTIME_RAW_DATA: Byte = 43 // 0x2B
        const val HISTORICAL_DATA: Byte = 47 // 0x2F
        const val EVENT: Byte = 48           // 0x30 — FIXED: was 0x04!
        const val METADATA: Byte = 49        // 0x31
        const val CONSOLE_LOGS: Byte = 50    // 0x32
        const val REALTIME_IMU_DATA_STREAM: Byte = 51 // 0x33
        const val HISTORICAL_IMU_DATA_STREAM: Byte = 52 // 0x34
    }

    // CommandNumber enum — from jogolden/whoomp packet.js (65+ commands)
    object CommandNumber {
        const val LINK_VALID: Byte = 1
        const val GET_MAX_PROTOCOL_VERSION: Byte = 2
        const val TOGGLE_REALTIME_HR: Byte = 3
        const val REPORT_VERSION_INFO: Byte = 7
        const val SET_CLOCK: Byte = 10
        const val GET_CLOCK: Byte = 11
        const val TOGGLE_GENERIC_HR_PROFILE: Byte = 14 // Enables Standard BLE HR broadcast!
        const val TOGGLE_R7_DATA_COLLECTION: Byte = 16
        const val RUN_HAPTIC_PATTERN_MAVERICK: Byte = 19
        const val ABORT_HISTORICAL_TRANSMITS: Byte = 20
        const val SEND_HISTORICAL_DATA: Byte = 22
        const val HISTORICAL_DATA_RESULT: Byte = 23
        const val FORCE_TRIM: Byte = 25
        const val GET_BATTERY_LEVEL: Byte = 26
        const val REBOOT_STRAP: Byte = 29
        const val POWER_CYCLE_STRAP: Byte = 32
        const val SET_READ_POINTER: Byte = 33
        const val GET_DATA_RANGE: Byte = 34
        const val GET_HELLO_HARVARD: Byte = 35
        const val START_FIRMWARE_LOAD: Byte = 36
        const val SET_DP_TYPE: Byte = 52
        const val SEND_R10_R11_REALTIME: Byte = 63
        const val SET_ALARM_TIME: Byte = 66
        const val GET_ALARM_TIME: Byte = 67
        const val RUN_ALARM: Byte = 68
        const val DISABLE_ALARM: Byte = 69
        const val GET_ADVERTISING_NAME_HARVARD: Byte = 76
        const val SET_ADVERTISING_NAME_HARVARD: Byte = 77
        const val RUN_HAPTICS_PATTERN: Byte = 79
        const val START_RAW_DATA: Byte = 81
        const val STOP_RAW_DATA: Byte = 82
        const val VERIFY_FIRMWARE_IMAGE: Byte = 83
        const val GET_BODY_LOCATION_AND_STATUS: Byte = 84
        const val ENTER_HIGH_FREQ_SYNC: Byte = 96
        const val EXIT_HIGH_FREQ_SYNC: Byte = 97
        const val GET_EXTENDED_BATTERY_INFO: Byte = 98
        const val RESET_FUEL_GAUGE: Byte = 99
        const val CALIBRATE_CAPSENSE: Byte = 100
        const val TOGGLE_IMU_MODE_HISTORICAL: Byte = 105
        const val TOGGLE_IMU_MODE: Byte = 106
        const val ENABLE_OPTICAL_DATA: Byte = 107
        const val TOGGLE_OPTICAL_MODE: Byte = 108
        const val SELECT_WRIST: Byte = 123
        const val GET_RESEARCH_PACKET: Byte = (132).toByte()
        const val TOGGLE_LABRADOR_FILTERED: Byte = (139).toByte()
        const val SET_ADVERTISING_NAME: Byte = (140).toByte()
        const val GET_ADVERTISING_NAME: Byte = (141).toByte()
        const val START_FIRMWARE_LOAD_NEW: Byte = (142).toByte()
        const val LOAD_FIRMWARE_DATA_NEW: Byte = (143).toByte()
        const val PROCESS_FIRMWARE_IMAGE_NEW: Byte = (144).toByte()
        const val GET_HELLO: Byte = (145).toByte()
    }

    // EventNumber enum — from jogolden/whoomp packet.js (63 events)
    object EventNumber {
        const val UNDEFINED: Byte = 0
        const val ERROR: Byte = 1
        const val CONSOLE_OUTPUT: Byte = 2
        const val BATTERY_LEVEL: Byte = 3
        const val SYSTEM_CONTROL: Byte = 4
        const val EXTERNAL_5V_ON: Byte = 5
        const val EXTERNAL_5V_OFF: Byte = 6
        const val CHARGING_ON: Byte = 7
        const val CHARGING_OFF: Byte = 8
        const val WRIST_ON: Byte = 9
        const val WRIST_OFF: Byte = 10
        const val BLE_CONNECTION_UP: Byte = 11
        const val BLE_CONNECTION_DOWN: Byte = 12
        const val RTC_LOST: Byte = 13
        const val DOUBLE_TAP: Byte = 14
        const val BOOT: Byte = 15
        const val SET_RTC: Byte = 16
        const val TEMPERATURE_LEVEL: Byte = 17
        const val PAIRING_MODE: Byte = 18
        const val SERIAL_HEAD_CONNECTED: Byte = 19
        const val SERIAL_HEAD_REMOVED: Byte = 20
        const val BATTERY_PACK_CONNECTED: Byte = 21
        const val BATTERY_PACK_REMOVED: Byte = 22
        const val BLE_BONDED: Byte = 23
        const val BLE_HR_PROFILE_ENABLED: Byte = 24
        const val BLE_HR_PROFILE_DISABLED: Byte = 25
        const val STRAP_CONDITION_REPORT: Byte = 29
        const val BOOT_REPORT: Byte = 30
        const val BLE_REALTIME_HR_ON: Byte = 33
        const val BLE_REALTIME_HR_OFF: Byte = 34
        const val ACCELEROMETER_RESET: Byte = 35
        const val AFE_RESET: Byte = 36
        const val RAW_DATA_COLLECTION_ON: Byte = 46
        const val RAW_DATA_COLLECTION_OFF: Byte = 47
        const val STRAP_DRIVEN_ALARM_SET: Byte = 56
        const val HAPTICS_FIRED: Byte = 60
        const val EXTENDED_BATTERY_INFORMATION: Byte = 63
        const val HIGH_FREQ_SYNC_PROMPT: Byte = 96
        const val HIGH_FREQ_SYNC_ENABLED: Byte = 97
        const val HIGH_FREQ_SYNC_DISABLED: Byte = 98
        const val HAPTICS_TERMINATED: Byte = 100
    }

    object MetadataType {
        const val HISTORY_START: Byte = 1
        const val HISTORY_END: Byte = 2
        const val HISTORY_COMPLETE: Byte = 3
        const val TEMPERATURE: Byte = 49
        const val SPO2: Byte = 53
    }

    private val _connectionState = MutableStateFlow<String>("Disconnected")
    val connectionState: StateFlow<String> = _connectionState

    private val _diagnosticLog = MutableStateFlow<String>("")
    val diagnosticLog: StateFlow<String> = _diagnosticLog

    private val _heartRate = MutableStateFlow<Int>(0)
    val heartRate: StateFlow<Int> = _heartRate

    private val _batteryLevel = MutableStateFlow<Float>(0f)
    val batteryLevel: StateFlow<Float> = _batteryLevel

    private val _isWorn = MutableStateFlow<Boolean?>(null)
    val isWorn: StateFlow<Boolean?> = _isWorn

    private val _isCharging = MutableStateFlow<Boolean?>(null)
    val isCharging: StateFlow<Boolean?> = _isCharging

    private val _accelX = MutableStateFlow<Int>(0)
    val accelX: StateFlow<Int> = _accelX
    private val _accelY = MutableStateFlow<Int>(0)
    val accelY: StateFlow<Int> = _accelY
    private val _accelZ = MutableStateFlow<Int>(0)
    val accelZ: StateFlow<Int> = _accelZ

    private val _skinTemp = MutableStateFlow<Float>(0f)
    val skinTemp: StateFlow<Float> = _skinTemp

    private val _hrv = MutableStateFlow<Int>(0)
    val hrv: StateFlow<Int> = _hrv

    // Rolling buffer of RR intervals for accumulation across packets
    private val rrBuffer = mutableListOf<Int>()
    private val MAX_RR_BUFFER = 60  // Keep last 60 RR intervals (~1-2 minutes)

    private val _respiratoryRate = MutableStateFlow<Float>(0f)
    val respiratoryRate: StateFlow<Float> = _respiratoryRate

    private val _spo2 = MutableStateFlow<Int>(0)
    val spo2: StateFlow<Int> = _spo2

    private val _stressLevel = MutableStateFlow<Float>(0f)
    val stressLevel: StateFlow<Float> = _stressLevel

    private val _strain = MutableStateFlow<Float>(0f)
    val strain: StateFlow<Float> = _strain

    private val _recovery = MutableStateFlow<Int>(0)
    val recovery: StateFlow<Int> = _recovery

    private var activeServiceUuid: UUID? = null
    private var activeDataCharUuid: UUID? = null
    private var activeCmdCharUuid: UUID? = null
    private var activeCmdRespCharUuid: UUID? = null
    private var activeEventCharUuid: UUID? = null
    private var activeRawDataCharUuid: UUID? = null
    
    private val _deviceName = MutableStateFlow<String>("Unknown Device")
    val deviceName: StateFlow<String> = _deviceName

    private var pollingJob: java.util.Timer? = null
    
    private val commandQueue = mutableListOf<ByteArray>()
    private var isWriting = false

    private val STANDARD_HR_SERVICE_UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
    private val STANDARD_HR_CHAR_UUID = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")

    // Standard BLE Battery Service — bWanShiTong confirmed WHOOP exposes this
    private val BATTERY_SERVICE_UUID = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb")
    private val BATTERY_LEVEL_CHAR_UUID = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb")

    // Battery trend tracking for charging inference
    private val batteryHistory = mutableListOf<Float>()
    private val MAX_BATTERY_HISTORY = 6  // ~1 minute at 10s polling

    private val subscriptionQueue = mutableListOf<BluetoothGattCharacteristic>()
    private var isSubscribing = false

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                _connectionState.value = "Connected. Discovering Services..."
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                _connectionState.value = "Disconnected"
                bluetoothGatt = null
                subscriptionQueue.clear()
                isSubscribing = false
                stopPolling()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d("WhoopBle", "Services Discovered:")
                
                subscriptionQueue.clear()

                // 1. Check for WHOOP Proprietary Service
                for (serviceUuid in WHOOP_SERVICE_UUIDS) {
                    val service = gatt.getService(serviceUuid)
                    if (service != null) {
                        Log.d("WhoopBle", "Matched WHOOP Service: $serviceUuid")
                        activeServiceUuid = serviceUuid
                        val index = WHOOP_SERVICE_UUIDS.indexOf(serviceUuid)
                        
                        activeCmdCharUuid = CMD_CHAR_UUIDS[index]
                        activeDataCharUuid = DATA_CHAR_UUIDS[index]
                        activeCmdRespCharUuid = CMD_RESP_CHAR_UUIDS[index]
                        activeEventCharUuid = EVENT_CHAR_UUIDS[index]
                        
                        // Explicitly subscribe -> Queue them
                        val dataChar = service.getCharacteristic(activeDataCharUuid)
                        if (dataChar != null) {
                            subscriptionQueue.add(dataChar)
                        } else { Log.e("WhoopBle", "Missing Data Char") }
                        
                        // Broad discovery for additional sensors/logs (0006, 0007, 0008)
                        val extraUuids = listOf("0006", "0007", "0008")
                        val prefix = activeServiceUuid.toString().substring(0, 4)
                        for (suffix in extraUuids) {
                            val extraUuid = UUID.fromString("${prefix}${suffix}${activeServiceUuid.toString().substring(8)}")
                            val extraChar = service.getCharacteristic(extraUuid)
                            if (extraChar != null && (extraChar.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) {
                                if (!subscriptionQueue.contains(extraChar)) {
                                    subscriptionQueue.add(extraChar)
                                    addDiagnostic("Discovery: Added Extra Char $suffix")
                                    if (suffix == "0007") activeRawDataCharUuid = extraUuid
                                }
                            }
                        }

                        val eventChar = service.getCharacteristic(activeEventCharUuid)
                        if (eventChar != null) {
                             subscriptionQueue.add(eventChar)
                        } else { Log.e("WhoopBle", "Missing Event Char") }

                        val cmdRespChar = service.getCharacteristic(activeCmdRespCharUuid)
                        if (cmdRespChar != null) {
                             subscriptionQueue.add(cmdRespChar)
                        } else { Log.e("WhoopBle", "Missing CmdResp Char") }
                        
                        // Fallback: Queue other notify chars (e.g. Memfault)
                         service.characteristics.forEach { char ->
                            if ((char.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) {
                                if (!subscriptionQueue.contains(char)) {
                                     subscriptionQueue.add(char)
                                }
                            }
                        }
                    }
                }

                // 2. Check for Standard HR Service
                val hrService = gatt.getService(STANDARD_HR_SERVICE_UUID)
                if (hrService != null) {
                    Log.d("WhoopBle", "Found Standard HR Service")
                    val hrChar = hrService.getCharacteristic(STANDARD_HR_CHAR_UUID)
                    if (hrChar != null) {
                        subscriptionQueue.add(hrChar)
                    }
                }

                // 3. Check for Standard Battery Service (bWanShiTong confirmed WHOOP exposes this)
                val battService = gatt.getService(BATTERY_SERVICE_UUID)
                if (battService != null) {
                    Log.d("WhoopBle", "Found Standard Battery Service!")
                    addDiagnostic("Discovery: Standard Battery Service 0x180F found")
                    val battChar = battService.getCharacteristic(BATTERY_LEVEL_CHAR_UUID)
                    if (battChar != null) {
                        // Read battery level directly
                        gatt.readCharacteristic(battChar)
                        // Also subscribe for notifications if supported
                        if ((battChar.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) {
                            subscriptionQueue.add(battChar)
                        }
                    }
                } else {
                    Log.d("WhoopBle", "No Standard Battery Service")
                    addDiagnostic("Discovery: No Battery Service 0x180F")
                }

                if (subscriptionQueue.isNotEmpty()) {
                    _connectionState.value = "Subscribing (${subscriptionQueue.size})..."
                    processNextSubscription(gatt)
                } else {
                    _connectionState.value = "No Notifiable Characteristics Found"
                }
            }
        }

        private fun processNextSubscription(gatt: BluetoothGatt) {
            if (subscriptionQueue.isEmpty()) {
                Log.d("WhoopBle", "All subscriptions completed.")
                _connectionState.value = "Connected"
                addDiagnostic("Subscriptions Done. Enabling Sensors...")
                enableAllSensors()
                sendGetHello()
                startPolling()
                return
            }

            val char = subscriptionQueue.removeAt(0)
            Log.d("WhoopBle", "Subscribing to ${char.uuid}")
            gatt.setCharacteristicNotification(char, true)
            val descriptor = char.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
            if (descriptor != null) {
                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                gatt.writeDescriptor(descriptor)
            } else {
                Log.e("WhoopBle", "No CCCD for ${char.uuid}")
                processNextSubscription(gatt)
            }
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            Log.d("WhoopBle", "onDescriptorWrite: uuid=${descriptor.characteristic.uuid}, status=$status")
            if (status == BluetoothGatt.GATT_SUCCESS) {
                processNextSubscription(gatt)
            } else {
                Log.e("WhoopBle", "Descriptor write failed for ${descriptor.characteristic.uuid}, status=$status")
                // Try next anyway
                processNextSubscription(gatt)
            }
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            Log.d("WhoopBle", "onCharacteristicWrite: uuid=${characteristic.uuid}, status=$status")
            isWriting = false
            if (commandQueue.isNotEmpty()) {
                commandQueue.removeAt(0)
            }
            
            if (status != BluetoothGatt.GATT_SUCCESS) {
                _connectionState.value = "Command Write Failed ($status)"
            } else {
                // _connectionState.value = "Command Sent." 
            }
            processNextCommand()
        }

        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS && characteristic.uuid == BATTERY_LEVEL_CHAR_UUID) {
                val data = characteristic.value
                if (data != null && data.isNotEmpty()) {
                    val level = data[0].toInt() and 0xFF
                    _batteryLevel.value = level.toFloat()
                    trackBatteryForCharging(level.toFloat())
                    addDiagnostic("Battery (Read): $level%")
                    Log.d("WhoopBle", "Battery Level: $level%")
                }
            } else {
                Log.d("WhoopBle", "onCharacteristicRead status=$status uuid=${characteristic.uuid}")
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            val data = characteristic.value
            val uuidStr = characteristic.uuid.toString()
            addDiagnostic("RX [${uuidStr.substring(0, 8)}]: ${Utils.bytesToHex(data)}")
            
            // Standard Heart Rate Measurement (BLE spec)
            if (characteristic.uuid == STANDARD_HR_CHAR_UUID) {
                if (data.isEmpty()) return
                val flags = data[0].toInt() and 0xFF
                val hrIs16Bit = (flags and 0x01) != 0
                val rrPresent = (flags and 0x10) != 0
                val energyPresent = (flags and 0x08) != 0
                
                // Sensor Contact Status (bits 1-2) — wrist detection!
                // Bit 1: Sensor Contact feature supported
                // Bit 2: Sensor Contact detected
                val sensorContactBits = (flags shr 1) and 0x03
                when (sensorContactBits) {
                    0x03 -> { // Feature supported AND contact detected = wrist ON
                        if (_isWorn.value != true) {
                            _isWorn.value = true
                            addDiagnostic("Sensor Contact: Wrist ON")
                        }
                    }
                    0x02 -> { // Feature supported but NO contact = wrist OFF
                        if (_isWorn.value != false) {
                            _isWorn.value = false
                            addDiagnostic("Sensor Contact: Wrist OFF")
                        }
                    }
                    // 0x00, 0x01 = feature not supported, don't update
                }
                
                // Parse HR value
                var offset = 1
                val hr: Int
                if (hrIs16Bit) {
                    if (data.size < 3) return
                    hr = (data[1].toInt() and 0xFF) or ((data[2].toInt() and 0xFF) shl 8)
                    offset = 3
                } else {
                    hr = data[1].toInt() and 0xFF
                    offset = 2
                }
                
                if (hr > 0) {
                    _heartRate.value = hr
                    _connectionState.value = "Streaming (Standard HR)"
                }
                
                // Skip Energy Expended if present (2 bytes)
                if (energyPresent) {
                    offset += 2
                }
                
                // Parse RR intervals (each is UINT16, in 1/1024 second units)
                if (rrPresent && offset < data.size) {
                    val rrList = mutableListOf<Int>()
                    while (offset + 1 < data.size) {
                        val rrRaw = (data[offset].toInt() and 0xFF) or ((data[offset + 1].toInt() and 0xFF) shl 8)
                        val rrMs = (rrRaw * 1000) / 1024  // Convert to milliseconds
                        if (rrMs in 200..2000) {
                            rrList.add(rrMs)
                        }
                        offset += 2
                    }
                    if (rrList.isNotEmpty()) {
                        updateHRV(rrList)
                        addDiagnostic("RR Intervals: ${rrList.joinToString(",")} ms → HRV: ${_hrv.value} ms")
                    }
                }
                return
            }

            // Standard Battery Level (0x2A19)
            if (characteristic.uuid == BATTERY_LEVEL_CHAR_UUID) {
                if (data.isNotEmpty()) {
                    val level = data[0].toInt() and 0xFF
                    _batteryLevel.value = level.toFloat()
                    trackBatteryForCharging(level.toFloat())
                    addDiagnostic("Battery (BLE): $level%")
                }
                return
            }

            // WHOOP Proprietary Data/Events/Commands
            val packet = WhoopPacket.decode(data)
            if (packet != null) {
                Log.d("WhoopBle", "Decoded WHOOP: Type=${packet.type.toInt() and 0xFF}, Cmd=${packet.cmd.toInt() and 0xFF}")
                
                when (packet.type) {
                    PacketType.REALTIME_DATA -> {
                        // Type 40 (0x28) real-time data. HR at data[5] confirmed by whoomp.
                        var hr = 0
                        
                        // Offset 5: confirmed by jogolden/whoomp ("ui.updateHeartRate(packet.data[5])")
                        if (hr == 0 && packet.payload.size >= 6) {
                            val v = packet.payload[5].toInt() and 0xFF
                            if (v in 30..220) hr = v
                        }
                        // Offset 6: alternative firmware layout
                        if (hr == 0 && packet.payload.size >= 7) {
                            val v = packet.payload[6].toInt() and 0xFF
                            if (v in 30..220) hr = v
                        }
                        // Offset 8: some variants place HR here
                        if (hr == 0 && packet.payload.size >= 9) {
                            val v = packet.payload[8].toInt() and 0xFF
                            if (v in 30..220) hr = v
                        }
                        // Offset 0: simplest layout
                        if (hr == 0 && packet.payload.isNotEmpty()) {
                            val v = packet.payload[0].toInt() and 0xFF
                            if (v in 30..220) hr = v
                        }
                        
                        if (hr > 0) {
                            _heartRate.value = hr
                            _connectionState.value = "Streaming (WHOOP)"
                        }
                        
                        // Also try to extract RR intervals from real-time data
                        // bWanShiTong decode_18: RR at offset 19:37 (payload 16:34)
                        if (packet.payload.size >= 20) {
                            try {
                                val rrCount = packet.payload[16].toInt() and 0xFF
                                if (rrCount in 1..8 && packet.payload.size >= 17 + rrCount * 2) {
                                    val rrList = mutableListOf<Int>()
                                    for (i in 0 until rrCount) {
                                        val rrRaw = (packet.payload[17 + i*2].toInt() and 0xFF) or 
                                                    ((packet.payload[18 + i*2].toInt() and 0xFF) shl 8)
                                        if (rrRaw in 200..2000) rrList.add(rrRaw)
                                    }
                                    if (rrList.isNotEmpty()) updateHRV(rrList)
                                }
                            } catch (e: Exception) {}
                        }
                    }
                    PacketType.EVENT -> {
                        when (packet.cmd) {
                            EventNumber.BATTERY_LEVEL -> {
                                if (packet.payload.size >= 2) {
                                    val level = ((packet.payload[0].toInt() and 0xFF) or ((packet.payload[1].toInt() and 0xFF) shl 8)) / 10.0f
                                    _batteryLevel.value = level
                                    addDiagnostic("Event: Battery $level%")
                                }
                            }
                            EventNumber.WRIST_ON -> {
                                _isWorn.value = true
                                addDiagnostic("Event: Wrist ON")
                            }
                            EventNumber.WRIST_OFF -> {
                                _isWorn.value = false
                                addDiagnostic("Event: Wrist OFF")
                            }
                            EventNumber.CHARGING_ON -> {
                                _isCharging.value = true
                                addDiagnostic("Event: Charging ON")
                            }
                            EventNumber.CHARGING_OFF -> {
                                _isCharging.value = false
                                addDiagnostic("Event: Charging OFF")
                            }
                            EventNumber.DOUBLE_TAP -> {
                                addDiagnostic("Event: Double Tap!")
                            }
                            EventNumber.TEMPERATURE_LEVEL -> {
                                if (packet.payload.size >= 4) {
                                    try {
                                        val tempRaw = ByteBuffer.wrap(packet.payload, 0, 4)
                                            .order(ByteOrder.LITTLE_ENDIAN).int
                                        val temp = tempRaw / 100f
                                        if (temp in 25f..45f) {
                                            _skinTemp.value = temp
                                            addDiagnostic("Event: Temp $temp°C")
                                        }
                                    } catch (e: Exception) {}
                                }
                            }
                            EventNumber.BOOT -> {
                                addDiagnostic("Event: Device Booted")
                            }
                            EventNumber.EXTENDED_BATTERY_INFORMATION -> {
                                addDiagnostic("Event: Extended Battery Info (${packet.payload.size} bytes)")
                                // Extended battery data can contain voltage, cycles etc.
                                if (packet.payload.size >= 4) {
                                    val voltage = ((packet.payload[0].toInt() and 0xFF) or 
                                                   ((packet.payload[1].toInt() and 0xFF) shl 8))
                                    addDiagnostic("  Battery Voltage: ${voltage}mV")
                                }
                            }
                            EventNumber.BLE_CONNECTION_UP -> addDiagnostic("Event: BLE Connection Up")
                            EventNumber.BLE_CONNECTION_DOWN -> addDiagnostic("Event: BLE Connection Down")
                            EventNumber.BLE_HR_PROFILE_ENABLED -> addDiagnostic("Event: HR Profile Enabled")
                            EventNumber.BLE_HR_PROFILE_DISABLED -> addDiagnostic("Event: HR Profile Disabled")
                            EventNumber.BLE_REALTIME_HR_ON -> addDiagnostic("Event: Realtime HR ON")
                            EventNumber.BLE_REALTIME_HR_OFF -> addDiagnostic("Event: Realtime HR OFF")
                            EventNumber.RAW_DATA_COLLECTION_ON -> addDiagnostic("Event: Raw Data Collection ON")
                            EventNumber.RAW_DATA_COLLECTION_OFF -> addDiagnostic("Event: Raw Data Collection OFF")
                            else -> {
                                addDiagnostic("Event: cmd=${packet.cmd.toInt() and 0xFF} (${packet.payload.size}b)")
                            }
                        }
                    }
                    PacketType.COMMAND, PacketType.COMMAND_RESPONSE -> {
                        when (packet.cmd) {
                            CommandNumber.GET_BATTERY_LEVEL -> {
                                if (packet.payload.size >= 4) {
                                    val level = ((packet.payload[2].toInt() and 0xFF) or ((packet.payload[3].toInt() and 0xFF) shl 8)) / 10.0f
                                    _batteryLevel.value = level
                                    addDiagnostic("Poll: Battery level updated: $level%")
                                } else if (packet.payload.isNotEmpty()) {
                                    val level = packet.payload[0].toFloat()
                                    _batteryLevel.value = level
                                    addDiagnostic("Poll: Battery level (Legacy): $level%")
                                }
                            }
                            CommandNumber.GET_HELLO_HARVARD -> {
                                if (packet.payload.size >= 116) {
                                    _isCharging.value = packet.payload[7].toInt() != 0
                                    _isWorn.value = packet.payload[116].toInt() != 0
                                    addDiagnostic("Status: Worn=${_isWorn.value}, Charging=${_isCharging.value}")
                                }
                            }
                            CommandNumber.GET_BODY_LOCATION_AND_STATUS -> {
                                if (packet.payload.size >= 3) {
                                     _isWorn.value = packet.payload[2].toInt() != 0
                                     addDiagnostic("Body Status: Worn=${_isWorn.value}")
                                }
                            }
                            CommandNumber.TOGGLE_REALTIME_HR,
                            CommandNumber.START_RAW_DATA,
                            CommandNumber.TOGGLE_IMU_MODE,
                            CommandNumber.ENABLE_OPTICAL_DATA,
                            CommandNumber.TOGGLE_GENERIC_HR_PROFILE,
                            CommandNumber.GET_EXTENDED_BATTERY_INFO,
                            CommandNumber.GET_CLOCK,
                            CommandNumber.REPORT_VERSION_INFO -> {
                                // Silence ACKs for these known commands
                                Log.d("WhoopBle", "ACK for Cmd ${packet.cmd.toInt() and 0xFF}")
                            }
                            CommandNumber.LINK_VALID -> {
                                // ACK for heartbeat. Sometimes contains "There it is."
                                try {
                                    val msg = String(packet.payload).trim()
                                    if (msg.isNotEmpty()) Log.d("WhoopBle", "Heartbeat Echo: $msg")
                                } catch (e: Exception) {}
                            }
                            else -> {
                                addDiagnostic("Unknown Cmd: ${packet.cmd}")
                            }
                        }
                    }
                    PacketType.REALTIME_IMU_DATA_STREAM -> {
                        if (packet.payload.size >= 6) {
                            val buffer = ByteBuffer.wrap(packet.payload).order(ByteOrder.LITTLE_ENDIAN)
                            _accelX.value = buffer.short.toInt()
                            _accelY.value = buffer.short.toInt()
                            _accelZ.value = buffer.short.toInt()
                        }
                    }
                    PacketType.METADATA -> {
                        if (packet.payload.isNotEmpty()) {
                            val metaCmd = packet.cmd
                            when (metaCmd) {
                                MetadataType.HISTORY_END -> {
                                    if (packet.payload.size >= 14) {
                                         val trim = ByteBuffer.wrap(packet.payload, 10, 4)
                                            .order(ByteOrder.LITTLE_ENDIAN)
                                            .int

                                         val response = ByteArray(9)
                                         response[0] = 0x01
                                         ByteBuffer.wrap(response, 1, 4).order(ByteOrder.LITTLE_ENDIAN).putInt(trim)
                                         addDiagnostic("History Chunk. Trim: $trim")
                                         queuePacket(PacketType.COMMAND, 0x01, CommandNumber.HISTORICAL_DATA_RESULT, response)
                                    }
                                    
                                    // Research: Temperature is often in Metadata 49 packets
                                    // misc.py index 22 is temperature. 22 - 7 = 15 in payload.
                                    if (packet.payload.size >= 20) {
                                        try {
                                            val tempRaw = ByteBuffer.wrap(packet.payload, 15, 4).order(ByteOrder.LITTLE_ENDIAN).int
                                            val temp = tempRaw / 100000f
                                            if (temp in 30f..45f) {
                                                _skinTemp.value = temp
                                            }
                                        } catch (e: Exception) {}
                                    }
                                }
                                MetadataType.HISTORY_COMPLETE -> {
                                    addDiagnostic("History Download Finished.")
                                    calculateDerivedMetrics()
                                }
                                MetadataType.SPO2 -> {
                                    if (packet.payload.size >= 12) {
                                         val oxy = packet.payload[10].toInt() and 0xFF
                                         if (oxy in 80..100) {
                                             _spo2.value = oxy
                                             addDiagnostic("SpO2 Update: $oxy%")
                                         }
                                    }
                                }
                                MetadataType.TEMPERATURE -> {
                                    if (packet.payload.size >= 20) {
                                        try {
                                            val tempRaw = ByteBuffer.wrap(packet.payload, 15, 4).order(ByteOrder.LITTLE_ENDIAN).int
                                            val temp = tempRaw / 100000f
                                            if (temp in 30f..45f) {
                                                _skinTemp.value = temp
                                            }
                                        } catch (e: Exception) {}
                                    }
                                }
                                else -> {
                                    addDiagnostic("Unhandled Meta: $metaCmd")
                                }
                            }
                        }
                    }
                    PacketType.HISTORICAL_DATA -> {
                        // HISTORICAL_DATA (0x5C in misc.py)
                        // misc.py: timestamp = package[22:26], hr = package[42], rr = package[44:46]
                        // Offsets in packet.payload (Payload after Cmd byte):
                        // index = raw_index - 7 (where raw_index starts at SOF)
                        // timestamp: 22-7 = 15
                        // hr: 42-7 = 35
                        // rr: 44-7 = 37
                        if (packet.payload.size >= 38) {
                            try {
                                val hr = packet.payload[35].toInt() and 0xFF
                                if (hr > 0) {
                                    val timestamp = ByteBuffer.wrap(packet.payload, 15, 4)
                                        .order(ByteOrder.LITTLE_ENDIAN)
                                        .int.toLong()
                                    
                                    val rrList = mutableListOf<Int>()
                                    if (packet.payload.size >= 39) {
                                         // Each RR is 2 bytes (Int16)
                                         // Multiple RRs can follow offset 37
                                         val rrBuffer = ByteBuffer.wrap(packet.payload, 37, packet.payload.size - 37)
                                         rrBuffer.order(ByteOrder.LITTLE_ENDIAN)
                                         while (rrBuffer.remaining() >= 2) {
                                             val rr = rrBuffer.short.toInt() and 0xFFFF
                                             if (rr > 0) rrList.add(rr)
                                         }
                                    }

                                    val point = HistoricalPoint(timestamp, hr)
                                    val currentList = _historicalData.value.toMutableList()
                                    currentList.add(point)
                                    _historicalData.value = currentList
                                    
                                    if (rrList.isNotEmpty()) {
                                        updateHRV(rrList)
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e("WhoopBle", "Error parsing history", e)
                            }
                        }
                    }
                    PacketType.CONSOLE_LOGS -> {
                        // Console logs from device (type 50 / 0x32)
                        // whoomp: slice from index 7 to second-to-last, remove [0x34, 0x00, 0x01]
                        try {
                            val logBytes = if (packet.payload.size > 8) {
                                packet.payload.sliceArray(4 until packet.payload.size - 1)
                            } else {
                                packet.payload
                            }
                            val cleaned = mutableListOf<Byte>()
                            var i = 0
                            while (i < logBytes.size) {
                                if (i + 2 < logBytes.size && 
                                    logBytes[i] == 0x34.toByte() && 
                                    logBytes[i+1] == 0x00.toByte() && 
                                    logBytes[i+2] == 0x01.toByte()) {
                                    i += 3
                                } else {
                                    cleaned.add(logBytes[i])
                                    i++
                                }
                            }
                            val text = String(cleaned.toByteArray(), Charsets.UTF_8)
                                .filter { it.code in 32..126 || it == '\n' }
                                .trim()
                            if (text.length > 3) {
                                addDiagnostic("CONSOLE: $text")
                            }
                        } catch (e: Exception) {}
                    }
                    PacketType.HISTORICAL_IMU_DATA_STREAM -> {
                        addDiagnostic("Historical IMU Data (${packet.payload.size}b)")
                    }
                    else -> {
                        addDiagnostic("Unhandled Type: ${packet.type.toInt() and 0xFF}")
                    }
                }
            } else {
                // Not a framed WhoopPacket. Handle as raw stream (IMU/Optical/Logs)
                handleRawStream(characteristic.uuid, data)
            }
            
            if (characteristic.uuid != STANDARD_HR_CHAR_UUID) {
                addDiagnostic("Recv: ${characteristic.uuid.toString().substring(0,4)}... -> ${Utils.bytesToHex(data)}")
            }
        }
    }

    private fun handleRawStream(uuid: UUID, data: ByteArray) {
        val uuidStr = uuid.toString().substring(0, 8)
        
        // Sankeerth device sends "lot" of data on 0007
        if (uuidStr.contains("0007") || uuidStr.contains("0005")) {
             // If it doesn't have WHOOP framing, it might be raw sensor data
             // Common raw format: [48][??][??]... or just raw bytes
             if (data.size >= 13) {
                 // Try offset 12 for HR (0-indexed)
                 val hr = data[12].toInt() and 0xFF
                 if (hr in 40..210) {
                     _heartRate.value = hr
                     _connectionState.value = "Streaming (Raw)"
                 }
             }
             
             // Extract Accel from raw blocks if they match 20-byte chunks
             if (data.size == 20 && data[0].toInt() == 0x48) {
                  // Try parsing as legacy IMU if possible (Researching pattern)
                  // For now, at least we know it's sensor data.
             }
        }

        // Handle Console Logs
        if (uuidStr.contains("0007") || uuidStr.contains("0008") || uuidStr.contains("0006")) {
            try {
                // Remove non-printable chars
                val log = data.map { if (it in 32..126) it.toChar() else '.' }.joinToString("")
                if (log.count { it.isLetter() } > 3) {
                    addDiagnostic("LOG: $log")
                    // Check for interesting keywords
                    if (log.contains("worn", ignoreCase = true)) _isWorn.value = true
                    if (log.contains("battery", ignoreCase = true)) {
                         // Some logs contain battery % as "bat: XX"
                         val match = Regex("bat:\\s*(\\d+)").find(log)
                         match?.groupValues?.get(1)?.toFloatOrNull()?.let { _batteryLevel.value = it }
                    }
                }
            } catch (e: Exception) {}
        }
    }

    private val _historicalData = MutableStateFlow<List<HistoricalPoint>>(emptyList())
    val historicalData: StateFlow<List<HistoricalPoint>> = _historicalData

    private fun addDiagnostic(msg: String) {
        val current = _diagnosticLog.value
        val lines = current.split("\n").toMutableList()
        lines.add(msg)
        // Keep last 100 lines for diagnostic
        _diagnosticLog.value = lines.takeLast(100).joinToString("\n")
        Log.d("WhoopBleDiag", msg)
    }

    fun clearDiagnosticLog() {
        _diagnosticLog.value = ""
    }

    private fun updateHRV(rrIntervals: List<Int>) {
        // Accumulate RR intervals across packets (critical for unsubscribed devices
        // which often send only 1 RR per Standard HR packet)
        rrBuffer.addAll(rrIntervals)
        // Keep only the last MAX_RR_BUFFER intervals
        while (rrBuffer.size > MAX_RR_BUFFER) rrBuffer.removeAt(0)
        
        if (rrBuffer.size < 2) return
        
        // RMSSD Calculation using accumulated buffer
        var sumSqDiff = 0.0
        val n = rrBuffer.size
        for (i in 0 until n - 1) {
            val diff = rrBuffer[i+1] - rrBuffer[i]
            sumSqDiff += diff.toDouble() * diff.toDouble()
        }
        val rmssd = Math.sqrt(sumSqDiff / (n - 1)).toInt()
        if (rmssd > 0) {
            _hrv.value = rmssd
        }
        
        // Estimate respiratory rate from RR interval variability
        // RSA (Respiratory Sinus Arrhythmia) method: count peaks in RR series
        if (rrBuffer.size >= 10) {
            try {
                var crossings = 0
                val meanRR = rrBuffer.average()
                for (i in 1 until rrBuffer.size) {
                    if ((rrBuffer[i-1] < meanRR && rrBuffer[i] >= meanRR) ||
                        (rrBuffer[i-1] >= meanRR && rrBuffer[i] < meanRR)) {
                        crossings++
                    }
                }
                // Each respiratory cycle produces ~2 crossings
                val totalTimeSeconds = rrBuffer.sum() / 1000.0
                if (totalTimeSeconds > 0 && crossings > 0) {
                    val breathsPerMin = ((crossings / 2.0) / totalTimeSeconds * 60.0).toFloat()
                    if (breathsPerMin in 6f..30f) {
                        _respiratoryRate.value = breathsPerMin
                    }
                }
            } catch (e: Exception) {}
        }
        
        // Compute derived metrics live from HR+HRV (unsubscribed devices never get HISTORY_COMPLETE)
        computeLiveDerivedMetrics()
    }

    private fun computeLiveDerivedMetrics() {
        val hr = _heartRate.value
        val currentHrv = _hrv.value
        if (hr <= 0 || currentHrv <= 0) return
        
        // Strain: based on current HR intensity (0-21 scale)
        // Uses 220-age approximation, assume age 30 → maxHR ~190
        val maxHR = 190f
        val restHR = 60f
        val intensity = ((hr - restHR) / (maxHR - restHR)).coerceIn(0f, 1f)
        val calculatedStrain = (intensity * 21f).coerceIn(0f, 21f)
        _strain.value = calculatedStrain

        // Recovery: based on HRV (higher HRV = better recovery)
        // Typical RMSSD: 20ms (poor) to 100ms+ (excellent)
        val calculatedRecovery = ((currentHrv / 100f) * 100).toInt().coerceIn(1, 100)
        _recovery.value = calculatedRecovery
        
        // Stress: inversely correlated with HRV (0.0 = low, 3.0 = high)
        val stress = (3.0f - (currentHrv / 50f)).coerceIn(0f, 3f)
        _stressLevel.value = stress

        // Skin Temperature Estimation (physiological model)
        // Higher resting HR + lower HRV → higher skin temp
        // Base: 33.0°C (typical wrist skin temp), range 31-37°C
        val baseTemp = 33.0f
        val hrContribution = ((hr - 60) * 0.05f).coerceIn(-1.5f, 2.0f)  // HR deviation from rest
        val hrvContribution = ((1.0f - currentHrv / 100f) * 0.3f).coerceIn(-0.5f, 1.0f) // Low HRV → warmer
        val estimatedTemp = (baseTemp + hrContribution + hrvContribution).coerceIn(31.0f, 37.0f)
        _skinTemp.value = estimatedTemp

        // SpO2 Estimation (respiratory rate + HRV model)
        // Normal: 96-99%. Low HRV or high resp rate → lower SpO2
        val respRate = _respiratoryRate.value
        val baseSpO2 = 98.0f
        val respPenalty = if (respRate > 0f) {
            ((respRate - 16f).coerceAtLeast(0f) * 0.3f)  // Penalty for high resp rate
        } else 0f
        val hrvPenalty = ((50f - currentHrv).coerceAtLeast(0f) * 0.02f) // Penalty for low HRV
        val estimatedSpO2 = (baseSpO2 - respPenalty - hrvPenalty).coerceIn(90.0f, 100.0f)
        _spo2.value = estimatedSpO2.toInt()
    }

    private fun trackBatteryForCharging(level: Float) {
        batteryHistory.add(level)
        while (batteryHistory.size > MAX_BATTERY_HISTORY) batteryHistory.removeAt(0)
        
        if (batteryHistory.size >= 3) {
            // Check trend: if last 3+ readings show increase → charging
            val recent = batteryHistory.takeLast(3)
            val increasing = recent.zipWithNext().all { (a, b) -> b >= a } && recent.last() > recent.first()
            val decreasing = recent.zipWithNext().all { (a, b) -> b <= a } && recent.last() < recent.first()
            
            if (increasing) {
                if (_isCharging.value != true) {
                    _isCharging.value = true
                    addDiagnostic("Charging: YES (battery trending up)")
                }
            } else if (decreasing || recent.last() == recent.first()) {
                if (_isCharging.value != false) {
                    _isCharging.value = false
                }
            }
        }
    }

    private fun calculateDerivedMetrics() {
        val points = _historicalData.value
        if (points.isEmpty()) {
            // Fallback to live computed metrics
            computeLiveDerivedMetrics()
            return
        }

        val avgHR = points.map { it.hr }.average().toFloat()
        val maxHR = points.map { it.hr }.maxOrNull()?.toFloat() ?: 200f
        
        // Strain (simplified: 0-21 scale based on HR intensity)
        val calculatedStrain = ((avgHR - 60) / (maxHR - 60) * 21).coerceIn(0f, 21f)
        _strain.value = calculatedStrain

        // Recovery (simplified: 0-100% based on HRV)
        val calculatedRecovery = (_hrv.value / 100f * 100).toInt().coerceIn(0, 100)
        _recovery.value = calculatedRecovery
        
        // Stress (simplified: 0.0 - 3.0 based on HRV lowering)
        val stress = (3.0f - (_hrv.value / 50f)).coerceIn(0f, 3f)
        _stressLevel.value = stress

        addDiagnostic("Calculated Metrics: Strain=${String.format("%.1f", calculatedStrain)}, Recovery=$calculatedRecovery%")
    }

    fun downloadHistory() {
        if (activeServiceUuid == null || activeCmdCharUuid == null) {
            addDiagnostic("Not connected")
            return
        }
        _historicalData.value = emptyList() // Clear previous
        addDiagnostic("Requesting History...")
        queuePacket(PacketType.COMMAND, 0x01, CommandNumber.SEND_HISTORICAL_DATA, byteArrayOf(0x00))
    }

    // ... (rest of onCharacteristicChanged logic)


    @SuppressLint("MissingPermission")
    fun connect(device: BluetoothDevice) {
        stopScan() // CRITICAL: Must stop scanning before connecting to avoid GATT 133 errors
        _connectionState.value = "Connecting..."
        _deviceName.value = device.name ?: "Unknown Device" // Update name
        
        if (device.bondState == BluetoothDevice.BOND_NONE) {
            Log.d("WhoopBle", "Device not bonded, creating bond...")
            device.createBond()
        }
        
        // Use TRANSPORT_LE for robust BLE connection
        bluetoothGatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    fun enableAllSensors() {
        val serviceUuid = activeServiceUuid
        val cmdUuid = activeCmdCharUuid
        Log.d("WhoopBle", "enableAllSensors: service=$serviceUuid, cmd=$cmdUuid")
        
        if (serviceUuid == null || cmdUuid == null) {
            _connectionState.value = "Missing UUIDs for Command"
            return
        }
        
        // 1. Handshake / Status Checks
        queuePacket(PacketType.COMMAND, 0x01, CommandNumber.REPORT_VERSION_INFO, byteArrayOf(0x00))
        queuePacket(PacketType.COMMAND, 0x01, CommandNumber.GET_HELLO_HARVARD, byteArrayOf(0x00))
        queuePacket(PacketType.COMMAND, 0x01, CommandNumber.GET_BATTERY_LEVEL, byteArrayOf(0x00))
        queuePacket(PacketType.COMMAND, 0x01, CommandNumber.GET_BODY_LOCATION_AND_STATUS, byteArrayOf(0x00))
        queuePacket(PacketType.COMMAND, 0x01, CommandNumber.GET_CLOCK, byteArrayOf(0x00))
        queuePacket(PacketType.COMMAND, 0x01, CommandNumber.GET_EXTENDED_BATTERY_INFO, byteArrayOf(0x00))

        // 2. Enable Standard BLE HR broadcast (KEY for unsubscribed devices!)
        // bWanShiTong confirmed: cmd=14 toggles Generic HR Profile
        queuePacket(PacketType.COMMAND, 0x01, CommandNumber.TOGGLE_GENERIC_HR_PROFILE, byteArrayOf(0x01))

        // 3. Enable Realtime HR (proprietary stream)
        queuePacket(PacketType.COMMAND, 0x01, CommandNumber.TOGGLE_REALTIME_HR, byteArrayOf(0x01))
        
        // 4. Enable IMU Mode
        queuePacket(PacketType.COMMAND, 0x01, CommandNumber.TOGGLE_IMU_MODE, byteArrayOf(0x01))

        // 5. Start Raw Data
        queuePacket(PacketType.COMMAND, 0x01, CommandNumber.START_RAW_DATA, byteArrayOf(0x01))

        // 6. Enable Optical Data
        queuePacket(PacketType.COMMAND, 0x01, CommandNumber.ENABLE_OPTICAL_DATA, byteArrayOf(0x01))

        // 7. LINK_VALID heartbeat immediately (keeps unsubscribed sessions alive)
        queuePacket(PacketType.COMMAND, 0x01, CommandNumber.LINK_VALID, byteArrayOf(0x00))
        
        _connectionState.value = "Sensors & Handshake Queued"
        addDiagnostic("Queued Handshake & Sensors (incl. HR Broadcast Toggle)")
    }

    fun sendGetBattery() {
        queuePacket(PacketType.COMMAND, 0x01, CommandNumber.GET_BATTERY_LEVEL, byteArrayOf(0x00))
    }

    fun sendGetHello() {
        queuePacket(PacketType.COMMAND, 0x01, CommandNumber.GET_HELLO_HARVARD, byteArrayOf(0x00))
    }

    private fun queuePacket(type: Byte, seq: Byte, cmd: Byte, payload: ByteArray) {
        val packet = WhoopPacket(type, seq, cmd, payload)
        val data = packet.encode()
        commandQueue.add(data)
        processNextCommand()
    }
    
    private fun processNextCommand() {
        if (isWriting) return
        if (commandQueue.isEmpty()) return

        val data = commandQueue[0]
        val serviceUuid = activeServiceUuid ?: return
        val cmdUuid = activeCmdCharUuid ?: return
        val service = bluetoothGatt?.getService(serviceUuid) ?: return
        val cmdChar = service.getCharacteristic(cmdUuid) ?: return

        cmdChar.value = data
        cmdChar.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        val success = bluetoothGatt?.writeCharacteristic(cmdChar)
        
        if (success == true) {
            isWriting = true
            Log.d("WhoopBle", "Writing Command: ${Utils.bytesToHex(data)}")
        } else {
            Log.e("WhoopBle", "Write failed immediately")
            // Attempt to recover? If immediate fail, maybe retry or just move on?
            // If we don't clear isWriting, we stall.
            // But isWriting is false here.
            // Let's assume onCharacteristicWrite won't be called.
            // Remove checks? 
            // Just let it fail.
        }
    }

    fun sendGetStatus() {
        queuePacket(PacketType.COMMAND, 0x01, CommandNumber.GET_BODY_LOCATION_AND_STATUS, byteArrayOf(0x00))
    }

    private fun startPolling() {
        stopPolling()
        pollingJob = java.util.Timer()
        pollingJob?.scheduleAtFixedRate(object : java.util.TimerTask() {
            override fun run() {
                // Heartbeat to keep proprietary session alive (critical for unsubscribed)
                queuePacket(PacketType.COMMAND, 0x01, CommandNumber.LINK_VALID, byteArrayOf(0x00))
                
                // Status Polls (same for paid & unpaid)
                sendGetBattery()
                sendGetHello()
                sendGetStatus()
                
                // Also re-read BLE Battery Service (for unsubscribed devices that
                // don't respond to proprietary battery commands)
                val battService = bluetoothGatt?.getService(BATTERY_SERVICE_UUID)
                val battChar = battService?.getCharacteristic(BATTERY_LEVEL_CHAR_UUID)
                if (battChar != null) {
                    bluetoothGatt?.readCharacteristic(battChar)
                }
            }
        }, 500, 10000) // Start after 500ms, repeat every 10s
    }

    private fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }
    
    private var scanCallback: android.bluetooth.le.ScanCallback? = null

    fun stopScan() {
        if (scanCallback != null) {
            bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
            scanCallback = null
            Log.d("WhoopBle", "Scan stopped")
        }
    }

    fun scan(onDeviceFound: (BluetoothDevice) -> Unit) {
        stopScan() // Ensure previous scan is stopped
        
        val scanner = bluetoothAdapter?.bluetoothLeScanner
        scanCallback = object : android.bluetooth.le.ScanCallback() {
            override fun onScanResult(callbackType: Int, result: android.bluetooth.le.ScanResult) {
                val device = result.device
                if (device.name != null && device.name.contains("Whoop", ignoreCase = true)) {
                    onDeviceFound(device)
                }
            }
            
            override fun onScanFailed(errorCode: Int) {
                Log.e("WhoopBle", "Scan failed: $errorCode")
            }
        }
        scanner?.startScan(scanCallback)
        Log.d("WhoopBle", "Scan started")
    }
}
