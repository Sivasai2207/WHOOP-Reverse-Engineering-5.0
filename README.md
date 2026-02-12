# WHOOP Reverse Engineering 5.0

**Successfully extracting raw health data from an unsubscribed WHOOP 5.0 device over BLE.**

## ✅ Extracted Metrics (No Subscription Required)

| Metric | Source | Method |
|--------|--------|--------|
| **Heart Rate (BPM)** | Standard BLE HR Service (0x2A37) | Direct read |
| **HRV (RMSSD)** | RR Intervals from Standard HR | Rolling buffer accumulation |
| **Strain (0-21)** | Derived from HR intensity | Real-time computation |
| **Recovery (0-100%)** | Derived from HRV | Real-time computation |
| **Stress (0-3)** | Inversely correlated with HRV | Real-time computation |
| **Respiratory Rate** | RR interval variability (RSA) | Real-time estimation |

## How It Works

This Android app connects to a WHOOP 5.0 via Bluetooth Low Energy and extracts health data using the **Standard BLE Heart Rate Measurement** characteristic — which broadcasts even on unsubscribed devices.

### Key Technical Details

- **Packet Protocol**: Full implementation of WHOOP's proprietary packet format (`AA` SOF + Length + CRC8 + Payload + CRC32)
- **65+ Command Numbers** and **40+ Event Types** mapped from open-source research
- **RR Interval Accumulation**: Single-RR packets are accumulated in a rolling buffer (60 intervals) for accurate RMSSD calculation
- **Live Derived Metrics**: Strain, Recovery, and Stress computed in real-time from HR + HRV without needing proprietary historical data
- **Dual Device Support**: Works with both `6108xxxx` (older) and `fd4bxxxx` (WHOOP 5.0) BLE service UUIDs

### Protocol Research Sources

- [jogolden/whoomp](https://github.com/jogolden/whoomp) — Packet structure, command & event enums
- [bWanShiTong/reverse-engineering-whoop](https://github.com/bWanShiTong/reverse-engineering-whoop) — Historical data decoding, temperature parsing
- [bWanShiTong blog post](https://github.com/bWanShiTong/reverse-engineering-whoop-post) — Sync process, HR broadcast toggle

## Tech Stack

- **Language**: Kotlin
- **UI**: Jetpack Compose
- **BLE**: Android BluetoothGatt API
- **Min SDK**: Android 12 (API 31)

## Building

```bash
cd WhoopScanner
./gradlew installDebug
```

## Screenshots

Connect to your WHOOP → Dashboard shows real-time BPM, HRV, Strain, Recovery, Stress, and Respiratory Rate.

## Disclaimer

This project is for **educational and research purposes only**. It uses the standard BLE Heart Rate Service that the device broadcasts publicly. No encryption is bypassed and no subscription controls are circumvented.
