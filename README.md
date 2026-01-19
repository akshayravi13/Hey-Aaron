# Hey Aaron: A Voice-Activated Music Announcer

<p align="center">
  <img src="./app/src/main/res/drawable/aaron_face.png" alt="Hey Aaron App Icon"/>
</p>

**"Hey Aaron"** is a unique Android application that listens for a wake word and tells you what song is currently playing on your device.

I built this app for a simple reason: walking through the cold winter days of Seattle, I hated taking my hands out of my pockets just to check what song was playing on YouTube Music. Hence, "Hey Aaron" was created.

The app's persona and voice are an homage to the character **Robert Robertson** from the game *Dispatch*, voiced by **Aaron Paul**. Just like the character manages emergency calls, this assistant manages your music—offering a hands-free way to identify tracks without interrupting your flow.

*(Please refer to the Legal Disclaimer at the end regarding the use of voice assets.)*

## Key Features ✨

- **Wake-Word Detection**: Listens for "Hey Aaron" to activate (powered by Picovoice).
- **Song Identification**: Automatically detects the current song title and artist from apps like YouTube Music.
- **Custom Voice Clone**: Features a custom AI voice model based on Aaron Paul's performance in *Dispatch*, powered by the **Fish Audio API**.
- **Smart Caching**: Caches generated MP3 responses locally to save data and reduce latency.
- **Fallback Support**: Automatically switches to Android's native Text-To-Speech (TTS) if offline.
- **Audio Ducking**: Smoothly lowers your music volume while speaking and fades it back in.
- **Stylish UI**: A modern, animated "3D" interface built with Jetpack Compose.

## Tech Stack 🛠️

- **Language**: Kotlin
- **UI**: Jetpack Compose (Material3)
- **Wake Word**: [Picovoice Porcupine](https://picovoice.ai/platform/porcupine/)
- **TTS Engine**: [Fish Audio API](https://fish.audio/)
- **Networking**: OkHttp
- **Architecture**: MVVM with Kotlin Coroutines

## Getting Started 🚀

### Prerequisites

- Android Studio Koala or later
- An Android device or emulator (API 26+)

### Setup

1.  **Clone the Repository**:
    ```bash
    git clone [https://github.com/your-username/hey-aaron.git](https://github.com/your-username/hey-aaron.git)
    ```

2.  **Add API Keys**:
    Create a file named `local.properties` in the root of your project. This file is ignored by Git to keep your secrets safe. Add your keys:

    ```properties
    PICOVOICE_ACCESS_KEY="YOUR_PICOVOICE_KEY"
    FISH_AUDIO_API_KEY="YOUR_FISH_AUDIO_KEY"
    FISH_AUDIO_VOICE_ID="YOUR_MODEL_ID"
    ```

3.  **Build the App**:
    Open the project in Android Studio, sync Gradle, and run on your device.

## How It Works 🤖

1.  **Start the Service**: Launch the app and tap **"Wake up Aaron"**.
2.  **Grant Permissions**: Grant Notification Access so the app can read the media player metadata.
3.  **Say "Hey Aaron"**: When music is playing, say the wake word. The app will lower the volume and announce the track.
4.  **Customization**: Use the toggle in the top-right to switch between the custom Aaron Paul voice and the standard Android robot voice.

## Disclaimer & Legal

This project is a **non-profit, educational portfolio project**.

- **Voice & Likeness**: The voice model and app icon are used solely for demonstration purposes as a fan tribute to Aaron Paul's performance in *Dispatch*. This project is **not affiliated with, endorsed by, or connected to** Aaron Paul, the creators of *Dispatch*, or the game's publishers.
- **Copyright**: All original game assets and audio recordings belong to their respective copyright holders.
- **Usage**: This code is provided under the MIT License for educational use. The custom voice model ID is not distributed with this repository.

## License 📝

This project is licensed under the MIT License. See the [LICENSE](LICENSE) file for details.