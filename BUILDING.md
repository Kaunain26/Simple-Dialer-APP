# Building Simple Dialer

To build the project locally you need to point Gradle at a valid Android SDK installation.

1. Install the Android SDK (for example via Android Studio or the command line tools).
2. Create a `local.properties` file in the project root with the following content:
   ```
   sdk.dir=/absolute/path/to/your/Android/sdk
   ```
   Replace the path with the location of your SDK.
3. Alternatively export the environment variable `ANDROID_HOME` (or `ANDROID_SDK_ROOT`) before running Gradle:
   ```bash
   export ANDROID_HOME=/absolute/path/to/your/Android/sdk
   ```
4. After configuring the SDK location, run `./gradlew assembleDebug` to build the app.

> **Note:** The build will fail with the message `SDK location not found` if Gradle cannot locate the SDK, which is expected in environments without the Android SDK installed.
