package pl.bezzalogowe.PhoneUAV;

import android.app.Activity;
import android.graphics.Color;
import android.util.Log;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.PictureCallback;
import android.hardware.Camera.ShutterCallback;
import android.hardware.Camera.Size;
import android.media.CamcorderProfile;
import android.media.ExifInterface;
import android.media.MediaRecorder;
import android.os.Environment;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

public class CameraAPI extends Activity implements TextureView.SurfaceTextureListener {
    private static final int MEDIA_TYPE_IMAGE = 1;
    private static final int MEDIA_TYPE_VIDEO = 2;
    /* https://newcircle.com/s/post/39/using__the_camera_api */
    MainActivity main;
    Camera camera1;
    Camera.Parameters param;
    TextureView mTextureView;
    MediaRecorder therecorder;
    boolean isRecording = false;    // is the camera currently recording
    boolean isBusy = false; // is the camera taking a picture while recording
    boolean torch = false;  // is the torch shining

    //String media = "/storage/extSdCard/DCIM/Camera/";
    //String media = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).getPath() + "/Camera/";
    String media = Environment.getExternalStorageDirectory().getPath() + "/DCIM/Camera/";

    String filenamePhoto;
    String filenameVideo;
    ShutterCallback shutterCallback = new ShutterCallback() {
        public void onShutter() {
            Log.d("camera", "onShutter'd");
        }
    };
    // Handles data for raw picture
    PictureCallback rawCallback = new PictureCallback() {
        public void onPictureTaken(byte[] data, Camera camera) {
            Log.d("camera", "onPictureTaken - raw");
        }
    };
    // Handles data for jpeg picture
    PictureCallback jpegCallback = new PictureCallback() {
        public void onPictureTaken(byte[] data, Camera camera) {
            FileOutputStream outStream;
            try {
                /* http://stackoverflow.com/questions/30411679/android-studio-open-failed-erofs-read-only-file-system-when-creating-a-file/30411821#30411821 */

                filenamePhoto = getTimeStamp() + ".jpg";
                outStream = new FileOutputStream(new File(media, filenamePhoto));

                outStream.write(data);
                outStream.close();

                File outFile = new File(media + filenamePhoto);
                setGeoTag(outFile, main.locObject.recentLocation);

                main.logGPX.saveComment("Picture taken: " + filenamePhoto);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
            }
            refreshCamera();
        }
    };
    public CameraAPI(MainActivity arg) {
        main = arg;
    }

    static public boolean setGeoTag(File image, android.location.Location loc) {
        /* http://stackoverflow.com/questions/10531544/write-geotag-jpegs-exif-data-in-android */
        if (loc != null) {
            try {
                ExifInterface exif = new ExifInterface(image.getAbsolutePath());

                double latitude = Math.abs(loc.getLatitude());
                double longitude = Math.abs(loc.getLongitude());

                int num1Lat = (int) Math.floor(latitude);
                int num2Lat = (int) Math.floor((latitude - num1Lat) * 60);
                double num3Lat = (latitude - ((double) num1Lat + ((double) num2Lat / 60))) * 3600000;

                int num1Lon = (int) Math.floor(longitude);
                int num2Lon = (int) Math.floor((longitude - num1Lon) * 60);
                double num3Lon = (longitude - ((double) num1Lon + ((double) num2Lon / 60))) * 3600000;

                String lat = num1Lat + "/1," + num2Lat + "/1," + num3Lat + "/1000";
                String lon = num1Lon + "/1," + num2Lon + "/1," + num3Lon + "/1000";

                if (latitude > 0) {
                    exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, "N");
                } else {
                    exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, "S");
                }

                if (longitude > 0) {
                    exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, "E");
                } else {
                    exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, "W");
                }

                exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE, lat);
                exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE, lon);

                exif.saveAttributes();

            } catch (IOException e) {
                e.printStackTrace();
                return false;
            }
        } else {
            return false;
        }
        return true;
    }

    //TODO: retest on Kitkat device or older
    public void cameraInit() {
        mTextureView = (TextureView) this.findViewById(R.id.preview);

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
                captureImage(false);
            }
        });

        main.videoButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!isRecording) {
                    captureVideoStart();
                } else {
                    captureVideoStop();
                }
            }
        });
    }

    void setListener(MainActivity argActivity) {
        main = argActivity;
        mTextureView.setSurfaceTextureListener(this);
        //main.frame = (LinearLayout) main.findViewById(R.id.previewFrame);
        //main.frame.addView(mTextureView);
    }

    String getTimeStamp() {
        Long time = System.currentTimeMillis();
        String dateISO8601 = new java.text.SimpleDateFormat("yyyy-MM-dd").format(new java.util.Date(time));
        String timeISO8601 = new java.text.SimpleDateFormat("HHmmss").format(new java.util.Date(time));
        return dateISO8601 + "_" + timeISO8601;
    }

    void setRecorder() throws IOException {
        therecorder = new MediaRecorder();
        therecorder.setCamera(camera1);

        therecorder.setAudioSource(MediaRecorder.AudioSource.DEFAULT);
        therecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);

        CamcorderProfile profile = CamcorderProfile.get(CamcorderProfile.QUALITY_HIGH);
        profile.videoFrameRate = 30;
        therecorder.setProfile(profile);
/*
        therecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
		therecorder.setVideoEncoder(MediaRecorder.AudioEncoder.AAC);
		therecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
		therecorder.setVideoFrameRate(30);
		therecorder.setVideoSize(1920, 1080);
		therecorder.setVideoEncodingBitRate(1000000);
		therecorder.setOrientationHint(0);
*/
        filenameVideo = getTimeStamp() + ".mp4";
        therecorder.setOutputFile(media + filenameVideo);

        try {
            therecorder.prepare();
        } catch (IllegalStateException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    /* http://www.androidhive.info/2013/04/android-developing-flashlight-application/ */
    public void turnOnTorch() {
        // activates torch if it is off
        if (!main.camObjectKitkat.torch) {
            try {
                param = camera1.getParameters();
                param.setFlashMode(Parameters.FLASH_MODE_TORCH);
                camera1.setParameters(param);
            } catch (Exception e) {
                Log.d("camera", "couldn't turn on torch: " + e);
            }
        }
        torch = true;
    }

    public void turnOffTorch() {
        // deactivates torch if it is on
        if (main.camObjectKitkat.torch) {
            try {
                param = camera1.getParameters();
                param.setFlashMode(Parameters.FLASH_MODE_OFF);
                camera1.setParameters(param);
            } catch (Exception e) {
                Log.d("camera", "couldn't turn off torch: " + e);
            }
        }
        torch = false;
    }

    public void captureVideoStart() {
        /* http://developer.android.com/guide/topics/media/camera.html#capture-video */
        try {
            camera1.unlock();
        } catch (IllegalStateException e) {
            Log.d("camera", "camera unlock: " + e);
        }
        try {
            setRecorder();
        } catch (IOException e) {
            Log.d("camera", "set recorder: " + e);
        }
        try {
            therecorder.start();
        } catch (IllegalStateException e) {
            Log.d("camera", "start recorder: " + e);
        }

        main.logGPX.saveComment("Video started: " + filenameVideo);
        isRecording = true;
        main.videoButton.setText("\u25A0");
        //main.serverUDP.sendFeedback(4, 1);
        //main.serverTCP.sendFeedback(4, 1);
    }

    public void captureVideoStop() {
        try {
            therecorder.stop();
            therecorder.reset();
            therecorder.release();
        } catch (IllegalStateException e) {
            Log.d("camera", "stop recording: " + e);
        }

        camera1.lock();
        main.logGPX.saveComment("Video stopped: " + filenameVideo);
        isRecording = false;
        main.videoButton.setText("\u25CF");
        //main.serverUDP.sendFeedback(4, 0);
        //main.serverTCP.sendFeedback(4, 0);
    }

    public void captureImage(boolean isRemotely) {
        if (isRecording) {
            if (isRemotely) {
                /* camera is recording, shutter pressed remotely, picture NOT taken */
            } else {
                /* camera is recording, shutter pressed locally, no feedback */
                isBusy = true;
                main.videoButton.setVisibility(Button.GONE);

                captureVideoStop();
                refreshCamera();
                //Long timePaused = 0L;
                Long timePaused = System.currentTimeMillis();
                ThreadB b = new ThreadB();
                b.start();
                synchronized (b) {
                    try {
                        b.wait();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    refreshCamera();
                    captureVideoStart();
                    Long timeResumed = System.currentTimeMillis();
                    Long difference = timeResumed - timePaused;
                    main.logGPX.saveComment("Video resumed after " + difference.toString() + " ms.");
                }

                isBusy = false;
                main.videoButton.setVisibility(Button.VISIBLE);
            }
        } else {
            /* camera is not recording */
            try {
                camera1.takePicture(null /*shutterCallback*/, null /*rawCallback*/, jpegCallback);
                /* camera is not recording, shutter pressed remotely, picture taken feedback */
            } catch (Exception e) {
                Log.d("camera", "Couldn't take picture: " + e);
                main.logGPX.saveComment("Error" + e.toString());
            }
        }
    }

    public void refreshCamera() {
        // if (mHolder.getSurface() == null) { return; }
        try {
            camera1.stopPreview();
        } catch (Exception e) {
            Log.d("refreshCamera", "stopPreview: " + e);
        }

        try {
            camera1.startPreview();
        } catch (Exception e) {
            Log.d("refreshCamera", "startPreview: " + e);
        }
    }

    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        /* safeCameraOpen() */
        try {
            //therecorder.setCamera(null);
            if (camera1 != null) {
                camera1.release();
                camera1 = null;
            }
            camera1 = Camera.open();
        } catch (Exception e) {
            Log.e(getString(R.string.app_name), "failed to open Camera");
            e.printStackTrace();
        }

        param = camera1.getParameters();

        try {
            List<String> focuslist = param.getSupportedFocusModes();
            Log.d("camera", "SupportedFocusModes: " + focuslist.toString());

            List<int[]> fpslist = param.getSupportedPreviewFpsRange();

            for (int i = 0; i < fpslist.size(); i++) {
                Log.d("camera", "SupportedPreviewFpsRange: " + fpslist.get(i)[0] / 1000 + " - " + fpslist.get(i)[1] / 1000);
            }

            Size prevsize = param.getPreferredPreviewSizeForVideo();
            Log.d("camera", "PreferredPreviewSizeForVideo: " + prevsize.width + "x" + prevsize.height);

            Size picturesize = param.getPictureSize();
            Log.d("camera", "PictureSize: " + picturesize.width + "x" + picturesize.height);

            List<Size> vidsizes = param.getSupportedVideoSizes();

            for (Size size : vidsizes) {
                Log.d("sizes", "VideoSize: " + size.width + "x" + size.height);
            }

            float fovH = param.getHorizontalViewAngle();
            float fovV = param.getVerticalViewAngle();
            Log.d("camera", "Horizontal/VerticalViewAngle: " + fovH + "/" + fovV);

        } catch (Exception e1) {
            e1.printStackTrace();
        }

        param.setJpegThumbnailSize(160, 120);
        param.setPreviewSize(176, 144);
        //param.setPreviewSize(1920, 1080);
        //param.setRotation((int) main.accObject.device_orientation[2] + 90);
        param.setPreviewFpsRange(7, 30);

        camera1.setParameters(param);
        camera1.setDisplayOrientation((int) main.device_orientation[2] + 90);

        try {
            camera1.setPreviewTexture(surface);
            camera1.startPreview();
        } catch (IOException ioe) {
            Log.d("camera", ioe.toString());
        }
/*
        try {
            hasFlash = getApplicationContext().getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH);

            if (camera1.getParameters().getSupportedFlashModes() != null) {
                hasFlash = true;
            }
        } catch (Exception e) {
            Log.d("camera", "flash detection error: " + e);
        }
*/
    }

    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
        /* Ignored, Camera does all the work for us */
    }

    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        camera1.stopPreview();
        camera1.release();
        return true;
    }

    public void onSurfaceTextureUpdated(SurfaceTexture surface) {
        /* Invoked every time there's a new Camera preview frame */
    }

    // "wait and notify" method
    // http://www.programcreek.com/2009/02/notify-and-wait-example/
    class ThreadB extends Thread {
        @Override
        public void run() {
            synchronized (this) {
                try {
                    camera1.takePicture(null, null, jpegCallback);
                } catch (Exception e) {
                    Log.d("camera", "Couldn't: " + e);
                    main.logGPX.saveComment("Error" + e.toString());
                }
                notify();
            }
        }
    }
}