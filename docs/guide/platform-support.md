# Platform Support

SweetEditor targets multiple Compose Multiplatform platforms.

## Supported Targets

- Android
- iOS
- JVM Desktop
- JS
- Wasm

## Project Structure

- `commonMain`: shared APIs, editor models, protocol decoding, controller logic
- `androidMain`: Android platform bridges
- `iosMain`: iOS platform bridges
- `jvmMain`: Desktop/JVM platform bridges
- `webMain`: web-specific platform code

## Platform Notes

### Android

- IME and text input are integrated through Android platform hooks
- Compose UI hosts the editor and bridges gestures into the native editor model

### iOS

- iOS bridge code lives in the dedicated iOS source set
- Native lifecycle and rendering coordination are handled in the Kotlin wrapper layer

### Desktop

- Mouse, wheel, keyboard, popup, and inline suggestion interaction are available
- Desktop-specific wheel normalization is handled at the platform layer

### Web / Wasm

- Supported as target outputs in the wrapper layer
- Depending on runtime environment, some platform interactions may continue to evolve

## Recommendation

Keep shared editor logic in `commonMain` and isolate platform-specific behavior in the corresponding source set.
