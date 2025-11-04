package vnext.android.vmcameratests;

import static android.view.View.GONE;

import static android.view.View.VISIBLE;

import android.Manifest;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.DngCreator; // IMPORT FOR DNG
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.OutputConfiguration;
import android.hardware.camera2.params.SessionConfiguration;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.provider.MediaStore;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintSet;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
//import androidx.exifinterface.media.ExifInterface; // No longer needed here if only used for manual setting

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collections; // IMPORT FOR Collections.max
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

// Import ExifInterface if you still need exifDegreesToOrientation for DNG
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import androidx.constraintlayout.widget.ConstraintLayout; // <-- Make sure you have this
import androidx.constraintlayout.widget.ConstraintSet;


public class MainActivity extends AppCompatActivity {

    private ConstraintLayout mainLayout;


    // --- STATE PERSISTENCE KEYS ---
    private static final String STATE_CAMERA_MODE = "camera_mode";
    private static final String STATE_FLASH_MODE = "flash_mode";
    private static final String STATE_TIMER_MODE = "timer_mode";
    private static final String STATE_VIDEO_RES = "video_res_mode";
    private static final String STATE_VIDEO_SIZE = "video_size_mode";
    private static final String STATE_IMAGE_SIZE = "image_size_mode";
    private static final String STATE_PORTRAIT_SIZE = "portrait_size_mode";
    // NEW KEY
    private static final String STATE_IMAGE_FORMAT = "image_format_mode";


    //Top Level Option Widget
    ImageButton btnSetting;
    ImageButton btnFlash;
    ImageButton btnTimer;
    ImageButton btnSize;
    ImageButton btnFilter;
    ImageButton btnResolution;
    //Extended Mode Size Widget
    ImageButton btnSize64mp;
    ImageButton btnSize3_4;
    ImageButton btnSize9_16;
    ImageButton btnSize1_1;
    ImageButton btnSizeFull;
    //Extended Mode Flash Widget
    ImageButton btnFlashAuto;
    ImageButton btnFlashOn;
    ImageButton btnFlashOff;
    //Extended Mode Timer Widget
    ImageButton btnTimerOff;
    ImageButton btnTimer5sec;
    ImageButton btnTimer10sec;
    //Extended Mode Resolution Widget
    ImageButton btnResolution4k;
    ImageButton btnResolutionFHD;
    ImageButton btnResolutionHD;
    //Extended Views
    private View extendedSizeOptionsView;
    private View extendedFlashOptionsView;
    private View extendedResolutionOptionsView;
    private View extendedFilterOptionsView;
    private View extendedTimerOptionsView;
    //Main Views Helper
    private LinearLayout modeDefaultOptionsContainer;
    private FrameLayout compactControlContainer;

    private TextView sliderTitle;
    private SeekBar controlSlider;
    private LinearLayout modeListLayout;

    // --- ENUM STATES ---
    private CameraMode currentCameraMode = CameraMode.PHOTO;
    private FlashMode currentFlashMode = FlashMode.OFF;
    private TimerMode currentTimerMode = TimerMode.OFF;
    private VideoResolutionMode currentVideoResolutionMode = VideoResolutionMode.QFHD;
    private VideoSizeMode currentVideoSizeMode = VideoSizeMode.S9_16;
    private ImageSizeMode currentImageSizeMode = ImageSizeMode.SFULL;
    private PortraitSizeMode currentPortraitSizeMode = PortraitSizeMode.S9_16;
    // NEW STATE: Image format for PHOTO mode
    private ImageFormatMode currentImageFormatMode = ImageFormatMode.JPEG;

    private View currentTopView = null;
    private boolean isExtendedOptionsVisible = false;
    private boolean isSliderControlVisible = false;

    //Background Thread
    HandlerThread backgroundThread;
    Handler backgroundHandler;


    //Surface View
    private ImageButton btnClick;
    private SurfaceView surfaceViewPreview;

    SurfaceHolder surfaceHolderPreview;

    //Camera Logic----------------------------
    String TAG = "VM Camera";
    //Permission Handler
    boolean PermissionGranted;
    int PERMISSION_REQUEST_CODE = 100;

    CameraManager cameraManager;
    CameraDevice cameraDevice;


    // In MainActivity.java class fields
    private boolean isCameraSetupInProgress = false;

    Map<String,String> CameraMap = new ConcurrentHashMap<>();
    Map<String, ImageReader> rawImageReaders = new ConcurrentHashMap<>();
    ConcurrentHashMap<Long,Image> rawImageHoldingMap = new ConcurrentHashMap<>();
    String logicalCameraID;
    CameraCharacteristics logicalCharacteristics;
    int MAX_RAW_IMAGES = 2;


    CameraCaptureSession captureSession;

    // Define repeatingCaptureCallback for error logging
    CameraCaptureSession.CaptureCallback repeatingCaptureCallback = new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureFailed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureFailure failure) {
            super.onCaptureFailed(session, request, failure);
            Log.e(TAG, "FATAL: Repeating preview request failed! Reason: " + failure.getReason());
            runOnUiThread(() -> Toast.makeText(MainActivity.this, "Preview stream failed to start.", Toast.LENGTH_LONG).show());
        }

        @Override
        public void onCaptureSequenceAborted(@NonNull CameraCaptureSession session, int sequenceId) {
            super.onCaptureSequenceAborted(session, sequenceId);
            Log.w(TAG, "Repeating preview sequence aborted.");
        }
    };


    ConcurrentHashMap < Long, TotalCaptureResult > captureResults = new ConcurrentHashMap < > ();

    // CRITICAL FIX: Replace the old unstable members with these stable ones
    private int containerMaxWidth = 0;
    private int containerMaxHeight = 0;

    //Saving dng
    ExecutorService fileSaveExecutor = Executors.newFixedThreadPool(4);

    private void hideSystemUI() {
        // Set decor fits system windows to false to allow your app to draw behind system bars
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        // Get the WindowInsetsControllerCompat
        WindowInsetsControllerCompat controller = new WindowInsetsControllerCompat(
                getWindow(),
                getWindow().getDecorView() // Or a specific view within your layout
        );

        // Hide the navigation bars
        controller.hide(WindowInsetsCompat.Type.navigationBars());

        // Optionally, specify the behavior of hidden system bars
        // For example, to show them temporarily on a swipe gesture:
        controller.setSystemBarsBehavior(
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        );
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_main);
        mainLayout = findViewById(R.id.main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
//            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            v.setPadding(0,0,0,0);
            return insets;
        });

        hideSystemUI();

        // --- STATE RESTORATION ---
        if (savedInstanceState != null) {
            currentCameraMode = CameraMode.values()[savedInstanceState.getInt(STATE_CAMERA_MODE, CameraMode.PHOTO.ordinal())];
            currentFlashMode = FlashMode.values()[savedInstanceState.getInt(STATE_FLASH_MODE, FlashMode.OFF.ordinal())];
            currentTimerMode = TimerMode.values()[savedInstanceState.getInt(STATE_TIMER_MODE, TimerMode.OFF.ordinal())];
            currentVideoResolutionMode = VideoResolutionMode.values()[savedInstanceState.getInt(STATE_VIDEO_RES, VideoResolutionMode.QFHD.ordinal())];
            currentVideoSizeMode = VideoSizeMode.values()[savedInstanceState.getInt(STATE_VIDEO_SIZE, VideoSizeMode.S9_16.ordinal())];
            currentImageSizeMode = ImageSizeMode.values()[savedInstanceState.getInt(STATE_IMAGE_SIZE, ImageSizeMode.SFULL.ordinal())];
            currentPortraitSizeMode = PortraitSizeMode.values()[savedInstanceState.getInt(STATE_PORTRAIT_SIZE, PortraitSizeMode.S9_16.ordinal())];
            // NEW STATE RESTORATION
            currentImageFormatMode = ImageFormatMode.values()[savedInstanceState.getInt(STATE_IMAGE_FORMAT, ImageFormatMode.JPEG.ordinal())];
        }
        // ------------------------------------

        checkPermissions();

        modeDefaultOptionsContainer = findViewById(R.id.mode_default_options_container);
        compactControlContainer = findViewById(R.id.compact_control_container);


        extendedFlashOptionsView = findViewById(R.id.extended_flash_options_view);
        extendedSizeOptionsView = findViewById(R.id.extended_size_options_view);
        extendedResolutionOptionsView = findViewById(R.id.extended_resolution_options_view);
        extendedFilterOptionsView = findViewById(R.id.extended_filter_options_view);
        extendedTimerOptionsView = findViewById(R.id.extended_timer_options_view);


        //Modes
        btnSetting = modeDefaultOptionsContainer.findViewById(R.id.btn_setting);
        btnFlash = modeDefaultOptionsContainer.findViewById(R.id.btn_flash);
        btnTimer = modeDefaultOptionsContainer.findViewById(R.id.btn_timer);
        btnSize = modeDefaultOptionsContainer.findViewById(R.id.btn_size);
        btnFilter = modeDefaultOptionsContainer.findViewById(R.id.btn_filter);
        btnResolution = modeDefaultOptionsContainer.findViewById(R.id.btn_resolution);


        //Correct Implementation
        //Extended Mode Size
        btnSize64mp = findViewById(R.id.btn_size_64mp);
        btnSize3_4 = findViewById(R.id.btn_size_3_4);
        btnSize9_16 = findViewById(R.id.btn_size_9_16);
        btnSize1_1 = findViewById(R.id.btn_size_1_1);
        btnSizeFull = findViewById(R.id.btn_size_full);


        //Extended Mode Flash
        btnFlashAuto = findViewById(R.id.btn_flash_auto);
        btnFlashOn = findViewById(R.id.btn_flash_on);
        btnFlashOff = findViewById(R.id.btn_flash_off);


        //Extended Mode Timer
        btnTimerOff = findViewById(R.id.btn_timer_off);
        btnTimer5sec = findViewById(R.id.btn_timer_five);
        btnTimer10sec = findViewById(R.id.btn_timer_ten);


        //Extended Mode Resolution
        btnResolution4k = findViewById(R.id.btn_resolution_2k);
        btnResolutionFHD = findViewById(R.id.btn_resolution_fhd);
        btnResolutionHD = findViewById(R.id.btn_resolution_hd);


        sliderTitle = findViewById(R.id.slider_title);
        controlSlider = findViewById(R.id.control_slider);

        modeListLayout = findViewById(R.id.mode_list);

        btnClick = findViewById(R.id.btnClick);
        surfaceViewPreview = findViewById(R.id.surfaceViewPreview); // Find the SurfaceView

        // Ensure SurfaceView is placed below other UI elements
        surfaceViewPreview.setZOrderOnTop(false);


        setupModeSelectionListeners();
        attachModeSpecificDefaultOptionListeners();

        // --- INITIAL UI SETUP AFTER STATE RESTORATION ---
        setModeUI(currentCameraMode);
        showTopView(modeDefaultOptionsContainer); // Ensure default options are visible
        updateSizeOptionsUI(); // Initialize size option highlights
        // ------------------------------------------------

        surfaceViewPreview.setOnClickListener(v -> handleMenuDismissal());


        btnClick.setEnabled(false);

        btnClick.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(captureSession == null || cameraDevice == null){
                    Log.e(TAG,"Capture Failed : Session or Device is null");
                }else{
                    // Trigger still capture
                    triggerRawCapture(captureSession,cameraDevice);
                }
            }
        });
        cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
// The rest of the camera setup logic is moved or modified

// FIX: Always attach the SurfaceHolder.Callback so it can handle its lifecycle events.
        surfaceHolderPreview = surfaceViewPreview.getHolder();
        surfaceHolderPreview.addCallback(surfaceCallback);

        if(PermissionGranted){
            // FIX: Populate CameraMap and start background thread immediately if permissions are granted.
            findCameraSupport();
            startBackgroundThread();
        }
    }

    // --- STATE PERSISTENCE ---
    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        // Save the ordinal (integer position) of the enums
        outState.putInt(STATE_CAMERA_MODE, currentCameraMode.ordinal());
        outState.putInt(STATE_FLASH_MODE, currentFlashMode.ordinal());
        outState.putInt(STATE_TIMER_MODE, currentTimerMode.ordinal());
        outState.putInt(STATE_VIDEO_RES, currentVideoResolutionMode.ordinal());
        outState.putInt(STATE_VIDEO_SIZE, currentVideoSizeMode.ordinal());
        outState.putInt(STATE_IMAGE_SIZE, currentImageSizeMode.ordinal());
        outState.putInt(STATE_PORTRAIT_SIZE, currentPortraitSizeMode.ordinal());
        // NEW STATE PERSISTENCE
        outState.putInt(STATE_IMAGE_FORMAT, currentImageFormatMode.ordinal());
    }
    // ------------------------------------


    //Checks and Request Permission
    void checkPermissions(){
        String[] permissions;
        // ... (Permission check logic is correct)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions = new String[]{
                    Manifest.permission.CAMERA,
                    Manifest.permission.READ_MEDIA_IMAGES
            };
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            permissions = new String[]{
                    Manifest.permission.CAMERA,
                    Manifest.permission.READ_EXTERNAL_STORAGE
            };
        } else {
            return;
        }

        List<String> permissionsToRequest = new java.util.ArrayList<>();

        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(permission);
            }
        }

        if (!permissionsToRequest.isEmpty()) {
            ActivityCompat.requestPermissions(
                    this,
                    permissionsToRequest.toArray(new String[0]),
                    PERMISSION_REQUEST_CODE
            );
        } else {
            PermissionGranted = true;
        }
    }


    //Permission Result HAndler
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }

            if (allGranted) {
                PermissionGranted = true;
                // FIX: If permissions are newly granted, start the thread and populate CameraMap
                if(backgroundThread == null){
                    startBackgroundThread();
                }
                findCameraSupport();
                // setupCamera() will be called from surfaceCreated/Changed.
            } else {
                PermissionGranted = false;
                // Optional: Inform the user the app cannot function without permission
                runOnUiThread(() -> Toast.makeText(this, "Camera permission denied. Cannot start camera.", Toast.LENGTH_LONG).show());
            }
        }
    }

    private void handleMenuDismissal() {
        if (isSliderControlVisible) {
            hideSliderControl();
        } else if (isExtendedOptionsVisible) {
            showTopView(modeDefaultOptionsContainer);
        }
    }

    private void setupModeSelectionListeners() {

        View.OnClickListener modeClickListener = v -> {
            TextView selectedModeTextView = (TextView) v;
            String modeText = selectedModeTextView.getText().toString();
            CameraMode newMode = null;

            switch (modeText) {
                case "PORTRAIT":
                    newMode = CameraMode.PORTRAIT;
                    break;
                case "VIDEO":
                    newMode = CameraMode.VIDEO;
                    break;
                case "PHOTO":
                    newMode = CameraMode.PHOTO;
                    break;
            }
            if (newMode != null) {
                setCameraMode(newMode,false);
            }
        };

        findViewById(R.id.mode_portrait).setOnClickListener(modeClickListener);
        findViewById(R.id.mode_video).setOnClickListener(modeClickListener);
        findViewById(R.id.mode_photo).setOnClickListener(modeClickListener);
    }

    // Helper to update the UI element for the current mode
    private void setModeUI(CameraMode mode) {
        for (int i = 0; i < modeListLayout.getChildCount(); i++) {
            View child = modeListLayout.getChildAt(i);
            if (child instanceof TextView) {
                ((TextView) child).setTextColor(Color.WHITE);
                if (mode.name().equalsIgnoreCase(((TextView) child).getText().toString())) {
                    ((TextView) child).setTextColor(Color.rgb(173, 216, 230));
                    centerSelectedMode(child);
                }
            }
        }
    }


    private void centerSelectedMode(View selectedView) {
        HorizontalScrollView modeScrollView = findViewById(R.id.mode_scroll_view);

        if (modeScrollView == null) return;

        int scrollWidth = modeScrollView.getWidth();
        int centerOfScroll = scrollWidth / 2;

        int selectedViewWidth = selectedView.getWidth();
        int selectedViewCenter = selectedView.getLeft() + (selectedViewWidth / 2);

        int scrollTo = selectedViewCenter - centerOfScroll;

        modeScrollView.smoothScrollTo(scrollTo, 0);
    }

    // --- NEW: UI UPDATE FOR SIZE OPTIONS ---
    /**
     * Updates the visual state of the size option buttons (3:4, 9:16, 1:1, FULL, 64MP)
     * to highlight the currently selected ratio based on the current mode.
     */
    private void updateSizeOptionsUI() {
        // 1. Reset all buttons to an unselected state.
        final int unselectedColor = Color.TRANSPARENT;
        // Use a different color for 64MP if it's in RAW mode
        final int selectedColorJPEG = Color.parseColor("#40ADD8E6"); // A light, translucent blue highlight (ARGB)
        final int selectedColorRAW = Color.parseColor("#40FFD700"); // A translucent gold highlight for RAW (ARGB)

        btnSize64mp.setBackgroundColor(unselectedColor);
        btnSize3_4.setBackgroundColor(unselectedColor);
        btnSize9_16.setBackgroundColor(unselectedColor);
        btnSize1_1.setBackgroundColor(unselectedColor);
        btnSizeFull.setBackgroundColor(unselectedColor);

        // 2. Determine which button should be selected
        ImageButton selectedButton = null;

        if (currentCameraMode == CameraMode.PHOTO) {
            switch (currentImageSizeMode) {
                case S64MP: selectedButton = btnSize64mp; break;
                case S3_4: selectedButton = btnSize3_4; break;
                case S9_16: selectedButton = btnSize9_16; break;
                case S1_1: selectedButton = btnSize1_1; break;
                case SFULL: selectedButton = btnSizeFull; break;
            }
        } else if (currentCameraMode == CameraMode.PORTRAIT) {
            switch (currentPortraitSizeMode) {
                case S3_4: selectedButton = btnSize3_4; break;
                case S9_16: selectedButton = btnSize9_16; break;
                case S1_1: selectedButton = btnSize1_1; break;
                case SFULL: selectedButton = btnSizeFull; break;
            }
        } else if (currentCameraMode == CameraMode.VIDEO) {
            switch (currentVideoSizeMode) {
                case S9_16: selectedButton = btnSize9_16; break;
                case S1_1: selectedButton = btnSize1_1; break;
                case SFULL: selectedButton = btnSizeFull; break;
            }
        }

        // 3. Apply the selected state highlight
        if (selectedButton != null) {
            if (currentCameraMode == CameraMode.PHOTO && currentImageSizeMode == ImageSizeMode.S64MP && currentImageFormatMode == ImageFormatMode.RAW) {
                selectedButton.setBackgroundColor(selectedColorRAW);
            } else {
                selectedButton.setBackgroundColor(selectedColorJPEG);
            }
        }
    }
    // --- END NEW: UI UPDATE FOR SIZE OPTIONS ---


    public void setCameraMode(CameraMode newMode,boolean forceReinit) {

        // Store the previous format to detect a change
        ImageFormatMode previousFormat = currentImageFormatMode;

        // Logic to determine the image format based on the selected size (specifically for S64MP)
        if (newMode == CameraMode.PHOTO) {
            if (currentImageSizeMode == ImageSizeMode.S64MP) {
                // In S64MP mode, we force RAW capture for full resolution
                currentImageFormatMode = ImageFormatMode.RAW;
            } else {
                // Other photo modes default to JPEG
                currentImageFormatMode = ImageFormatMode.JPEG;
            }
        } else {
            // Non-photo modes don't use the ImageFormatMode or default to JPEG for stability
            currentImageFormatMode = ImageFormatMode.JPEG;
        }

        // Determine if a full camera re-initialization is needed
        boolean shouldReinitCamera = (cameraDevice == null) || (newMode != currentCameraMode) || (currentImageFormatMode != previousFormat) || forceReinit;

        currentCameraMode = newMode; // Set the new mode regardless of re-init

        if (shouldReinitCamera) {
            if (isCameraSetupInProgress) {
                // CRITICAL FIX 1: Ignore the event if a setup/teardown is already running.
                Log.w(TAG, "Ignoring setCameraMode call: Setup is already in progress.");
                return;
            }

            if (cameraDevice != null) {
                // Start teardown
                isCameraSetupInProgress = true; // Set flag to block new calls

                // --- FIX: GRACEFUL SESSION CLOSURE (Prevents Stuck Preview Race) ---
                if (captureSession != null) {
                    try {
                        // 1. Explicitly stop the repeating request first
                        captureSession.stopRepeating();
                    } catch (CameraAccessException e) {
                        Log.w(TAG, "Exception while stopping repeating request for mode change: " + e.getMessage());
                    }
                    // 2. Close the capture session
                    captureSession.close();
                    captureSession = null;
                }

                cameraDevice.close();
                // The flag is cleared in the onClosed callback when teardown completes, which triggers the next setup.
            } else {
                // Start initial setup. This handles the case where the app is launched and cameraDevice is null.
                setupCamera();
            }
        }


        setModeUI(newMode); // Update UI highlight
        showTopView(modeDefaultOptionsContainer);

        switch (newMode) {
            case PHOTO:
                //Modes
                btnFlash.setVisibility(VISIBLE);
                btnTimer.setVisibility(VISIBLE);
                btnResolution.setVisibility(GONE);
                // Extended
                // Only show 64MP/3:4 buttons for PHOTO mode, which is correct
                btnSize64mp.setVisibility(VISIBLE);
                btnSize3_4.setVisibility(VISIBLE);
                break;
            case PORTRAIT:
                btnTimer.setVisibility(VISIBLE);
                btnResolution.setVisibility(GONE);
                btnFlash.setVisibility(GONE);
                // Extended
                btnSize64mp.setVisibility(GONE);
                btnSize3_4.setVisibility(VISIBLE);
                break;
            case VIDEO:
                btnFlash.setVisibility(VISIBLE);
                btnTimer.setVisibility(GONE);
                btnResolution.setVisibility(VISIBLE);
                // Extended
                btnSize64mp.setVisibility(GONE);
                btnSize3_4.setVisibility(GONE);
                break;
            default:
                break;
        }

        // CRITICAL FIX: Update the UI for the newly active size mode
        updateSizeOptionsUI();
    }

    private void attachModeSpecificDefaultOptionListeners() {
        if (btnFlash != null)
            btnFlash.setOnClickListener(v -> showTopView(extendedFlashOptionsView));
        if (btnTimer != null)
            btnTimer.setOnClickListener(v -> showTopView(extendedTimerOptionsView));
        if (btnSize != null) btnSize.setOnClickListener(v -> showTopView(extendedSizeOptionsView));
        if (btnFilter != null)
            btnFilter.setOnClickListener(v -> showTopView(extendedFilterOptionsView));
        if (btnResolution != null)
            btnResolution.setOnClickListener(v -> showTopView(extendedResolutionOptionsView));

        //Extended Mode Size (Requires session re-creation)
        if (btnSize64mp != null) btnSize64mp.setOnClickListener(v -> {
            currentImageSizeMode = ImageSizeMode.S64MP;
            // Pass false: The format change (JPEG->RAW) already triggers re-init inside setCameraMode.
            setCameraMode(currentCameraMode, false);
            updateSizeOptionsUI();
            showTopView(modeDefaultOptionsContainer);
        });

        if (btnSize3_4 != null) btnSize3_4.setOnClickListener(v -> {
            if (currentCameraMode == CameraMode.PORTRAIT) {
                currentPortraitSizeMode = PortraitSizeMode.S3_4;
            } else {
                currentImageSizeMode = ImageSizeMode.S3_4;
            }
            // FIX: Force re-init to apply new size
            setCameraMode(currentCameraMode, true);
            updateSizeOptionsUI();
            showTopView(modeDefaultOptionsContainer);
        });

        if (btnSize9_16 != null) btnSize9_16.setOnClickListener(v -> {
            if (currentCameraMode == CameraMode.VIDEO) {
                currentVideoSizeMode = VideoSizeMode.S9_16;
            } else if (currentCameraMode == CameraMode.PORTRAIT) {
                currentPortraitSizeMode = PortraitSizeMode.S9_16;
            } else {
                currentImageSizeMode = ImageSizeMode.S9_16;
            }
            // FIX: Force re-init to apply new size and fix stretching
            setCameraMode(currentCameraMode, true);
            updateSizeOptionsUI();
            showTopView(modeDefaultOptionsContainer);
        });

        if (btnSize1_1 != null) btnSize1_1.setOnClickListener(v -> {
            if (currentCameraMode == CameraMode.VIDEO) {
                currentVideoSizeMode = VideoSizeMode.S1_1;
            } else if (currentCameraMode == CameraMode.PORTRAIT) {
                currentPortraitSizeMode = PortraitSizeMode.S1_1;
            } else {
                currentImageSizeMode = ImageSizeMode.S1_1;
            }
            // FIX: Force re-init to apply new size
            setCameraMode(currentCameraMode, true);
            updateSizeOptionsUI();
            showTopView(modeDefaultOptionsContainer);
        });

        if (btnSizeFull != null) btnSizeFull.setOnClickListener(v -> {
            if (currentCameraMode == CameraMode.VIDEO) {
                currentVideoSizeMode = VideoSizeMode.SFULL;
            } else if (currentCameraMode == CameraMode.PORTRAIT) {
                currentPortraitSizeMode = PortraitSizeMode.SFULL;
            } else {
                currentImageSizeMode = ImageSizeMode.SFULL;
            }
            // FIX: Force re-init to apply new size
            setCameraMode(currentCameraMode, true);
            updateSizeOptionsUI();
            showTopView(modeDefaultOptionsContainer);
        });


        //Extended Mode Flash (Fast updates - no session recreation)
        if (btnFlashAuto != null) btnFlashAuto.setOnClickListener(v -> {
            currentFlashMode = FlashMode.AUTO;
            if (captureSession != null) updateRepeatingRequest();
            showTopView(modeDefaultOptionsContainer);
        });
        if (btnFlashOn != null) btnFlashOn.setOnClickListener(v -> {
            currentFlashMode = FlashMode.ON;
            if (captureSession != null) updateRepeatingRequest();
            showTopView(modeDefaultOptionsContainer);
        });
        if (btnFlashOff != null) btnFlashOff.setOnClickListener(v -> {
            currentFlashMode = FlashMode.OFF;
            if (captureSession != null) updateRepeatingRequest();
            showTopView(modeDefaultOptionsContainer);
        });

        //Extended Mode Timer (No Camera2 change needed)
        if (btnTimerOff != null) btnTimerOff.setOnClickListener(v -> {
            currentTimerMode = TimerMode.OFF;
            showTopView(modeDefaultOptionsContainer);
        });
        if (btnTimer5sec != null) btnTimer5sec.setOnClickListener(v -> {
            currentTimerMode = TimerMode.FIVE;
            showTopView(modeDefaultOptionsContainer);
        });
        if (btnTimer10sec != null) btnTimer10sec.setOnClickListener(v -> {
            currentTimerMode = TimerMode.TEN;
            showTopView(modeDefaultOptionsContainer);
        });


        //Extended Mode Resolution (Requires session re-creation)
        if (btnResolution4k != null) btnResolution4k.setOnClickListener(v -> {
            currentVideoResolutionMode = VideoResolutionMode.Q2K;
            setCameraMode(currentCameraMode,true);
            showTopView(modeDefaultOptionsContainer);
        });
        if (btnResolutionFHD != null) btnResolutionFHD.setOnClickListener(v -> {
            currentVideoResolutionMode = VideoResolutionMode.QFHD;
            setCameraMode(currentCameraMode,true);
            showTopView(modeDefaultOptionsContainer);
        });
        if (btnResolutionHD != null) btnResolutionHD.setOnClickListener(v -> {
            currentVideoResolutionMode = VideoResolutionMode.QHD;
            setCameraMode(currentCameraMode,true);
            showTopView(modeDefaultOptionsContainer);
        });


    }

    // Helper function to update the preview request without recreating the session
    private void updateRepeatingRequest() {
        // Note: 'captureSession' is the member variable holding the active session.
        if (cameraDevice == null || captureSession == null) return;

        try {
            CaptureRequest.Builder previewBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            previewBuilder.addTarget(this.surfaceViewPreview.getHolder().getSurface());

            // Apply current settings from enums
            applyFlashModeToBuilder(previewBuilder, true);
            previewBuilder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);

            // FIX: Change 'session' to 'captureSession'
            captureSession.setRepeatingRequest(previewBuilder.build(), repeatingCaptureCallback, this.backgroundHandler);

        } catch (CameraAccessException e) {
            Log.e(TAG, "Failed to update repeating request: " + e.getMessage());
        }
    }


    private void showTopView(View targetView) {

        if (currentTopView != null) {
            currentTopView.setVisibility(GONE);
        }

        if (targetView != null) {
            currentTopView = targetView;
            targetView.setVisibility(VISIBLE);
            isExtendedOptionsVisible = (targetView != modeDefaultOptionsContainer);
        }

        hideSliderControl();

    }

    private void showSliderControl(String title, int max) {
        sliderTitle.setText(title);
        controlSlider.setMax(max);
        compactControlContainer.setVisibility(VISIBLE);
        isSliderControlVisible = true;
    }

    private void hideSliderControl() {
        compactControlContainer.setVisibility(GONE);
        isSliderControlVisible = false;
    }


    // MODES
    public enum CameraMode {PHOTO, VIDEO, PORTRAIT}


    public enum FlashMode {AUTO, ON, OFF}

    public enum TimerMode {OFF, FIVE, TEN}


    public enum VideoResolutionMode {Q2K, QFHD, QHD}

    public enum VideoSizeMode {S9_16, S1_1, SFULL}

    public enum ImageSizeMode {S64MP, S3_4, S9_16, S1_1, SFULL}

    public enum PortraitSizeMode {S3_4, S9_16, S1_1, SFULL}

    // NEW ENUM for image format selection
    public enum ImageFormatMode {JPEG, RAW}


    // CRITICAL: Helper to apply the current flash mode to a CaptureRequest.Builder
    private void applyFlashModeToBuilder(CaptureRequest.Builder builder, boolean isPreview) {
        switch (currentFlashMode) {
            case AUTO:
                builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
                if (!isPreview) {
                    builder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_SINGLE);
                }
                break;
            case ON:
                builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_ALWAYS_FLASH);
                if (!isPreview) {
                    builder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_SINGLE);
                }
                break;
            case OFF:
            default:
                builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON); // AE ON, but no flash
                builder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF);
                break;
        }
    }

    // --- ROTATION HELPERS (NEEDED FOR JPEG AND DNG FIXES) ---

    /**
     * Converts the Surface rotation constant to degrees.
     */
    private int getRotationDegrees(int rotation) {
        switch (rotation) {
            case Surface.ROTATION_0: return 0;
            case Surface.ROTATION_90: return 90;
            case Surface.ROTATION_180: return 180;
            case Surface.ROTATION_270: return 270;
            default: return 0;
        }
    }

    /**
     * Calculates the correct JPEG orientation based on sensor orientation and device rotation.
     * This is also used for DNG orientation.
     * @param cameraCharacteristics The characteristics for the camera device.
     * @return The angle (0, 90, 180, 270) to set for orientation.
     */
    private int getJpegOrientation(@NonNull CameraCharacteristics cameraCharacteristics) {
        // 1. Get the sensor's native orientation (usually 90 or 270 for phones)
        Integer sensorOrientation = cameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
        if (sensorOrientation == null) {
            sensorOrientation = 0;
        }

        // 2. Get the device's current rotation from the display service
        int deviceRotation = getWindowManager().getDefaultDisplay().getRotation();
        int degrees = getRotationDegrees(deviceRotation);

        // 3. Calculate the final orientation
        Integer facing = cameraCharacteristics.get(CameraCharacteristics.LENS_FACING);
        if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) {
            // Front camera: orientation = (sensorOrientation + degrees + 360) % 360
            return (sensorOrientation + degrees + 360) % 360;
        } else {
            // Back camera: orientation = (sensorOrientation - degrees + 360) % 360
            return (sensorOrientation - degrees + 360) % 360;
        }
    }


    // NEW HELPER: Converts JPEG Orientation degrees (0, 90, 180, 270) to EXIF Orientation Tag values (1-8)
    private int exifDegreesToOrientation(int degrees) {
        // We only care about the common 90-degree steps
        switch (degrees) {
            case 90: return androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_90; // Tag value 6
            case 180: return androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_180; // Tag value 3
            case 270: return androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_270; // Tag value 8
            case 0:
            default: return androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL; // Tag value 1
        }
    }

    // --- CRITICAL FIX: ASPECT RATIO HELPERS ---

    /**
     * Returns the hardcoded target output size for the current Photo/Portrait mode and size selection.
     * NOTE: This is primarily used for JPEG captures. For RAW, we use the MAX RAW size from characteristics.
     *
     * *** MODIFIED to use WxH based on user's HxW feedback ***
     */
    private Size getTargetOutputSize() {
        // Default fallback size
        Size defaultSize = new Size(4624, 3468); // Default to 4:3 WxH size

        if (currentCameraMode == CameraMode.PHOTO) {
            switch (currentImageSizeMode) {
                case S64MP:
                case S3_4:
                    // 3:4: 4624x3468 WxH (from user's HxW of 3468x4624)
                    return new Size(4624, 3468);
                case S9_16:
                    // 9:16: 4624x2600 WxH (from user's HxW of 2600x4624)
                    return new Size(4624, 2600);
                case S1_1:
                    // 1:1: 3468x3468 WxH (from user's HxW of 3468x3468)
                    return new Size(3468, 3468);
                case SFULL:
                    // Full: 6424x2080 WxH (from user's HxW of 2080x6424)
                    return new Size(4624, 2080);
            }
        } else if (currentCameraMode == CameraMode.PORTRAIT) {
            switch (currentPortraitSizeMode) {
                case S3_4:
                    return new Size(4624, 3468);
                case S9_16:
                    return new Size(4624, 2600);
                case S1_1:
                    return new Size(3468, 3468);
                case SFULL:
                    return new Size(4624, 2080);
            }
        }
        // VIDEO mode uses a different path (MediaRecorder or similar) and is not implemented here.
        return defaultSize;
    }

    /**
     * Finds the largest RAW_SENSOR size reported by the camera characteristics.
     * This is the true 64MP resolution (e.g. 9248x6936).
     */
    private Size getMaxRawSize() {
        try {
            if (logicalCharacteristics == null) return null;
            StreamConfigurationMap map = logicalCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            if (map == null) return null;

            Size[] rawSizes = map.getOutputSizes(ImageFormat.RAW_SENSOR);

            if (rawSizes == null || rawSizes.length == 0) {
                Log.w(TAG, "RAW_SENSOR format not supported or no sizes available.");
                return null;
            }

            // The largest size is typically the full sensor resolution (e.g., 9248x6936 for 64MP)
            return Collections.max(Arrays.asList(rawSizes), Comparator.comparingLong(s -> (long) s.getWidth() * s.getHeight()));

        } catch (Exception e) {
            Log.e(TAG, "Error getting max RAW size.", e);
            return null;
        }
    }


    /**
     * Determines the desired aspect ratio (Width:Height) based on the current mode and size selection.
     * *** MODIFIED to use WxH based on user's HxW feedback ***
     */
//    private Size getCurrentTargetAspectRatio() {
//        int w = 0;
//        int h = 0;
//
//        // PHOTO Mode
//        if (currentCameraMode == CameraMode.PHOTO) {
//            if (currentImageSizeMode == ImageSizeMode.S64MP || currentImageSizeMode == ImageSizeMode.S3_4) {
//                w = 4;
//                h = 3;
//            } else if (currentImageSizeMode == ImageSizeMode.S9_16) {
//                w = 16;
//                h = 9;
//            } else if (currentImageSizeMode == ImageSizeMode.S1_1) {
//                w = 1;
//                h = 1;
//            } else { // SFULL (Using 11:5(2.2) for the extended ratio of Full mode)
//                w = 11;
//                h = 5;
//            }
//        }
//        // PORTRAIT Mode
//        else if (currentCameraMode == CameraMode.PORTRAIT) {
//            if (currentPortraitSizeMode == PortraitSizeMode.S3_4) {
//                w = 4;
//                h = 3;
//            } else if (currentPortraitSizeMode == PortraitSizeMode.S9_16) {
//                w = 16;
//                h = 9;
//            } else if (currentPortraitSizeMode == PortraitSizeMode.S1_1) {
//                w = 1;
//                h = 1;
//            } else { // SFULL
//                w = 11;
//                h = 5;
//            }
//        }
//        // VIDEO Mode
//        else if (currentCameraMode == CameraMode.VIDEO) {
//            if (currentVideoSizeMode == VideoSizeMode.S9_16) {
//                w = 16;
//                h = 9;
//            } else if (currentVideoSizeMode == VideoSizeMode.S1_1) {
//                w = 1;
//                h = 1;
//            } else { // SFULL
//                w = 11;
//                h = 5;
//            }
//        }
//
//        // Default to a common ratio if no mode is matched
//        if (w == 0 || h == 0) {
//            w = 4;
//            h = 3;
//        }
//
//        return new Size(w, h);
//    }


    private Size getCurrentTargetAspectRatio() {
        int w = 0;
        int h = 0;

        // PHOTO Mode
        if (currentCameraMode == CameraMode.PHOTO) {
            if (currentImageSizeMode == ImageSizeMode.S64MP || currentImageSizeMode == ImageSizeMode.S3_4) {
                w = 4;
                h = 3;
            } else if (currentImageSizeMode == ImageSizeMode.S9_16) {
                w = 16;
                h = 9;
            } else if (currentImageSizeMode == ImageSizeMode.S1_1) {
                w = 1;
                h = 1;
            } else { // SFULL (Using 11:5(2.2) for the extended ratio of Full mode)
                w = 11;
                h = 5;
            }
        }
        // PORTRAIT Mode
        else if (currentCameraMode == CameraMode.PORTRAIT) {
            if (currentPortraitSizeMode == PortraitSizeMode.S3_4) {
                w = 4;
                h = 3;
            } else if (currentPortraitSizeMode == PortraitSizeMode.S9_16) {
                w = 16;
                h = 9;
            } else if (currentPortraitSizeMode == PortraitSizeMode.S1_1) {
                w = 1;
                h = 1;
            } else { // SFULL
                w = 11;
                h = 5;
            }
        }
        // VIDEO Mode
        else if (currentCameraMode == CameraMode.VIDEO) {
            if (currentVideoSizeMode == VideoSizeMode.S9_16) {
                w = 16;
                h = 9;
            } else if (currentVideoSizeMode == VideoSizeMode.S1_1) {
                w = 1;
                h = 1;
            } else { // SFULL
                w = 11;
                h = 5;
            }
        }

        // Default to a common ratio if no mode is matched
        if (w == 0 || h == 0) {
            w = 4;
            h = 3;
        }

        return new Size(w, h);
    }

    /**
     * Selects the largest preview size that matches the target aspect ratio.
     */
    private Size chooseOptimalSize(Size[] choices, Size targetRatio) {


        switch (currentCameraMode){
            case PHOTO:
                if(currentImageSizeMode == ImageSizeMode.S64MP || currentImageSizeMode == ImageSizeMode.S3_4){
                    return new Size(1440,1080);
                } else if (currentImageSizeMode == ImageSizeMode.S9_16) {
                    return new Size(1920,1080);
                }else if(currentImageSizeMode == ImageSizeMode.S1_1){
                    return new Size(1080,1080);
                }else if(currentImageSizeMode == ImageSizeMode.SFULL) {
                    return new Size(2408, 1080);
                }
            case VIDEO:
                if (currentVideoSizeMode == VideoSizeMode.S9_16) {
                    return new Size(1920,1080);
                }else if(currentVideoSizeMode == VideoSizeMode.S1_1){
                    return new Size(1080,1080);
                }else if(currentVideoSizeMode == VideoSizeMode.SFULL) {
                    return new Size(2408, 1080);
                }
            case PORTRAIT:
                if(currentPortraitSizeMode == PortraitSizeMode.S3_4){
                    return new Size(1440,1080);
                } else if (currentPortraitSizeMode == PortraitSizeMode.S9_16) {
                    return new Size(1920,1080);
                }else if(currentPortraitSizeMode == PortraitSizeMode.S1_1){
                    return new Size(1080,1080);
                }else if(currentPortraitSizeMode == PortraitSizeMode.SFULL) {
                    return new Size(2408, 1080);
                }
        }
        if((currentCameraMode == CameraMode.PHOTO || currentCameraMode == CameraMode.PORTRAIT) && (currentImageSizeMode == ImageSizeMode.S64MP || currentImageSizeMode == ImageSizeMode.S3_4)){
            return new Size(1440,1080);
        }else if((currentCameraMode == CameraMode.PHOTO || currentCameraMode == CameraMode.PORTRAIT) &&  currentImageSizeMode == ImageSizeMode.S9_16){
            return new Size(1920,1080);
        }else if((currentCameraMode == CameraMode.PHOTO || currentCameraMode == CameraMode.PORTRAIT) &&  currentImageSizeMode == ImageSizeMode.S1_1){
            return new Size(1080,1080);
        }else if((currentCameraMode == CameraMode.PHOTO || currentCameraMode == CameraMode.PORTRAIT || currentCameraMode == CameraMode.VIDEO) &&  currentImageSizeMode == ImageSizeMode.SFULL){
            return new Size(2408,1080);
        }

        int targetWidth = targetRatio.getWidth();
        int targetHeight = targetRatio.getHeight();
        double ratio = (double) targetWidth / targetHeight;

        List<Size> suitableSizes = Arrays.asList(choices).stream()
                // Filter to sizes with the correct aspect ratio (within a small tolerance)
                .filter(size -> Math.abs((double) size.getWidth() / size.getHeight() - ratio) < 0.01)
                .collect(Collectors.toList());

        if (suitableSizes.isEmpty()) {
            Log.w(TAG, "Couldn't find any size with the exact aspect ratio: " + ratio + ". Falling back to largest size.");
            // Fallback: Pick the largest available size
            return Arrays.asList(choices).stream()
                    .max(Comparator.comparingLong(s -> (long) s.getWidth() * s.getHeight()))
                    .orElse(choices[0]);
        } else {
            // Pick the largest size that fits within the current view dimensions for the best quality
            return suitableSizes.stream()
                    .max(Comparator.comparingLong(s -> (long) s.getWidth() * s.getHeight()))
                    .get();
        }
    }
    // --- END ASPECT RATIO HELPERS ---


    //BAckground Thraed
    private void startBackgroundThread() {
        backgroundThread = new HandlerThread("CameraBackground");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
        Log.i("VMBACK","THREAD Stared");
    }

    private void stopBackgroundThread() {
        if (backgroundThread != null) {
            backgroundThread.quitSafely();
            try {
                backgroundThread.join();
                backgroundThread = null;
                backgroundHandler = null;
            } catch (InterruptedException e) {
                Log.e(TAG,"ERROR : "+e);
            }
        }
        Log.i("VMBACK","THREAD STOP");
    }


    SurfaceHolder.Callback surfaceCallback = new SurfaceHolder.Callback() {
        @Override
        public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {
            Log.i("VMBACK","CHANGED SURFACE: WIDTH: "+width+"  HEIGHT: "+height);

            // CRITICAL FIX: Only set the MAX container dimensions if they haven't been set yet (i.e., are 0).
            // This prevents the Aspect Fit calculation from overwriting them with the shrunken SurfaceView size.
            // This assumes the first layout pass gives the full container size.
            if (containerMaxWidth == 0 || containerMaxHeight == 0) {
                View parent = (View) surfaceViewPreview.getParent();
                if (parent != null) { // Safety check
                    containerMaxWidth = parent.getWidth();
                    containerMaxHeight = parent.getHeight();
                    Log.i(TAG, "Container Max Dims set to: " + containerMaxWidth + "x" + containerMaxHeight);
                } else {
                    Log.e(TAG, "SurfaceView parent is null, cannot get max dims.");
                    // Consider using screen dimensions as a fallback
                }
            }


            // 2. Trigger setup ONLY when valid dimensions are reported (width/height > 0),
            // the camera device is not open, and setup is not already running.
            if (containerMaxWidth > 0 && containerMaxHeight > 0 && cameraDevice == null && !isCameraSetupInProgress) {
                // Calling setCameraMode(currentCameraMode) re-runs the initial setup flow.
                setCameraMode(currentCameraMode,false);
            }
        }

        @Override
        public void surfaceCreated(@NonNull SurfaceHolder holder) {
            Log.i("VMBACK","CREATED SURFACE");

            // 1. Initial Camera Map Population (Quick and necessary)
            if (CameraMap.isEmpty()) {
                findCameraSupport();
            }

            // 2. CRITICAL FIX: REMOVED setupCamera() call from here.
            // The camera setup is correctly deferred to surfaceChanged above.
        }

        @Override
        public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
            Log.i("VMBACK","DESTROYED SURFACE");

            // Close all resources synchronously when the surface is destroyed.
            if (captureSession != null) {
                try {
                    captureSession.stopRepeating();
                } catch (CameraAccessException e) {
                    Log.w(TAG, "Exception while stopping repeating request during surfaceDestroyed: " + e.getMessage());
                }

                captureSession.close();
                captureSession = null;
            }
            if (cameraDevice != null) {
                cameraDevice.close();
                cameraDevice = null;
            }
        }
    };

    void findCameraSupport(){
        try {
            // Ensure CameraManager is initialized (safety check)
            if (cameraManager == null) {
                cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
            }
            // Clear map before repopulating (in case of lifecycle reset)
            CameraMap.clear();

            for(String cameraID : cameraManager.getCameraIdList()){
                Integer facing = cameraManager.getCameraCharacteristics(cameraID).get(CameraCharacteristics.LENS_FACING);
                if (facing == CameraCharacteristics.LENS_FACING_BACK){
                    CameraMap.put("BACK",cameraID);
                } else if(facing == CameraCharacteristics.LENS_FACING_FRONT){
                    CameraMap.put("FRONT",cameraID);
                }

            }
        } catch (CameraAccessException e) {
            Log.e(TAG,"Camera Acess Exception FindCameraSuport",e);
        }
    }


//    void setupCamera(){
//
//        if (isCameraSetupInProgress) {
//            Log.w(TAG, "Setup already in progress. Aborting current call.");
//            return;
//        }
//
//        if (surfaceViewPreview.getHolder().getSurface() == null) {
//            Log.e(TAG, "Preview surface is not ready. Aborting camera setup.");
//            return;
//        }
//
//        // FIX: Check if CameraMap is populated. If not, try to find support again.
//        if (CameraMap.isEmpty()) {
//            findCameraSupport();
//            if (CameraMap.isEmpty()) {
//                Log.e(TAG, "FATAL: Cannot find camera support even after attempt. Aborting setup.");
//                isCameraSetupInProgress = false;
//                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Back Camera not found or is missing.", Toast.LENGTH_LONG).show());
//                return;
//            }
//        }
//
//        isCameraSetupInProgress = true;
//        logicalCameraID = CameraMap.get("BACK");
//
//        if (logicalCameraID == null) {
//            Log.e(TAG, "FATAL: 'BACK' camera ID not available. Cannot proceed with camera setup.");
//            runOnUiThread(() -> Toast.makeText(MainActivity.this, "Back Camera not found or is missing.", Toast.LENGTH_LONG).show());
//            isCameraSetupInProgress = false; // MUST reset the flag here
//            return;
//        }
//
//        // CRITICAL CHECK: Ensure MAX container dimensions are set before calculation
//        if (containerMaxWidth == 0 || containerMaxHeight == 0) {
//            Log.e(TAG, "FATAL: Max container dimensions not set. Camera setup postponed.");
//            isCameraSetupInProgress = false;
//            return;
//        }
//
//        // --- FIX: Declare variables here to make them "effectively final" ---
//        final Size optimalPreviewSize;
//        final boolean needToSwapPreviewDimensions;
//        final int newWidth;
//        final int newHeight;
//        // --- END FIX ---
//
//        try {
//            logicalCharacteristics = cameraManager.getCameraCharacteristics(logicalCameraID);
//            StreamConfigurationMap map = logicalCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
//
//            if (map == null) return;
//
//            Size[] previewSizes = map.getOutputSizes(SurfaceHolder.class);
//            if (previewSizes == null || previewSizes.length == 0) return;
//
//            // 1. Get the target aspect ratio from the currently selected UI mode
//            Size targetAspectRatio = getCurrentTargetAspectRatio();
//
//            // 2. Choose the largest preview size that matches the target ratio
//            Size optimalPreviewSize = chooseOptimalSize(previewSizes, targetAspectRatio);
//
//            Log.i(TAG, "Target ratio: " + targetAspectRatio.getWidth() + ":" + targetAspectRatio.getHeight());
//            Log.i(TAG, "Optimal preview size: " + optimalPreviewSize.getWidth() + "x" + optimalPreviewSize.getHeight());
//
//            // Use the reliable MAX container dimensions for calculation
//            final int maxW = containerMaxWidth;
//            final int maxH = containerMaxHeight;
//
//            int newWidth;
//            int newHeight;
//
//            // Ratios based on the camera's selected size
//            float containerRatio = (float) maxW / maxH; // Use max dimensions (Portrait: e.g. 1080/1920 = 0.5625)
//
//            // --- PREVIEW ASPECT RATIO FIX ---
//            Integer sensorOrientation = logicalCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
//            if (sensorOrientation == null) sensorOrientation = 0;
//            int displayRotation = getWindowManager().getDefaultDisplay().getRotation();
//
//            int totalRotation = (sensorOrientation - getRotationDegrees(displayRotation) + 360) % 360;
//            // Dimensions must be swapped if the total rotation is 90 or 270 degrees
//            boolean needToSwapPreviewDimensions = totalRotation == 90 || totalRotation == 270;
//
//            float optimalRatio; // This is the *displayed* ratio
//            if (needToSwapPreviewDimensions) {
//                // Swap W and H for ratio calculation to match display
//                // e.g. 4:3 sensor (1920x1440) becomes 3:4 display (1440/1920 = 0.75)
//                optimalRatio = (float) optimalPreviewSize.getHeight() / optimalPreviewSize.getWidth();
//            } else {
//                // e.g. 16:9 sensor (1920x1080) becomes 16:9 display (1920/1080 = 1.77)
//                optimalRatio = (float) optimalPreviewSize.getWidth() / optimalPreviewSize.getHeight();
//            }
//            Log.i(TAG, "Container Ratio: " + containerRatio + ", Optimal Displayed Ratio: " + optimalRatio);
//            // --- END PREVIEW FIX ---
//
//
//            // 3. Aspect Fit Logic (Ensures no stretching/cropping, uses black bars if needed)
//            if (containerRatio > optimalRatio) {
//                // Container is wider than preview ratio: match height, calculate smaller width (Pillarbox)
//                newHeight = maxH; // Use max height
//                newWidth = (int) (maxH * optimalRatio);
//            } else {
//                // Container is taller/equal ratio: match width, calculate smaller/equal height (Letterbox/Full)
//                newWidth = maxW; // Use max width
//                newHeight = (int) (maxW / optimalRatio);
//            }
//
//            // Clamp the calculated size to the container size (safety measure)
//            newWidth = Math.min(newWidth, maxW);
//            newHeight = Math.min(newHeight, maxH);
//
//            // Make variables final for the lambda expression
//            final int finalNewWidth = newWidth;
//            final int finalNewHeight = newHeight;
//
//
//            runOnUiThread(() -> {
//                // Use ConstraintLayout.LayoutParams to control the SurfaceView within its parent ConstraintLayout
//                androidx.constraintlayout.widget.ConstraintLayout.LayoutParams params =
//                        (androidx.constraintlayout.widget.ConstraintLayout.LayoutParams) surfaceViewPreview.getLayoutParams();
//
//                params.width = finalNewWidth;
//                params.height = finalNewHeight;
//
//                // Keep this line
//                surfaceViewPreview.setLayoutParams(params);
//                Log.i(TAG, "Resized SurfaceView to ASPECT FIT: W:" + finalNewWidth + " H:" + finalNewHeight);
//            });
//
//        } catch (CameraAccessException e) {
//            Log.e(TAG, "Failed to get camera characteristics for size calculation.", e);
//        }
//
//        setupImageReader();
//        openLogicalCamera();
//    }

    void setupCamera(){

        if (isCameraSetupInProgress) {
            Log.w(TAG, "Setup already in progress. Aborting current call.");
            return;
        }

        if (surfaceViewPreview.getHolder().getSurface() == null) {
            Log.e(TAG, "Preview surface is not ready. Aborting camera setup.");
            return;
        }

        // FIX: Check if CameraMap is populated. If not, try to find support again.
        if (CameraMap.isEmpty()) {
            findCameraSupport();
            if (CameraMap.isEmpty()) {
                Log.e(TAG, "FATAL: Cannot find camera support even after attempt. Aborting setup.");
                isCameraSetupInProgress = false;
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Back Camera not found or is missing.", Toast.LENGTH_LONG).show());
                return;
            }
        }

        isCameraSetupInProgress = true;
        logicalCameraID = CameraMap.get("BACK");

        if (logicalCameraID == null) {
            Log.e(TAG, "FATAL: 'BACK' camera ID not available. Cannot proceed with camera setup.");
            runOnUiThread(() -> Toast.makeText(MainActivity.this, "Back Camera not found or is missing.", Toast.LENGTH_LONG).show());
            isCameraSetupInProgress = false; // MUST reset the flag here
            return;
        }

        // CRITICAL CHECK: Ensure MAX container dimensions are set before calculation
        if (containerMaxWidth == 0 || containerMaxHeight == 0) {
            Log.e(TAG, "FATAL: Max container dimensions not set. Camera setup postponed.");
            isCameraSetupInProgress = false;
            return;
        }

        // --- FIX: Declare variables here to make them "effectively final" ---
        final Size optimalPreviewSize;

        try {
            logicalCharacteristics = cameraManager.getCameraCharacteristics(logicalCameraID);
            StreamConfigurationMap map = logicalCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

            if (map == null) {
                isCameraSetupInProgress = false; // Reset flag
                return;
            }

            Size[] previewSizes = map.getOutputSizes(SurfaceHolder.class);
            if (previewSizes == null || previewSizes.length == 0) {
                isCameraSetupInProgress = false; // Reset flag
                return;
            }

            // 1. Get the target aspect ratio from the currently selected UI mode
            Size targetAspectRatio = getCurrentTargetAspectRatio();

            // 2. Choose the largest preview size that matches the target ratio
            optimalPreviewSize = chooseOptimalSize(previewSizes, targetAspectRatio); // Assign to final var

            Log.i(TAG, "Target ratio: " + targetAspectRatio.getWidth() + ":" + targetAspectRatio.getHeight());
            Log.i(TAG, "Optimal preview size: " + optimalPreviewSize.getWidth() + "x" + optimalPreviewSize.getHeight());

            // Use the reliable MAX container dimensions for calculation
            final int maxW = containerMaxWidth;
            final int maxH = containerMaxHeight;

            int calculatedWidth;
            int calculatedHeight;

            // Ratios based on the camera's selected size
            float containerRatio = (float) maxW / maxH; // Use max dimensions (Portrait: e.g. 1080/1920 = 0.5625)

            // --- PREVIEW ASPECT RATIO FIX ---
            Integer sensorOrientation = logicalCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
            if (sensorOrientation == null) sensorOrientation = 0;
            int displayRotation = getWindowManager().getDefaultDisplay().getRotation();

            int totalRotation = (sensorOrientation - getRotationDegrees(displayRotation) + 360) % 360;

            // --- DEFINE THE VARIABLE HERE ONLY ---
            final boolean needToSwapPreviewDimensions = totalRotation == 90 || totalRotation == 270;

// 2. Calculate the camera's actual output dimensions (rotated for display)
            final int bufferWidth = needToSwapPreviewDimensions ? optimalPreviewSize.getHeight() : optimalPreviewSize.getWidth();
            final int bufferHeight = needToSwapPreviewDimensions ? optimalPreviewSize.getWidth() : optimalPreviewSize.getHeight();

// The CRITICAL CHANGE: Prefix with "H," to force ConstraintLayout to respect Height first.
// The syntax is "H,Width:Height". For a portrait 9:16 preview, this is "H,1080:1920"
            final String ratioString = "H," + bufferWidth + ":" + bufferHeight;
            Log.i(TAG, "Calculated Constraint Ratio: " + ratioString);


            runOnUiThread(() -> {
                // 1. Set the SurfaceHolder's buffer size (tells the camera what resolution to produce)
                surfaceViewPreview.getHolder().setFixedSize(bufferWidth, bufferHeight);
                Log.i(TAG, "Set SurfaceHolder buffer size to: W:" + bufferWidth + " H:" + bufferHeight);

                // 2. Use ConstraintSet to apply the aspect ratio to the SurfaceView
                ConstraintSet constraintSet = new ConstraintSet();
                constraintSet.clone(mainLayout);

                // **Set the SurfaceView to match the height of its parent (the screen)**
                // This combined with the H, prefix will ensure the view is constrained to the
                // vertical space while maintaining the aspect ratio, resulting in pillarboxing
                // for shorter ratios (like 4:3) or filling the screen for taller ratios.
                constraintSet.constrainHeight(R.id.surfaceViewPreview, ConstraintSet.MATCH_CONSTRAINT);

                // Set the dimension ratio using the "H,W:H" format
                constraintSet.setDimensionRatio(R.id.surfaceViewPreview, ratioString);

                constraintSet.applyTo(mainLayout);
                Log.i(TAG, "Applied ConstraintSet ratio: " + ratioString);
            });

        } catch (CameraAccessException e) {
            Log.e(TAG, "Failed to get camera characteristics for size calculation.", e);
            isCameraSetupInProgress = false; // --- ADDED: Reset flag on error ---
        }

        setupImageReader();
        openLogicalCamera();
    }




    // --- END CRITICAL FIX ---

    // --- MODIFIED: setupImageReader (To handle RAW vs JPEG) ---
    void setupImageReader(){
        try {
            if (logicalCharacteristics == null) {
                Log.e(TAG, "Characteristics not available, cannot set up ImageReader.");
                return;
            }

            StreamConfigurationMap map = logicalCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            if (map == null) return;

            int format;
            Size finalOutputSize = null;
            Size[] outputSizes;

            // 1. DETERMINE FORMAT (RAW or JPEG)
            if (currentCameraMode == CameraMode.PHOTO && currentImageSizeMode == ImageSizeMode.S64MP) {
                format = ImageFormat.RAW_SENSOR;
                outputSizes = map.getOutputSizes(format);

                if (outputSizes == null || outputSizes.length == 0) {
                    Log.e(TAG, "RAW_SENSOR format not supported! Falling back to JPEG.");
                    format = ImageFormat.JPEG; // Fallback
                    outputSizes = map.getOutputSizes(format);
                } else {
                    Log.i(TAG, "ImageReader setup for RAW_SENSOR.");
                }
            } else {
                format = ImageFormat.JPEG;
                outputSizes = map.getOutputSizes(format);
                if (outputSizes == null || outputSizes.length == 0) {
                    Log.e(TAG, "JPEG format not supported!");
                    isCameraSetupInProgress = false;
                    return;
                }
                Log.i(TAG, "ImageReader setup for JPEG.");
            }

            // 2. DETERMINE OUTPUT SIZE
            if (format == ImageFormat.RAW_SENSOR) {
                // For RAW, we want the largest size (e.g., 9248x6936)
                finalOutputSize = getMaxRawSize();
                if (finalOutputSize == null) {
                    Log.e(TAG, "FATAL: Could not get Max RAW_SENSOR size. Falling back to largest JPEG.");
                    format = ImageFormat.JPEG;
                    outputSizes = map.getOutputSizes(format); // Re-fetch JPEG sizes if needed
                    // Fall-through to JPEG logic
                }
            }

            // This needs to run if format is JPEG OR if RAW fallback occurred
            if (format == ImageFormat.JPEG) {
                if (outputSizes == null || outputSizes.length == 0) { // Check again after potential fallback
                    Log.e(TAG, "JPEG format not supported after fallback!");
                    isCameraSetupInProgress = false;
                    return;
                }
                // 1. Get the hardcoded target size based on user's requirements
                Size requestedSize = getTargetOutputSize();

                // 2. Check if the requested size is actually supported by the camera hardware (for JPEG)
                if (requestedSize != null) {
                    boolean isSupported = false;
                    for (Size supportedSize : outputSizes) {
                        if (supportedSize.equals(requestedSize)) {
                            isSupported = true;
                            break;
                        }
                    }

                    if (isSupported) {
                        finalOutputSize = requestedSize;
                        Log.i(TAG, "Found exact hardcoded JPEG Output Size: " + finalOutputSize.getWidth() + "x" + finalOutputSize.getHeight());
                    } else {
                        Log.w(TAG, "Hardcoded size " + requestedSize.getWidth() + "x" + requestedSize.getHeight() + " is NOT supported. Falling back to optimal size selection.");
                    }
                }

                // 3. Fallback: If the hardcoded size isn't supported, use the optimal size selection (ratio-based)
                if (finalOutputSize == null) {
                    Size targetAspectRatio = getCurrentTargetAspectRatio();
                    // For all photo/portrait modes, we choose the largest size matching the ratio
                    finalOutputSize = chooseOptimalSize(outputSizes, targetAspectRatio);
                }
            }

            // Final safety fallback
            if (finalOutputSize == null) {
                Log.e(TAG, "Could not find an appropriate output size. Aborting ImageReader setup.");
                return;
            }

            // Log the final selected output size and format
            Log.i(TAG, "Final Output Size used: " + finalOutputSize.getWidth() + "x" + finalOutputSize.getHeight() + ", Format: " + (format == ImageFormat.JPEG ? "JPEG" : "RAW_SENSOR"));

            // Clear existing readers before attempting to re-create
            rawImageReaders.values().forEach(ImageReader::close);
            rawImageReaders.clear();

            // Initialize the new ImageReader with the determined output size and format
            ImageReader imageReader = ImageReader.newInstance(
                    finalOutputSize.getWidth(),
                    finalOutputSize.getHeight(),
                    format,
                    MAX_RAW_IMAGES
            );
            imageReader.setOnImageAvailableListener(imageAvailableListener,backgroundHandler);
            rawImageReaders.put(logicalCameraID,imageReader);

        } catch (Exception e) {
            Log.e(TAG,"GET CAMERA CHARACTERIOSTICS ERRor",e);
        }
    }

    ImageReader.OnImageAvailableListener imageAvailableListener = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader reader) {
            Image image = reader.acquireLatestImage();
            if(image != null){
                long timeStamp = image.getTimestamp();
                rawImageHoldingMap.put(timeStamp,image);
                processImageIfReady(timeStamp);
            }
        }
    };

    void openLogicalCamera(){
        try {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                cameraManager.openCamera(logicalCameraID, cameraStateCallback, backgroundHandler);
            }
        } catch (CameraAccessException e) {
            Log.e(TAG,"CAMERA ACESS EXCEPTION : ",e);
        }

    }

    CameraDevice.StateCallback cameraStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            Log.i(TAG, "Camera device disconnected.");
            camera.close();
            cameraDevice = null;
            captureSession = null; // Also clear session reference
            isCameraSetupInProgress = false; // Allow new setup
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            Log.e(TAG, "Camera device error: " + error);
            camera.close();
            cameraDevice = null;
            captureSession = null;
            // CRITICAL FIX 4: Clear the flag on error to allow recovery attempts
            isCameraSetupInProgress = false;
        }

        @Override
        public void onClosed(@NonNull CameraDevice camera) {
            super.onClosed(camera);
            Log.i(TAG, "Camera device fully closed. Checking if re-setup is needed.");

            if (cameraDevice == camera) {
                cameraDevice = null;
            }

            // --- CRITICAL FIX: SERIALIZED, DELAYED RE-SETUP ---
            if (isCameraSetupInProgress) {
                isCameraSetupInProgress = false; // Teardown complete, clear the flag

                // FIX: Use the stable container dimensions (containerMaxWidth/Height)
                if (containerMaxWidth > 0 && containerMaxHeight > 0) {
                    // Post a small delay (e.g., 50ms) to the Main Thread. This prevents the next
                    // openCamera call from racing the final resource cleanup of the previous close.
                    new Handler(getMainLooper()).postDelayed(() -> {
                        Log.i(TAG, "Re-setup initiated after a successful close.");
                        // The next call to setupCamera will immediately set isCameraSetupInProgress = true again
                        setupCamera();
                    }, 50);
                }
            }
        }

        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            cameraDevice = camera;
            createRawCaptureSession(cameraDevice);
        }
    };

    void createRawCaptureSession(CameraDevice device){

        // --- CRITICAL CRASH FIX: Check Surface Validity ---
        Surface previewSurface = surfaceHolderPreview.getSurface();

        if (previewSurface == null || !previewSurface.isValid()) {
            Log.e(TAG, "FATAL: Preview Surface is null or not valid. Session creation aborted.");
            // Since the surface is not ready, we abort. The SurfaceChanged callback should run again when ready.
            isCameraSetupInProgress = false; // MUST reset the flag here
            return;
        }

        OutputConfiguration previewConfig = new OutputConfiguration(previewSurface);

        List<OutputConfiguration> outputConfigs = rawImageReaders.entrySet().stream().map(entry -> {
                    Surface readerSurface = entry.getValue().getSurface();
                    // Optional: Robustness check for ImageReader surface
                    if (readerSurface == null || !readerSurface.isValid()) {
                        Log.e(TAG, "FATAL: ImageReader Surface is not valid. Skipping this stream.");
                        return null;
                    }
                    OutputConfiguration config = new OutputConfiguration(readerSurface);
                    return config;
                }).filter(config -> config != null)
                .collect(Collectors.toList());

        if (outputConfigs.isEmpty()) {
            Log.e(TAG, "FATAL: No valid ImageReader surface found. Aborting session creation.");
            isCameraSetupInProgress = false;
            return;
        }

        outputConfigs.add(0,previewConfig);

        // ------------------------------------------------


        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                SessionConfiguration sessionConfig = new SessionConfiguration(
                        SessionConfiguration.SESSION_REGULAR,
                        outputConfigs,
                        Executors.newSingleThreadExecutor(),
                        sessionStateCallback
                );
                device.createCaptureSession(sessionConfig);
            } else {
                // Fallback for older devices (using deprecated API)
                List < Surface > surfaces = outputConfigs.stream().map(OutputConfiguration::getSurface).collect(Collectors.toList());
                device.createCaptureSession(surfaces, sessionStateCallback, backgroundHandler);
            }
        } catch (CameraAccessException e) {
            Log.e(TAG, "Camera access exception in createRawCaptureSession", e);
            isCameraSetupInProgress = false; // MUST reset the flag here
        }

    }

    CameraCaptureSession.StateCallback sessionStateCallback = new CameraCaptureSession.StateCallback() {
        @Override
        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
            Log.e(TAG, "Camera capture session configuration failed");
            runOnUiThread(() -> Toast.makeText(MainActivity.this, "Camera capture session configuration failed", Toast.LENGTH_SHORT).show());
            isCameraSetupInProgress = false; // MUST reset the flag here
        }

        @Override
        public void onConfigured(@NonNull CameraCaptureSession session) {
            captureSession = session;
            isCameraSetupInProgress = false; // Setup complete, clear flag for new mode changes
            Log.i(TAG, "Camera capture session configured");
            startPreview(session, cameraDevice);
        }
    };


    void startPreview(CameraCaptureSession session,CameraDevice device){
        if (device == null) { // CRITICAL FIX: Use local 'device' param for null check
            Log.e(TAG, "Cannot start preview: CameraDevice is null or closed.");
            // Simply exit the function if the device is gone.
            return;
        }
        try {
            CaptureRequest.Builder previewBuilder = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW); // CRITICAL FIX: Use local 'device'
            previewBuilder.addTarget(this.surfaceViewPreview.getHolder().getSurface());

            // Apply flash mode for preview
            applyFlashModeToBuilder(previewBuilder, true);
            previewBuilder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);
            // RAW capture often requires CONTROL_AF_MODE_OFF or better CONTROL_AF_MODE_CONTINUOUS_PICTURE
            previewBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);


            session.setRepeatingRequest(previewBuilder.build(), repeatingCaptureCallback, this.backgroundHandler);

            runOnUiThread(() -> this.btnClick.setEnabled(true));
        } catch(CameraAccessException e) {
            Log.e(TAG, "Camera access exception in startPreview", e);
        } catch (IllegalStateException e) { // CRITICAL FIX: Catch race condition crash
            Log.w(TAG, "Attempted to start preview on a closed CameraDevice (race condition).", e);
        }
    }

    void triggerRawCapture(CameraCaptureSession session, CameraDevice device) {
        runOnUiThread(() -> this.btnClick.setEnabled(false));

        try {
            // Use different template for better RAW/ZSL capture experience
//            int template = currentImageFormatMode == ImageFormatMode.RAW
//                    ? CameraDevice.TEMPLATE_ZERO_SHUTTER_LAG
//                    : CameraDevice.TEMPLATE_STILL_CAPTURE;
//
//            CaptureRequest.Builder captureBuilder = device.createCaptureRequest(template);

            CaptureRequest.Builder captureBuilder = device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);

            // Add all ImageReader surfaces for capture
            rawImageReaders.values().forEach(reader -> captureBuilder.addTarget(reader.getSurface()));

            // Add the preview surface (often required for capture to work)
            // captureBuilder.addTarget(surfaceViewPreview.getHolder().getSurface()); // Optional

            // --- JPEG ROTATION FIX ---
            // This key is ignored for RAW captures, so it's safe to set it here.
            // It will only apply to JPEG captures.
            if (logicalCharacteristics != null) {
                captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, getJpegOrientation(logicalCharacteristics));
                Log.i(TAG, "Set CaptureRequest.JPEG_ORIENTATION to " + getJpegOrientation(logicalCharacteristics));
            }
            // --- END FIX ---


            // Apply flash mode for still capture
            applyFlashModeToBuilder(captureBuilder, false);
            captureBuilder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);
            captureBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);

            // RAW specific settings
            if (currentImageFormatMode == ImageFormatMode.RAW) {
                captureBuilder.set(CaptureRequest.CONTROL_CAPTURE_INTENT, CaptureRequest.CONTROL_CAPTURE_INTENT_STILL_CAPTURE);
                // Ensure image stabilization is off or at least doesn't interfere with the raw stream
                captureBuilder.set(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE, CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_OFF);
                captureBuilder.set(CaptureRequest.CONTROL_AE_LOCK, true); // Lock AE before capture
            }else{
                // --- 📸 FINAL MAX QUALITY CONFIGURATION (Pixel-Level Detail) ---

                // 1. Core Quality & Intent
                captureBuilder.set(CaptureRequest.JPEG_QUALITY, (byte) 100);
                captureBuilder.set(CaptureRequest.CONTROL_CAPTURE_INTENT, CaptureRequest.CONTROL_CAPTURE_INTENT_STILL_CAPTURE);
                captureBuilder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);
                captureBuilder.set(CaptureRequest.CONTROL_SCENE_MODE, CaptureRequest.CONTROL_SCENE_MODE_DISABLED);

                // 2. Maximum Sharpness & Detail (Addressing low sharpness when zoomed)
                // EDGE_MODE_HIGH_QUALITY maximizes sharpening for fine detail.
                captureBuilder.set(CaptureRequest.EDGE_MODE, CaptureRequest.EDGE_MODE_HIGH_QUALITY);

                // NOISE_REDUCTION_MODE_HIGH_QUALITY uses complex algorithms for cleaner pixels.
                captureBuilder.set(CaptureRequest.NOISE_REDUCTION_MODE, CaptureRequest.NOISE_REDUCTION_MODE_HIGH_QUALITY);

                // 3. Maximum Color Fidelity (Addressing RGB color scattering/chromatic aberration)
                // COLOR_CORRECTION_ABERRATION_MODE_HIGH_QUALITY explicitly corrects color fringing.
                captureBuilder.set(CaptureRequest.COLOR_CORRECTION_ABERRATION_MODE, CaptureRequest.COLOR_CORRECTION_ABERRATION_MODE_HIGH_QUALITY);

                // COLOR_CORRECTION_MODE_HIGH_QUALITY ensures the best demosaicing pipeline.
                captureBuilder.set(CaptureRequest.COLOR_CORRECTION_MODE, CaptureRequest.COLOR_CORRECTION_MODE_HIGH_QUALITY);

                // SHADING_MODE_HIGH_QUALITY ensures uniform pixel coloring/brightness across the sensor.
                captureBuilder.set(CaptureRequest.SHADING_MODE, CaptureRequest.SHADING_MODE_HIGH_QUALITY);

                // TONEMAP_MODE_HIGH_QUALITY preserves maximum dynamic range and subtle color gradients.
                captureBuilder.set(CaptureRequest.TONEMAP_MODE, CaptureRequest.TONEMAP_MODE_HIGH_QUALITY);

                // 4. Stabilization & Correction Locks (Ensuring pixel consistency)
                captureBuilder.set(CaptureRequest.CONTROL_AE_LOCK, true);
                captureBuilder.set(CaptureRequest.CONTROL_AWB_LOCK, true);
                captureBuilder.set(CaptureRequest.HOT_PIXEL_MODE, CaptureRequest.HOT_PIXEL_MODE_FAST);
                captureBuilder.set(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE, CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_OFF);

                // 5. Disable Thumbnail (Dedicated power to the main image)
                captureBuilder.set(CaptureRequest.JPEG_THUMBNAIL_SIZE, new Size(0, 0));

                // --- END FINAL CONFIGURATION ---
            }


            session.capture(captureBuilder.build(), cameraCaptureCallback, backgroundHandler);

        } catch (CameraAccessException e) {
            Log.e(TAG, "Capture Builder error" + e);
            runOnUiThread(() -> btnClick.setEnabled(true)); // Re-enable on immediate failure
        }

    }


    CameraCaptureSession.CaptureCallback cameraCaptureCallback = new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureBufferLost(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull Surface target, long frameNumber) {
            super.onCaptureBufferLost(session, request, target, frameNumber);
        }

        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
            Long timeStamp = result.get(CaptureResult.SENSOR_TIMESTAMP);
            if (timeStamp != null) {
                captureResults.put(timeStamp, result);
                Log.i(TAG, "Captured result for timestamp: " + timeStamp + " (Frame: " + result.getFrameNumber() + ")");
                processImageIfReady(timeStamp);
            } else {
                Log.w(TAG, "TotalCaptureResult is missing SENSOR_TIMESTAMP.");
            }
        }

        @Override
        public void onCaptureProgressed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureResult partialResult) {
            super.onCaptureProgressed(session, request, partialResult);
        }

        @Override
        public void onCaptureFailed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureFailure failure) {
            super.onCaptureFailed(session, request, failure);
        }

        @Override
        public void onCaptureSequenceAborted(@NonNull CameraCaptureSession session, int sequenceId) {
            super.onCaptureSequenceAborted(session, sequenceId);
        }

        @Override
        public void onCaptureSequenceCompleted(@NonNull CameraCaptureSession session, int sequenceId, long frameNumber) {
            super.onCaptureSequenceCompleted(session, sequenceId, frameNumber);
            // Re-unlock AE after capture sequence is completed for RAW mode (if it was locked)
            if (currentImageFormatMode == ImageFormatMode.RAW) {
                updateRepeatingRequest(); // Re-enable auto-exposure
            }

        }

        @Override
        public void onCaptureStarted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, long timestamp, long frameNumber) {
            super.onCaptureStarted(session, request, timestamp, frameNumber);
        }

        @Override
        public void onReadoutStarted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, long timestamp, long frameNumber) {
            super.onReadoutStarted(session, request, timestamp, frameNumber);
        }
    };


    // --- MODIFIED: processImageIfReady (To handle RAW vs JPEG) ---
    void processImageIfReady(long timeStamp){
        Image image = rawImageHoldingMap.remove(timeStamp);
        TotalCaptureResult result = captureResults.remove(timeStamp);

        if (image == null || result == null) {
            if (image != null) rawImageHoldingMap.put(timeStamp, image);
            if (result != null) captureResults.put(timeStamp, result);

            Log.v(TAG, "Waiting for match (TS: " + timeStamp + "). Image=" + (image != null) + ", Result=" + (result != null));
            return;
        }

        String physicalId = logicalCameraID;

        Log.i(TAG, "MATCH FOUND (TS: " + timeStamp + "). Offloading save for ID: " + physicalId);

        // --- DUAL FORMAT SAVE LOGIC ---
        fileSaveExecutor.execute(() -> {
            try {
                if (image.getFormat() == ImageFormat.RAW_SENSOR) {
                    // Call the DNG saver
                    saveImage(image, physicalId, result);
                } else {
                    // Call the JPEG saver
                    saveJpegImage(image, physicalId, result);
                }
            } finally {
                image.close();
            }
        });
        // --- END DUAL FORMAT SAVE LOGIC ---


        runOnUiThread(() -> btnClick.setEnabled(true));
    }


    // --- RENAMED & FIXED: This is the JPEG saver (NO MANUAL EXIF) ---
    void saveJpegImage(Image image, String physicalID, TotalCaptureResult result) {

        String filename = String.format("IMG_%s_%d.jpeg", physicalID, image.getTimestamp());
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, filename);
        values.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg");
        values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DCIM + "/" + TAG);

        Uri uri = null;
        try {
            uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (uri == null) {
                Log.e(TAG, "Failed to create new MediaStore entry for DCIM.");
                runOnUiThread(() -> Toast.makeText(this, "Failed to create MediaStore entry.", Toast.LENGTH_LONG).show());
                return;
            }

            // 1. Write the JPEG data to the URI
            try (OutputStream outputStream = getContentResolver().openOutputStream(uri)) {
                if (outputStream == null) {
                    Log.e(TAG, "Failed to open output stream for URI: " + uri);
                    if (uri != null) getContentResolver().delete(uri, null, null); // Clean up URI if stream fails
                    return;
                }

                ByteBuffer byteBuffer = image.getPlanes()[0].getBuffer();
                byte[] bytes = new byte[byteBuffer.remaining()];
                byteBuffer.get(bytes);
                outputStream.write(bytes);

            } catch (IOException e) { // Catch FileNotFoundException too via IOException
                Log.e(TAG, "Error writing JPEG data to output stream.", e);
                if (uri != null) {
                    getContentResolver().delete(uri, null, null); // Clean up URI if write fails
                }
                return; // Stop processing if writing failed
            }

            // 2. CRITICAL FIX REMOVED: Do NOT manually set EXIF Orientation Tag.
            // Rely on CaptureRequest.JPEG_ORIENTATION set in triggerRawCapture.

            Log.i(TAG, "Successfully saved JPEG file to URI: " + uri);
            runOnUiThread(() -> Toast.makeText(this, "JPEG saved: " + filename, Toast.LENGTH_LONG).show());

        } catch (Exception e) {
            Log.e(TAG, "Error saving image: " + e);
            if (uri != null) {
                // If a MediaStore entry was created but writing failed, clean it up
                getContentResolver().delete(uri, null, null);
            }
        }
    }


    // --- MODIFIED: This is the DNG saver (from user, with orientation fix added) ---
    void saveImage(Image image, String physicalID, TotalCaptureResult captureResult) {

        if (this.logicalCharacteristics == null) {
            Log.e(TAG, "Logical Camera Characteristics are missing for DNG save.");
            return;
        }

        String filename = String.format("RAW_%s_%d.dng", physicalID, captureResult.getFrameNumber());
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, filename);
        values.put(MediaStore.MediaColumns.MIME_TYPE, "image/dng"); // Correct MIME type
        values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DCIM + "/" + TAG);


        try {
            Uri uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (uri == null) {
                Log.e(TAG, "Failed to create new MediaStore entry for DNG.");
                runOnUiThread(() -> Toast.makeText(this, "Failed to create DNG entry.", Toast.LENGTH_LONG).show());
                return;
            }

            // Use the characteristics we already have
            CameraCharacteristics imageSourceCharacteristics = this.logicalCharacteristics;

            try (OutputStream outputStream = getContentResolver().openOutputStream(uri);
                 DngCreator dngCreator = new DngCreator(imageSourceCharacteristics, captureResult)) { // Use try-with-resources for DngCreator

                if (outputStream == null) {
                    Log.e(TAG, "Failed to open output stream for DNG URI: " + uri);
                    if (uri != null) getContentResolver().delete(uri, null, null); // Clean up
                    return;
                }

                // --- ROTATION FIX FOR DNG ---
                int rotation = getJpegOrientation(imageSourceCharacteristics); // Get degrees (0, 90, 180, 270)
                Log.i(TAG, "Setting DNG orientation (degrees): " + rotation);

                // CRITICAL FIX: Convert degrees (e.g., 90) to EXIF constant (e.g., 6)
                dngCreator.setOrientation(exifDegreesToOrientation(rotation));

                dngCreator.writeImage(outputStream, image);
                // --- END FIX ---

                Log.i(TAG, "Successfully saved DNG file for " + physicalID + " to DCIM URI: " + uri);
                runOnUiThread(() -> Toast.makeText(this, "DNG saved: " + filename, Toast.LENGTH_LONG).show());

            } catch (IOException e) { // Catches FileNotFoundException too
                Log.e(TAG, "Error writing DNG data to output stream.", e);
                if (uri != null) {
                    getContentResolver().delete(uri, null, null);
                }
            }
        } catch (Exception e) { // Catch generic Exception just in case DngCreator fails
            Log.e(TAG, "Could not save DNG file for ID: " + physicalID, e);
            runOnUiThread(() -> Toast.makeText(this, "Failed to save DNG.", Toast.LENGTH_LONG).show());
            // Attempt to clean up URI if created but save failed
            // Note: URI might be null here if insert failed earlier
            // A more robust implementation would handle this better.
        }
    }


    @Override
    protected void onPause() {
        super.onPause();

        // The SurfaceDestroyed callback handles closing camera resources now.
        // But keep this for safety if the activity is paused without surface destruction.
        if (captureSession != null) {
            // FIX: Explicitly stop the repeating request to prevent the HAL bug during close()
            try {
                captureSession.stopRepeating();
            } catch (CameraAccessException e) {
                Log.w(TAG, "Exception while stopping repeating request during onPause (likely HAL bug): " + e.getMessage());
            } catch (IllegalStateException e) {
                Log.w(TAG, "Session already closed during onPause stopRepeating.");
            }

            captureSession.close();
            captureSession = null;
        }
        if (cameraDevice != null) {
            cameraDevice.close();
            cameraDevice = null;
        }

        stopBackgroundThread();

        rawImageReaders.values().forEach(ImageReader::close);
        rawImageReaders.clear();

        captureResults.clear();

        rawImageHoldingMap.values().forEach(Image::close);
        rawImageHoldingMap.clear();

        fileSaveExecutor.shutdown(); // Shutdown executor

    }


    @Override
    protected void onResume() {
        super.onResume();
        if (PermissionGranted) {
            if (backgroundThread == null) {
                startBackgroundThread();
            }
            if (fileSaveExecutor == null || fileSaveExecutor.isShutdown()) {
                fileSaveExecutor = Executors.newFixedThreadPool(4); // Recreate executor if needed
            }

            // CRITICAL FIX: Camera setup is now solely controlled by SurfaceHolder.Callback
            // (surfaceCreated/surfaceChanged) to guarantee the preview Surface is stable and ready.
        }
    }
}