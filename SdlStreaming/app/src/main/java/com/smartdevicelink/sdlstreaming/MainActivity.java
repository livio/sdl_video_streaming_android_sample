package com.smartdevicelink.sdlstreaming;

import static android.Manifest.permission.BLUETOOTH_CONNECT;

import android.Manifest;
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

import java.util.ArrayList;

/**
 * Using a video view works super easy.
 *
 */


public class MainActivity extends Activity {

    private static final int REQUEST_CODE = 200;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        String[] permissionsNeeded = permissionsNeeded();
        if (permissionsNeeded.length > 0) {
            requestPermission(permissionsNeeded, REQUEST_CODE);
            for (String permission : permissionsNeeded) {
                if (Manifest.permission.BLUETOOTH_CONNECT.equals(permission)) {
                    // We need to request BLUETOOTH_CONNECT permission to connect to SDL via Bluetooth
                    return;
                }
            }
        }

        SdlBroadcastReceiver.queryForConnectedService(this);
    }

    /**
     * Boolean method that checks API level and check to see if we need to request BLUETOOTH_CONNECT permission
     * @return false if we need to request BLUETOOTH_CONNECT permission
     */
    private boolean hasBTPermission() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ? checkPermission(Manifest.permission.BLUETOOTH_CONNECT) : true;
    }

    /**
     * Boolean method that checks API level and check to see if we need to request POST_NOTIFICATIONS permission
     * @return false if we need to request POST_NOTIFICATIONS permission
     */
    private boolean hasPNPermission() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ? checkPermission(Manifest.permission.POST_NOTIFICATIONS) : true;
    }

    private boolean checkPermission(String permission) {
        return PackageManager.PERMISSION_GRANTED == ContextCompat.checkSelfPermission(getApplicationContext(), permission);
    }

    private void requestPermission(String[] permissions, int REQUEST_CODE) {
        ActivityCompat.requestPermissions(this, permissions, REQUEST_CODE);
    }

    private @NonNull String[] permissionsNeeded() {
        ArrayList<String> result = new ArrayList<>();
        if (!hasBTPermission()) {
            result.add(Manifest.permission.BLUETOOTH_CONNECT);
        }
        if (!hasPNPermission()) {
            result.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        return (result.toArray(new String[result.size()]));
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case REQUEST_CODE:
                if (grantResults.length > 0) {
                    for (int i = 0; i < grantResults.length; i++) {
                        if (permissions[i].equals(Manifest.permission.BLUETOOTH_CONNECT)) {
                            boolean btConnectGranted =
                                    grantResults[i] == PackageManager.PERMISSION_GRANTED;
                            if (btConnectGranted) {
                                SdlReceiver.queryForConnectedService(this);
                            }
                        } else if (permissions[i].equals(Manifest.permission.POST_NOTIFICATIONS)) {
                            boolean postNotificationGranted =
                                    grantResults[i] == PackageManager.PERMISSION_GRANTED;
                            if (!postNotificationGranted) {
                                // User denied permission, Notifications for SDL will not appear
                                // on Android 13 devices.
                            }
                        }
                    }
                }
                break;
        }
    }

}
