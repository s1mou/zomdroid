package com.zomdroid;

import android.annotation.SuppressLint;
import android.content.pm.ActivityInfo;
import android.graphics.PixelFormat;
import android.os.Bundle;
import android.hardware.input.InputManager;
import android.view.InputDevice;
import android.system.ErrnoException;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.zomdroid.input.GLFWBinding;
import com.zomdroid.input.InputNativeInterface;
import com.zomdroid.databinding.ActivityGameBinding;
import com.zomdroid.game.GameInstance;
import com.zomdroid.game.GameInstancesManager;

import org.fmod.FMOD;


public class GameActivity extends AppCompatActivity implements InputManager.InputDeviceListener {
    public static final String EXTRA_GAME_INSTANCE_NAME = "com.zomdroid.GameActivity.EXTRA_GAME_INSTANCE_NAME";
    private static final String LOG_TAG = GameActivity.class.getName();

    private ActivityGameBinding binding;
    private Surface gameSurface;
    private static boolean isGameStarted = false;
    private GestureDetector gestureDetector;

    private InputManager inputManager;

    @SuppressLint({"UnsafeDynamicallyLoadedCode", "ClickableViewAccessibility"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityGameBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        inputManager = (InputManager) getSystemService(INPUT_SERVICE);
        inputManager.registerInputDeviceListener(this, null);

        boolean gamepadFound = false;
        int[] deviceIds = inputManager.getInputDeviceIds();
        for (int id : deviceIds) {
            InputDevice dev = inputManager.getInputDevice(id);
            if (dev != null && isGamepadDevice(dev)) {
                gamepadFound = true;
                break;
            }
        }
        if (gamepadFound) onGamepadConnected();
        getWindow().setDecorFitsSystemWindows(false);
        final WindowInsetsController controller = getWindow().getInsetsController();
        if (controller != null) {
            controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
            controller.setSystemBarsBehavior(
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        }

        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);

        String gameInstanceName = getIntent().getStringExtra(EXTRA_GAME_INSTANCE_NAME);
        if (gameInstanceName == null)
            throw new RuntimeException("Expected game instance name to be passed as intent extra");
        GameInstance gameInstance = GameInstancesManager.requireSingleton().getInstanceByName(gameInstanceName);
        if (gameInstance == null)
            throw new RuntimeException("Game instance with name " + gameInstanceName + " not found");

        System.loadLibrary("zomdroid");

        System.load(AppStorage.requireSingleton().getHomePath() + "/" + gameInstance.getFmodLibraryPath() + "/libfmod.so");
        System.load(AppStorage.requireSingleton().getHomePath() + "/" + gameInstance.getFmodLibraryPath() + "/libfmodstudio.so");

        FMOD.init(this);

/*        gestureDetector = new GestureDetector(this, new GestureDetector.OnGestureListener() {
            private boolean showPress = false;
            @Override
            public boolean onDown(@NonNull MotionEvent e) {
                Log.v("", "onDown " + e.getX() + " " + e.getY());
                //InputBridge.sendMouseButton(GLFWConstants.GLFW_MOUSE_BUTTON_LEFT, GLFWConstants.GLFW_PRESS, event.getX(), event.getY());
                return true;
            }

            @Override
            public void onShowPress(@NonNull MotionEvent e) {
                Log.v("", "onShowPress " + e.getX() + " " + e.getY());
                showPress = true;
                InputNativeInterface.sendCursorPos(e.getX(), e.getY());
                InputNativeInterface.sendMouseButton(GLFWBinding.MOUSE_BUTTON_LEFT.code, true);
            }

            @Override
            public boolean onSingleTapUp(@NonNull MotionEvent e) {
                Log.v("", "onSingleTapUp " + e.getX() + " " + e.getY());
                InputNativeInterface.sendCursorPos(e.getX(), e.getY());
                if (showPress) {
                    InputNativeInterface.sendMouseButton(GLFWBinding.MOUSE_BUTTON_LEFT.code, false);
                }
                showPress = false;
                return true;
            }

            @Override
            public boolean onScroll(@Nullable MotionEvent e1, @NonNull MotionEvent e2, float distanceX, float distanceY) {
                InputNativeInterface.sendCursorPos(e2.getX(), e2.getY());
                Log.v("", "onScroll " + (e1 == null ? "0" : e1.getX()) + " " + (e1 == null ? "0" : e1.getY()) + " " + e2.getX() + " " + e2.getY());
                return true;
            }

            @Override
            public void onLongPress(@NonNull MotionEvent e) {
                Log.v("", "onLongPress " + e.getX() + " " + e.getY());
            }

            @Override
            public boolean onFling(@Nullable MotionEvent e1, @NonNull MotionEvent e2, float velocityX, float velocityY) {
                Log.v("", "onFling " + velocityX + " " + velocityY);
                return true;
            }
        });
        gestureDetector.setIsLongpressEnabled(false);*/

        binding.gameSv.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(@NonNull SurfaceHolder holder) {
                Log.d(LOG_TAG, "Game surface created.");
                float renderScale = LauncherPreferences.requireSingleton().getRenderScale();
                int width = (int) (binding.gameSv.getWidth() * renderScale);
                int height = (int) (binding.gameSv.getHeight() * renderScale);
                binding.gameSv.getHolder().setFixedSize(width, height);
            }

            @Override
            public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {
                Log.d(LOG_TAG, "Game surface changed.");


                gameSurface = binding.gameSv.getHolder().getSurface();
                if (gameSurface == null) throw new RuntimeException();

                if (format != PixelFormat.RGBA_8888) {
                    Log.w(LOG_TAG, "Using unsupported pixel format " + format); // LIAMELUI seems like default is RGB_565
                }

                GameLauncher.setSurface(gameSurface, width, height);
                if (!isGameStarted) {
                    Thread thread = new Thread(() -> {
                        try {
                            GameLauncher.launch(gameInstance);
                        } catch (ErrnoException e) {
                            throw new RuntimeException(e);
                        }
                    });
                    thread.start();
                    isGameStarted = true;
                }
            }

            @Override
            public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
                Log.d(LOG_TAG, "Game surface destroyed.");
                GameLauncher.destroySurface();
            }
        });

        binding.gameSv.setOnTouchListener(new View.OnTouchListener() {
            float renderScale = LauncherPreferences.requireSingleton().getRenderScale();
            int pointerId = -1;
            @Override
            public boolean onTouch(View v, MotionEvent e) { // this should be in InputControlsView
                int action = e.getActionMasked();
                int actionIndex = e.getActionIndex();
                int pointerId = e.getPointerId(actionIndex);
                switch (action) {
                    case MotionEvent.ACTION_DOWN:
                    case MotionEvent.ACTION_POINTER_DOWN: {
                        float x = e.getX(actionIndex);
                        float y = e.getY(actionIndex);
                        this.pointerId = pointerId;
                        InputNativeInterface.sendCursorPos(x * this.renderScale, y * this.renderScale);
                        InputNativeInterface.sendMouseButton(GLFWBinding.MOUSE_BUTTON_LEFT.code, true);
                        return true;
                    }
                    case MotionEvent.ACTION_MOVE: {
                        if (this.pointerId < 0) return false;
                        int pointerIndex = e.findPointerIndex(this.pointerId);
                        if (pointerIndex < 0) {
                            this.pointerId = -1;
                            return false;
                        }
                        float x = e.getX(pointerIndex);
                        float y = e.getY(pointerIndex);
                        InputNativeInterface.sendCursorPos(x * this.renderScale, y * this.renderScale);
                        return false;
                    }
                    case MotionEvent.ACTION_UP: {
                        if (pointerId != this.pointerId) return false;
                        this.pointerId = -1;
                        InputNativeInterface.sendMouseButton(GLFWBinding.MOUSE_BUTTON_LEFT.code, false);
                        return true;
                    }
                }
                return false;
            }
        });

        isGamepadConnected = false;
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (inputManager != null) {
            inputManager.unregisterInputDeviceListener(this);
        }
    }

    private boolean isGamepadDevice(InputDevice device) {
        int sources = device.getSources();
        return ((sources & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD)
                || ((sources & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK);
    }

    @Override
    public void onInputDeviceAdded(int deviceId) {
        InputDevice dev = inputManager.getInputDevice(deviceId);
        if (dev != null && isGamepadDevice(dev)) {
            onGamepadConnected();
        }
    }

    @Override
    public void onInputDeviceRemoved(int deviceId) {
        boolean anyGamepad = false;
        int[] deviceIds = inputManager.getInputDeviceIds();
        for (int id : deviceIds) {
            InputDevice dev = inputManager.getInputDevice(id);
            if (dev != null && isGamepadDevice(dev)) {
                anyGamepad = true;
                break;
            }
        }
        if (!anyGamepad) onGamepadDisconnected();
    }

    @Override
    public void onInputDeviceChanged(int deviceId) {
        //
    }

    private boolean isGamepadConnected = false;

    @Override
    public boolean dispatchKeyEvent(android.view.KeyEvent event) {
        if (isGamepadEvent(event)) {
            handleGamepadKeyEvent(event);
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent event) {
        if (isGamepadMotionEvent(event)) {
            handleGamepadMotionEvent(event);
            return true;
        }
        return super.dispatchGenericMotionEvent(event);
    }

    private boolean isGamepadEvent(android.view.KeyEvent event) {
        int source = event.getSource();
        return ((source & android.view.InputDevice.SOURCE_GAMEPAD) == android.view.InputDevice.SOURCE_GAMEPAD)
                || ((source & android.view.InputDevice.SOURCE_JOYSTICK) == android.view.InputDevice.SOURCE_JOYSTICK);
    }

    private boolean isGamepadMotionEvent(MotionEvent event) {
        int source = event.getSource();
        return ((source & android.view.InputDevice.SOURCE_GAMEPAD) == android.view.InputDevice.SOURCE_GAMEPAD)
                || ((source & android.view.InputDevice.SOURCE_JOYSTICK) == android.view.InputDevice.SOURCE_JOYSTICK);
    }

    private void handleGamepadKeyEvent(android.view.KeyEvent event) {
        int keyCode = event.getKeyCode();
        boolean isPressed = event.getAction() == android.view.KeyEvent.ACTION_DOWN;
        int button = mapKeyCodeToGLFWButton(keyCode);
        if (button >= 0) {
            com.zomdroid.input.InputNativeInterface.sendJoystickButton(button, isPressed);
        }
    }

    private void handleGamepadMotionEvent(MotionEvent event) {
        // Sticks
        float lx = event.getAxisValue(MotionEvent.AXIS_X);
        float ly = event.getAxisValue(MotionEvent.AXIS_Y);
        float rx = event.getAxisValue(MotionEvent.AXIS_Z);
        float ry = event.getAxisValue(MotionEvent.AXIS_RZ);
        com.zomdroid.input.InputNativeInterface.sendJoystickAxis(0, lx); // GLFWBinding.GAMEPAD_AXIS_LX.code
        com.zomdroid.input.InputNativeInterface.sendJoystickAxis(1, ly); // GLFWBinding.GAMEPAD_AXIS_LY.code
        com.zomdroid.input.InputNativeInterface.sendJoystickAxis(2, rx); // GLFWBinding.GAMEPAD_AXIS_RX.code
        com.zomdroid.input.InputNativeInterface.sendJoystickAxis(3, ry); // GLFWBinding.GAMEPAD_AXIS_RY.code

        // Trigger
        float lt = event.getAxisValue(MotionEvent.AXIS_LTRIGGER);
        float rt = event.getAxisValue(MotionEvent.AXIS_RTRIGGER);
        com.zomdroid.input.InputNativeInterface.sendJoystickAxis(4, lt); // GLFWBinding.GAMEPAD_AXIS_LT.code
        com.zomdroid.input.InputNativeInterface.sendJoystickAxis(5, rt); // GLFWBinding.GAMEPAD_AXIS_RT.code

        // D-Pad
        float hatX = event.getAxisValue(MotionEvent.AXIS_HAT_X);
        float hatY = event.getAxisValue(MotionEvent.AXIS_HAT_Y);
        char dpadState = 0;
        if (hatY < -0.5f) dpadState |= 0x01; // up
        if (hatY > 0.5f) dpadState |= 0x04; // down
        if (hatX < -0.5f) dpadState |= 0x08; // left
        if (hatX > 0.5f) dpadState |= 0x02; // right
        com.zomdroid.input.InputNativeInterface.sendJoystickDpad(0, dpadState);
    }

    private int mapKeyCodeToGLFWButton(int keyCode) {
        // Mapping Android keycodes to GLFWBinding button codes
        switch (keyCode) {
            case android.view.KeyEvent.KEYCODE_BUTTON_A: return 0; // A
            case android.view.KeyEvent.KEYCODE_BUTTON_B: return 1; // B
            case android.view.KeyEvent.KEYCODE_BUTTON_X: return 2; // X
            case android.view.KeyEvent.KEYCODE_BUTTON_Y: return 3; // Y
            case android.view.KeyEvent.KEYCODE_BUTTON_L1: return 4; // LB
            case android.view.KeyEvent.KEYCODE_BUTTON_R1: return 5; // RB
            case android.view.KeyEvent.KEYCODE_BUTTON_SELECT: return 6; // SELECT
            case android.view.KeyEvent.KEYCODE_BUTTON_START: return 7; // BACK/START
            case android.view.KeyEvent.KEYCODE_BUTTON_THUMBL: return 9; // LSTICK
            case android.view.KeyEvent.KEYCODE_BUTTON_THUMBR: return 10; // RSTICK
            case android.view.KeyEvent.KEYCODE_BUTTON_MODE: return 8; // GUIDE
            default: return -1;
        }
    }

    private void onGamepadConnected() {
        isGamepadConnected = true;
        if (binding.inputControlsV != null) {
            binding.inputControlsV.setVisibility(View.GONE);
        }
    }

    private void onGamepadDisconnected() {
        isGamepadConnected = false;
        if (binding.inputControlsV != null) {
            binding.inputControlsV.setVisibility(View.VISIBLE);
        }
    }
}