package com.smartdevicelink.sdlstreaming;

import static android.Manifest.permission.BLUETOOTH_CONNECT;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;

import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.smartdevicelink.sdlstreaming.sdl.SdlReceiver;
import com.smartdevicelink.transport.SdlBroadcastReceiver;

/**
 * Using a video view works super easy.
 *
 */


public class MainActivity extends Activity {

    private static final int BLUETOOTH_REQUEST_CODE = 200;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!checkPermission()) {
                requestPermission();
                return;
            }
        }

        SdlBroadcastReceiver.queryForConnectedService(this);
    }

    private boolean checkPermission() {
        int btConnectPermission = ContextCompat
            .checkSelfPermission(getApplicationContext(), BLUETOOTH_CONNECT);

        return btConnectPermission == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermission() {
        ActivityCompat.requestPermissions(this, new String[]{BLUETOOTH_CONNECT},
                BLUETOOTH_REQUEST_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case BLUETOOTH_REQUEST_CODE:
                if (grantResults.length > 0) {

                    boolean connectAccepted = grantResults[0] == PackageManager.PERMISSION_GRANTED;
                    
                    if (connectAccepted) {
                        SdlReceiver.queryForConnectedService(this);
                    }

                    if (!connectAccepted) {
                        Toast.makeText(this, "BLUETOOTH_CONNECT Permission is needed for Bluetooth testing", Toast.LENGTH_LONG).show();
                    }
                }
                break;
        }
    }

}
