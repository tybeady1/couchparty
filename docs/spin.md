# Experimental marked-ball spin

Open Settings > Experimental marked-ball spin, also available in the live-camera controls. Requires Android 9+. The app lists exposed rear-camera Camera2 high-speed modes at 120–480 fps up to 1080p. Stock-camera slow motion does not guarantee third-party access; recording stays disabled if no supported modes are exposed.

Use a white ball with one small red dot halfway from the center to the edge of the camera-facing hemisphere. Use bright light and a dark background. Aim for a ball diameter of 40–100 pixels in the recorded image (24 minimum). Mount the phone outside the swing and ball path. The spin axis must face the camera and the dot must stay visible. Start with controlled low-spin motion.

Open preview, record a two-second burst, review, select a frame just before rotation, tap the ball, enter a realistic maximum RPM and analyze. This is manual burst capture followed by analysis, not continuous live RPM. The last clip stays in app cache and is replaced by the next capture.

The model fits unwrapped marker angles over consecutive original frames and checks marker geometry, frame timing, angular span and fit consistency. It reports apparent projected RPM and clockwise/counterclockwise image direction, not total 3D spin or backspin/sidespin decomposition. The assumed ceiling is capture fps × 10 (240 fps: 2,400 RPM; 480 fps: 4,800 RPM). Actual spin above that ceiling can still alias into a plausible lower value. A repeated marker cannot prove the assumed bound. Blur, a tilted axis and a disappearing dot invalidate the model.

Results are never sent to simulators or used by swing calibration. No interpolated frames or retimed imported clips are used.

Validation: model tests and Gradle build/lint pass. The emulator correctly disabled capture when no high-speed modes were exposed. A synthetic 240 fps video with a 600 RPM red marker passed decoding, timing checks and UI analysis, returning 600 RPM over 120 frames. OnePlus 15 capture support and physical spin accuracy remain unverified.

References: [Android high-speed sessions](https://developer.android.com/reference/android/hardware/camera2/CameraConstrainedHighSpeedCaptureSession), [OnePlus 15 stock camera specifications](https://www.oneplus.com/global/15/specs).
