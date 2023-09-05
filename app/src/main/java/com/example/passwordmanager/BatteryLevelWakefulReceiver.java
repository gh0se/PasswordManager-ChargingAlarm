package com.example.passwordmanager;

import android.content.Context;
import android.content.Intent;

import androidx.legacy.content.WakefulBroadcastReceiver;

public class BatteryLevelWakefulReceiver extends WakefulBroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        Intent serviceIntent = new Intent(context, BatteryCheckService.class);
        startWakefulService(context, serviceIntent);
    }

}
