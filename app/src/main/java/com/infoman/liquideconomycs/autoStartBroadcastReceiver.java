package com.infoman.liquideconomycs;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import java.util.Objects;

import static android.content.Intent.ACTION_BOOT_COMPLETED;
import static com.infoman.liquideconomycs.Utils.ACTION_STOP_SERVICE;

public class autoStartBroadcastReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (Objects.equals(intent.getAction(), ACTION_BOOT_COMPLETED)){
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(new Intent(context.getApplicationContext(), TrieServiceIntent.class).setAction(ACTION_STOP_SERVICE));
                return;
            }
            context.startService(new Intent(context.getApplicationContext(), TrieServiceIntent.class).setAction(ACTION_STOP_SERVICE));
        }
    }
}
