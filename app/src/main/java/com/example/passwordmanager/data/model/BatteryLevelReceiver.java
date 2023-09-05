package com.example.passwordmanager.data.model;

import android.annotation.SuppressLint;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.PowerManager;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.example.passwordmanager.BatteryCheckService;
import com.example.passwordmanager.MainActivity;
import com.example.passwordmanager.R;

public class BatteryLevelReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction().equals("android.intent.action.BOOT_COMPLETED")) {
            Intent serviceIntent = new Intent(context, BatteryCheckService.class);
            ContextCompat.startForegroundService(context, serviceIntent);

            MainActivity.scheduleBatteryCheck(context);
        }
    }
}


