package com.fanbridge.local;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {

    public static final String PREFS = "fan_bridge_prefs";
    public static final String KEY_DEVICE_ID = "sinric_device_id";
    public static final String KEY_APP_KEY = "sinric_app_key";
    public static final String KEY_APP_SECRET = "sinric_app_secret";
    public static final String KEY_SINRIC_STATUS = "sinric_status";
    public static final String KEY_BRIDGE_ENABLED = "bridge_enabled";
    public static final String KEY_EVENT_HISTORY = "event_history";
    private static final int MAX_HISTORY_LINES = 80;

    private static final int REQ_PERMS = 1001;

    private BluetoothAdapter adapter;
    private TextView status;
    private Button onButton;
    private Button offButton;
    private Button enableBridgeButton;
    private Button stopBridgeButton;
    private EditText deviceIdInput;
    private EditText appKeyInput;
    private EditText appSecretInput;
    private TextView historyText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();

        BluetoothManager manager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        adapter = manager != null ? manager.getAdapter() : null;

        loadCredentials();
        requestPermissionsIfNeeded();
        updateReadyState();

        onButton.setOnClickListener(v ->
                sendLocalCommand(FanBridgeService.ACTION_ON, "Orden local enviada · velocidad 3"));
        offButton.setOnClickListener(v ->
                sendLocalCommand(FanBridgeService.ACTION_OFF, "Orden local enviada · apagar"));

        enableBridgeButton.setOnClickListener(v -> enableBridge());
        stopBridgeButton.setOnClickListener(v -> stopBridge());
    }

    private void buildUi() {
        int pad = dp(24);
        int gap = dp(14);

        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(pad, dp(40), pad, dp(40));
        root.setBackgroundColor(Color.rgb(246, 244, 239));
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText("Fan Bridge");
        title.setTextSize(30);
        title.setTextColor(Color.rgb(63, 48, 38));
        title.setGravity(Gravity.CENTER);
        root.addView(title, fullWrap());

        TextView subtitle = new TextView(this);
        subtitle.setText("v0.7 · reconexión automática · historial");
        subtitle.setTextSize(16);
        subtitle.setTextColor(Color.DKGRAY);
        subtitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams subLp = fullWrap();
        subLp.setMargins(0, dp(8), 0, dp(20));
        root.addView(subtitle, subLp);

        status = new TextView(this);
        status.setTextSize(15);
        status.setGravity(Gravity.CENTER);
        status.setPadding(dp(12), dp(12), dp(12), dp(12));
        root.addView(status, fullWrap());

        TextView safetyTitle = new TextView(this);
        safetyTitle.setText("Seguridad");
        safetyTitle.setTextSize(20);
        safetyTitle.setTextColor(Color.rgb(63, 48, 38));
        LinearLayout.LayoutParams safetyTitleLp = fullWrap();
        safetyTitleLp.setMargins(0, dp(18), 0, dp(8));
        root.addView(safetyTitle, safetyTitleLp);

        TextView safetyInfo = new TextView(this);
        safetyInfo.setText("El puente inicia detenido después de instalar v0.5. Cada orden manda un solo pulso BLE corto. Si llegan demasiadas órdenes, Fan Bridge se desconecta solo.");
        safetyInfo.setTextSize(14);
        safetyInfo.setTextColor(Color.DKGRAY);
        safetyInfo.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams safetyInfoLp = fullWrap();
        safetyInfoLp.setMargins(0, 0, 0, gap);
        root.addView(safetyInfo, safetyInfoLp);

        enableBridgeButton = makeButton("ACTIVAR PUENTE");
        root.addView(enableBridgeButton, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(58)));

        stopBridgeButton = makeButton("DETENER PUENTE");
        LinearLayout.LayoutParams stopLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(58));
        stopLp.setMargins(0, gap, 0, dp(24));
        root.addView(stopBridgeButton, stopLp);

        TextView section = new TextView(this);
        section.setText("Sinric Pro");
        section.setTextSize(20);
        section.setTextColor(Color.rgb(63, 48, 38));
        LinearLayout.LayoutParams sectionLp = fullWrap();
        sectionLp.setMargins(0, dp(8), 0, dp(8));
        root.addView(section, sectionLp);

        deviceIdInput = makeInput("Device ID (24 caracteres)");
        root.addView(deviceIdInput, fullWrapWithBottom(gap));

        appKeyInput = makeInput("App Key");
        root.addView(appKeyInput, fullWrapWithBottom(gap));

        appSecretInput = makeInput("App Secret");
        appSecretInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        root.addView(appSecretInput, fullWrapWithBottom(gap));

        Button save = makeButton("GUARDAR CREDENCIALES");
        save.setOnClickListener(v -> saveCredentials());
        root.addView(save, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(58)));

        TextView privacy = new TextView(this);
        privacy.setText("Las credenciales se guardan en los datos privados de esta app en el Flip. No se suben a GitHub.");
        privacy.setTextSize(13);
        privacy.setTextColor(Color.GRAY);
        privacy.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams privLp = fullWrap();
        privLp.setMargins(0, dp(10), 0, dp(24));
        root.addView(privacy, privLp);

        TextView test = new TextView(this);
        test.setText("Prueba local");
        test.setTextSize(20);
        test.setTextColor(Color.rgb(63, 48, 38));
        LinearLayout.LayoutParams testLp = fullWrap();
        testLp.setMargins(0, dp(8), 0, dp(8));
        root.addView(test, testLp);

        onButton = makeButton("ENCENDER · VELOCIDAD 3");
        root.addView(onButton, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(58)));

        offButton = makeButton("APAGAR VENTILADOR");
        LinearLayout.LayoutParams offLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(58));
        offLp.setMargins(0, gap, 0, 0);
        root.addView(offButton, offLp);

        TextView note = new TextView(this);
        note.setText("Si notas pitidos repetidos, toca DETENER PUENTE. La conexión con Sinric se corta y Fan Bridge deja de transmitir por BLE.");
        note.setTextSize(14);
        note.setTextColor(Color.GRAY);
        note.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams noteLp = fullWrap();
        noteLp.setMargins(0, dp(24), 0, dp(24));
        root.addView(note, noteLp);

        TextView historyTitle = new TextView(this);
        historyTitle.setText("Historial");
        historyTitle.setTextSize(20);
        historyTitle.setTextColor(Color.rgb(63, 48, 38));
        root.addView(historyTitle, fullWrapWithBottom(dp(8)));

        TextView historyInfo = new TextView(this);
        historyInfo.setText("Guarda conexiones, reconexiones, órdenes de Alexa y activaciones de protección.");
        historyInfo.setTextSize(13);
        historyInfo.setTextColor(Color.GRAY);
        LinearLayout.LayoutParams historyInfoLp = fullWrap();
        historyInfoLp.setMargins(0, 0, 0, dp(10));
        root.addView(historyInfo, historyInfoLp);

        historyText = new TextView(this);
        historyText.setTextSize(13);
        historyText.setTextColor(Color.DKGRAY);
        historyText.setPadding(dp(10), dp(10), dp(10), dp(10));
        root.addView(historyText, fullWrapWithBottom(dp(10)));

        Button refreshHistory = makeButton("ACTUALIZAR HISTORIAL");
        refreshHistory.setOnClickListener(v -> updateHistoryView());
        root.addView(refreshHistory, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(52)));

        Button clearHistory = makeButton("BORRAR HISTORIAL");
        clearHistory.setOnClickListener(v -> {
            clearEventHistory(this);
            appendEvent(this, "Historial borrado manualmente");
            updateHistoryView();
        });
        LinearLayout.LayoutParams clearHistoryLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(52));
        clearHistoryLp.setMargins(0, gap, 0, 0);
        root.addView(clearHistory, clearHistoryLp);

        updateHistoryView();
        setContentView(scroll);
    }

    private EditText makeInput(String hint) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setSingleLine(true);
        e.setTextSize(15);
        e.setPadding(dp(12), dp(8), dp(12), dp(8));
        return e;
    }

    private Button makeButton(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(16);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(95, 72, 55)));
        return b;
    }

    private LinearLayout.LayoutParams fullWrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams fullWrapWithBottom(int bottom) {
        LinearLayout.LayoutParams lp = fullWrap();
        lp.setMargins(0, 0, 0, bottom);
        return lp;
    }

    private void loadCredentials() {
        SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
        deviceIdInput.setText(p.getString(KEY_DEVICE_ID, ""));
        appKeyInput.setText(p.getString(KEY_APP_KEY, ""));
        appSecretInput.setText(p.getString(KEY_APP_SECRET, ""));
    }

    private void saveCredentials() {
        String deviceId = deviceIdInput.getText().toString().trim();
        String appKey = appKeyInput.getText().toString().trim();
        String appSecret = appSecretInput.getText().toString().trim();

        if (!deviceId.matches("(?i)[a-f0-9]{24}")) {
            setStatus("Device ID inválido: debe tener 24 caracteres hexadecimales.", true);
            return;
        }
        if (!appKey.matches("(?i)[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}")) {
            setStatus("App Key inválida.", true);
            return;
        }
        if (appSecret.length() < 32) {
            setStatus("App Secret inválida.", true);
            return;
        }

        SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
        p.edit()
                .putString(KEY_DEVICE_ID, deviceId)
                .putString(KEY_APP_KEY, appKey)
                .putString(KEY_APP_SECRET, appSecret)
                .apply();

        if (p.getBoolean(KEY_BRIDGE_ENABLED, false)) {
            sendServiceAction(FanBridgeService.ACTION_RECONNECT);
            setStatus("Credenciales guardadas · reconectando a Sinric Pro", false);
        } else {
            setStatus("Credenciales guardadas · puente detenido", false);
        }
    }

    private void enableBridge() {
        if (!hasBtPermissions()) {
            requestPermissionsIfNeeded();
            return;
        }

        try {
            if (adapter == null || !adapter.isEnabled()) {
                setStatus("Activa Bluetooth antes de activar el puente.", true);
                return;
            }
        } catch (SecurityException e) {
            setStatus("Falta permiso de Bluetooth.", true);
            return;
        }

        getSharedPreferences(PREFS, MODE_PRIVATE)
                .edit().putBoolean(KEY_BRIDGE_ENABLED, true).apply();

        sendServiceAction(FanBridgeService.ACTION_ENABLE_BRIDGE);
        setStatus("Puente activado · conectando a Sinric Pro", false);
        updateBridgeButtons(true);
    }

    private void stopBridge() {
        getSharedPreferences(PREFS, MODE_PRIVATE)
                .edit().putBoolean(KEY_BRIDGE_ENABLED, false).apply();

        sendServiceAction(FanBridgeService.ACTION_STOP_BRIDGE);
        setStatus("Puente detenido · sin transmisión BLE", false);
        updateBridgeButtons(false);
    }

    private void requestPermissionsIfNeeded() {
        List<String> wanted = new ArrayList<>();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED) {
                wanted.add(Manifest.permission.BLUETOOTH_ADVERTISE);
            }
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                wanted.add(Manifest.permission.BLUETOOTH_CONNECT);
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            wanted.add(Manifest.permission.POST_NOTIFICATIONS);
        }

        if (!wanted.isEmpty()) {
            requestPermissions(wanted.toArray(new String[0]), REQ_PERMS);
        }
    }

    private boolean hasBtPermissions() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true;
        return checkSelfPermission(Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED
                && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
    }

    private void updateReadyState() {
        if (adapter == null) {
            setStatus("Este teléfono no tiene Bluetooth disponible.", true);
            updateBridgeButtons(false);
            return;
        }

        if (!hasBtPermissions()) {
            setStatus("Da permiso de dispositivos cercanos/Bluetooth.", true);
            updateBridgeButtons(false);
            return;
        }

        try {
            if (!adapter.isEnabled()) {
                setStatus("Activa Bluetooth para continuar.", true);
                updateBridgeButtons(false);
                return;
            }
        } catch (SecurityException e) {
            setStatus("Falta permiso de Bluetooth.", true);
            updateBridgeButtons(false);
            return;
        }

        startBridgeService();

        SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
        boolean enabled = p.getBoolean(KEY_BRIDGE_ENABLED, false);

        if (!enabled) {
            setStatus("Modo seguro · puente detenido", false);
        } else {
            String sinricStatus = p.getString(KEY_SINRIC_STATUS, "");
            setStatus(sinricStatus.isEmpty()
                    ? "Puente activo · conectando a Sinric Pro"
                    : "Puente activo · " + sinricStatus, false);
        }

        updateBridgeButtons(enabled);
    }

    private void updateBridgeButtons(boolean enabled) {
        if (onButton != null) onButton.setEnabled(enabled);
        if (offButton != null) offButton.setEnabled(enabled);
        if (enableBridgeButton != null) enableBridgeButton.setEnabled(!enabled);
        if (stopBridgeButton != null) stopBridgeButton.setEnabled(enabled);
    }

    private void startBridgeService() {
        sendServiceAction(FanBridgeService.ACTION_START);
    }

    private void sendLocalCommand(String action, String message) {
        if (!getSharedPreferences(PREFS, MODE_PRIVATE)
                .getBoolean(KEY_BRIDGE_ENABLED, false)) {
            setStatus("El puente está detenido. Actívalo primero.", true);
            return;
        }

        sendServiceAction(action);
        setStatus(message + " ✓", false);
    }

    private void sendServiceAction(String action) {
        Intent intent = new Intent(this, FanBridgeService.class).setAction(action);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent);
        else startService(intent);
    }

    private void setStatus(String text, boolean error) {
        runOnUiThread(() -> {
            status.setText(text);
            status.setTextColor(error ? Color.rgb(170, 40, 40) : Color.rgb(48, 110, 70));
        });
    }

    public static synchronized void appendEvent(Context context, String event) {
        if (context == null || event == null || event.trim().isEmpty()) return;

        String clean = event.replace("\n", " ").replace("\r", " ").trim();
        String stamp = new SimpleDateFormat("dd/MM HH:mm:ss", Locale.getDefault())
                .format(new Date());
        String newLine = stamp + "  " + clean;

        SharedPreferences p = context.getSharedPreferences(PREFS, MODE_PRIVATE);
        String old = p.getString(KEY_EVENT_HISTORY, "");
        String combined = old.isEmpty() ? newLine : old + "\n" + newLine;
        String[] lines = combined.split("\n");

        int start = Math.max(0, lines.length - MAX_HISTORY_LINES);
        StringBuilder trimmed = new StringBuilder();
        for (int i = start; i < lines.length; i++) {
            if (trimmed.length() > 0) trimmed.append("\n");
            trimmed.append(lines[i]);
        }

        p.edit().putString(KEY_EVENT_HISTORY, trimmed.toString()).apply();
    }

    public static synchronized String getEventHistory(Context context) {
        return context.getSharedPreferences(PREFS, MODE_PRIVATE)
                .getString(KEY_EVENT_HISTORY, "");
    }

    public static synchronized void clearEventHistory(Context context) {
        context.getSharedPreferences(PREFS, MODE_PRIVATE)
                .edit().remove(KEY_EVENT_HISTORY).apply();
    }

    private void updateHistoryView() {
        if (historyText == null) return;
        String history = getEventHistory(this);
        historyText.setText(history.isEmpty() ? "Sin eventos todavía." : history);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_PERMS) updateReadyState();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateReadyState();
        updateHistoryView();
    }
}
