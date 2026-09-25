package com.fanbridge.local;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelUuid;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;
import java.util.UUID;

public class MainActivity extends Activity {

    private static final int REQ_BT = 1001;
    private static final long ADVERTISE_MS = 900;

    // Capturado y validado en el ventilador: VELOCIDAD 3
    // Raw original:
    // 0201011B03F00820823639FD5FC739F96D1B64770FB184FF00A5C6532BB458
    private static final int[] FAN_ON_SPEED_3 = {
            0x08F0, 0x8220, 0x3936, 0x5FFD, 0x39C7, 0x6DF9, 0x641B,
            0x0F77, 0x84B1, 0x00FF, 0xC6A5, 0x2B53, 0x58B4
    };

    // Capturado y validado en el ventilador: FAN OFF
    // Raw original:
    // 0201011B03F0082082361FFD5FC739F96D1B64770FB180FF9B72C6532B932E
    private static final int[] FAN_OFF = {
            0x08F0, 0x8220, 0x1F36, 0x5FFD, 0x39C7, 0x6DF9, 0x641B,
            0x0F77, 0x80B1, 0x9BFF, 0xC672, 0x2B53, 0x2E93
    };

    private BluetoothAdapter adapter;
    private BluetoothLeAdvertiser advertiser;
    private AdvertiseCallback activeCallback;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView status;
    private Button onButton;
    private Button offButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();

        BluetoothManager manager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        adapter = manager != null ? manager.getAdapter() : null;

        requestBtPermissionsIfNeeded();
        updateReadyState();

        onButton.setOnClickListener(v -> transmit(FAN_ON_SPEED_3, "Encendiendo · velocidad 3"));
        offButton.setOnClickListener(v -> transmit(FAN_OFF, "Apagando ventilador"));
    }

    private void buildUi() {
        int pad = dp(24);
        int gap = dp(16);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(pad, dp(48), pad, pad);
        root.setBackgroundColor(Color.rgb(246, 244, 239));

        TextView title = new TextView(this);
        title.setText("Fan Bridge");
        title.setTextSize(30);
        title.setTextColor(Color.rgb(63, 48, 38));
        title.setGravity(Gravity.CENTER);
        root.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView subtitle = new TextView(this);
        subtitle.setText("Prueba local · Flip 4 → BLE → ventilador");
        subtitle.setTextSize(16);
        subtitle.setTextColor(Color.DKGRAY);
        subtitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        subLp.setMargins(0, dp(8), 0, dp(28));
        root.addView(subtitle, subLp);

        status = new TextView(this);
        status.setTextSize(16);
        status.setTextColor(Color.rgb(70, 70, 70));
        status.setGravity(Gravity.CENTER);
        status.setPadding(dp(12), dp(12), dp(12), dp(12));
        root.addView(status, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        onButton = makeButton("ENCENDER · VELOCIDAD 3");
        LinearLayout.LayoutParams onLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(64));
        onLp.setMargins(0, dp(32), 0, gap);
        root.addView(onButton, onLp);

        offButton = makeButton("APAGAR VENTILADOR");
        root.addView(offButton, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(64)));

        TextView note = new TextView(this);
        note.setText("Esta primera versión solo prueba ON/OFF. No usa FanLamp Pro ni Alexa todavía.");
        note.setTextSize(14);
        note.setTextColor(Color.GRAY);
        note.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams noteLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        noteLp.setMargins(0, dp(28), 0, 0);
        root.addView(note, noteLp);

        setContentView(root);
    }

    private Button makeButton(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(17);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(95, 72, 55)));
        return b;
    }

    private void requestBtPermissionsIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            boolean advertise = checkSelfPermission(Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED;
            boolean connect = checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
            if (!advertise || !connect) {
                requestPermissions(new String[]{
                        Manifest.permission.BLUETOOTH_ADVERTISE,
                        Manifest.permission.BLUETOOTH_CONNECT
                }, REQ_BT);
            }
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
            if (!adapter.isMultipleAdvertisementSupported()) {
                setStatus("Este teléfono no reporta soporte para BLE Advertising.", true);
                setButtons(false);
                return;
            }
            advertiser = adapter.getBluetoothLeAdvertiser();
            if (advertiser == null) {
                setStatus("No pude abrir el transmisor BLE.", true);
                setButtons(false);
                return;
            }
            setStatus("Listo · Bluetooth activo", false);
            setButtons(true);
        } catch (SecurityException e) {
            setStatus("Falta permiso de Bluetooth.", true);
            setButtons(false);
        }
    }

    private void transmit(int[] uuids, String actionText) {
        if (!hasBtPermissions()) {
            requestBtPermissionsIfNeeded();
            return;
        }
        updateReadyState();
        if (advertiser == null) return;

        stopCurrentAdvert();

        AdvertiseSettings settings = new AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                .setConnectable(true)
                .setTimeout(0)
                .build();

        AdvertiseData.Builder data = new AdvertiseData.Builder()
                .setIncludeDeviceName(false)
                .setIncludeTxPowerLevel(false);

        // Android reconoce estos UUID Bluetooth-base como UUIDs de 16 bits y los empaqueta
        // en la lista de Service UUIDs, igual que el paquete clonado en nRF Connect.
        for (int uuid16 : uuids) {
            data.addServiceUuid(uuid16(uuid16));
        }

        final AdvertiseCallback callback = new AdvertiseCallback() {
            @Override
            public void onStartSuccess(AdvertiseSettings settingsInEffect) {
                activeCallback = this;
                setStatus(actionText + "…", false);
                handler.postDelayed(() -> {
                    if (activeCallback == this) {
                        stopCurrentAdvert();
                        setStatus("Comando enviado ✓", false);
                    }
                }, ADVERTISE_MS);
            }

            @Override
            public void onStartFailure(int errorCode) {
                activeCallback = null;
                setStatus("Error BLE: " + explainAdvertiseError(errorCode), true);
            }
        };

        try {
            advertiser.startAdvertising(settings, data.build(), callback);
        } catch (SecurityException e) {
            setStatus("Android bloqueó el Bluetooth: revisa permisos.", true);
        } catch (IllegalArgumentException e) {
            setStatus("El paquete BLE no cabe o no es válido.", true);
        }
    }

    private ParcelUuid uuid16(int value) {
        String full = String.format(Locale.US,
                "0000%04x-0000-1000-8000-00805f9b34fb", value & 0xFFFF);
        return new ParcelUuid(UUID.fromString(full));
    }

    private void stopCurrentAdvert() {
        if (advertiser != null && activeCallback != null && hasBtPermissions()) {
            try {
                advertiser.stopAdvertising(activeCallback);
            } catch (SecurityException ignored) {
            }
            activeCallback = null;
        }
    }

    private String explainAdvertiseError(int code) {
        switch (code) {
            case AdvertiseCallback.ADVERTISE_FAILED_DATA_TOO_LARGE:
                return "paquete demasiado grande";
            case AdvertiseCallback.ADVERTISE_FAILED_TOO_MANY_ADVERTISERS:
                return "demasiados anuncios BLE activos";
            case AdvertiseCallback.ADVERTISE_FAILED_ALREADY_STARTED:
                return "el anuncio ya estaba activo";
            case AdvertiseCallback.ADVERTISE_FAILED_INTERNAL_ERROR:
                return "error interno de Bluetooth";
            case AdvertiseCallback.ADVERTISE_FAILED_FEATURE_UNSUPPORTED:
                return "BLE Advertising no soportado";
            default:
                return "código " + code;
        }
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
        if (requestCode == REQ_BT) updateReadyState();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateReadyState();
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopCurrentAdvert();
    }

    @Override
    protected void onDestroy() {
        stopCurrentAdvert();
        super.onDestroy();
    }
}
