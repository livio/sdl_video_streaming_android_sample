package com.smartdevicelink.sdlstreaming.videostreaming;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.SurfaceTexture;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;

import com.smartdevicelink.exception.SdlException;
import com.smartdevicelink.proxy.SdlProxyALM;
import com.smartdevicelink.proxy.rpc.ImageResolution;
import com.smartdevicelink.proxy.rpc.OnTouchEvent;

import java.io.IOException;


@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
public class CameraToMpegTest implements Runnable {

	/**
	 * Record video from the camera preview and encode it as an MP4 file.
	 * Demonstrates the use of MediaCodec with Camera input. Does not record
	 * audio.
	 * <p>
	 * Generally speaking, it's better to use MediaRecorder for this sort of
	 * thing. This example demonstrates one possible advantage: editing of video
	 * as it's being encoded. A GLES 2.0 fragment shader is used to perform a
	 * silly color tweak every 15 frames.
	 * <p>
	 * This uses various features first available in Android "Jellybean" 4.3
	 * (API 18). There is no equivalent functionality in previous releases. (You
	 * can send the Camera preview to a byte buffer with a fully-specified
	 * format, but MediaCodec encoders want different input formats on different
	 * devices, and this use case wasn't well exercised in CTS pre-4.3.)
	 */
	private static final String TAG = "CameraToMpegTest";
	private static final boolean VERBOSE = false; // lots of logging

	public static final int PREFS_DEFAULT_FPS = 15;
	public static final int PREFS_DEFAULT_WIDTH = 800;
	public static final int PREFS_DEFAULT_HEIGHT = 400;
	public static final int PREFS_DEFAULT_BITRATE = 1000000;
	public static final int PREFS_DEFAULT_BUFFER_SIZE = 1048576;
	public static final boolean PREFS_DEFAULT_ENCRYPTED = false;

	/**
	 * Intent to alert anyone who cares
	 */
	public static final String STREAM_CONNECTION_STATUS = "STREAM_CONNECTION_STATUS";
	public static final String STREAM_CONNECTION_STATUS_CONNECTED = "STREAM_CONNECTION_STATUS_CONNECTED";

	// parameters for the encoder
	private static final int IFRAME_INTERVAL = 25;

	// encoder state
	private static InputSurface mInputSurface;

	// camera state
	private Thread t = null;
	private int mFPS;
	private int mWidth;
	private int mHeight;
	private int mBitrate;
	private int mIframeRate;

	private boolean isEncryptedVideo;
	SdlProxyALM proxy;
	Context context;

	Surface surface = null;
	MediaPlayer mp;
	String uri;

	private boolean isStreaming = false;
	private static boolean streamConnected = false;
	public CameraToMpegTest(SdlProxyALM proxy, Context context, int fps, int width, int height, int bitrate, boolean isEncryptedVideo) {
		mFPS = fps;
		mWidth = width;
		mHeight = height;
		mBitrate = bitrate;
		mIframeRate = IFRAME_INTERVAL;
		this.isEncryptedVideo = isEncryptedVideo;
		this.proxy = proxy;
		this.context = context;
		streamConnected = false;
	}

	public CameraToMpegTest(SdlProxyALM proxy, Context context) {
		ImageResolution ir = null;

		try {
			ir = proxy.getDisplayCapabilities().getScreenParams().getImageResolution();
		} catch (SdlException e) {
			Log.i(TAG, "Couldn't get image resolution.");
		}

		this.context = context;
		this.proxy = proxy;
		mFPS = PREFS_DEFAULT_FPS;
		if(ir != null){
			mWidth = ir.getResolutionWidth();
			mHeight = ir.getResolutionHeight();
		}else{
			mWidth = PREFS_DEFAULT_WIDTH;
			mHeight = PREFS_DEFAULT_HEIGHT;
		}
		mBitrate = getInt("codec_bitrate",PREFS_DEFAULT_BITRATE);
		mIframeRate =  getInt("codec_iframe_rate",IFRAME_INTERVAL);
		this.isEncryptedVideo = PREFS_DEFAULT_ENCRYPTED;
		streamConnected = false;
	}

	public void setUri(String uri){
		this.uri = uri;
	}

	public boolean isStreaming(){
		return isStreaming;
	}

	public void start() throws IOException {
		if (t == null) {
			t = new Thread(this);
			t.start();
		}
	}

	public void stop() {
		if (t != null)
		{
			t.interrupt();
			t = null;
		}
	}
	public static boolean isStreamConnected(){
		return streamConnected;
	}
	private void sendSdlConnectedIntent(boolean connected){
		streamConnected = connected;
		Intent intent = new Intent(STREAM_CONNECTION_STATUS);
		intent.putExtra(STREAM_CONNECTION_STATUS_CONNECTED, connected);
		context.sendBroadcast(intent);

	}

	@Override
	public void run() {
		try {
			isStreaming = true;
			this.encodeCameraToMpeg(mFPS, mWidth, mHeight, mBitrate);
			isStreaming = false;
		} catch (Throwable th) {
			th.printStackTrace();
			isStreaming = false;
		}
	}
	private MediaPlayer.OnPreparedListener prepared = new MediaPlayer.OnPreparedListener(){

		@Override
		public void onPrepared(MediaPlayer mp) {
			try{
				Log.d(TAG, "MediaPlayer has been prepared. Attempting to rev this up. EEEEHHHHHHHHHHHHH");
				sendSdlConnectedIntent(true);
				mp.start();
			}catch(IllegalStateException illegal){
				illegal.printStackTrace();
				sendSdlConnectedIntent(false);
			}
		}
	};

	/**
	 * Tests encoding of AVC video from Camera input. The output is saved as an
	 * MP4 file.
	 */
	public void encodeCameraToMpeg(int fps, int width, int height, int bitrate) {
		int encWidth = width;
		int encHeight = height;
		int encBitRate = bitrate;
		Log.d(TAG, "Output " + encWidth + "x" + encHeight + " @"
				+ encBitRate);

		try {

            if(proxy != null) {
           	    surface = proxy.createOpenGLInputSurface(fps, mIframeRate, width, height, bitrate, isEncryptedVideo);
				if (surface != null) {
            	    mInputSurface = new InputSurface(surface, context);
            	    if (mInputSurface != null) {
            	        mInputSurface.surfaceChanged(width, height);
            	        proxy.startEncoder();
            	    }else{
						Log.e(TAG,"InputSurface was null can't encode");
						return;
					}
            	}else{
					Log.e(TAG,"Surface was null can't encode");
					return;
				}
            }else{
				Log.e(TAG,"Proxy was null can't encode");
				return;
			}

            TextureView tv = mInputSurface.getTextureView();
        	SurfaceTexture st = tv.getSurfaceTexture();
			Surface mpSurf = new Surface(st);

            try {
				Log.d(TAG, "Creating new player");

				mp = MediaPlayer.create(context, Uri.parse(uri));
				Log.d(TAG, "Player setting data source to: " + uri);

				mp.setSurface(mpSurf);
				Log.d(TAG, "Player preparing");
				mp.setOnPreparedListener(prepared);
				mp.setOnInfoListener(new MediaPlayer.OnInfoListener() {
					@Override
					public boolean onInfo(MediaPlayer mp, int what, int extra) {
						Log.d(TAG, "onInfo:   What: " + what + " extra: " + extra);
						return false;
					}
				});
				Log.d(TAG, "Player ready");



            } catch (Exception ioe) {
                throw new RuntimeException("setPreviewTexture failed", ioe);
            }

			long startWhen = System.nanoTime();

            while (true) {
                proxy.drainEncoder(false);

				// Acquire a new frame of input, and render it to the Surface.
				// If we had a
				// GLSurfaceView we could switch EGL contexts and call
				// drawImage() a second
				// time to render it on screen. The texture can be shared
				// between contexts by
				// passing the GLSurfaceView's EGLContext as
				// eglCreateContext()'s share_context
				// argument.
				mInputSurface.awaitNewImage();
				mInputSurface.drawImage();

				if (VERBOSE) {
					Log.d(TAG, "present: "
							+ ((st.getTimestamp() - startWhen) / 1000000.0)
							+ "ms");
				}
				mInputSurface.setPresentationTime(st.getTimestamp());

				// Submit it to the encoder. The eglSwapBuffers call will block
				// if the input
				// is full, which would be bad if it stayed full until we
				// dequeued an output
				// buffer (which we can't do, since we're stuck here). So long
				// as we fully drain
				// the encoder before supplying additional input, the system
				// guarantees that we
				// can supply another frame without blocking.
				if (VERBOSE)
					Log.d(TAG, "sending frame to encoder");
				mInputSurface.swapBuffers();
			}
		} catch (Exception ex) {
			ex.printStackTrace();
		} finally {
			Log.d(TAG,"Releasing assets");
			releaseMp();
			releaseEncoder();
			releaseSurfaceTexture();
			releaseSurface();
			sendSdlConnectedIntent(false);
		}
	}

	/**
	 * Stops camera preview, and releases the camera to the system.
	 */
	private void releaseMp(){
		if (VERBOSE)
			Log.d(TAG, "releasing camera");
		if (mp != null) {
			mp.stop();
			mp.release();
			mp = null;
			Log.i(TAG, "Media player released released");
		}
	}

	/**
	 * Releases the SurfaceTexture.
	 */
	private void releaseSurfaceTexture() {
		if (mInputSurface != null) {
			mInputSurface.release();
			mInputSurface = null;
		}
		Log.i(TAG, "Surface texture released");
	}
	/**
	 * Releases the SurfaceTexture.
	 */
	private void releaseSurface() {
		if (surface != null) {
			surface.release();
			surface = null;
		}
		Log.i(TAG, "Surface  released");
	}
	/**
	 * Releases encoder resources.
	 */
	private void releaseEncoder() {
		if (VERBOSE)
            Log.d(TAG, "releasing encoder objects");
        if(proxy!= null) {
			proxy.releaseEncoder();
			proxy.endH264();
			Log.i(TAG, "Encoder  released");
        }
		if (mInputSurface != null) {
			mInputSurface.release();
			mInputSurface = null;
			Log.i(TAG, "Surface texture released after encoder");
		}
	}

	public static void onTouchEvent (OnTouchEvent notification) {
		if (mInputSurface != null) {
			mInputSurface.onTouchEvent(notification);
		}
	}



	//Preferences
	SharedPreferences preferences = null;
	private int getInt(String key, int defVal){

		if(preferences == null){
			preferences =  PreferenceManager.getDefaultSharedPreferences(this.context);
		}
		String value = preferences.getString(key, null);
		return value == null ? defVal : Integer.valueOf(value);
		//return preferences.getInt(key, defVal);
	}
}
