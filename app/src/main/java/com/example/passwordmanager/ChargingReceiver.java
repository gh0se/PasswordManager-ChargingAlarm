package com.example.passwordmanager;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.core.content.ContextCompat;

public class ChargingReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();

        if (Intent.ACTION_POWER_CONNECTED.equals(action)) {
            Intent serviceIntent = new Intent(context, BatteryCheckService.class);
            ContextCompat.startForegroundService(context, serviceIntent);
            Log.d("BatteryService", "Device Started charging....service started?");
        } else if (Intent.ACTION_POWER_DISCONNECTED.equals(action)) {
            Log.d("BatteryService", "Device stopped charging...service stopped?");

            AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            Intent stopIntent = new Intent(context, BatteryCheckService.class);
            PendingIntent pendingIntent = PendingIntent.getService(context, 0, stopIntent, PendingIntent.FLAG_MUTABLE);
            alarmManager.cancel(pendingIntent);

            stopIntent.setAction("STOP_CHECKING");
            context.startService(stopIntent);
        }
    }
}
