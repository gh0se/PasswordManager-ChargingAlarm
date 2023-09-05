package com.example.passwordmanager;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

public class BatteryCheckService extends Service {
    private final int ALARM_INTERVAL = 2 * 60 * 1000;
    private final int NOTIFICATION_ID = 999;
    private final String CHANNEL_ID = "BatteryServiceNotificationChannel";

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && "STOP_CHECKING".equals(intent.getAction())) {
            SharedPreferences preferences = getSharedPreferences("battery_prefs", MODE_PRIVATE);
            preferences.edit().putBoolean("alreadyNotified", false).apply();

            stopSelf();
            return START_NOT_STICKY;
        }

        createOrUpdateNotification();

        checkBatteryStatus();

        scheduleNextBatteryCheck();

        BatteryLevelWakefulReceiver.completeWakefulIntent(intent);

        return START_NOT_STICKY;
    }

    private void scheduleNextBatteryCheck() {
        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(this, BatteryCheckService.class);
        PendingIntent pendingIntent = PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_MUTABLE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            AlarmManager.AlarmClockInfo alarmClockInfo = new AlarmManager.AlarmClockInfo(System.currentTimeMillis() + ALARM_INTERVAL, pendingIntent);

            alarmManager.setAlarmClock(alarmClockInfo, pendingIntent);
        } else {
            alarmManager.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + ALARM_INTERVAL, pendingIntent);
        }
    }

    private void createOrUpdateNotification() {
        createNotificationChannel();

        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_MUTABLE);

        Intent stopIntent = new Intent(this, BatteryCheckService.class);
        stopIntent.setAction("STOP_CHECKING");
        PendingIntent stopPendingIntent = PendingIntent.getService(this, 0, stopIntent, PendingIntent.FLAG_MUTABLE);

        NotificationCompat.Action stopAction = new NotificationCompat.Action.Builder(R.drawable.ic_stop, "Stop Checking", stopPendingIntent).build();

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Battery Check Service")
                .setContentText("Service is running...")
                .setSmallIcon(R.drawable.ic_battery_alert)
                .setContentIntent(pendingIntent)
                .addAction(stopAction)
                .build();

        startForeground(NOTIFICATION_ID, notification);
    }


    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID, "Battery Check Service Channel", NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(serviceChannel);
        }
    }

    private void checkBatteryStatus() {
        BatteryManager batteryManager = (BatteryManager) getSystemService(Context.BATTERY_SERVICE);
        int level = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);

        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = registerReceiver(null, ifilter);

        int chargePlug = batteryStatus.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
        boolean isCharging = chargePlug == BatteryManager.BATTERY_PLUGGED_AC ||
                chargePlug == BatteryManager.BATTERY_PLUGGED_USB ||
                chargePlug == BatteryManager.BATTERY_PLUGGED_WIRELESS;

        if (!isCharging) {
            Log.d("BatteryService", "Device is not charging. Skipping battery level check.");
            return;
        }

        SharedPreferences preferences = getSharedPreferences("battery_prefs", MODE_PRIVATE);
        boolean alreadyNotified = preferences.getBoolean("alreadyNotified", false);

        if (level >= 80 && !alreadyNotified) {
            Log.d("BatteryService", "Triggering alarm at " + level + "% while charging");
            triggerAlarm();
            preferences.edit().putBoolean("alreadyNotified", true).apply();
        } else if (level < 80) {
            Log.d("BatteryService", "Resetting alarm at " + level + "%");
            preferences.edit().putBoolean("alreadyNotified", false).apply();
        }
    }

    private void triggerAlarm() {
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);


        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel("batteryChannel", "Battery Notification", NotificationManager.IMPORTANCE_HIGH);

            Uri soundUri = Uri.parse("android.resource://" + getPackageName() + "/raw/alarm");
            channel.setSound(soundUri, new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build());

            notificationManager.createNotificationChannel(channel);
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, "batteryChannel")
                .setContentTitle("Battery Level Alert!")
                .setContentText("Battery has reached 80% while charging!")
                .setSmallIcon(R.drawable.ic_battery_alert)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setSound(Uri.parse("android.resource://" + getPackageName() + "/raw/alarm"));

        notificationManager.notify(12345, builder.build());
        Log.d("BatteryService", "Alarm triggered");
    }

}

