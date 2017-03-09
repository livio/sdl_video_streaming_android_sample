package com.smartdevicelink.sdlstreaming.sdl;

import android.content.Context;
import android.content.Intent;

import com.smartdevicelink.transport.SdlBroadcastReceiver;
import com.smartdevicelink.transport.SdlRouterService;

public class SdlReceiver extends SdlBroadcastReceiver {
    public SdlReceiver() {
    }


    @Override
    public Class<? extends SdlRouterService> defineLocalSdlRouterClass() {
        return com.smartdevicelink.sdlstreaming.sdl.SdlRouterService.class;
    }

    @Override
    public void onSdlEnabled(Context context, Intent intent) {
        intent.setClass(context,SdlService.class);
        context.startService(intent);
    }
}
