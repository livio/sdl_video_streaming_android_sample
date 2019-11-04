package com.smartdevicelink.sdlstreaming.sdl;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import com.smartdevicelink.transport.SdlBroadcastReceiver;
import com.smartdevicelink.transport.SdlRouterService;

public class SdlReceiver extends SdlBroadcastReceiver {

    private static final String TAG ="SdlReceiver";

    public SdlReceiver() {
    }


    @Override
    public Class<? extends SdlRouterService> defineLocalSdlRouterClass() {
        return com.smartdevicelink.sdlstreaming.sdl.SdlRouterService.class;
    }

    @Override
    public void onSdlEnabled(Context context, Intent intent) {
        Log.d(TAG, "SDL Enabled");
        intent.setClass(context, SdlService.class);

        // SdlService needs to be foregrounded in Android O and above
        // This will prevent apps in the background from crashing when they try to start SdlService
        // Because Android O doesn't allow background apps to start background services
        ComponentName serviceStarted ;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            serviceStarted = context.startForegroundService(intent);
        } else {
            serviceStarted = context.startService(intent);
        }
        if(serviceStarted != null){
            Log.i(TAG, "onSdlEnabled: Service started = " + serviceStarted.flattenToShortString());
        }else{
            Log.e(TAG, "onSdlEnabled: Unable to start service");
        }
    }
}
