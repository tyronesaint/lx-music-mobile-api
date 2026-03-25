package cn.toside.music.mobile.openapi;

import android.util.Log;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;

import org.json.JSONObject;
import org.nanohttpd.NanoHTTPD;
import org.nanohttpd.NanoHTTPD.Response.Status;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * HTTP Server for OpenAPI
 * Provides REST API for third-party applications
 */
public class OpenApiServer extends NanoHTTPD {
    private static final String TAG = "OpenApiServer";
    private final String hostname;
    private final ReactApplicationContext reactContext;
    
    // Player status cache
    private JSONObject playerStatus;
    private final Object statusLock = new Object();

    public OpenApiServer(ReactApplicationContext reactContext, int port, String hostname) {
        super(hostname, port);
        this.reactContext = reactContext;
        this.hostname = hostname;
        
        // Initialize default player status
        try {
            JSONObject status = new JSONObject();
            status.put("status", false);
            status.put("name", "");
            status.put("singer", "");
            status.put("albumName", "");
            status.put("lyricLineText", "");
            status.put("lyric", "");
            status.put("tlyric", "");
            status.put("rlyric", "");
            status.put("lxlyric", "");
            status.put("duration", 0);
            status.put("progress", 0);
            status.put("playbackRate", 1);
            status.put("volume", 100);
            this.playerStatus = status;
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize player status", e);
            this.playerStatus = new JSONObject();
        }
    }

    @Override
    public Response serve(IHTTPSession session) {
        String uri = session.getUri();
        Method method = session.getMethod();
        Map<String, String> headers = session.getHeaders();
        Map<String, String> parms = new HashMap<>();
        
        // Parse query parameters
        String queryString = session.getQueryParameterString();
        if (queryString != null && !queryString.isEmpty()) {
            for (String param : queryString.split("&")) {
                String[] pair = param.split("=", 2);
                if (pair.length == 2) {
                    parms.put(pair[0], pair[1]);
                } else if (pair.length == 1) {
                    parms.put(pair[0], "");
                }
            }
        }

        Log.d(TAG, "Request: " + method + " " + uri);

        // Add CORS headers
        Response response;
        try {
            response = handleRequest(uri, method, parms);
        } catch (Exception e) {
            Log.e(TAG, "Error handling request: " + uri, e);
            response = newFixedLengthResponse(Status.INTERNAL_ERROR, "application/json", 
                "{\"error\":\"Internal server error\"}");
        }
        
        // Add CORS headers
        response.addHeader("Access-Control-Allow-Origin", "*");
        response.addHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        response.addHeader("Access-Control-Allow-Headers", "Content-Type");
        response.addHeader("Content-Type", "application/json; charset=utf-8");
        
        return response;
    }

    private Response handleRequest(String uri, Method method, Map<String, String> parms) {
        switch (uri) {
            case "/status":
                return handleStatus(parms);
            case "/lyric":
                return handleLyric();
            case "/lyric-all":
                return handleLyricAll();
            case "/play":
                return handlePlay();
            case "/pause":
                return handlePause();
            case "/skip-next":
                return handleSkipNext();
            case "/skip-prev":
                return handleSkipPrev();
            case "/seek":
                return handleSeek(parms);
            case "/volume":
                return handleVolume(parms);
            case "/mute":
                return handleMute(parms);
            case "/collect":
                return handleCollect();
            case "/uncollect":
                return handleUncollect();
            default:
                return newFixedLengthResponse(Status.NOT_FOUND, "application/json", 
                    "{\"error\":\"Not found\"}");
        }
    }

    /**
     * GET /status
     * Returns current player status
     * Optional filter parameter to select specific fields
     */
    private Response handleStatus(Map<String, String> parms) {
        synchronized (statusLock) {
            try {
                String filter = parms.get("filter");
                if (filter != null && !filter.isEmpty()) {
                    // Filter specific fields
                    JSONObject filtered = new JSONObject();
                    String[] fields = filter.split(",");
                    for (String field : fields) {
                        field = field.trim();
                        if (playerStatus.has(field)) {
                            filtered.put(field, playerStatus.get(field));
                        }
                    }
                    return newFixedLengthResponse(Status.OK, "application/json", filtered.toString());
                }
                return newFixedLengthResponse(Status.OK, "application/json", playerStatus.toString());
            } catch (Exception e) {
                Log.e(TAG, "Error getting status", e);
                return newFixedLengthResponse(Status.INTERNAL_ERROR, "application/json", 
                    "{\"error\":\"Failed to get status\"}");
            }
        }
    }

    /**
     * GET /lyric
     * Returns current song's LRC lyric
     */
    private Response handleLyric() {
        synchronized (statusLock) {
            try {
                String lyric = playerStatus.optString("lyric", "");
                return newFixedLengthResponse(Status.OK, "text/plain; charset=utf-8", lyric);
            } catch (Exception e) {
                Log.e(TAG, "Error getting lyric", e);
                return newFixedLengthResponse(Status.INTERNAL_ERROR, "application/json", 
                    "{\"error\":\"Failed to get lyric\"}");
            }
        }
    }

    /**
     * GET /lyric-all
     * Returns all types of lyrics (lyric, tlyric, rlyric, lxlyric)
     */
    private Response handleLyricAll() {
        synchronized (statusLock) {
            try {
                JSONObject lyrics = new JSONObject();
                lyrics.put("lyric", playerStatus.optString("lyric", ""));
                lyrics.put("tlyric", playerStatus.optString("tlyric", ""));
                lyrics.put("rlyric", playerStatus.optString("rlyric", ""));
                lyrics.put("lxlyric", playerStatus.optString("lxlyric", ""));
                return newFixedLengthResponse(Status.OK, "application/json", lyrics.toString());
            } catch (Exception e) {
                Log.e(TAG, "Error getting all lyrics", e);
                return newFixedLengthResponse(Status.INTERNAL_ERROR, "application/json", 
                    "{\"error\":\"Failed to get lyrics\"}");
            }
        }
    }

    /**
     * POST /play
     * Start playback
     */
    private Response handlePlay() {
        sendControlEvent("play", null);
        return newFixedLengthResponse(Status.OK, "text/plain", "OK");
    }

    /**
     * POST /pause
     * Pause playback
     */
    private Response handlePause() {
        sendControlEvent("pause", null);
        return newFixedLengthResponse(Status.OK, "text/plain", "OK");
    }

    /**
     * POST /skip-next
     * Skip to next track
     */
    private Response handleSkipNext() {
        sendControlEvent("skip-next", null);
        return newFixedLengthResponse(Status.OK, "text/plain", "OK");
    }

    /**
     * POST /skip-prev
     * Skip to previous track
     */
    private Response handleSkipPrev() {
        sendControlEvent("skip-prev", null);
        return newFixedLengthResponse(Status.OK, "text/plain", "OK");
    }

    /**
     * GET/POST /seek?offset=xxx
     * Seek to position (in seconds)
     */
    private Response handleSeek(Map<String, String> parms) {
        String offsetStr = parms.get("offset");
        if (offsetStr == null || offsetStr.isEmpty()) {
            return newFixedLengthResponse(Status.BAD_REQUEST, "application/json", 
                "{\"error\":\"Missing offset parameter\"}");
        }
        
        try {
            double offset = Double.parseDouble(offsetStr);
            if (offset < 0) {
                return newFixedLengthResponse(Status.BAD_REQUEST, "application/json", 
                    "{\"error\":\"Invalid offset value\"}");
            }
            
            JSONObject data = new JSONObject();
            data.put("offset", offset);
            sendControlEvent("seek", data);
            return newFixedLengthResponse(Status.OK, "text/plain", "OK");
        } catch (NumberFormatException e) {
            return newFixedLengthResponse(Status.BAD_REQUEST, "application/json", 
                "{\"error\":\"Invalid offset value\"}");
        }
    }

    /**
     * GET/POST /volume?volume=xxx
     * Set volume (1-100)
     */
    private Response handleVolume(Map<String, String> parms) {
        String volumeStr = parms.get("volume");
        if (volumeStr == null || volumeStr.isEmpty()) {
            return newFixedLengthResponse(Status.BAD_REQUEST, "application/json", 
                "{\"error\":\"Missing volume parameter\"}");
        }
        
        try {
            int volume = Integer.parseInt(volumeStr);
            if (volume < 0 || volume > 100) {
                return newFixedLengthResponse(Status.BAD_REQUEST, "application/json", 
                    "{\"error\":\"Invalid volume value (must be 1-100)\"}");
            }
            
            JSONObject data = new JSONObject();
            data.put("volume", volume);
            sendControlEvent("volume", data);
            return newFixedLengthResponse(Status.OK, "text/plain", "OK");
        } catch (NumberFormatException e) {
            return newFixedLengthResponse(Status.BAD_REQUEST, "application/json", 
                "{\"error\":\"Invalid volume value\"}");
        }
    }

    /**
     * GET/POST /mute?mute=true/false
     * Set mute state
     */
    private Response handleMute(Map<String, String> parms) {
        String muteStr = parms.get("mute");
        if (muteStr == null || muteStr.isEmpty()) {
            return newFixedLengthResponse(Status.BAD_REQUEST, "application/json", 
                "{\"error\":\"Missing mute parameter\"}");
        }
        
        boolean mute = Boolean.parseBoolean(muteStr);
        JSONObject data = new JSONObject();
        try {
            data.put("mute", mute);
        } catch (Exception e) {
            // ignore
        }
        sendControlEvent("mute", data);
        return newFixedLengthResponse(Status.OK, "text/plain", "OK");
    }

    /**
     * POST /collect
     * Add current song to favorites
     */
    private Response handleCollect() {
        sendControlEvent("collect", null);
        return newFixedLengthResponse(Status.OK, "text/plain", "OK");
    }

    /**
     * POST /uncollect
     * Remove current song from favorites
     */
    private Response handleUncollect() {
        sendControlEvent("uncollect", null);
        return newFixedLengthResponse(Status.OK, "text/plain", "OK");
    }

    /**
     * Update player status from JS
     */
    public void updatePlayerStatus(ReadableMap status) {
        synchronized (statusLock) {
            try {
                JSONObject newStatus = new JSONObject();
                
                // 播放状态
                newStatus.put("status", status.hasKey("status") ? status.getBoolean("status") : false);
                
                // 歌曲信息
                newStatus.put("name", status.hasKey("name") ? status.getString("name") : "");
                newStatus.put("singer", status.hasKey("singer") ? status.getString("singer") : "");
                newStatus.put("albumName", status.hasKey("albumName") ? status.getString("albumName") : "");
                
                // 歌词
                newStatus.put("lyricLineText", status.hasKey("lyricLineText") ? status.getString("lyricLineText") : "");
                newStatus.put("lyric", status.hasKey("lyric") ? status.getString("lyric") : "");
                newStatus.put("tlyric", status.hasKey("tlyric") ? status.getString("tlyric") : "");
                newStatus.put("rlyric", status.hasKey("rlyric") ? status.getString("rlyric") : "");
                newStatus.put("lxlyric", status.hasKey("lxlyric") ? status.getString("lxlyric") : "");
                
                // 播放进度
                newStatus.put("duration", status.hasKey("duration") ? status.getDouble("duration") : 0);
                newStatus.put("progress", status.hasKey("progress") ? status.getDouble("progress") : 0);
                newStatus.put("playbackRate", status.hasKey("playbackRate") ? status.getDouble("playbackRate") : 1);
                
                // 音量
                newStatus.put("volume", status.hasKey("volume") ? status.getInt("volume") : 100);
                
                this.playerStatus = newStatus;
                Log.d(TAG, "Player status updated: " + newStatus.toString());
            } catch (Exception e) {
                Log.e(TAG, "Failed to update player status", e);
            }
        }
    }

    /**
     * Send control event to JS
     */
    private void sendControlEvent(String action, JSONObject data) {
        try {
            WritableMap params = Arguments.createMap();
            params.putString("action", action);
            if (data != null) {
                params.putString("data", data.toString());
            }
            
            reactContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                .emit("OpenApiControl", params);
            
            Log.d(TAG, "Control event sent: " + action);
        } catch (Exception e) {
            Log.e(TAG, "Failed to send control event", e);
        }
    }

    /**
     * Get server address string
     */
    public String getAddressString() {
        try {
            int port = getListeningPort();
            return hostname + ":" + port;
        } catch (Exception e) {
            return hostname + ":" + super.getListeningPort();
        }
    }

    /**
     * Check if server is running
     */
    public boolean isServerRunning() {
        return isAlive();
    }
}
