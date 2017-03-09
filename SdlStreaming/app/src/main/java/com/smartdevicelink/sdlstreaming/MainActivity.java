package com.smartdevicelink.sdlstreaming;

import android.app.Activity;
import android.os.Bundle;

import com.smartdevicelink.transport.SdlBroadcastReceiver;

/**
 * Using a video view works super easy.
 *
 */


public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        SdlBroadcastReceiver.queryForConnectedService(this);
    }

}
