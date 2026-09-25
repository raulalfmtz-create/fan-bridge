package com.fanbridge.local;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.ParcelUuid;

import java.util.Locale;
import java.util.UUID;

public class FanBridgeService extends Service implements SinricClient.Listener {

    public static final String ACTION_START = "com.fanbridge.local.START";
    public static final String ACTION_ON = "com.fanbridge.local.FAN_ON";
    public static final String ACTION_OFF = "com.fanbridge.local.FAN_OFF";
    public static final String ACTION_RECONNECT = "com.fanbridge.local.RECONNECT";

    private static final String CHANNEL_ID = "fan_bridge_channel";
    private static final int NOTIFICATION_ID = 71;
    private static final long ADVERTISE_MS = 900;

    private static final int[] FAN_ON_SPEED_3 = {
            0x08F0, 0x8220, 0x3936, 0x5FFD, 0x39C7, 0x6DF9, 0x641B,
            0x0F77, 0x84B1, 0x00FF, 0xC6A5, 0x2B53, 0x58B4
    };

    private static final int[] FAN_OFF = {
            0x08F0, 0x8220, 0x1F36, 0x5FFD, 0x39C7, 0x6DF9, 0x641B,
            0x0F77, 0x80B1, 0x9BFF, 0xC672, 0x2B53, 0x2E93
    };

    private BluetoothAdapter adapter;
    private BluetoothLeAdvertiser advertiser;
    private AdvertiseCallback activeCallback;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private SinricClient sinricClient;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        BluetoothManager manager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        adapter = manager != null ? manager.getAdapter() : null;
        sinricClient = new SinricClient(this, this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : ACTION_START;

        startForeground(NOTIFICATION_ID, buildNotification("Puente activo · iniciando"));

        if (ACTION_ON.equals(action)) {
            transmit(FAN_ON_SPEED_3, "Ventilador encendido · velocidad 3");
        } else if (ACTION_OFF.equals(action)) {
            transmit(FAN_OFF, "Ventilador apagado");
        } else if (ACTION_RECONNECT.equals(action)) {
            connectSinric(true);
        } else {
            connectSinric(false);
        }

        return START_STICKY;
    }

    private void connectSinric(boolean force) {
        SharedPreferences p = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE);
        String deviceId = p.getString(MainActivity.KEY_DEVICE_ID, "").trim();
        String appKey = p.getString(MainActivity.KEY_APP_KEY, "").trim();
        String appSecret = p.getString(MainActivity.KEY_APP_SECRET, "").trim();

        if (deviceId.isEmpty() || appKey.isEmpty() || appSecret.isEmpty()) {
            setSinricStatus("Sinric Pro sin configurar");
            return;
        }

        if (force) sinricClient.disconnect();
        sinricClient.connect(deviceId, appKey, appSecret);
    }

    private boolean hasBtPermissions() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true;
        return checkSelfPermission(Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED
                && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean transmit(int[] uuids, String doneText) {
        if (adapter == null || !hasBtPermissions()) {
            updateNotification("Falta permiso de Bluetooth");
            return false;
        }

        try {
            if (!adapter.isEnabled()) {
                updateNotification("Bluetooth está apagado");
                return false;
            }
            advertiser = adapter.getBluetoothLeAdvertiser();
        } catch (SecurityException e) {
            updateNotification("Android bloqueó el Bluetooth");
            return false;
        }

        if (advertiser == null) {
            updateNotification("No pude abrir el transmisor BLE");
            return false;
        }

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

        for (int uuid16 : uuids) data.addServiceUuid(uuid16(uuid16));

        final AdvertiseCallback callback = new AdvertiseCallback() {
            @Override
            public void onStartSuccess(AdvertiseSettings settingsInEffect) {
                activeCallback = this;
                updateNotification("Enviando comando BLE…");
                handler.postDelayed(() -> {
                    if (activeCallback == this) {
                        stopCurrentAdvert();
                        updateNotification(doneText + " ✓ · " + sinricClient.getShortStatus());
                    }
                }, ADVERTISE_MS);
            }

            @Override
            public void onStartFailure(int errorCode) {
                activeCallback = null;
                updateNotification("Error BLE · código " + errorCode);
            }
        };

        try {
            advertiser.startAdvertising(settings, data.build(), callback);
            return true;
        } catch (SecurityException e) {
            updateNotification("Falta permiso de Bluetooth");
            return false;
        } catch (IllegalArgumentException e) {
            updateNotification("Paquete BLE inválido");
            return false;
        }
    }

    @Override
    public boolean onPowerState(boolean on) {
        return on
                ? transmit(FAN_ON_SPEED_3, "Alexa/Sinric: ventilador encendido")
                : transmit(FAN_OFF, "Alexa/Sinric: ventilador apagado");
    }

    @Override
    public void onSinricStatus(String text, boolean connected) {
        setSinricStatus(text);
    }

    private void setSinricStatus(String text) {
        getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE)
                .edit().putString(MainActivity.KEY_SINRIC_STATUS, text).apply();
        updateNotification(text);
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
            } catch (SecurityException ignored) {}
            activeCallback = null;
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "Fan Bridge", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Mantiene activo el puente Bluetooth y Sinric Pro");
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification(String text) {
        Intent openIntent = new Intent(this, MainActivity.class);
        PendingIntent openPending = PendingIntent.getActivity(
                this, 10, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent onIntent = new Intent(this, FanBridgeService.class).setAction(ACTION_ON);
        PendingIntent onPending = PendingIntent.getService(
                this, 11, onIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent offIntent = new Intent(this, FanBridgeService.class).setAction(ACTION_OFF);
        PendingIntent offPending = PendingIntent.getService(
                this, 12, offIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder b = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);

        return b.setContentTitle("Fan Bridge")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_menu_manage)
                .setContentIntent(openPending)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .addAction(android.R.drawable.ic_media_play, "ENCENDER", onPending)
                .addAction(android.R.drawable.ic_media_pause, "APAGAR", offPending)
                .build();
    }

    private void updateNotification(String text) {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(NOTIFICATION_ID, buildNotification(text));
    }

    @Override
    public void onDestroy() {
        stopCurrentAdvert();
        if (sinricClient != null) sinricClient.disconnect();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
