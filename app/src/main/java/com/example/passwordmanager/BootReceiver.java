package com.example.passwordmanager;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import androidx.core.content.ContextCompat;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction().equals("android.intent.action.BOOT_COMPLETED")) {
            Intent serviceIntent = new Intent(context, BatteryCheckService.class);
            ContextCompat.startForegroundService(context, serviceIntent);

            MainActivity.scheduleBatteryCheck(context);
        }
    }
}

