/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.smartdevicelink.sdlstreaming.videostreaming;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.SurfaceTexture;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLExt;
import android.opengl.EGLSurface;
import android.os.Build;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;

import com.smartdevicelink.proxy.rpc.OnTouchEvent;
import com.smartdevicelink.proxy.rpc.enums.TouchType;

import java.util.Timer;
import java.util.TimerTask;

/**
 * Holds state associated with a Surface used for MediaCodec encoder input.
 * <p>
 * The constructor takes a Surface obtained from MediaCodec.createInputSurface(), and uses that
 * to create an EGL window surface.  Calls to eglSwapBuffers() cause a frame of data to be sent
 * to the video encoder.
 */
@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
class InputSurface {
    private static final String TAG = "InputSurface";
    private static final boolean VERBOSE = false;
    private static final int EGL_RECORDABLE_ANDROID = 0x3142;
    private static final int EGL_OPENGL_ES2_BIT = 4;
    private EGLDisplay mEGLDisplay;
    private EGLContext mEGLContext;
    private EGLSurface mEGLSurface;
    private Surface mSurface;
    private Context mContext;
    private Object mFrameSyncObject = new Object();     // guards mFrameAvailable
    private Timer timer;
    private TimerTask task;
    private TextureView mTextureView;
    private SurfaceTexture mSurfaceTexture;
    private TextureRender mTextureRender;
    /**
     * Creates an InputSurface from a Surface.
     */
    public InputSurface(Surface surface, Context context) {
        if (surface == null) {
            throw new NullPointerException();
        }
        mSurface = surface;
        mContext = context;
        eglSetup();
        makeCurrent();
        setup();
    }
    /**
     * Creates instances of TextureRender and SurfaceTexture, and a Surface associated
     * with the SurfaceTexture.
     */
    private void setup() {
    	mTextureView = new TextureView(mContext);
        mTextureRender = new TextureRender();
        mTextureRender.surfaceCreated();
        // Even if we don't access the SurfaceTexture after the constructor returns, we
        // still need to keep a reference to it.  The Surface doesn't retain a reference
        // at the Java level, so if we don't either then the object can get GCed, which
        // causes the native finalizer to run.
        if (VERBOSE) Log.d(TAG, "textureID=" + mTextureRender.getTextureId());
        mSurfaceTexture = new SurfaceTexture(mTextureRender.getTextureId());
        mTextureView.setSurfaceTexture(mSurfaceTexture);
        
        task = new TimerTask() {
            public void run() {
              if (VERBOSE) Log.d(TAG, "new frame available");
              synchronized (mFrameSyncObject) {
                  mFrameSyncObject.notifyAll();
              }
            }
        };
        timer = new Timer();
        timer.scheduleAtFixedRate(task, 25, 25);
    }
    /**
     * Prepares EGL.  We want a GLES 2.0 context and a surface that supports recording.
     */
    private void eglSetup() {
        mEGLDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        if (mEGLDisplay == EGL14.EGL_NO_DISPLAY) {
            throw new RuntimeException("unable to get EGL14 display");
        }
        int[] version = new int[2];
        if (!EGL14.eglInitialize(mEGLDisplay, version, 0, version, 1)) {
            mEGLDisplay = null;
            throw new RuntimeException("unable to initialize EGL14");
        }
        // Configure EGL for pbuffer and OpenGL ES 2.0.  We want enough RGB bits
        // to be able to tell if the frame is reasonable.
        int[] attribList = {
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
                EGL_RECORDABLE_ANDROID, 1,
                EGL14.EGL_NONE
        };
        EGLConfig[] configs = new EGLConfig[1];
        int[] numConfigs = new int[1];
        if (!EGL14.eglChooseConfig(mEGLDisplay, attribList, 0, configs, 0, configs.length,
                numConfigs, 0)) {
            throw new RuntimeException("unable to find RGB888+recordable ES2 EGL config");
        }
        // Configure context for OpenGL ES 2.0.
        int[] attrib_list = {
                EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
                EGL14.EGL_NONE
        };
        mEGLContext = EGL14.eglCreateContext(mEGLDisplay, configs[0], EGL14.EGL_NO_CONTEXT,
                attrib_list, 0);
        checkEglError("eglCreateContext");
        if (mEGLContext == null) {
            throw new RuntimeException("null context");
        }
        // Create a window surface, and attach it to the Surface we received.
        int[] surfaceAttribs = {
                EGL14.EGL_NONE
        };
        mEGLSurface = EGL14.eglCreateWindowSurface(mEGLDisplay, configs[0], mSurface,
                surfaceAttribs, 0);
        checkEglError("eglCreateWindowSurface");
        if (mEGLSurface == null) {
            throw new RuntimeException("surface was null");
        }
    }
    /**
     * Discard all resources held by this class, notably the EGL context.  Also releases the
     * Surface that was passed to our constructor.
     */
    public void release() {
        if (EGL14.eglGetCurrentContext().equals(mEGLContext)) {
            // Clear the current context and surface to ensure they are discarded immediately.
            EGL14.eglMakeCurrent(mEGLDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE,
                    EGL14.EGL_NO_CONTEXT);
        }
        EGL14.eglDestroySurface(mEGLDisplay, mEGLSurface);
        EGL14.eglDestroyContext(mEGLDisplay, mEGLContext);
        //EGL14.eglTerminate(mEGLDisplay);
        mSurface.release();
        // null everything out so future attempts to use this object will cause an NPE
        mEGLDisplay = null;
        mEGLContext = null;
        mEGLSurface = null;
        mSurface = null;
    }
    /**
     * Makes our EGL context and surface current.
     */
    public void makeCurrent() {
        if (!EGL14.eglMakeCurrent(mEGLDisplay, mEGLSurface, mEGLSurface, mEGLContext)) {
            throw new RuntimeException("eglMakeCurrent failed");
        }
    }
    /**
     * Calls eglSwapBuffers.  Use this to "publish" the current frame.
     */
    public boolean swapBuffers() {
        return EGL14.eglSwapBuffers(mEGLDisplay, mEGLSurface);
    }
    /**
     * Returns the Surface that the MediaCodec receives buffers from.
     */
    public Surface getSurface() {
        return mSurface;
    }
    /**
     * Sends the presentation time stamp to EGL.  Time is expressed in nanoseconds.
     */
    public void setPresentationTime(long nsecs) {
        EGLExt.eglPresentationTimeANDROID(mEGLDisplay, mEGLSurface, nsecs);
    }
    /**
     * Checks for EGL errors.
     */
    private void checkEglError(String msg) {
        boolean failed = false;
        int error;
        while ((error = EGL14.eglGetError()) != EGL14.EGL_SUCCESS) {
            Log.e(TAG, msg + ": EGL error: 0x" + Integer.toHexString(error));
            failed = true;
        }
        if (failed) {
            throw new RuntimeException("EGL error encountered (see log)");
        }
    }
    /**
     * Latches the next buffer into the texture.  Must be called from the thread that created
     * the OutputSurface object, after the onFrameAvailable callback has signaled that new
     * data is available.
     */
    public void awaitNewImage() {
        final int TIMEOUT_MS = 1000;
        synchronized (mFrameSyncObject) {
            try {
                // Wait for onFrameAvailable() to signal us.  Use a timeout to avoid
                // stalling the test if it doesn't arrive.
                mFrameSyncObject.wait(TIMEOUT_MS);
            } catch (InterruptedException ie) {
                // shouldn't happen
                throw new RuntimeException(ie);
            }
        }
        // Latch the data.
        mTextureRender.checkGlError("before updateTexImage");
        mSurfaceTexture.updateTexImage();
    }
    /**
     * Draws the data from SurfaceTexture onto the current EGL surface.
     */
    public void drawImage() {
        mTextureRender.drawFrame(mSurfaceTexture);
    }
    public TextureView getTextureView() {
    	return mTextureView;
    }
    public void surfaceChanged(int width, int height) {
        mTextureRender.surfaceChanged(width, height);
    }
    public void onTouchEvent (OnTouchEvent notification) {
        float X = notification.getEvent().get(0).getC().get(0).getX();
        float Y = notification.getEvent().get(0).getC().get(0).getY();
    	
        if (notification.getType() == TouchType.BEGIN) {
        	mTextureRender.touchEvent(true);
        	mTextureRender.touchEvent(X, Y);
        } else if (notification.getType() == TouchType.MOVE) {
        	mTextureRender.touchEvent(X, Y);
        } else if (notification.getType() == TouchType.END) {
        	mTextureRender.touchEvent(false);
        }
    }
}