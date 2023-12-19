package com.smartdevicelink.sdlstreaming.sdl;

import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import com.smartdevicelink.transport.SdlBroadcastReceiver;
import com.smartdevicelink.transport.SdlRouterService;
import com.smartdevicelink.transport.TransportConstants;
import com.smartdevicelink.util.AndroidTools;
import com.smartdevicelink.util.DebugTool;

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
        DebugTool.logInfo(TAG, "SDL Enabled");
        intent.setClass(context, SdlService.class);

        ComponentName serviceStarted = null;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (intent.getParcelableExtra(TransportConstants.PENDING_INTENT_EXTRA) != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    if (!AndroidTools.hasForegroundServiceTypePermission(context)) {
                        DebugTool.logInfo(TAG, "Permission missing for ForegroundServiceType connected device." + context);
                        return;
                    }
                }
                PendingIntent pendingIntent = (PendingIntent) intent.getParcelableExtra(TransportConstants.PENDING_INTENT_EXTRA);
                try {
                    //Here we are allowing the RouterService that is in the Foreground to start the SdlService on our behalf
                    pendingIntent.send(context, 0, intent);
                } catch (PendingIntent.CanceledException e) {
                    e.printStackTrace();
                }
            }
        } else {
            // SdlService needs to be foregrounded in Android O and above
            // This will prevent apps in the background from crashing when they try to start SdlService
            // Because Android O doesn't allow background apps to start background services
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                serviceStarted = context.startForegroundService(intent);
            } else {
                serviceStarted = context.startService(intent);
            }
        }

        if(serviceStarted != null){
            Log.i(TAG, "onSdlEnabled: Service started = " + serviceStarted.flattenToShortString());
        }else {
            Log.i(TAG, "onSdlEnabled: Unable to start service");
        }
    }
}
