package com.smartdevicelink.sdlstreaming.sdl;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbAccessory;
import android.hardware.usb.UsbManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.view.Display;
import android.widget.Toast;
import android.widget.VideoView;

import com.smartdevicelink.encoder.VirtualDisplayEncoder;
import com.smartdevicelink.exception.SdlException;
import com.smartdevicelink.proxy.RPCRequest;
import com.smartdevicelink.proxy.SdlProxyALM;
import com.smartdevicelink.proxy.SdlProxyBuilder;
import com.smartdevicelink.proxy.callbacks.OnServiceEnded;
import com.smartdevicelink.proxy.callbacks.OnServiceNACKed;
import com.smartdevicelink.proxy.interfaces.IProxyListenerALM;
import com.smartdevicelink.proxy.rpc.AddCommand;
import com.smartdevicelink.proxy.rpc.AddCommandResponse;
import com.smartdevicelink.proxy.rpc.AddSubMenuResponse;
import com.smartdevicelink.proxy.rpc.AlertManeuverResponse;
import com.smartdevicelink.proxy.rpc.AlertResponse;
import com.smartdevicelink.proxy.rpc.ChangeRegistrationResponse;
import com.smartdevicelink.proxy.rpc.CreateInteractionChoiceSetResponse;
import com.smartdevicelink.proxy.rpc.DeleteCommandResponse;
import com.smartdevicelink.proxy.rpc.DeleteFileResponse;
import com.smartdevicelink.proxy.rpc.DeleteInteractionChoiceSetResponse;
import com.smartdevicelink.proxy.rpc.DeleteSubMenuResponse;
import com.smartdevicelink.proxy.rpc.DiagnosticMessageResponse;
import com.smartdevicelink.proxy.rpc.DialNumberResponse;
import com.smartdevicelink.proxy.rpc.EndAudioPassThruResponse;
import com.smartdevicelink.proxy.rpc.GenericResponse;
import com.smartdevicelink.proxy.rpc.GetDTCsResponse;
import com.smartdevicelink.proxy.rpc.GetVehicleDataResponse;
import com.smartdevicelink.proxy.rpc.GetWayPointsResponse;
import com.smartdevicelink.proxy.rpc.ListFiles;
import com.smartdevicelink.proxy.rpc.ListFilesResponse;
import com.smartdevicelink.proxy.rpc.MenuParams;
import com.smartdevicelink.proxy.rpc.OnAudioPassThru;
import com.smartdevicelink.proxy.rpc.OnButtonEvent;
import com.smartdevicelink.proxy.rpc.OnButtonPress;
import com.smartdevicelink.proxy.rpc.OnCommand;
import com.smartdevicelink.proxy.rpc.OnDriverDistraction;
import com.smartdevicelink.proxy.rpc.OnHMIStatus;
import com.smartdevicelink.proxy.rpc.OnHashChange;
import com.smartdevicelink.proxy.rpc.OnKeyboardInput;
import com.smartdevicelink.proxy.rpc.OnLanguageChange;
import com.smartdevicelink.proxy.rpc.OnLockScreenStatus;
import com.smartdevicelink.proxy.rpc.OnPermissionsChange;
import com.smartdevicelink.proxy.rpc.OnStreamRPC;
import com.smartdevicelink.proxy.rpc.OnSystemRequest;
import com.smartdevicelink.proxy.rpc.OnTBTClientState;
import com.smartdevicelink.proxy.rpc.OnTouchEvent;
import com.smartdevicelink.proxy.rpc.OnVehicleData;
import com.smartdevicelink.proxy.rpc.OnWayPointChange;
import com.smartdevicelink.proxy.rpc.PerformAudioPassThruResponse;
import com.smartdevicelink.proxy.rpc.PerformInteractionResponse;
import com.smartdevicelink.proxy.rpc.PutFile;
import com.smartdevicelink.proxy.rpc.PutFileResponse;
import com.smartdevicelink.proxy.rpc.ReadDIDResponse;
import com.smartdevicelink.proxy.rpc.ResetGlobalPropertiesResponse;
import com.smartdevicelink.proxy.rpc.ScrollableMessageResponse;
import com.smartdevicelink.proxy.rpc.SendLocationResponse;
import com.smartdevicelink.proxy.rpc.SetAppIconResponse;
import com.smartdevicelink.proxy.rpc.SetDisplayLayoutResponse;
import com.smartdevicelink.proxy.rpc.SetGlobalPropertiesResponse;
import com.smartdevicelink.proxy.rpc.SetMediaClockTimerResponse;
import com.smartdevicelink.proxy.rpc.ShowConstantTbtResponse;
import com.smartdevicelink.proxy.rpc.ShowResponse;
import com.smartdevicelink.proxy.rpc.SliderResponse;
import com.smartdevicelink.proxy.rpc.SpeakResponse;
import com.smartdevicelink.proxy.rpc.StreamRPCResponse;
import com.smartdevicelink.proxy.rpc.SubscribeButtonResponse;
import com.smartdevicelink.proxy.rpc.SubscribeVehicleDataResponse;
import com.smartdevicelink.proxy.rpc.SubscribeWayPointsResponse;
import com.smartdevicelink.proxy.rpc.SystemRequestResponse;
import com.smartdevicelink.proxy.rpc.UnsubscribeButtonResponse;
import com.smartdevicelink.proxy.rpc.UnsubscribeVehicleDataResponse;
import com.smartdevicelink.proxy.rpc.UnsubscribeWayPointsResponse;
import com.smartdevicelink.proxy.rpc.UpdateTurnListResponse;
import com.smartdevicelink.proxy.rpc.enums.AppHMIType;
import com.smartdevicelink.proxy.rpc.enums.FileType;
import com.smartdevicelink.proxy.rpc.enums.HMILevel;
import com.smartdevicelink.proxy.rpc.enums.SdlDisconnectedReason;
import com.smartdevicelink.proxy.rpc.enums.TextAlignment;
import com.smartdevicelink.sdlstreaming.R;
import com.smartdevicelink.sdlstreaming.videostreaming.CameraToMpegTest;
import com.smartdevicelink.transport.BaseTransportConfig;
import com.smartdevicelink.transport.MultiplexTransportConfig;
import com.smartdevicelink.transport.TransportConstants;
import com.smartdevicelink.transport.USBTransportConfig;
import com.smartdevicelink.util.CorrelationIdGenerator;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Vector;

public class SdlService extends Service implements IProxyListenerALM{

    private static final String TAG 					= "SDL Service";

    private static final String APP_NAME 				= "SdlStreaming";
    private static final String APP_ID 					= "9035769";

    private static final String ICON_FILENAME 			= "ic_sdl.png";
    private static final boolean WRITING_TO_FILE_MODE      = false;

    private int iconCorrelationId;

    List<String> remoteFiles;

    private static final String TEST_COMMAND_NAME 		= "Test Command";
    private static final int TEST_COMMAND_ID 			= 1;

    public static String LOCAL_VIDEO_URI = null;
    public volatile VirtualDisplayEncoder vdEncoder = new VirtualDisplayEncoder();

    // variable to create and call functions of the SyncProxy
    private static SdlProxyALM proxy = null;
    private CameraToMpegTest cameraTest = null;

    private boolean firstNonHmiNone = true;
    boolean layoutSet = false;
    private final boolean enhancedStreamCapable = (android.os.Build.VERSION.SDK_INT >= 21);

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        Log.d(TAG, "onCreate");
        super.onCreate();
        remoteFiles = new ArrayList<String>();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        setLocalVideo(); // Set local video path on runtime

        startProxy(intent);

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        stopVideoStream();
        disposeSyncProxy();

        super.onDestroy();
    }

    public static SdlProxyALM getProxy() {
        return proxy;
    }

    public void startProxy(Intent intent) {
        if (proxy == null) {
            try {
                BaseTransportConfig transport = null;
                if(intent!=null && intent.hasExtra(UsbManager.EXTRA_ACCESSORY)){ //If we want to support USB transport
                    if(android.os.Build.VERSION.SDK_INT <= android.os.Build.VERSION_CODES.HONEYCOMB){
                        Log.e(TAG, "Unable to start proxy. Android OS version is too low");
                        return;
                    }
                    //We have a usb transport
                    transport = new USBTransportConfig(getBaseContext(),(UsbAccessory)intent.getParcelableExtra(UsbManager.EXTRA_ACCESSORY));
                }else{
                    //If we don't want anything but USB then would just do a return here and bail
                    transport = new MultiplexTransportConfig(getBaseContext(), APP_ID);
                    Log.e(TAG, "Not usb intent. Going to use bluetooth. Glob help us");
                }
                Log.d(TAG, "Transport type: " + transport.getTransportType());
                Vector<AppHMIType> appType = new Vector<AppHMIType>();
                appType.add(AppHMIType.NAVIGATION);

                SdlProxyBuilder.Builder builder = new SdlProxyBuilder.Builder(this, APP_ID, APP_NAME, false,  this);
                builder.setTransportType(transport);
                builder.setVrAppHMITypes(appType);
                proxy = builder.build();

                boolean forced = intent !=null && intent.getBooleanExtra(TransportConstants.FORCE_TRANSPORT_CONNECTED, false);
                if(forced){
                    proxy.forceOnConnected();
                }

            } catch (SdlException e) {
                e.printStackTrace();
                // error creating proxy, returned proxy = null
                if (proxy == null) {
                    stopSelf();
                }
            }
        }
    }

    /*
     * Create a Presentation class that sets ContentView layout and touch response callbacks
     * Note: It must extend VirtualDisplayEncoder.SdlPresentation!
     */
    public static class MyPresentation extends VirtualDisplayEncoder.SdlPresentation{

        public MyPresentation(Context context, Display display) {
            super(context, display);
        }

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            setContentView(R.layout.stream);

            VideoView videoView = (VideoView) findViewById(R.id.videoView);

            videoView.setVideoURI(Uri.parse(LOCAL_VIDEO_URI));
            videoView.start();
        }
    }

    public void setLocalVideo(){
        // Set this to an .mp4 file in res/raw during runtime
        LOCAL_VIDEO_URI = "android.resource://" + getApplicationContext().getPackageName() + "/" + R.raw.sdl;
    }

    public void startVideoStream(){
        if(enhancedStreamCapable){
            startEnhancedVideoStream();
            Log.d(TAG, "Enhanced video stream capable.");
        }else{
            startClassicVideoStream();
            Log.d(TAG, "Restricted to classic streaming.");
        }
    }

    private void startClassicVideoStream(){
        Log.i(TAG, "Starting test.");
        if(proxy == null){
            Log.w(TAG, "Proxy was null still");
            return;
        }
        if(cameraTest != null){
            stopVideoStream();
        }
        cameraTest = new CameraToMpegTest(proxy,this);
        if(LOCAL_VIDEO_URI != null){
            cameraTest.setUri(LOCAL_VIDEO_URI);
        }else{
            return;
        }
        try {
            cameraTest.start();
        } catch (Throwable th) {
            th.printStackTrace();
            cameraTest = null;
        }
    }

    private void startEnhancedVideoStream(){
        if(vdEncoder != null && proxy != null){
            try {
                vdEncoder.init(getApplicationContext(), proxy.startH264(false), MyPresentation.class, proxy.getDisplayCapabilities().getScreenParams());
                vdEncoder.start();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void stopVideoStream(){
        if(enhancedStreamCapable){
            stopEnhancedVideoStream();
        }else{
            stopClassicVideoStream();
        }
    }

    private void stopClassicVideoStream(){
        if(cameraTest!=null){
            cameraTest.stop();
            cameraTest = null;
        }
    }

    private void stopEnhancedVideoStream(){
        if(vdEncoder != null){
            vdEncoder.shutDown();
        }
    }

    public void disposeSyncProxy() {
        if (proxy != null) {
            try {
                proxy.dispose();
            } catch (SdlException e) {
                e.printStackTrace();
            }
            proxy = null;

        }
        this.firstNonHmiNone = true;
    }

    /**
     * Will show a sample test message on screen as well as speak a sample test message
     */
    public void showTest(){
        try {
            proxy.show(TEST_COMMAND_NAME, "Command has been selected", TextAlignment.CENTERED, CorrelationIdGenerator.generateId());
            proxy.speak(TEST_COMMAND_NAME, CorrelationIdGenerator.generateId());
        } catch (SdlException e) {
            e.printStackTrace();
        }
    }

    /**
     *  Add commands for the app on SDL.
     */
    public void sendCommands(){
        AddCommand command = new AddCommand();
        MenuParams params = new MenuParams();
        params.setMenuName(TEST_COMMAND_NAME);
        command = new AddCommand();
        command.setCmdID(TEST_COMMAND_ID);
        command.setMenuParams(params);
        command.setVrCommands(Arrays.asList(new String[]{TEST_COMMAND_NAME}));
        sendRpcRequest(command);
    }

    /**
     * Sends an RPC Request to the connected head unit. Automatically adds a correlation id.
     * @param request
     */
    private void sendRpcRequest(RPCRequest request){
        request.setCorrelationID(CorrelationIdGenerator.generateId());
        try {
            proxy.sendRPCRequest(request);
        } catch (SdlException e) {
            e.printStackTrace();
        }
    }
    /**
     * Sends the app icon through the uploadImage method with correct params
     * @throws SdlException
     */
    private void sendIcon() throws SdlException {
        iconCorrelationId = CorrelationIdGenerator.generateId();
        uploadImage(R.drawable.ic_sdl, ICON_FILENAME, iconCorrelationId, true);
    }

    /**
     * This method will help upload an image to the head unit
     * @param resource the R.drawable.__ value of the image you wish to send
     * @param imageName the filename that will be used to reference this image
     * @param correlationId the correlation id to be used with this request. Helpful for monitoring putfileresponses
     * @param isPersistent tell the system if the file should stay or be cleared out after connection.
     */
    private void uploadImage(int resource, String imageName, int correlationId, boolean isPersistent){
        PutFile putFile = new PutFile();
        putFile.setFileType(FileType.GRAPHIC_PNG);
        putFile.setSdlFileName(imageName);
        putFile.setCorrelationID(correlationId);
        putFile.setPersistentFile(isPersistent);
        putFile.setSystemFile(false);
        putFile.setBulkData(contentsOfResource(resource));

        try {
            proxy.sendRPCRequest(putFile);
        } catch (SdlException e) {
            e.printStackTrace();
        }
    }

    /**
     * Helper method to take resource files and turn them into byte arrays
     * @param resource Resource file id.
     * @return Resulting byte array.
     */
    private byte[] contentsOfResource(int resource) {
        InputStream is = null;
        try {
            is = getResources().openRawResource(resource);
            ByteArrayOutputStream os = new ByteArrayOutputStream(is.available());
            final int bufferSize = 4096;
            final byte[] buffer = new byte[bufferSize];
            int available;
            while ((available = is.read(buffer)) >= 0) {
                os.write(buffer, 0, available);
            }
            return os.toByteArray();
        } catch (IOException e) {
            Log.w(TAG, "Can't read icon file", e);
            return null;
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    @Override
    public void onProxyClosed(String info, Exception e, SdlDisconnectedReason reason) {
       if(!WRITING_TO_FILE_MODE){
           stopSelf();
       }
    }

    public void notifyStreaming(){
        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(new Runnable() {

            @Override
            public void run() {
                Toast.makeText(getApplicationContext(),
                        "Streaming to module",
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onOnHMIStatus(OnHMIStatus notification) {
        if(notification.getHmiLevel().equals(HMILevel.HMI_FULL)){
            if (notification.getFirstRun()) {
                notifyStreaming();
                startVideoStream();
            }
        }


        if(!notification.getHmiLevel().equals(HMILevel.HMI_NONE)
                && firstNonHmiNone){
            sendCommands();
            firstNonHmiNone = false;
        }else{
            //We have HMI_NONE
            if(notification.getFirstRun()){
                uploadImages();
            }
        }

    }

    /**
     *  Requests list of images to SDL, and uploads images that are missing.
     */
    private void uploadImages(){
        ListFiles listFiles = new ListFiles();
        this.sendRpcRequest(listFiles);

    }

    @Override
    public void onListFilesResponse(ListFilesResponse response) {
        Log.i(TAG, "onListFilesResponse from SDL ");
        if(response.getSuccess()){
            remoteFiles = response.getFilenames();
        }

        // Check the mutable set for the AppIcon
        // If not present, upload the image
        if(remoteFiles== null || !remoteFiles.contains(SdlService.ICON_FILENAME)){
            try {
                sendIcon();
            } catch (SdlException e) {
                e.printStackTrace();
            }
        }else{
            // If the file is already present, send the SetAppIcon request
            try {
                proxy.setappicon(ICON_FILENAME, CorrelationIdGenerator.generateId());
            } catch (SdlException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onPutFileResponse(PutFileResponse response) {
        Log.i(TAG, "onPutFileResponse from SDL");
        if(response.getCorrelationID() == iconCorrelationId){ //If we have successfully uploaded our icon, we want to set it
            try {
                proxy.setappicon(ICON_FILENAME, CorrelationIdGenerator.generateId());
            } catch (SdlException e) {
                e.printStackTrace();
            }
        }

    }

    @Override
    public void onOnLockScreenNotification(OnLockScreenStatus notification) {

    }

    @Override
    public void onOnCommand(OnCommand notification){
        Integer id = notification.getCmdID();
        if(id != null){
            switch(id){
                case TEST_COMMAND_ID:
                    showTest();
                    break;
            }
            //onAddCommandClicked(id);
        }
    }

    /**
     *  Callback method that runs when the add command response is received from SDL.
     */
    @Override
    public void onAddCommandResponse(AddCommandResponse response) {
        Log.i(TAG, "AddCommand response from SDL: " + response.getResultCode().name());

    }


	/*  Vehicle Data   */


    @Override
    public void onOnPermissionsChange(OnPermissionsChange notification) {
        Log.i(TAG, "Permision changed: " + notification);
    }

    @Override
    public void onSubscribeVehicleDataResponse(SubscribeVehicleDataResponse response) {
        if(response.getSuccess()){
            Log.i(TAG, "Subscribed to vehicle data");
        }
    }

    @Override
    public void onOnVehicleData(OnVehicleData notification) {
        Log.i(TAG, "Vehicle data notification from SDL");
    }

    /**
     * Rest of the SDL callbacks from the head unit
     */

    @Override
    public void onAddSubMenuResponse(AddSubMenuResponse response) {
        Log.i(TAG, "AddSubMenu response from SDL: " + response.getResultCode().name() + " Info: " + response.getInfo());
    }

    @Override
    public void onCreateInteractionChoiceSetResponse(CreateInteractionChoiceSetResponse response) {
        Log.i(TAG, "CreateInteractionChoiceSet response from SDL: " + response.getResultCode().name() + " Info: " + response.getInfo());
    }

    @Override
    public void onAlertResponse(AlertResponse response) {
        Log.i(TAG, "Alert response from SDL: " + response.getResultCode().name() + " Info: " + response.getInfo());
    }

    @Override
    public void onDeleteCommandResponse(DeleteCommandResponse response) {
        Log.i(TAG, "DeleteCommand response from SDL: " + response.getResultCode().name() + " Info: " + response.getInfo());
    }

    @Override
    public void onDeleteInteractionChoiceSetResponse(DeleteInteractionChoiceSetResponse response) {
        Log.i(TAG, "DeleteInteractionChoiceSet response from SDL: " + response.getResultCode().name() + " Info: " + response.getInfo());
    }

    @Override
    public void onDeleteSubMenuResponse(DeleteSubMenuResponse response) {
        Log.i(TAG, "DeleteSubMenu response from SDL: " + response.getResultCode().name() + " Info: " + response.getInfo());
    }

    @Override
    public void onPerformInteractionResponse(PerformInteractionResponse response) {
        Log.i(TAG, "PerformInteraction response from SDL: " + response.getResultCode().name() + " Info: " + response.getInfo());
    }

    @Override
    public void onResetGlobalPropertiesResponse(
            ResetGlobalPropertiesResponse response) {
        Log.i(TAG, "ResetGlobalProperties response from SDL: " + response.getResultCode().name() + " Info: " + response.getInfo());
    }

    @Override
    public void onSetGlobalPropertiesResponse(SetGlobalPropertiesResponse response) {
        Log.i(TAG, "SetGlobalProperties response from SDL: " + response.getResultCode().name() + " Info: " + response.getInfo());
    }

    @Override
    public void onSetMediaClockTimerResponse(SetMediaClockTimerResponse response) {
        Log.i(TAG, "SetMediaClockTimer response from SDL: " + response.getResultCode().name() + " Info: " + response.getInfo());
    }

    @Override
    public void onShowResponse(ShowResponse response) {
        Log.i(TAG, "Show response from SDL: " + response.getResultCode().name() + " Info: " + response.getInfo());
    }

    @Override
    public void onSpeakResponse(SpeakResponse response) {
        Log.i(TAG, "SpeakCommand response from SDL: " + response.getResultCode().name() + " Info: " + response.getInfo());
    }

    @Override
    public void onOnButtonEvent(OnButtonEvent notification) {
        Log.i(TAG, "OnButtonEvent notification from SDL: " + notification);
    }

    @Override
    public void onOnButtonPress(OnButtonPress notification) {
        Log.i(TAG, "OnButtonPress notification from SDL: " + notification);
    }

    @Override
    public void onSubscribeButtonResponse(SubscribeButtonResponse response) {
        Log.i(TAG, "SubscribeButton response from SDL: " + response.getResultCode().name() + " Info: " + response.getInfo());
    }

    @Override
    public void onUnsubscribeButtonResponse(UnsubscribeButtonResponse response) {
        Log.i(TAG, "UnsubscribeButton response from SDL: " + response.getResultCode().name() + " Info: " + response.getInfo());
    }


    @Override
    public void onOnTBTClientState(OnTBTClientState notification) {
        Log.i(TAG, "OnTBTClientState notification from SDL: " + notification);
    }

    @Override
    public void onUnsubscribeVehicleDataResponse(
            UnsubscribeVehicleDataResponse response) {
        Log.i(TAG, "UnsubscribeVehicleData response from SDL: " + response.getResultCode().name() + " Info: " + response.getInfo());

    }

    @Override
    public void onGetVehicleDataResponse(GetVehicleDataResponse response) {
        Log.i(TAG, "GetVehicleData response from SDL: " + response.getResultCode().name() + " Info: " + response.getInfo());

    }

    @Override
    public void onReadDIDResponse(ReadDIDResponse response) {
        Log.i(TAG, "ReadDID response from SDL: " + response.getResultCode().name() + " Info: " + response.getInfo());

    }

    @Override
    public void onGetDTCsResponse(GetDTCsResponse response) {
        Log.i(TAG, "GetDTCs response from SDL: " + response.getResultCode().name() + " Info: " + response.getInfo());

    }


    @Override
    public void onPerformAudioPassThruResponse(PerformAudioPassThruResponse response) {
        Log.i(TAG, "PerformAudioPassThru response from SDL: " + response.getResultCode().name() + " Info: " + response.getInfo());

    }

    @Override
    public void onEndAudioPassThruResponse(EndAudioPassThruResponse response) {
        Log.i(TAG, "EndAudioPassThru response from SDL: " + response.getResultCode().name() + " Info: " + response.getInfo());

    }

    @Override
    public void onOnAudioPassThru(OnAudioPassThru notification) {
        Log.i(TAG, "OnAudioPassThru notification from SDL: " + notification );

    }

    @Override
    public void onDeleteFileResponse(DeleteFileResponse response) {
        Log.i(TAG, "DeleteFile response from SDL: " + response.getResultCode().name() + " Info: " + response.getInfo());

    }

    @Override
    public void onSetAppIconResponse(SetAppIconResponse response) {
        Log.i(TAG, "SetAppIcon response from SDL: " + response.getResultCode().name() + " Info: " + response.getInfo());

    }

    @Override
    public void onScrollableMessageResponse(ScrollableMessageResponse response) {
        Log.i(TAG, "ScrollableMessage response from SDL: " + response.getResultCode().name() + " Info: " + response.getInfo());

    }

    @Override
    public void onChangeRegistrationResponse(ChangeRegistrationResponse response) {
        Log.i(TAG, "ChangeRegistration response from SDL: " + response.getResultCode().name() + " Info: " + response.getInfo());

    }

    @Override
    public void onSetDisplayLayoutResponse(SetDisplayLayoutResponse response) {
        Log.i(TAG, "SetDisplayLayout response from SDL: " + response.getResultCode().name() + " Info: " + response.getInfo());

    }

    @Override
    public void onOnLanguageChange(OnLanguageChange notification) {
        Log.i(TAG, "OnLanguageChange notification from SDL: " + notification);

    }

    @Override
    public void onSliderResponse(SliderResponse response) {
        Log.i(TAG, "Slider response from SDL: " + response.getResultCode().name() + " Info: " + response.getInfo());

    }


    @Override
    public void onOnHashChange(OnHashChange notification) {
        Log.i(TAG, "OnHashChange notification from SDL: " + notification);

    }

    @Override
    public void onOnSystemRequest(OnSystemRequest notification) {
        Log.i(TAG, "OnSystemRequest notification from SDL: " + notification);

    }

    @Override
    public void onSystemRequestResponse(SystemRequestResponse response) {
        Log.i(TAG, "SystemRequest response from SDL: " + response.getResultCode().name() + " Info: " + response.getInfo());

    }

    @Override
    public void onOnKeyboardInput(OnKeyboardInput notification) {
        Log.i(TAG, "OnKeyboardInput notification from SDL: " + notification);

    }

    @Override
    public void onOnTouchEvent(OnTouchEvent notification) {
        Log.i(TAG, "OnTouchEvent notification from SDL: " + notification);

    }

    @Override
    public void onDiagnosticMessageResponse(DiagnosticMessageResponse response) {
        Log.i(TAG, "DiagnosticMessage response from SDL: " + response.getResultCode().name() + " Info: " + response.getInfo());

    }

    @Override
    public void onOnStreamRPC(OnStreamRPC notification) {
        Log.i(TAG, "OnStreamRPC notification from SDL: " + notification);

    }

    @Override
    public void onStreamRPCResponse(StreamRPCResponse response) {
        Log.i(TAG, "StreamRPC response from SDL: " + response.getResultCode().name() + " Info: " + response.getInfo());

    }

    @Override
    public void onDialNumberResponse(DialNumberResponse response) {
        Log.i(TAG, "DialNumber response from SDL: " + response.getResultCode().name() + " Info: " + response.getInfo());

    }

    @Override
    public void onSendLocationResponse(SendLocationResponse response) {
        Log.i(TAG, "SendLocation response from SDL: " + response.getResultCode().name() + " Info: " + response.getInfo());

    }

    @Override
    public void onServiceEnded(OnServiceEnded serviceEnded) {
        Log.d(TAG, "Service ended: " + serviceEnded.getSessionType());

    }

    @Override
    public void onServiceNACKed(OnServiceNACKed serviceNACKed) {
        Log.d(TAG, "Service NACKed: " + serviceNACKed.getSessionType());
    }

    @Override
    public void onShowConstantTbtResponse(ShowConstantTbtResponse response) {
        Log.i(TAG, "ShowConstantTbt response from SDL: " + response.getResultCode().name() + " Info: " + response.getInfo());

    }

    @Override
    public void onAlertManeuverResponse(AlertManeuverResponse response) {
        Log.i(TAG, "AlertManeuver response from SDL: " + response.getResultCode().name() + " Info: " + response.getInfo());

    }

    @Override
    public void onUpdateTurnListResponse(UpdateTurnListResponse response) {
        Log.i(TAG, "UpdateTurnList response from SDL: " + response.getResultCode().name() + " Info: " + response.getInfo());

    }

    @Override
    public void onServiceDataACK(int dataSize) {

    }

    @Override
    public void onGetWayPointsResponse(GetWayPointsResponse response) {

    }

    @Override
    public void onSubscribeWayPointsResponse(SubscribeWayPointsResponse response) {

    }

    @Override
    public void onUnsubscribeWayPointsResponse(UnsubscribeWayPointsResponse response) {

    }

    @Override
    public void onOnWayPointChange(OnWayPointChange notification) {

    }

    @Override
    public void onOnDriverDistraction(OnDriverDistraction notification) {
        // Some RPCs (depending on region) cannot be sent when driver distraction is active.
    }

    @Override
    public void onError(String info, Exception e) {
    e.printStackTrace();
    }

    @Override
    public void onGenericResponse(GenericResponse response) {
        Log.i(TAG, "Generic response from SDL: " + response.getResultCode().name() + " Info: " + response.getInfo());
    }


    /**************************************************************************************************************************************************************************************************************************
     ************************************************************************************ DRONE MADNESS ***********************************************************************************************************************
     **************************************************************************************************************************************************************************************************************************/


}
