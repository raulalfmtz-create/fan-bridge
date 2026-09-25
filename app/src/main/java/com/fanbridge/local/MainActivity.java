package com.fanbridge.local;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {

    private static final int REQ_PERMS = 1001;

    private BluetoothAdapter adapter;
    private TextView status;
    private Button onButton;
    private Button offButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();

        BluetoothManager manager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        adapter = manager != null ? manager.getAdapter() : null;

        requestPermissionsIfNeeded();
        updateReadyState();

        onButton.setOnClickListener(v -> sendToBridge(FanBridgeService.ACTION_ON, "Orden enviada · velocidad 3"));
        offButton.setOnClickListener(v -> sendToBridge(FanBridgeService.ACTION_OFF, "Orden enviada · apagar"));
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
        subtitle.setText("v0.2 · modo puente en segundo plano");
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
        note.setText("Después de abrir la app una vez, Fan Bridge mantiene un servicio activo. " +
                "También tendrás ENCENDER/APAGAR en la notificación para probarlo con la app cerrada.");
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
        setStatus("Puente activo · puedes cerrar la app", false);
        setButtons(true);
    }

    private void startBridgeService() {
        Intent intent = new Intent(this, FanBridgeService.class).setAction(FanBridgeService.ACTION_START);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }

    private void sendToBridge(String action, String message) {
        if (!hasBtPermissions()) {
            requestPermissionsIfNeeded();
            return;
        }

        Intent intent = new Intent(this, FanBridgeService.class).setAction(action);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
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
