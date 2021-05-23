package pl.bezzalogowe.PhoneUAV;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;
import android.view.TextureView.SurfaceTextureListener;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;

import static android.content.Context.CAMERA_SERVICE;

public class Camera2API implements SurfaceTextureListener {
    private static final String TAG = "camera2";
    private static final int STATE_PREVIEW = 0;
    private static final int STATE_WAIT_LOCK = 1;
    public HandlerThread camera2thread;
    public Handler camera2handler;
    private final ImageReader.OnImageAvailableListener mOnImageAvailableListener = new
            ImageReader.OnImageAvailableListener() {
                @Override
                public void onImageAvailable(ImageReader reader) {
                    camera2handler.post(new ImageSaver(reader.acquireLatestImage()));
                }
            };
    public CameraCaptureSession mPreviewCaptureSession;
    public String imageFileName, imageFilePath, videoFilePath;
    public File imageFolder, videoFolder;
    MainActivity main;

    /*
     http://developer.android.com/reference/android/hardware/camera2/package-summary.html
     https://www.youtube.com/channel/UC4jh7YBBb0UnPIef2NOSJhQ
     */

    CameraManager cameraManager;
    String[] camerasList;
    boolean cameraHasAutoFocus = false;
    boolean torch = false;
    boolean cameraInitialised = false;
    TextureView mTextureView;
    Thread previewThread;
    Size maxResJPEG, maxResMP4;
    String emailHost;
    int emailPort;
    String fromEmail;
    String fromPassword;
    String toEmails = null;
    List<String> toEmailList = null;
    CameraDevice mCameraDevice;
    boolean isRecording = false;
    boolean isBusy = false;
    Handler handlerRecord, handlerTorch;
    SurfaceTexture surfaceTexture;
    Surface preview;
    CameraCaptureSession.StateCallback previewCallback;
    private int mCaptureState = STATE_PREVIEW;
    private ImageReader mImageReader;
    private MediaRecorder mMediaRecorder;
    CameraDevice.StateCallback mCameraDeviceStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(CameraDevice camera) {
            mCameraDevice = camera;
            mMediaRecorder = new MediaRecorder();
            startPreview();
        }

        @Override
        public void onError(CameraDevice camera, int error) {
            camera.close();
            mCameraDevice = null;
        }

        @Override
        public void onDisconnected(CameraDevice camera) {
            stopPreview();
            camera.close();
            mCameraDevice = null;
        }
    };
    private CameraCaptureSession mRecordCaptureSession;
    private CaptureRequest.Builder mCaptureRequestBuilder;
    TextureView.SurfaceTextureListener msurfacetextureviewlistener = new TextureView.SurfaceTextureListener() {

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface,
                                                int width, int height) {
            // TODO Auto-generated method stub
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            // TODO Auto-generated method stub
            return false;
        }

        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            if (mCameraDevice == null) {
                setupCamera();
                connectCamera();
            } else {
                if (isRecording) {
                    //FIXME: Preview doesn't restart after screen is re-activated, start preview without stopping recording
                    resumePreview();
                } else {
                    //previewThread.start();
                    startPreview();
                }
            }
        }
    };
    private CameraCaptureSession.CaptureCallback mPreviewCaptureCallback = new
            CameraCaptureSession.CaptureCallback() {
                private void process(CaptureResult captureResult) {
                    switch (mCaptureState) {
                        case STATE_PREVIEW:
                            break;
                        case STATE_WAIT_LOCK:
                            mCaptureState = STATE_PREVIEW;
                            Integer afState = captureResult.get(CaptureResult.CONTROL_AF_STATE);

                            if (cameraHasAutoFocus) {
                                if (afState == CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED ||
                                        afState == CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED) {
                                    startStillCaptureRequest();
                                }
                            } else {
                                startStillCaptureRequest();
                            }
                            break;
                    }
                }

                @Override
                public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
                    super.onCaptureCompleted(session, request, result);
                    process(result);
                }
            };

    public Camera2API(MainActivity arg) {
        main = arg;
    }

    private static Size pickSizeJPEG(StreamConfigurationMap map) {
        /** https://developer.android.com/reference/android/graphics/ImageFormat#JPEG */
        Size[] sizes = map.getOutputSizes(ImageFormat.JPEG);
        /* min, last item from the list */
        /* max, first item from the list */
        return sizes[0];
    }

    private static Size pickSizeMP4(StreamConfigurationMap map) {
        /** https://developer.android.com/reference/android/graphics/ImageFormat#YUV_420_888 */
        Size[] sizes = map.getOutputSizes(ImageFormat.YUV_420_888);
        return sizes[0];
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width,
                                          int height) {
        // TODO Auto-generated method stub

    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width,
                                            int height) {
        // TODO Auto-generated method stub

    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {
        // TODO Auto-generated method stub
    }

    public void captureImage() {
        /* https://www.nigeapptuts.com/android-video-app-still-capture-recording/ */
        if (!isRecording) {
            if (!isBusy) {
                lockFocus();
            }
        } else {
            //TODO: stop recording, take picture, start recording again
            //main.update.updateConversationHandler.post(new updateVisibilityThread(main.photoButton, View.INVISIBLE));
            //main.update.updateConversationHandler.post(new updateVisibilityThread(main.photoButton, View.VISIBLE));
            Log.d(TAG, "Can't take photo while recording yet!");
        }
    }

    public void setListener(MainActivity argActivity) {
        main = argActivity;
        try {
            mTextureView.setSurfaceTextureListener(msurfacetextureviewlistener);
        } catch (Exception e) {
            Log.d(TAG, e.toString());
            main.logGPX.saveComment("error" + e);
        }
    }

    private void printSizes(StreamConfigurationMap map) {
        /* https://developer.android.com/reference/android/graphics/ImageFormat.html
         34 - PRIVATE, 35 - YUV_420_888, 256 - JPEG, 842094169 - YV12 */

        int[] formats = map.getOutputFormats();
        for (int item : formats) {

            switch (item) {
                case 34: {
                    Log.d(TAG, "format: PRIVATE");
                }
                break;
                case 35: {
                    Log.d(TAG, "format: YUV_420_888");
                }
                break;
                case 842094169: {
                    Log.d(TAG, "format: YV12");
                }
                break;
                case 256:
                    Log.d(TAG, "format: JPEG");
                break;
                default:
                    Log.d(TAG, "format: " + item);
                break;
            }

            String str = "";
            Size[] sizes = map.getOutputSizes(item);
            for (int i = 0; i < sizes.length; i++) {
                str = str + sizes[i].getWidth() + "×" + sizes[i].getHeight();
                if (i < sizes.length - 1) {
                    str = str + ", ";
                }
            }
            Log.d(TAG, "resolutions: " + str);
        }
    }

    public void setupCamera() {
        try {
            camerasList = cameraManager.getCameraIdList();
        } catch (CameraAccessException e) {
            Log.d(TAG, "list: " + e.toString());
        }

        try {
            // When camera ID is not in the cameras array, use primary camera.
            if (Integer.valueOf(main.cameraID) >= (cameraManager.getCameraIdList().length))
                main.cameraID = "0";
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

        String backCameraId = "";
        try {
            for (String id : cameraManager.getCameraIdList()) {
                CameraCharacteristics cameraChar = cameraManager.getCameraCharacteristics(id);

                if (cameraChar.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT) {
                    Size sensorFront = cameraChar.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE);
                    Log.d(TAG, "camera " + id + " front facing: " +
                            sensorFront.getWidth() + "×" +
                            sensorFront.getHeight());
                }

                if (cameraChar.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK) {
                    Size sensorBack = cameraChar.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE);
                    Log.d(TAG, "camera " + id + " back facing: " +
                            sensorBack.getWidth() + "×" +
                            sensorBack.getHeight());
                    backCameraId = id;
                }

                if (cameraChar.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_EXTERNAL) {

                    Log.d(TAG, "camera " + id + " external: " +
                            cameraChar.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE).getWidth() + "×" +
                            cameraChar.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE).getHeight());
                }
            }

            CameraCharacteristics cameraCharacteristics = cameraManager.getCameraCharacteristics(backCameraId);
            Log.d(TAG, "back camera sensor orientation: " + cameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION));

            StreamConfigurationMap map = getStreamConfigurationMap();
            // Prints all available picture sizes for all formats.
            printSizes(map);

            // Picks maximum picture size.
            maxResJPEG = pickSizeJPEG(map);
            Log.d(TAG, "picked photo size: " + maxResJPEG.getWidth() + "×" + maxResJPEG.getHeight());

            maxResMP4 = pickSizeMP4(map);
            Log.d(TAG, "picked video size: " + maxResMP4.getWidth() + "×" + maxResMP4.getHeight());

            mImageReader = ImageReader.newInstance(maxResJPEG.getWidth(), maxResJPEG.getHeight(), ImageFormat.JPEG, 1);
            mImageReader.setOnImageAvailableListener(mOnImageAvailableListener, camera2handler);


            /** https://developer.android.com/reference/android/hardware/camera2/CaptureRequest.html#CONTROL_AF_MODE */
            // Checks if camera has autofocus feature.
            int[] afModes = cameraManager.getCameraCharacteristics(main.cameraID).get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES);

            Log.d(TAG, "CONTROL_AF_AVAILABLE_MODES:");
            for (int position : afModes) {
                switch (afModes[position]) {
                    case 0:
                        Log.d(TAG, "CONTROL_AF_MODE_OFF (0)");
                        break;
                    case 1:
                        Log.d(TAG, "CONTROL_AF_MODE_AUTO (1)");
                        break;
                    case 2:
                        Log.d(TAG, "CONTROL_AF_MODE_MACRO (2)");
                        break;
                    case 3:
                        Log.d(TAG, "CONTROL_AF_MODE_CONTINUOUS_VIDEO (3)");
                        break;
                    case 4:
                        Log.d(TAG, "CONTROL_AF_MODE_CONTINUOUS_PICTURE (4)");
                        break;
                    case 5:
                        Log.d(TAG, "CONTROL_AF_MODE_EDOF (5)");
                        break;
                    default:
                        Log.d(TAG, String.valueOf(afModes[position]));
                }
            }

            if (afModes.length <= 1 && afModes[0] == 0) {
                cameraHasAutoFocus = false;
            } else {
                cameraHasAutoFocus = true;
            }

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private StreamConfigurationMap getStreamConfigurationMap() throws CameraAccessException {
        StreamConfigurationMap map;
        map = cameraManager.getCameraCharacteristics(main.cameraID).get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        return map;
    }

    void connectCamera() {
        try {
            if (Build.VERSION.SDK_INT <= 23) {
                try {
                    cameraManager.openCamera(main.cameraID, mCameraDeviceStateCallback, camera2handler);
                } catch (SecurityException e) {
                    e.printStackTrace();
                }
            } else {
                if (ContextCompat.checkSelfPermission(main, android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                    cameraManager.openCamera(main.cameraID, mCameraDeviceStateCallback, camera2handler);
                } else {
                    /* if(shouldShowRequestPermissionRationale(android.Manifest.permission.CAMERA)) */
                    Toast.makeText(main, "Video app required access to camera", Toast.LENGTH_SHORT).show();
                    main.requestPermissions(new String[]{android.Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO
                    }, main.REQUEST_CAMERA_PERMISSION_RESULT);
                }

            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void setupMediaRecorder() throws IOException, CameraAccessException {
        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        mMediaRecorder.setOutputFile(videoFilePath);
        mMediaRecorder.setVideoEncodingBitRate(8000000);
        mMediaRecorder.setVideoFrameRate(30);
        StreamConfigurationMap map;
        map = getStreamConfigurationMap();
        maxResMP4 = pickSizeMP4(map);
        mMediaRecorder.setVideoSize(maxResMP4.getWidth(), maxResMP4.getHeight());
        //mMediaRecorder.setVideoSize(1920, 1080);
        mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        mMediaRecorder.setOrientationHint(0);
        mMediaRecorder.prepare();
    }

    /* https://stackoverflow.com/questions/34093508/toggle-flashlight-in-camera2-without-interrupting-preview */
    public void turnOnTorch() {
        /** activates torch if it is off */
        if (!torch) {
            try {
                CameraCharacteristics tempChar = cameraManager.getCameraCharacteristics(main.cameraID);
                if (tempChar.get(CameraCharacteristics.FLASH_INFO_AVAILABLE)) {
                    mCaptureRequestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH);
                    if (isRecording) {
                        mRecordCaptureSession.setRepeatingRequest(mCaptureRequestBuilder.build(), null, null);
                    } else {
                        mPreviewCaptureSession.setRepeatingRequest(mCaptureRequestBuilder.build(), null, camera2handler);
                    }
                }
            } catch (Exception e) {
                Log.d("camera", "couldn't turn on torch: " + e);
            }
        }
        torch = true;
    }

    public void turnOffTorch() {
        /** deactivates torch if it is on */
        if (torch) {
            try {
                mCaptureRequestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF);
                if (isRecording) {
                    mRecordCaptureSession.setRepeatingRequest(mCaptureRequestBuilder.build(), null, null);
                } else {
                    mPreviewCaptureSession.setRepeatingRequest(mCaptureRequestBuilder.build(), null, camera2handler);
                }
            } catch (Exception e) {
                Log.d("camera", "couldn't turn off torch: " + e);
            }
        }
        torch = false;
    }

    public void sendPhotoByEmail() {
        /* photo is sent only if you set password for sending mailbox and e-mail recipients are defined */
        if (fromPassword != null && fromPassword != "" && toEmailList != null) {
            if (toEmailList.size() > 0) {
                try {
                    String emailSubject = "Subject";
                    String emailBody = "attached photo: " + imageFileName;
                    new SendMailTask().execute(emailHost, emailPort, fromEmail, fromPassword, toEmailList, emailSubject, emailBody, main);
                } catch (Exception e) {
                    System.out.println(e.getMessage());
                }
            }
        } else {
            System.out.println("no password or recipients");
        }
    }

    public void cameraInit() {
        handlerRecord = new Handler(new Handler.Callback() {
            @Override
            public boolean handleMessage(Message msg) {
                if (msg.arg1 == 1) {
                    //startRecord();
                } else {
                    //stopRecord();
                }
                return false;
            }
        });

        handlerTorch = new Handler(new Handler.Callback() {
            @Override
            public boolean handleMessage(Message msg) {
                if (msg.arg1 == 1) {
                    turnOnTorch();
                    main.torchButton.setTextColor(Color.rgb(255, 255, 255));
                    torch = true;
                } else {
                    turnOffTorch();
                    main.torchButton.setTextColor(Color.rgb(0, 0, 0));
                    torch = false;
                }
                return false;
            }
        });

        emailHost = main.settings.getString("email-smtp-host", "mail.active24.pl"); // mail.active24.pl; smtp.wp.pl; smtp.gmail.com;
        emailPort = Integer.valueOf(main.settings.getString("email-smtp-port", "587")); //no SSL: 587, SSL: 465
        fromEmail = main.settings.getString("email-from", "outgoing@bezzalogowe.pl"); //outgoing@bezzalogowe.pl; foto.pulapka@wp.pl
        fromPassword = main.settings.getString("email-password", "");

        /* e-mail recipients separated with commas or semicolons */
        try {
            toEmails = main.settings.getString("email-list", "");
            if (toEmails != "") {
                toEmailList = Arrays.asList(toEmails.split("\\s*[,;]\\s*"));
            }
        } catch (NumberFormatException e) {
            e.printStackTrace();
        }

        createVideoFolder();
        createImageFolder();

        mTextureView = (TextureView) main.findViewById(R.id.preview);

        main.torchButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (torch) {
                    turnOffTorch();
                    main.torchButton.setTextColor(Color.rgb(255, 255, 255));
                } else {
                    turnOnTorch();
                    main.torchButton.setTextColor(Color.rgb(0, 0, 0));
                }
            }
        });

        main.photoButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                captureImage();
            }
        });

        main.videoButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!isRecording) {
                    startRecord();
                } else {
                    stopRecord();
                }
            }
        });
    }

    boolean hasCameraPermission() {
        return ContextCompat.checkSelfPermission(main, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    void cameraPause() {
        if (cameraInitialised) {
            if (Build.VERSION.SDK_INT >= 21) {
                /* API>20 */
                if (mPreviewCaptureSession == null) {
                    // Preview not initialized, improbable situation
                    closeActiveCamera();
                    stopCameraThread();
                } else {
                    // Preview initialized, hides preview
                    mTextureView.setVisibility(View.INVISIBLE);
                }
            } else {
                /* Build.VERSION_CODES.KITKAT_WATCH */
            }
        }
    }

    void cameraResume() {
        if (cameraInitialised) {
            if (Build.VERSION.SDK_INT >= 21) {
                if (mPreviewCaptureSession == null) {
                    // Preview not initialized
                    setListener(main);
                    startCameraThread();
                    if (mTextureView.isAvailable()) {
                        Log.d("camera", "surface IS available");
                        setupCamera();
                    } else {
                        Log.d("camera", "surface IS NOT yet available");
                        try {
                            cameraManager = (android.hardware.camera2.CameraManager) main.getSystemService(CAMERA_SERVICE);
                        } catch (Exception e) {
                            Log.d("camera", "manager: " + e.toString());
                        }
                    }
                } else {
                    // Preview initialized, un-hides preview
                    mTextureView.setVisibility(View.VISIBLE);
                }
            } else {
                /* Build.VERSION_CODES.KITKAT_WATCH */
                //camObjectKitkat.setListener(this);
            }
        }
    }

    public void startPreview() {
        previewThread = new Thread(new PreviewThread());
        previewThread.start();
    }

    public void stopPreview() {
        if (/*previewThread != null*/ previewThread.getState() == Thread.State.RUNNABLE) {
            try {
                mPreviewCaptureSession.stopRepeating();
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
            previewThread.interrupt();
            previewThread = null;
        }
    }

    public void startRecord() {
        try {
            //FIXME: Android 7 issue: screen must stay on, otherwise preview won't restart when it is reactivated, this forces the screen to stay on */
            main.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

            createVideoFileName();
            setupMediaRecorder();

            surfaceTexture = mTextureView.getSurfaceTexture();
            surfaceTexture.setDefaultBufferSize(176, 144);
            preview = new Surface(surfaceTexture);
            Surface recordSurface = mMediaRecorder.getSurface();
            mCaptureRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);

            mCaptureRequestBuilder.addTarget(preview);
            mCaptureRequestBuilder.addTarget(recordSurface);

            mCameraDevice.createCaptureSession(Arrays.asList(preview, recordSurface, mImageReader.getSurface()),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(CameraCaptureSession session) {
                            mRecordCaptureSession = session;
                            try {

                                mRecordCaptureSession.setRepeatingRequest(mCaptureRequestBuilder.build(), null, null);
                            } catch (CameraAccessException e) {
                                e.printStackTrace();
                            }
                        }

                        @Override
                        public void onConfigureFailed(CameraCaptureSession session) {
                            Log.d(TAG, "onConfigureFailed: startRecord");
                        }
                    }, null);
        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            mMediaRecorder.start();
        } catch (IllegalStateException e) {
            e.printStackTrace();
            Log.d(TAG, "start error: " + e);
        }

        isRecording = true;
        main.videoButton.setText("\u25A0");
        main.videoButton.setTextColor(0xffffffff);

        Thread feedbackCameraThread = new Thread(new Wrap());
        feedbackCameraThread.start();

        Thread torch = new Thread(new setTorchDelayed());
        torch.start();

        //TODO: add condition
        main.logSubRip.startLog();
    }

    public void stopRecord() {
        /* Starting the preview prior to stopping recording which should hopefully resolve issues being seen in Samsung devices. */
        startPreview();
        try {
            mMediaRecorder.start();
        } catch (IllegalStateException e) {
            e.printStackTrace();
            Log.d(TAG, "error: " + e);
        }

        mMediaRecorder.reset();

        isRecording = false;
        main.videoButton.setText("\u25CF");
        main.videoButton.setTextColor(0xffcc0000);

        Thread feedbackFeedbackCamera = new Thread(new Wrap());
        feedbackFeedbackCamera.start();
        truncateVideoPath();

        Thread torch = new Thread(new setTorchDelayed());
        torch.start();

        /** Android 7 issue: screen must stay on, otherwise preview won't restart when it is reactivated,
         * this allows the screen to go dark once recording has stopped */
        main.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        //TODO: add condition
        main.logSubRip.stopLog(main);
    }

    public void toggleRecording() {
        if (Build.VERSION.SDK_INT <= 20) {
            if (!main.camObjectKitkat.isRecording) {
                main.camObjectKitkat.captureVideoStart();
            } else {
                main.camObjectKitkat.captureVideoStop();
            }
        } else {
            if (!main.camObjectLolipop.isRecording) {
                try {
                    final Message msg = new Message();
                    new Thread() {
                        public void run() {
                            msg.arg1 = 1;
                            main.camObjectLolipop.handlerRecord.sendMessage(msg);
                        }
                    }.start();
                } catch (Exception e) {
                    e.printStackTrace();
                    System.out.println("handlerCamera error: " + e.toString() + "\n");
                }
            } else {
                try {
                    final Message msg = new Message();
                    new Thread() {
                        public void run() {
                            msg.arg1 = 0;
                            main.camObjectLolipop.handlerRecord.sendMessage(msg);
                        }
                    }.start();
                } catch (Exception e) {
                    e.printStackTrace();
                    System.out.println("handlerCamera error: " + e.toString() + "\n");
                }
            }
        }
    }

    private void startStillCaptureRequest() {
        try {
            if (isRecording) {
                mCaptureRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_VIDEO_SNAPSHOT);
            } else {
                mCaptureRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            }
            mCaptureRequestBuilder.addTarget(mImageReader.getSurface());
            mCaptureRequestBuilder.set(CaptureRequest.JPEG_ORIENTATION, 0);

            CameraCaptureSession.CaptureCallback stillCaptureCallback = new
                    CameraCaptureSession.CaptureCallback() {
                        @Override
                        public void onCaptureStarted(CameraCaptureSession session, CaptureRequest request, long timestamp, long frameNumber) {
                            super.onCaptureStarted(session, request, timestamp, frameNumber);
                            try {
                                isBusy = true;
                                createImageFileName();
                            } catch (IOException e) {
                                e.printStackTrace();
                                Log.d("onCaptureStarted", "error: " + e);
                            }
                        }

                        @Override
                        public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
                            truncateImagePath();
                            Log.d("SendMailTask", "onCaptureCompleted");
                            sendPhotoByEmail();
                            isBusy = false;
                        }

                        @Override
                        public void onCaptureFailed(CameraCaptureSession session, CaptureRequest request, CaptureFailure failure) {
                            Log.d("SendMailTask", "Image capture failed.");
                        }
                    };

            if (isRecording) {
                mRecordCaptureSession.capture(mCaptureRequestBuilder.build(), stillCaptureCallback, null);
            } else {
                mPreviewCaptureSession.capture(mCaptureRequestBuilder.build(), stillCaptureCallback, null);
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
            Log.d("startStillCaptureReq.", "error: " + e);
        }
    }

    public void startCameraThread() {
        camera2thread = new HandlerThread("Camera2");
        camera2thread.start();
        camera2handler = new Handler(camera2thread.getLooper());
    }

    public void stopCameraThread() {
        camera2thread.quitSafely();
        try {
            camera2thread.join();
            camera2thread = null;
            camera2handler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void closeActiveCamera() {
        if (mCameraDevice != null) {
            mCameraDevice.close();
            mCameraDevice = null;
        }
    }

    String getTimeStamp() {
        Long time = System.currentTimeMillis();
        String dateISO8601 = new java.text.SimpleDateFormat("yyyy-MM-dd").format(new java.util.Date(time));
        String timeISO8601 = new java.text.SimpleDateFormat("HHmmss").format(new java.util.Date(time));
        return dateISO8601 + "_" + timeISO8601;
    }

    public void createVideoFolder() {
        /* DIRECTORY_MOVIES */
        File movieFile = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM);
        videoFolder = new File(movieFile, "PhoneUAVmpeg4");
        if (!videoFolder.exists()) {
            videoFolder.mkdirs();
        }
    }

    public void createImageFolder() {
        /* DIRECTORY_PICTURES */
        File imageFile = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM);
        imageFolder = new File(imageFile, "PhoneUAVjpeg");
        if (!imageFolder.exists()) {
            imageFolder.mkdirs();
        }
    }

    public File createVideoFileName() throws IOException {
        String timestamp = getTimeStamp();
        File videoFile = null;
        try {
            videoFile = File.createTempFile(timestamp, ".mp4", videoFolder);
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(main, e.toString(), Toast.LENGTH_SHORT).show();
        }
        videoFilePath = videoFile.getAbsolutePath();
        return videoFile;
    }

    // unclear what this does
    private CameraCaptureSession.CaptureCallback mRecordCaptureCallback = new
            CameraCaptureSession.CaptureCallback() {
                private void process(CaptureResult captureResult) {
                    switch (mCaptureState) {
                        case STATE_PREVIEW:
                            // Do nothing
                            break;
                        case STATE_WAIT_LOCK:
                            mCaptureState = STATE_PREVIEW;
                            Integer afState = captureResult.get(CaptureResult.CONTROL_AF_STATE);
                            if (afState == CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED ||
                                    afState == CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED) {
                                Log.d(TAG, "camera " + main.cameraID + " AF Locked!");
                                startStillCaptureRequest();
                            }
                            break;
                    }
                }

                @Override
                public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
                    super.onCaptureCompleted(session, request, result);

                    process(result);
                }
            };

    private File createImageFileName() throws IOException {
        String timestamp = getTimeStamp();
        File imageFile = null;
        try {
            imageFile = File.createTempFile(timestamp, ".jpg", imageFolder);
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(main, e.toString(), Toast.LENGTH_SHORT).show();
        }
        imageFilePath = imageFile.getAbsolutePath();
        imageFileName = imageFile.getName();
        return imageFile;
    }

    private void truncateVideoPath() {
        /** Removes trailing number from filename. */
        String shortPath = videoFilePath.replaceFirst("([0-9]){10}(?=\\.)", "");

        /** Truncates filename by fixed number of characters and adds back extension. */
        //String shortName = path.substring(0, path.length() - 14) + ".mp4";

        File from = new File(videoFilePath);
        File to = new File(shortPath);
        from.renameTo(to);

        videoFilePath = shortPath;
    }

    private void truncateImagePath() {
        /** Removes trailing number from filename. */
        String shortPath = imageFilePath.replaceFirst("([0-9]){10}(?=\\.)", "");

        File from = new File(imageFilePath);
        File to = new File(shortPath);
        from.renameTo(to);

        imageFilePath = shortPath;
        imageFileName = to.getName();
    }

    public void lockFocus() {
        mCaptureState = STATE_WAIT_LOCK;
        mCaptureRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_START);
        try {
            mPreviewCaptureSession.capture(mCaptureRequestBuilder.build(), mPreviewCaptureCallback, camera2handler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
            Log.d("lockFocus", "error: " + e);
        }
    }

    void resumePreview() {
        //FIXME: Preview may crash
        try {
            mPreviewCaptureSession.setRepeatingRequest(mCaptureRequestBuilder.build(), null, camera2handler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

        try {
            mCameraDevice.createCaptureSession(Arrays.asList(preview, mImageReader.getSurface()), previewCallback, camera2handler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private class ImageSaver implements Runnable {

        private final Image mImage;
        public ImageSaver(Image image) {
            mImage = image;
        }

        @Override
        public void run() {
            ByteBuffer byteBuffer = mImage.getPlanes()[0].getBuffer();
            byte[] bytes = new byte[byteBuffer.remaining()];
            byteBuffer.get(bytes);

            FileOutputStream fileOutputStream = null;
            try {
                fileOutputStream = new FileOutputStream(imageFilePath);
                fileOutputStream.write(bytes);
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                mImage.close();

                Intent mediaStoreUpdateIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                mediaStoreUpdateIntent.setData(Uri.fromFile(new File(imageFilePath)));
                //sendBroadcast(mediaStoreUpdateIntent);

                if (fileOutputStream != null) {
                    try {
                        fileOutputStream.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    class setTorchDelayed implements Runnable {
        /** Sets torch with delay. */

        @Override
        public void run() {
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            try {
                if (torch) {
                    CameraCharacteristics tempChar = cameraManager.getCameraCharacteristics(main.cameraID);
                    if (tempChar.get(CameraCharacteristics.FLASH_INFO_AVAILABLE)) {
                        mCaptureRequestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH);
                    }
                } else {
                    try {
                        mCaptureRequestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF);
                    } catch (Exception e) {
                        Log.d("camera", "torch mode setting error: " + e);
                    }
                }

                if (isRecording) {
                    mRecordCaptureSession.setRepeatingRequest(mCaptureRequestBuilder.build(), null, null);
                } else {
                    mPreviewCaptureSession.setRepeatingRequest(mCaptureRequestBuilder.build(), null, camera2handler);
                }
            } catch (Exception e) {
                Log.d("camera", "torch repeating request setting error: " + e);
            }
        }
    }

    class PreviewThread implements Runnable {
        @SuppressWarnings("all")
        public void run() {
            if (mCameraDevice != null) {
                surfaceTexture = mTextureView.getSurfaceTexture();
                surfaceTexture.setDefaultBufferSize(176, 144);
                preview = new Surface(surfaceTexture);

                try {
                    mCaptureRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                } catch (CameraAccessException e) {
                    e.printStackTrace();
                }

                mCaptureRequestBuilder.addTarget(preview);

                previewCallback = new CameraCaptureSession.StateCallback() {

                    @Override
                    public void onConfigured(CameraCaptureSession session) {
                        mPreviewCaptureSession = session;
                        try {
                            //FIXME: causes crashes when the app run for the first time after re-installation
                            session.setRepeatingRequest(mCaptureRequestBuilder.build(), null, camera2handler);
                        } catch (CameraAccessException e) {
                            e.printStackTrace();
                        }
                    }

                    @Override
                    public void onConfigureFailed(CameraCaptureSession session) {
                        Log.d(TAG, "configuration failed");
                    }
                };

                try {
                    mCameraDevice.createCaptureSession(Arrays.asList(preview, mImageReader.getSurface()), previewCallback, camera2handler);
                } catch (CameraAccessException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    class Wrap implements Runnable {
        @Override
        public void run() {
            try {
                main.sendTelemetry((byte) 4, isRecording ? true : false);
            } catch (Exception e) {
                Log.d(TAG, "error: " + e);
                main.logGPX.saveComment("error: " + e.toString());
            }
        }
    }
}