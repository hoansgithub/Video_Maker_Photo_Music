---
name: camera1-to-camerax
description: Migrate legacy Android camera implementations (Camera1 or raw Camera2 APIs) to CameraX. Use when modernizing camera code to lifecycle-aware CameraX with proper rotation handling.
allowed-tools: Read, Grep, Glob, Edit, Write, Bash(./gradlew:*)
hooks:
  pre_tool_use:
    - tool: Bash
      script: |
        # Ensure JAVA_HOME is set
        if [ -z "$JAVA_HOME" ]; then
          export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
        fi
---

<!-- Adapted from github.com/android/skills (2026-05) -->

# Camera1/Camera2 to CameraX Migration Skill

Migrate legacy camera code to CameraX — a lifecycle-aware Jetpack library built on Camera2.

## Step 0: Add Dependencies

Use CameraX 1.3.0+ for interop, or 1.5.0+ for Compose extensions.

### Version Catalog (`libs.versions.toml`)

```toml
[versions]
camerax = "1.5.0"

[libraries]
androidx-camera-core = { group = "androidx.camera", name = "camera-core", version.ref = "camerax" }
androidx-camera-camera2 = { group = "androidx.camera", name = "camera-camera2", version.ref = "camerax" }
androidx-camera-lifecycle = { group = "androidx.camera", name = "camera-lifecycle", version.ref = "camerax" }
# For Android Views
androidx-camera-view = { group = "androidx.camera", name = "camera-view", version.ref = "camerax" }
# For Jetpack Compose
androidx-camera-compose = { group = "androidx.camera", name = "camera-compose", version.ref = "camerax" }
```

### build.gradle.kts

```kotlin
// Core (always required)
implementation(libs.androidx.camera.core)
implementation(libs.androidx.camera.camera2)
implementation(libs.androidx.camera.lifecycle)

// Choose one:
implementation(libs.androidx.camera.view)     // Android Views
implementation(libs.androidx.camera.compose)  // Jetpack Compose
```

## Step 1: Remove Legacy Implementation

1. Delete all `android.hardware.Camera` instances
2. Delete `SurfaceView` and `SurfaceHolder.Callback` implementations
3. Remove custom lifecycle handling that opens/releases camera in `onResume`/`onPause`
4. Remove manual matrix calculations for orientation

## Step 2: Initialize ProcessCameraProvider

```kotlin
val context = LocalContext.current
val lifecycleOwner = LocalLifecycleOwner.current

LaunchedEffect(context, lifecycleOwner) {
    val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
    cameraProviderFuture.addListener({
        val cameraProvider = cameraProviderFuture.get()
        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_BACK)
            .build()
        val preview = Preview.Builder().build()
        val imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()

        cameraProvider.unbindAll()
        val camera = cameraProvider.bindToLifecycle(
            lifecycleOwner, cameraSelector, preview, imageCapture
        )
        val cameraControl = camera.cameraControl
    }, ContextCompat.getMainExecutor(context))
}
```

## Step 3: Preview & Tap-to-Focus

### Option A: Android Views (XML)

```kotlin
// Setup preview
preview.setSurfaceProvider(previewView.surfaceProvider)

// Tap-to-focus
val factory = previewView.meteringPointFactory
val point = factory.createPoint(x, y)
val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF).build()
cameraControl?.startFocusAndMetering(action)
```

### Option B: Jetpack Compose

```kotlin
// Setup preview + SurfaceRequest
var surfaceRequest by remember { mutableStateOf<SurfaceRequest?>(null) }
val preview = remember {
    Preview.Builder().build().apply {
        setSurfaceProvider { request -> surfaceRequest = request }
    }
}

// Render viewfinder
surfaceRequest?.let { request ->
    CameraXViewfinder(
        surfaceRequest = request,
        coordinateTransformer = coordinateTransformer,
        modifier = Modifier
    )
}

// Tap-to-focus in Compose
val surfaceCoords = with(coordinateTransformer) { offset.transform() }
val factory = SurfaceOrientedMeteringPointFactory(
    request.resolution.width.toFloat(),
    request.resolution.height.toFloat()
)
val point = factory.createPoint(surfaceCoords.x, surfaceCoords.y)
val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF).build()
cameraControl?.startFocusAndMetering(action)
```

Update target rotation for Compose:

```kotlin
LaunchedEffect(configuration) {
    if (!view.isInEditMode) {
        val rotation = view.display?.rotation ?: Surface.ROTATION_0
        imageCapture.targetRotation = rotation
        preview.targetRotation = rotation
    }
}
```

## Step 4: Capture Photo

```kotlin
imageCapture.takePicture(
    cameraExecutor,
    object : ImageCapture.OnImageCapturedCallback() {
        override fun onCaptureSuccess(image: ImageProxy) {
            val buffer = image.planes[0].buffer
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)

            val matrix = Matrix()
            matrix.postRotate(image.imageInfo.rotationDegrees.toFloat())
            if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
                matrix.postScale(-1f, 1f) // Mirror for front camera
            }
            val rotatedBitmap = Bitmap.createBitmap(
                bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
            )

            image.close() // MUST close ImageProxy
        }

        override fun onError(exception: ImageCaptureException) {
            Log.e("CameraX", "Capture failed: ${exception.message}", exception)
        }
    }
)
```

## Step 5: Switch Cameras

Toggle `lensFacing` and rebind:

```kotlin
lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
    CameraSelector.LENS_FACING_FRONT
} else {
    CameraSelector.LENS_FACING_BACK
}
// Re-trigger ProcessCameraProvider binding with new CameraSelector
```

## Critical Don'ts

- **Don't manage camera lifecycle manually** — bind to `LifecycleOwner` via `ProcessCameraProvider`
- **Don't calculate focus matrices manually** — `MeteringPointFactory` handles coordinate transforms
- **Don't forget to close ImageProxy** — `image.close()` is required or capture pipeline locks
- **Don't wrap `PreviewView` in `AndroidView` for Compose** — use `CameraXViewfinder` instead

## Verification

```bash
./gradlew assembleDebug
./gradlew test
```

- [ ] No `android.hardware.Camera` imports remain
- [ ] No manual `onResume`/`onPause` camera lifecycle code
- [ ] `image.close()` called in every capture callback
- [ ] Preview renders correctly on front and rear cameras
