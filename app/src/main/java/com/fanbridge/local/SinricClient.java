package com.fanbridge.local;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Base64;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

public class SinricClient {

    public interface Listener {
        boolean onPowerState(boolean on);
        void onSinricStatus(String text, boolean connected);
    }

    private static final long[] RECONNECT_DELAYS_MS = {
            5000L, 15000L, 30000L, 60000L
    };

    private final Context context;
    private final Listener listener;
    private final OkHttpClient httpClient;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private WebSocket socket;
    private String deviceId = "";
    private String appKey = "";
    private String appSecret = "";

    private boolean connected = false;
    private boolean connecting = false;
    private boolean shouldReconnect = false;
    private boolean reconnectScheduled = false;
    private int reconnectAttempt = 0;
    private int generation = 0;

    private final Set<String> processedReplyTokens = new HashSet<>();

    public SinricClient(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
        this.httpClient = new OkHttpClient.Builder()
                .pingInterval(5, TimeUnit.MINUTES)
                .retryOnConnectionFailure(true)
                .build();
    }

    public synchronized void connect(String deviceId, String appKey, String appSecret) {
        boolean sameCredentials = this.deviceId.equals(deviceId)
                && this.appKey.equals(appKey)
                && this.appSecret.equals(appSecret);

        if (sameCredentials && shouldReconnect
                && (connected || connecting || reconnectScheduled)) {
            return;
        }

        stopSocket(false);
        processedReplyTokens.clear();

        this.deviceId = deviceId;
        this.appKey = appKey;
        this.appSecret = appSecret;
        this.shouldReconnect = true;
        this.reconnectAttempt = 0;

        listener.onSinricStatus("Sinric Pro conectando…", false);
        openSocket();
    }

    private synchronized void openSocket() {
        if (!shouldReconnect || connecting || connected) return;
        if (deviceId.isEmpty() || appKey.isEmpty() || appSecret.isEmpty()) return;

        reconnectScheduled = false;
        connecting = true;

        final int myGeneration = ++generation;

        String androidId = Settings.Secure.getString(
                context.getContentResolver(), Settings.Secure.ANDROID_ID);
        if (androidId == null || androidId.isEmpty()) androidId = "fanbridge";

        Request request = new Request.Builder()
                .url("wss://ws.sinric.pro:443/")
                .addHeader("appkey", appKey)
                .addHeader("deviceids", deviceId)
                .addHeader("platform", "Android")
                .addHeader("SDKVersion", "FanBridge-0.7")
                .addHeader("mac", "android-" + androidId)
                .build();

        socket = httpClient.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                synchronized (SinricClient.this) {
                    if (myGeneration != generation || !shouldReconnect) {
                        webSocket.close(1000, "stale connection");
                        return;
                    }
                    connected = true;
                    connecting = false;
                    reconnectScheduled = false;
                    reconnectAttempt = 0;
                }
                listener.onSinricStatus("Sinric Pro conectado ✓", true);
            }

            @Override
            public void onMessage(WebSocket webSocket, String text) {
                synchronized (SinricClient.this) {
                    if (myGeneration != generation || !connected) return;
                }
                handleMessage(webSocket, text);
            }

            @Override
            public void onClosing(WebSocket webSocket, int code, String reason) {
                webSocket.close(code, reason);
            }

            @Override
            public void onClosed(WebSocket webSocket, int code, String reason) {
                synchronized (SinricClient.this) {
                    if (myGeneration != generation) return;
                    connected = false;
                    connecting = false;
                    socket = null;
                }

                if (shouldReconnect) {
                    String detail = reason == null || reason.trim().isEmpty()
                            ? "conexión cerrada"
                            : reason.trim();
                    scheduleReconnect(detail);
                }
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                synchronized (SinricClient.this) {
                    if (myGeneration != generation) return;
                    connected = false;
                    connecting = false;
                    socket = null;
                }

                if (shouldReconnect) {
                    String detail = t.getMessage() == null
                            ? "error de conexión"
                            : t.getMessage();
                    scheduleReconnect(detail);
                }
            }
        });
    }

    private synchronized void scheduleReconnect(String reason) {
        if (!shouldReconnect || reconnectScheduled) return;

        long delay = RECONNECT_DELAYS_MS[
                Math.min(reconnectAttempt, RECONNECT_DELAYS_MS.length - 1)];
        reconnectAttempt++;
        reconnectScheduled = true;

        long seconds = delay / 1000L;
        listener.onSinricStatus(
                "Sinric Pro desconectado · reintento en " + seconds + " s · " + sanitizeReason(reason),
                false);

        final int expectedGeneration = generation;
        handler.postDelayed(() -> {
            synchronized (SinricClient.this) {
                if (!shouldReconnect || expectedGeneration != generation) {
                    reconnectScheduled = false;
                    return;
                }
                reconnectScheduled = false;
            }
            listener.onSinricStatus("Sinric Pro reconectando…", false);
            openSocket();
        }, delay);
    }

    private String sanitizeReason(String reason) {
        if (reason == null) return "sin detalle";
        String clean = reason.replace("\n", " ").replace("\r", " ").trim();
        if (clean.length() > 70) clean = clean.substring(0, 70);
        return clean.isEmpty() ? "sin detalle" : clean;
    }

    private void handleMessage(WebSocket webSocket, String raw) {
        try {
            JSONObject root = new JSONObject(raw);

            if (root.has("timestamp") && !root.has("payload")) return;
            if (!root.has("payload") || !root.has("signature")) return;

            if (!validateIncoming(raw, root)) {
                listener.onSinricStatus("Sinric Pro: firma inválida", false);
                return;
            }

            JSONObject payload = root.getJSONObject("payload");
            if (!"request".equals(payload.optString("type"))) return;

            String incomingDeviceId = payload.optString("deviceId", "");
            if (!deviceId.equalsIgnoreCase(incomingDeviceId)) return;

            String action = payload.optString("action", "");
            if ("setPowerState".equals(action)) {
                JSONObject value = payload.optJSONObject("value");
                String state = value != null ? value.optString("state", "") : "";
                boolean on = "On".equalsIgnoreCase(state);
                boolean off = "Off".equalsIgnoreCase(state);

                if (!on && !off) {
                    sendResponse(webSocket, payload, false, new JSONObject(), "Estado no válido");
                    return;
                }

                String replyToken = payload.optString("replyToken", "");

                if (!replyToken.isEmpty() && processedReplyTokens.contains(replyToken)) {
                    JSONObject responseValue = new JSONObject();
                    responseValue.put("state", on ? "On" : "Off");
                    sendResponse(webSocket, payload, true, responseValue, "OK");
                    return;
                }

                if (!replyToken.isEmpty()) {
                    if (processedReplyTokens.size() > 100) processedReplyTokens.clear();
                    processedReplyTokens.add(replyToken);
                }

                boolean success = listener.onPowerState(on);
                JSONObject responseValue = new JSONObject();
                if (success) responseValue.put("state", on ? "On" : "Off");
                sendResponse(webSocket, payload, success, responseValue,
                        success ? "OK" : "No se pudo enviar el comando BLE");
            } else {
                sendResponse(webSocket, payload, false, new JSONObject(),
                        "Acción aún no soportada por Fan Bridge");
            }
        } catch (Exception e) {
            listener.onSinricStatus("Sinric Pro: mensaje no válido", false);
        }
    }

    private void sendResponse(WebSocket webSocket, JSONObject requestPayload,
                              boolean success, JSONObject value, String message) throws Exception {
        JSONObject payload = new JSONObject();
        payload.put("action", requestPayload.optString("action", ""));
        if (requestPayload.has("clientId")) {
            payload.put("clientId", requestPayload.optString("clientId", ""));
        }
        payload.put("createdAt", System.currentTimeMillis() / 1000L);
        payload.put("deviceId", requestPayload.optString("deviceId", deviceId));
        payload.put("message", message);
        payload.put("replyToken", requestPayload.optString("replyToken", ""));
        payload.put("scope", "device");
        payload.put("success", success);
        payload.put("type", "response");
        payload.put("value", value);
        if (requestPayload.has("instanceId")) {
            payload.put("instanceId", requestPayload.optString("instanceId", ""));
        }

        String payloadStr = payload.toString();
        String signature = hmacBase64(payloadStr);

        JSONObject header = new JSONObject();
        header.put("payloadVersion", 2);
        header.put("signatureVersion", 1);

        String envelope = "{\"header\":" + header.toString()
                + ",\"payload\":" + payloadStr
                + ",\"signature\":{\"HMAC\":" + JSONObject.quote(signature) + "}}";

        webSocket.send(envelope);
    }

    private boolean validateIncoming(String raw, JSONObject root) {
        try {
            String marker = "\"payload\":";
            String sigMarker = ",\"signature\"";
            int begin = raw.indexOf(marker);
            int end = raw.indexOf(sigMarker, begin);
            if (begin < 0 || end < 0) return false;

            String payloadRaw = raw.substring(begin + marker.length(), end);
            String expected = hmacBase64(payloadRaw);
            String claimed = root.getJSONObject("signature").optString("HMAC", "");

            return MessageDigest.isEqual(
                    expected.getBytes(StandardCharsets.UTF_8),
                    claimed.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            return false;
        }
    }

    private String hmacBase64(String message) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(appSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] bytes = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
        return Base64.encodeToString(bytes, Base64.NO_WRAP);
    }

    public synchronized void disconnect() {
        stopSocket(true);
    }

    private synchronized void stopSocket(boolean manualStop) {
        shouldReconnect = !manualStop;
        reconnectScheduled = false;
        connected = false;
        connecting = false;
        reconnectAttempt = 0;

        generation++;
        handler.removeCallbacksAndMessages(null);

        if (socket != null) {
            socket.cancel();
            socket = null;
        }
    }

    public synchronized String getShortStatus() {
        if (connected) return "Sinric conectado";
        if (connecting || reconnectScheduled) return "Sinric reconectando";
        return "Sinric sin conexión";
    }
}
