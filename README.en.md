# Heyu Agent Keyboard

An experimental Android keyboard with bring-your-own-key AI and on-device document tools. The phone talks directly to the official DeepSeek API using the user's own `deepseek-flash` account. File parsing, calculations, image transforms and exports run on Android. No Mac bridge, local network service or self-hosted server is required.

[中文说明](README.md) · [Build](docs/BUILD.md) · [Privacy](PRIVACY.md) · [Validation](docs/VALIDATION.md)

Install an APK from Releases or build it yourself. Enable **Heyu Agent** in Android keyboard settings, then open the app and enter your own DeepSeek API key. AI features require an internet connection and provider credits. Ordinary keyboard input and Pinyin decoding are local.

Features include draft polishing with five styles, opt-in replies from visible WhatsApp messages, task recognition with manual selection, and local PPTX/DOCX/PDF/XLSX/image/ICS exports. Review each plan before execution. Polishing never sends a message. File delivery requires explicit per-file selection and is a version-specific WhatsApp UI adapter, not an official messaging API.

Android 11+, arm64-v8a and x86_64. Voice input, creative image generation, OCR, arbitrary complex Office editing and broad messaging-app compatibility are not implemented. See the Chinese README for exact task and file limits. Local history is not cloud synchronization, and interrupted tasks are not a durable server queue.

Keys are entered on the device, encrypted with an Android Keystore key and excluded from app backups. No shared provider key is bundled. Never put keys or private chats into issues. Rooted or compromised devices remain outside this protection.

```sh
export ANDROID_HOME=/path/to/android-sdk
export ANDROID_NDK_HOME="$ANDROID_HOME/ndk/29.0.14206865"
bash build.sh
bash scripts/test-release.sh
```

Build dependencies are JDK17+, Python3, Android SDK36, Build Tools36.0.0 and NDK29.0.14206865. Maven artifacts are checksum pinned. The output is `output/heyu-agent-1.0.0.apk`. Local build keys are not committed. An APK compiled with a different signing certificate cannot update an installed APK in place.

Apache-2.0. AOSP PinyinIME, PdfBox-Android and other dependency attributions are listed in NOTICE. This project is not affiliated with WhatsApp, Google or DeepSeek.
