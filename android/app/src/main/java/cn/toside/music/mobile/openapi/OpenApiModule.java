package cn.toside.music.mobile.openapi;

import android.util.Log;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;

/**
 * React Native Module for OpenAPI
 * Provides bridge between JS and native HTTP server
 */
public class OpenApiModule extends ReactContextBaseJavaModule {
    private static final String TAG = "OpenApiModule";
    private OpenApiServer server;
    private final ReactApplicationContext reactContext;
    private int listenerCount = 0;

    public OpenApiModule(ReactApplicationContext reactContext) {
        super(reactContext);
        this.reactContext = reactContext;
        this.server = null;
    }

    @Override
    public String getName() {
        return "OpenApiModule";
    }

    @Override
    public void onCatalystInstanceDestroy() {
        super.onCatalystInstanceDestroy();
        stopServerInternal();
    }

    @ReactMethod
    public void addListener(String eventName) {
        if (listenerCount == 0) {
            // Set up any upstream listeners or background tasks as necessary
        }
        listenerCount += 1;
    }

    @ReactMethod
    public void removeListeners(Integer count) {
        listenerCount -= count;
        if (listenerCount == 0) {
            // Remove upstream listeners, stop unnecessary background tasks
        }
    }

    /**
     * Start the HTTP server
     * @param port Port number to listen on
     * @param bindLan Whether to bind to LAN address (0.0.0.0) or localhost (127.0.0.1)
     * @param promise Promise to resolve with result
     */
    @ReactMethod
    public void startServer(int port, boolean bindLan, Promise promise) {
        try {
            // Stop existing server if running
            stopServerInternal();

            String hostname = bindLan ? "0.0.0.0" : "127.0.0.1";
            server = new OpenApiServer(reactContext, port, hostname);
            server.start();

            WritableMap result = Arguments.createMap();
            result.putBoolean("status", true);
            result.putString("message", "");
            result.putString("address", server.getAddressString());

            Log.i(TAG, "Server started on " + hostname + ":" + port);
            promise.resolve(result);
        } catch (Exception e) {
            Log.e(TAG, "Failed to start server", e);

            WritableMap result = Arguments.createMap();
            result.putBoolean("status", false);
            result.putString("message", e.getMessage() != null ? e.getMessage() : "Unknown error");
            result.putString("address", "");

            // Try to clean up
            if (server != null) {
                try {
                    server.stop();
                } catch (Exception ignored) {}
                server = null;
            }

            promise.resolve(result);
        }
    }

    /**
     * Stop the HTTP server
     * @param promise Promise to resolve with result
     */
    @ReactMethod
    public void stopServer(Promise promise) {
        try {
            stopServerInternal();
            promise.resolve(true);
        } catch (Exception e) {
            Log.e(TAG, "Failed to stop server", e);
            promise.reject("STOP_FAILED", e.getMessage());
        }
    }

    /**
     * Get current server status
     * @param promise Promise to resolve with status
     */
    @ReactMethod
    public void getStatus(Promise promise) {
        WritableMap status = Arguments.createMap();
        if (server != null && server.isServerRunning()) {
            status.putBoolean("status", true);
            status.putString("message", "");
            status.putString("address", server.getAddressString());
        } else {
            status.putBoolean("status", false);
            status.putString("message", "");
            status.putString("address", "");
        }
        promise.resolve(status);
    }

    /**
     * Update player status from JS
     * This should be called when player state changes
     * @param status Player status map
     */
    @ReactMethod
    public void updatePlayerStatus(ReadableMap status) {
        if (server != null) {
            server.updatePlayerStatus(status);
        }
    }

    /**
     * Internal method to stop server
     */
    private void stopServerInternal() {
        if (server != null) {
            try {
                server.stop();
            } catch (Exception e) {
                Log.e(TAG, "Error stopping server", e);
            }
            server = null;
        }
    }
}
