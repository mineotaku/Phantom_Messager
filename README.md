<div align="center">

# 👻 Phantom Messenger

**End-to-End Encrypted Messaging for Android**

[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-purple?logo=kotlin)](https://kotlinlang.org/)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material3-blue?logo=jetpackcompose)](https://developer.android.com/compose)
[![License](https://img.shields.io/badge/License-MIT-green)](LICENSE)

*Private by design. No compromises.*

</div>

---

## Overview

Phantom is a privacy-first messaging app for Android that implements real end-to-end encryption using the **Signal Protocol** cryptographic stack. Messages are encrypted on-device before they ever leave your phone, and only the intended recipient can decrypt them.

**No one — not even the server — can read your messages.**

## ✨ Features

| Feature | Description |
|---------|-------------|
| 🔐 **X3DH Key Exchange** | Extended Triple Diffie-Hellman handshake for secure session establishment |
| 🔄 **Double Ratchet** | Forward secrecy & post-compromise security via symmetric + DH ratcheting |
| 🛡️ **AES-256-GCM AEAD** | Authenticated encryption with associated data for every message |
| 🗄️ **Encrypted Local DB** | SQLCipher-encrypted Room database — data at rest is always protected |
| 🔑 **Safety Numbers** | Signal-style 48-digit security code verification between contacts |
| 📱 **Modern UI** | Jetpack Compose Material 3 with dark-mode-first design |
| 📎 **Media Sharing** | Encrypted image, video, and audio attachments via Supabase Storage |
| ⚡ **Real-time Delivery** | Supabase Realtime channels with polling fallback |
| 🔄 **Recovery Keys** | 24-word mnemonic backup phrase for account recovery |

## 🏗️ Architecture

```
┌─────────────────────────────────────────────────┐
│                    UI Layer                      │
│  Jetpack Compose · Material 3 · Dark Theme      │
│  AuthScreen · ChatsListScreen · ChatDetailScreen │
│  SocialScreen · ProfileScreen · KeyVerification  │
├─────────────────────────────────────────────────┤
│                 ViewModel Layer                  │
│         PhantomViewModel (Hilt-injected)         │
├─────────────────────────────────────────────────┤
│               Repository Layer                   │
│     PhantomRepository (Single Source of Truth)    │
├──────────────────────┬──────────────────────────┤
│    Local Data        │      Remote Data          │
│  Room + SQLCipher    │   Supabase (Postgrest     │
│  DAOs & Entities     │   + Realtime + Storage)   │
├──────────────────────┴──────────────────────────┤
│              Crypto Layer                        │
│  X3DH · Double Ratchet · AEAD · HKDF · ECDSA    │
│  CryptoUtils · Safety Numbers · Recovery Keys    │
└─────────────────────────────────────────────────┘
```

## 🛠️ Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Kotlin 2.0 |
| UI | Jetpack Compose + Material 3 |
| DI | Hilt (Dagger) |
| Database | Room + SQLCipher |
| Backend | Supabase (Postgrest, Realtime, Storage, Auth) |
| Networking | Ktor Client, OkHttp, Retrofit |
| Serialization | Kotlinx Serialization, Moshi |
| Image Loading | Coil |
| Auth | Google Sign-In (Credential Manager) |
| Crypto | JCA (ECDH, ECDSA, AES-GCM, HKDF-SHA256) |
| Build | Gradle KTS with Version Catalog |

## 📋 Prerequisites

- [Android Studio](https://developer.android.com/studio) (Ladybug or later)
- Android SDK 24+ (target SDK 36)
- JDK 11+

## 🚀 Getting Started

1. **Clone the repository**
   ```bash
   git clone https://github.com/mineotaku/Phantom_Messager.git
   cd Phantom_Messager
   ```

2. **Configure credentials**
   
   Copy the example environment file and fill in your own keys:
   ```bash
   cp .env.example .env
   ```
   
   Edit `.env` with your:
   - Google Web Client ID (from [Google Cloud Console](https://console.cloud.google.com/))
   - Supabase URL and Anon Key (from [Supabase Dashboard](https://supabase.com/dashboard))

3. **Set up Firebase**
   
   Place your `google-services.json` in the `app/` directory.  
   Get it from the [Firebase Console](https://console.firebase.google.com/).

4. **Open in Android Studio**
   
   Open the project root directory. Allow Gradle to sync and resolve dependencies.

5. **Run the app**
   
   Select an emulator or physical device and click ▶️ Run.

> **Note:** For release builds, configure signing by setting `KEYSTORE_PATH`, `STORE_PASSWORD`, and `KEY_PASSWORD` environment variables.

## 🔐 Cryptographic Protocols

### X3DH (Extended Triple Diffie-Hellman)
- Establishes shared secrets between users who may be offline
- Uses Identity Keys, Signed Prekeys, and One-Time Prekeys
- Provides mutual authentication and forward secrecy

### Double Ratchet
- Combines symmetric-key ratchet with DH ratchet
- Every message uses a unique encryption key
- Provides forward secrecy and post-compromise security

### AEAD (AES-256-GCM)
- 256-bit keys with 128-bit authentication tags
- 12-byte random IVs per message
- Associated data binding prevents message reordering attacks

## 📁 Project Structure

```
phantom/
├── app/
│   ├── src/main/java/com/example/
│   │   ├── MainActivity.kt
│   │   ├── PhantomApplication.kt
│   │   └── phantom/
│   │       ├── crypto/          # X3DH, Double Ratchet, AEAD, CryptoUtils
│   │       ├── data/
│   │       │   ├── db/          # Room entities, DAOs, encrypted database
│   │       │   ├── network/     # Supabase manager, network models
│   │       │   └── repository/  # PhantomRepository (single source of truth)
│   │       ├── di/              # Hilt dependency injection modules
│   │       └── ui/
│   │           ├── PhantomViewModel.kt
│   │           └── screens/     # Compose UI screens
│   └── src/main/res/            # Android resources
├── gradle/
│   └── libs.versions.toml       # Version catalog
├── .env.example                 # Credential template
├── build.gradle.kts             # Root build config
└── settings.gradle.kts          # Project settings
```

## 📄 License

This project is licensed under the MIT License — see the [LICENSE](LICENSE) file for details.

---

<div align="center">

**Built with 🔒 by [mineotaku](https://github.com/mineotaku)**

</div>
