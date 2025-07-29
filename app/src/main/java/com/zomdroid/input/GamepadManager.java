
package com.zomdroid.input;

import android.content.Context;
import android.hardware.input.InputManager;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;


/**
 * Handles gamepad connection, disconnection, and input event mapping for Android.
 * Delegates gamepad events to a listener interface for integration with the app/game logic.
 */
public class GamepadManager implements InputManager.InputDeviceListener {
    // Android input manager for device events
    private final InputManager inputManager;
    // Listener for gamepad events (delegated to activity or logic)
    private final GamepadListener listener;

    // Listener interface for gamepad events
    public interface GamepadListener {
        void onGamepadConnected();
        void onGamepadDisconnected();
        void onGamepadButton(int button, boolean pressed);
        void onGamepadAxis(int axis, float value);
        void onGamepadDpad(int dpad, char state);
    }

    // Create a new GamepadManager
    public GamepadManager(Context context, GamepadListener listener) {
        this.inputManager = (InputManager) context.getSystemService(Context.INPUT_SERVICE);
        this.listener = listener;
    }

    // Register for gamepad device events and check for already connected gamepads
    public void register() {
        inputManager.registerInputDeviceListener(this, null);
        // Check on start if a gamepad is already connected
        int[] deviceIds = inputManager.getInputDeviceIds();
        for (int id : deviceIds) {
            InputDevice dev = inputManager.getInputDevice(id);
            if (dev != null && isGamepadDevice(dev)) {
                listener.onGamepadConnected();
                break;
            }
        }
    }

    // Unregister from gamepad device events
    public void unregister() {
        inputManager.unregisterInputDeviceListener(this);
    }

    // Returns true if the device is a gamepad or joystick
    private boolean isGamepadDevice(InputDevice device) {
        int sources = device.getSources();
        return ((sources & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD)
                || ((sources & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK);
    }

    @Override
    public void onInputDeviceAdded(int deviceId) {
        InputDevice dev = inputManager.getInputDevice(deviceId);
        if (dev != null && isGamepadDevice(dev)) {
            listener.onGamepadConnected();
        }
    }

    @Override
    public void onInputDeviceRemoved(int deviceId) {
        int[] deviceIds = inputManager.getInputDeviceIds();
        boolean anyGamepad = false;
        for (int id : deviceIds) {
            InputDevice dev = inputManager.getInputDevice(id);
            if (dev != null && isGamepadDevice(dev)) {
                anyGamepad = true;
                break;
            }
        }
        if (!anyGamepad) {
            listener.onGamepadDisconnected();
        }
    }

    @Override
    public void onInputDeviceChanged(int deviceId) {
        // Not used
    }

    // Returns true if the KeyEvent is from a gamepad
    public boolean isGamepadEvent(KeyEvent event) {
        int source = event.getSource();
        return ((source & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD)
                || ((source & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK);
    }

    // Returns true if the MotionEvent is from a gamepad
    public boolean isGamepadMotionEvent(MotionEvent event) {
        int source = event.getSource();
        return ((source & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD)
                || ((source & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK);
    }

    // Handles a KeyEvent, mapping it to a gamepad button if applicable
    public boolean handleKeyEvent(KeyEvent event) {
        if (!isGamepadEvent(event)) return false;
        int keyCode = event.getKeyCode();
        boolean isPressed = event.getAction() == KeyEvent.ACTION_DOWN;
        int button = mapKeyCodeToGLFWButton(keyCode);
        if (button >= 0) {
            listener.onGamepadButton(button, isPressed);
            return true;
        }
        return false;
    }

    // Handles a MotionEvent, mapping axes and dpad to listener
    public boolean handleMotionEvent(MotionEvent event) {
        if (!isGamepadMotionEvent(event)) return false;
        // Sticks
        float lx = event.getAxisValue(MotionEvent.AXIS_X);
        float ly = event.getAxisValue(MotionEvent.AXIS_Y);
        float rx = event.getAxisValue(MotionEvent.AXIS_Z);
        float ry = event.getAxisValue(MotionEvent.AXIS_RZ);
        listener.onGamepadAxis(0, lx);
        listener.onGamepadAxis(1, ly);
        listener.onGamepadAxis(2, rx);
        listener.onGamepadAxis(3, ry);
        // Trigger
        float lt = event.getAxisValue(MotionEvent.AXIS_LTRIGGER);
        float rt = event.getAxisValue(MotionEvent.AXIS_RTRIGGER);
        listener.onGamepadAxis(4, lt);
        listener.onGamepadAxis(5, rt);
        // D-Pad
        float hatX = event.getAxisValue(MotionEvent.AXIS_HAT_X);
        float hatY = event.getAxisValue(MotionEvent.AXIS_HAT_Y);
        char dpadState = 0;
        if (hatY < -0.5f) dpadState |= 0x01; // up
        if (hatY > 0.5f) dpadState |= 0x04; // down
        if (hatX < -0.5f) dpadState |= 0x08; // left
        if (hatX > 0.5f) dpadState |= 0x02; // right
        listener.onGamepadDpad(0, dpadState);
        return true;
    }

    // Maps Android keycodes to GLFW button codes
    private int mapKeyCodeToGLFWButton(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_BUTTON_A: return 0; // A
            case KeyEvent.KEYCODE_BUTTON_B: return 1; // B
            case KeyEvent.KEYCODE_BUTTON_X: return 2; // X
            case KeyEvent.KEYCODE_BUTTON_Y: return 3; // Y
            case KeyEvent.KEYCODE_BUTTON_L1: return 4; // LB
            case KeyEvent.KEYCODE_BUTTON_R1: return 5; // RB
            case KeyEvent.KEYCODE_BUTTON_SELECT: return 6; // SELECT
            case KeyEvent.KEYCODE_BUTTON_START: return 7; // BACK/START
            case KeyEvent.KEYCODE_BUTTON_THUMBL: return 9; // LSTICK
            case KeyEvent.KEYCODE_BUTTON_THUMBR: return 10; // RSTICK
            case KeyEvent.KEYCODE_BUTTON_MODE: return 8; // GUIDE
            default: return -1;
        }
    }
}
