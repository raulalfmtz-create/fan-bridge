package com.fanbridge.local;

import android.content.Context;
import android.provider.Settings;
import android.util.Base64;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.TimeUnit;\nimport java.util.LinkedHashMap;\nimport java.util.Iterator;\nimport java.util.Map;

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

    private final Context context;
    private final Listener listener;
    private final OkHttpClient httpClient;

    private WebSocket socket;
    private String deviceId = "";
    private String appKey = "";
    private String appSecret = "";
    private boolean connected = false;
    private boolean manualDisconnect = false;\n    private final LinkedHashMap<String, Long> processedReplyTokens = new LinkedHashMap<>();\n    private static final long REPLY_TOKEN_TTL_MS = 60000L;

    public SinricClient(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
        this.httpClient = new OkHttpClient.Builder()
                .pingInterval(5, TimeUnit.MINUTES)
                .retryOnConnectionFailure(true)
                .build();
    }

    public synchronized void connect(String deviceId, String appKey, String appSecret) {
        if (connected && this.deviceId.equals(deviceId)
                && this.appKey.equals(appKey) && this.appSecret.equals(appSecret)) {
            return;
        }

        disconnect();

        this.deviceId = deviceId;
        this.appKey = appKey;
        this.appSecret = appSecret;
        this.manualDisconnect = false;

        listener.onSinricStatus("Sinric Pro conectando…", false);

        String androidId = Settings.Secure.getString(
                context.getContentResolver(), Settings.Secure.ANDROID_ID);
        if (androidId == null || androidId.isEmpty()) androidId = "fanbridge";

        Request request = new Request.Builder()
                .url("wss://ws.sinric.pro:443/")
                .addHeader("appkey", appKey)
                .addHeader("deviceids", deviceId)
                .addHeader("platform", "Android")
                .addHeader("SDKVersion", "FanBridge-0.4")
                .addHeader("mac", "android-" + androidId)
                .build();

        socket = httpClient.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                connected = true;
                listener.onSinricStatus("Sinric Pro conectado ✓", true);
            }

            @Override
            public void onMessage(WebSocket webSocket, String text) {
                handleMessage(webSocket, text);
            }

            @Override
            public void onClosing(WebSocket webSocket, int code, String reason) {
                connected = false;
                webSocket.close(code, reason);
                listener.onSinricStatus("Sinric Pro desconectando…", false);
            }

            @Override
            public void onClosed(WebSocket webSocket, int code, String reason) {
                connected = false;
                if (!manualDisconnect) {
                    listener.onSinricStatus("Sinric Pro desconectado · reabre Fan Bridge para reconectar", false);
                }
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                connected = false;
                if (!manualDisconnect) {
                    String msg = t.getMessage() == null ? "error de conexión" : t.getMessage();
                    listener.onSinricStatus("Sinric Pro: " + msg, false);
                }
            }
        });
    }

    private void handleMessage(WebSocket webSocket, String raw) {
        try {
            JSONObject root = new JSONObject(raw);

            // Sinric puede enviar un mensaje de timestamp que no lleva payload.
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

                // A resent Sinric request must receive a response, but must not
                // trigger another BLE transmission.
                if (isDuplicateReplyToken(replyToken)) {
                    JSONObject responseValue = new JSONObject();
                    responseValue.put("state", on ? "On" : "Off");
                    sendResponse(webSocket, payload, true, responseValue, "OK");
                    return;
                }

                rememberReplyToken(replyToken);

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

    private synchronized boolean isDuplicateReplyToken(String token) {
        if (token == null || token.isEmpty()) return false;
        pruneReplyTokens();
        return processedReplyTokens.containsKey(token);
    }

    private synchronized void rememberReplyToken(String token) {
        if (token == null || token.isEmpty()) return;
        pruneReplyTokens();
        processedReplyTokens.put(token, System.currentTimeMillis());

        while (processedReplyTokens.size() > 50) {
            Iterator<String> it = processedReplyTokens.keySet().iterator();
            if (it.hasNext()) {
                it.next();
                it.remove();
            } else {
                break;
            }
        }
    }

    private synchronized void pruneReplyTokens() {
        long cutoff = System.currentTimeMillis() - REPLY_TOKEN_TTL_MS;
        Iterator<Map.Entry<String, Long>> it = processedReplyTokens.entrySet().iterator();
        while (it.hasNext()) {
            if (it.next().getValue() < cutoff) it.remove();
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
        manualDisconnect = true;
        connected = false;
        if (socket != null) {
            socket.close(1000, "Fan Bridge reconnect");
            socket = null;
        }
    }

    public String getShortStatus() {
        return connected ? "Sinric conectado" : "Sinric sin conexión";
    }
}
