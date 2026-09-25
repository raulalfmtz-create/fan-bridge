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

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {

    public static final String PREFS = "fan_bridge_prefs";
    public static final String KEY_DEVICE_ID = "sinric_device_id";
    public static final String KEY_APP_KEY = "sinric_app_key";
    public static final String KEY_APP_SECRET = "sinric_app_secret";
    public static final String KEY_SINRIC_STATUS = "sinric_status";

    private static final int REQ_PERMS = 1001;

    private BluetoothAdapter adapter;
    private TextView status;
    private Button onButton;
    private Button offButton;
    private EditText deviceIdInput;
    private EditText appKeyInput;
    private EditText appSecretInput;

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
                sendToBridge(FanBridgeService.ACTION_ON, "Orden local enviada · velocidad 3"));
        offButton.setOnClickListener(v ->
                sendToBridge(FanBridgeService.ACTION_OFF, "Orden local enviada · apagar"));
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
        subtitle.setText("v0.3 · Sinric Pro → Flip 4 → BLE");
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

        TextView section = new TextView(this);
        section.setText("Sinric Pro");
        section.setTextSize(20);
        section.setTextColor(Color.rgb(63, 48, 38));
        LinearLayout.LayoutParams sectionLp = fullWrap();
        sectionLp.setMargins(0, dp(18), 0, dp(8));
        root.addView(section, sectionLp);

        deviceIdInput = makeInput("Device ID (24 caracteres)");
        root.addView(deviceIdInput, fullWrapWithBottom(gap));

        appKeyInput = makeInput("App Key");
        root.addView(appKeyInput, fullWrapWithBottom(gap));

        appSecretInput = makeInput("App Secret");
        appSecretInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        root.addView(appSecretInput, fullWrapWithBottom(gap));

        Button save = makeButton("GUARDAR Y CONECTAR");
        save.setOnClickListener(v -> saveAndConnect());
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
        note.setText("Después de guardar, revisa la notificación de Fan Bridge. Debe indicar “Sinric Pro conectado”.");
        note.setTextSize(14);
        note.setTextColor(Color.GRAY);
        note.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams noteLp = fullWrap();
        noteLp.setMargins(0, dp(24), 0, 0);
        root.addView(note, noteLp);

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

    private void saveAndConnect() {
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

        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putString(KEY_DEVICE_ID, deviceId)
                .putString(KEY_APP_KEY, appKey)
                .putString(KEY_APP_SECRET, appSecret)
                .apply();

        sendToBridge(FanBridgeService.ACTION_RECONNECT, "Credenciales guardadas · conectando a Sinric Pro");
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
            setButtons(false);
            return;
        }
        if (!hasBtPermissions()) {
            setStatus("Da permiso de dispositivos cercanos/Bluetooth.", true);
            setButtons(false);
            return;
        }

        try {
            if (!adapter.isEnabled()) {
                setStatus("Activa Bluetooth para continuar.", true);
                setButtons(false);
                return;
            }
        } catch (SecurityException e) {
            setStatus("Falta permiso de Bluetooth.", true);
            setButtons(false);
            return;
        }

        startBridgeService();

        SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
        String sinricStatus = p.getString(KEY_SINRIC_STATUS, "");
        if (sinricStatus.isEmpty()) {
            setStatus("Bluetooth listo · configura Sinric Pro", false);
        } else {
            setStatus("Bluetooth listo · " + sinricStatus, false);
        }
        setButtons(true);
    }

    private void startBridgeService() {
        Intent intent = new Intent(this, FanBridgeService.class).setAction(FanBridgeService.ACTION_START);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent);
        else startService(intent);
    }

    private void sendToBridge(String action, String message) {
        if (!hasBtPermissions()) {
            requestPermissionsIfNeeded();
            return;
        }

        Intent intent = new Intent(this, FanBridgeService.class).setAction(action);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent);
        else startService(intent);
        setStatus(message + " ✓", false);
    }

    private void setButtons(boolean enabled) {
        if (onButton != null) onButton.setEnabled(enabled);
        if (offButton != null) offButton.setEnabled(enabled);
    }

    private void setStatus(String text, boolean error) {
        runOnUiThread(() -> {
            status.setText(text);
            status.setTextColor(error ? Color.rgb(170, 40, 40) : Color.rgb(48, 110, 70));
        });
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
    }
}
