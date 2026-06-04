# ⚡ Blip

> Offline Bluetooth mesh messaging. No internet. No accounts. No servers.

![Blip Banner](docs/banner.png)

---

## What is Blip?

Blip is a fully offline peer-to-peer messaging app that uses **Bluetooth Low Energy (BLE)** to discover nearby users and exchange messages — no Wi-Fi, no mobile data, no accounts required.

Messages hop through other Blip devices automatically, extending range far beyond a single BLE connection (mesh networking).

---

## Features

| Feature | Detail |
|---|---|
| 💬 **Messaging** | One-to-one + group chat |
| 🔒 **Encryption** | ECDH key exchange + AES-256-GCM per message |
| 🌐 **Mesh** | Auto relay through nearby devices (TTL-based flood routing) |
| 🎙 **Voice messages** | Record & send chunked over BLE |
| 📁 **File sharing** | Images + files (chunked BLE transfer) |
| 📍 **Location** | Share your coordinates in a message |
| 🗺 **Mesh map** | Canvas-drawn node graph showing network topology |
| 👤 **Profile** | Username + avatar colour — no phone number or email |
| 🥷 **Stealth mode** | Stop advertising — receive only |
| 🔔 **Notifications** | Foreground service keeps mesh alive |

---

## Tech Stack

```
Language      Kotlin 2.0
UI            Jetpack Compose + Material 3
DI            Hilt
DB            Room (local, encrypted)
Preferences   DataStore
BLE           Android BluetoothLeScanner + BluetoothLeAdvertiser + GATT Server
Crypto        AndroidKeyStore ECDH + AES-GCM (javax.crypto)
Font          Sora (Google Fonts)
Build         Gradle 8.7 + Version Catalogs
CI/CD         GitHub Actions → signed Release APK
```

---

## Project Structure

```
app/src/main/
├── java/com/blip/app/
│   ├── data/
│   │   ├── ble/          BleManager, BlipMeshService (foreground)
│   │   ├── crypto/       BlipCrypto (ECDH + AES-GCM)
│   │   ├── mesh/         MeshRouter (relay, routing table)
│   │   ├── model/        BlipUser, BlipMessage, BlipPacket, …
│   │   └── storage/      BlipDatabase (Room), UserPreferences (DataStore)
│   ├── ui/
│   │   ├── components/   Avatar, MessageBubble, UserCard, TopBar, …
│   │   ├── screens/      Home, Chat, Conversations, MeshMap, Profile, Onboarding
│   │   └── theme/        Theme.kt (Sora font, blue gradient palette)
│   ├── viewmodel/        BlipViewModel
│   ├── AppModule.kt      Hilt DI
│   ├── BlipApplication.kt
│   └── MainActivity.kt   Navigation host + bottom nav
└── res/
    ├── drawable/         ic_launcher_background/foreground, ic_blip_notif
    ├── font/             sora_*.ttf (fetched by download_fonts.sh)
    ├── mipmap-*/         Adaptive launcher icons
    ├── values/           strings, colors, themes
    └── xml/              backup_rules, data_extraction_rules
```

---

## Building Locally

### Prerequisites

- Android Studio Hedgehog or newer
- JDK 17
- Android SDK 34
- A physical Android device with BLE (emulators do not support BLE advertising)

### 1 — Clone

```bash
git clone https://github.com/YOUR_USERNAME/blip.git
cd blip
```

### 2 — Download Sora fonts

```bash
chmod +x download_fonts.sh
./download_fonts.sh
```

### 3 — Open in Android Studio

File → Open → select the `blip` folder.

### 4 — Run

Plug in a BLE-capable Android 8+ device, select it in the device picker, and hit ▶ Run.

---

## Release Signing Setup (GitHub Actions)

### Step 1 — Generate your keystore (run once, locally)

```bash
chmod +x generate_keystore.sh
./generate_keystore.sh
```

The script will output **4 values** that you need to add as GitHub Secrets.

### Step 2 — Add secrets to GitHub

Go to your repo → **Settings → Secrets and variables → Actions → New repository secret**

| Secret name | Value |
|---|---|
| `KEYSTORE_BASE64` | Base64-encoded `.jks` file (output by script) |
| `KEYSTORE_PASSWORD` | Your keystore password |
| `KEY_ALIAS` | `blip` |
| `KEY_PASSWORD` | Your key password |

### Step 3 — Trigger a release

Push a version tag:

```bash
git tag v1.0.0
git push origin v1.0.0
```

GitHub Actions will:
1. Decode the keystore
2. Download fonts
3. Build a signed Release APK
4. Attach it to a GitHub Release automatically

---

## CI / CD Workflows

| Workflow | Trigger | Output |
|---|---|---|
| `release.yml` | Push to `main` or `v*` tag | Signed Release APK + GitHub Release |
| `pr_check.yml` | Pull Request | Lint report + Debug build + Unit tests |

---

## BLE Packet Format

```
[type:1B][senderId][0x00][recipientId][0x00][ttl:1B][timestamp:8B][payload]
```

| Field | Size | Notes |
|---|---|---|
| type | 1 byte | PacketType enum |
| senderId | variable | UUID string, null-terminated |
| recipientId | variable | UUID or "BROADCAST", null-terminated |
| ttl | 1 byte | Mesh hop limit (default 5) |
| timestamp | 8 bytes | Big-endian long |
| payload | variable | Encrypted bytes |

---

## Privacy

- **No servers.** All data stays on device.
- **No accounts.** Just a username.
- **No internet required.** Ever.
- Messages are encrypted end-to-end using keys stored in the Android Keystore.
- Stealth mode disables BLE advertising so your device is invisible to others.

---

## Requirements

- Android **8.0+** (API 26)
- Bluetooth LE hardware
- Permissions: `BLUETOOTH_SCAN`, `BLUETOOTH_ADVERTISE`, `BLUETOOTH_CONNECT`, `ACCESS_FINE_LOCATION`, `RECORD_AUDIO`

---

## License

MIT © Blip Contributors
