package com.smartdevicelink.sdlstreaming.sdl;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.util.Log;
import android.view.Display;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Toast;
import android.widget.VideoView;

import com.smartdevicelink.managers.CompletionListener;
import com.smartdevicelink.managers.SdlManager;
import com.smartdevicelink.managers.SdlManagerListener;
import com.smartdevicelink.managers.file.filetypes.SdlArtwork;
import com.smartdevicelink.managers.lifecycle.LifecycleConfigurationUpdate;
import com.smartdevicelink.managers.video.VideoStreamManager;
import com.smartdevicelink.protocol.enums.FunctionID;
import com.smartdevicelink.proxy.RPCNotification;
import com.smartdevicelink.proxy.rpc.OnHMIStatus;
import com.smartdevicelink.proxy.rpc.enums.AppHMIType;
import com.smartdevicelink.proxy.rpc.enums.FileType;
import com.smartdevicelink.proxy.rpc.enums.HMILevel;
import com.smartdevicelink.proxy.rpc.enums.Language;
import com.smartdevicelink.proxy.rpc.listeners.OnRPCNotificationListener;
import com.smartdevicelink.sdlstreaming.R;
import com.smartdevicelink.streaming.video.SdlRemoteDisplay;
import com.smartdevicelink.transport.MultiplexTransportConfig;
import com.smartdevicelink.util.DebugTool;

import java.util.Vector;

public class SdlService extends Service {

    private static final String TAG 					= "SDL Service";

    private static final String APP_NAME 				= "";
    private static final String APP_ID 					= "";

    private static final String ICON_FILENAME 			= "ic_sdl.png";
    public static String LOCAL_VIDEO_URI = null;

    // variable to create and call functions of the SyncProxy
    private SdlManager sdlManager = null;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    public void onCreate() {
        Log.d(TAG, "onCreate");
        DebugTool.enableDebugTool();
        super.onCreate();
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            NotificationChannel channel = new NotificationChannel("VideoStreaming", "VideoStreaming", NotificationManager.IMPORTANCE_DEFAULT);
            notificationManager.createNotificationChannel(channel);
            Notification serviceNotification = new Notification.Builder(this, channel.getId())
                    .setChannelId(channel.getId())
                    .setSmallIcon((android.R.drawable.star_on))
                    .build();
            startForeground(237, serviceNotification);
        }

        //SdlManager man = new SdlManager();
        //man.sendRPC(new Show());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        setLocalVideo(); // Set local video path on runtime
        startProxy();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            stopForeground(true);
        }

        if (sdlManager != null) {
            sdlManager.dispose();
            sdlManager = null;
        }

        super.onDestroy();
    }

    public void setLocalVideo(){
        // Set this to an .mp4 file in res/raw during runtime
        LOCAL_VIDEO_URI = "android.resource://" + getApplicationContext().getPackageName() + "/" + R.raw.sdl;
    }


    private void startProxy() {
        if (sdlManager == null) {
            Log.i(TAG, "Starting SDL Proxy");
            MultiplexTransportConfig mtc = new MultiplexTransportConfig(this, APP_ID, MultiplexTransportConfig.FLAG_MULTI_SECURITY_OFF);;
            mtc.setRequiresHighBandwidth(true);
            mtc.setRequiresAudioSupport(false);

            // The app type to be used
            Vector<AppHMIType> appType = new Vector<>();
            appType.add(AppHMIType.NAVIGATION);

            // The manager listener helps you know when certain events that pertain to the SDL Manager happen
            // Here we will listen for ON_HMI_STATUS and ON_COMMAND notifications
            SdlManagerListener listener = new SdlManagerListener() {
                @Override
                public void onStart() {

                    // HMI Status Listener
                    sdlManager.addOnRPCNotificationListener(FunctionID.ON_HMI_STATUS, new OnRPCNotificationListener() {
                        @Override
                        public void onNotified(RPCNotification notification) {
                            OnHMIStatus status = (OnHMIStatus) notification;
                            if (status.getHmiLevel() == HMILevel.HMI_FULL && ((OnHMIStatus) notification).getFirstRun()) {
                                notifyStreaming("Start VSM");
                                //TODO start streaming

                                final VideoStreamManager vsm = sdlManager.getVideoStreamManager();
                                vsm.start(new CompletionListener() {
                                    @Override
                                    public void onComplete(boolean success) {
                                        if(success){
                                            notifyStreaming("Starting to stream");
                                            if(vsm != null){
                                                vsm.startRemoteDisplayStream(getBaseContext(), MyPresentation.class, null, false );
                                            }else{
                                                notifyStreaming("vsm was null when starting remote display");
                                            }
                                        }else{
                                            notifyStreaming("An error occurred trying to stream");
                                        }
                                    }
                                });
                            }
                        }
                    });


                }


                @Override
                public void onDestroy() {
                    Log.i(TAG, "SdlManager onDestroy ");
                    SdlService.this.sdlManager = null;
                    SdlService.this.stopSelf();
                }

                @Override
                public void onError(String info, Exception e) {
                    Log.i(TAG, "onError: " + info);

                }

                @Override
                public LifecycleConfigurationUpdate managerShouldUpdateLifecycle(Language language) {
                    return null;
                }


            };


            // Create App Icon, this is set in the SdlManager builder
            SdlArtwork appIcon = new SdlArtwork(ICON_FILENAME, FileType.GRAPHIC_PNG, R.drawable.car, true);

            // The manager builder sets options for your session
            if (APP_ID.equals("") || APP_NAME.equals("")) {
                Log.e(TAG, "The App ID or App Name is missing, For SmartDeviceLink to work an App ID and App Name must be provided");
            } else {
                SdlManager.Builder builder = new SdlManager.Builder(this, APP_ID, APP_NAME, listener);
                builder.setAppTypes(appType);
                builder.setTransportType(mtc);
                builder.setAppIcon(appIcon);
                sdlManager = builder.build();
                sdlManager.start();
            }
        }
    }

    public void notifyStreaming(final String message){
        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(new Runnable() {

            @Override
            public void run() {
                Toast.makeText(getApplicationContext(),
                        message,
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    /*
     * Create a Presentation class that sets ContentView layout and touch response callbacks
     * Note: It must extend VirtualDisplayEncoder.SdlPresentation!
     */
    public static class MyPresentation extends SdlRemoteDisplay {

        public MyPresentation(Context context, Display display) {
            super(context, display);
        }
        VideoView videoView;

        @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR1)
        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            setContentView(R.layout.stream);

            videoView = (VideoView) findViewById(R.id.videoView);

            videoView.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    Log.d(TAG, "Received motion event on video view");
                    Toast.makeText(v.getContext(),"Touch event received: " + event.getX(),Toast.LENGTH_SHORT).show();
                    return true;
                }
            });

            videoView.setOnInfoListener(new MediaPlayer.OnInfoListener() {
                @Override
                public boolean onInfo(MediaPlayer mp, int what, int extra) {
                    Log.i(TAG, "onInfo: " + what + " , " + extra);
                    return false;
                }
            });

            videoView.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                @Override
                public boolean onError(MediaPlayer mp, int what, int extra) {
                    Log.i(TAG, "onError: " + what + " , " + extra);
                    return false;
                }
            });
            videoView.setVideoURI(Uri.parse(LOCAL_VIDEO_URI));
            videoView.start();
        }


        @Override
        protected void onStop() {
            if(videoView != null){
                videoView.stopPlayback();
            }
            super.onStop();
        }
    }

}
