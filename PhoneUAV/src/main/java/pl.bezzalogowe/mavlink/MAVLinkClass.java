package pl.bezzalogowe.mavlink;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.util.Log;

import pl.bezzalogowe.PhoneUAV.MainActivity;
import pl.bezzalogowe.PhoneUAV.PWM;
import pl.bezzalogowe.PhoneUAV.UpdateFromOutsideThread;
import pl.bezzalogowe.PhoneUAV.UpdateSoundThread;

public class MAVLinkClass {
    private final static String TAG = MAVLinkClass.class.getName();

    /* Used to load the native library on application startup. */
    static {
        System.loadLibrary("mavlink_udp");
    }

    public boolean r2pressed = false;
    MainActivity main;

    public MAVLinkClass(final MainActivity argActivity) {
        main = argActivity;
    }

    public static native void classInit();

    public static void doRestart(Context c) {
        try {
            // check if the context is given
            if (c != null) {
                //fetch the packagemanager so we can get the default launch activity
                //(you can replace this intent with any other activity if you want)
                PackageManager pm = c.getPackageManager();
                //check if we got the PackageManager
                if (pm != null) {
                    //create the intent with the default start activity for your application
                    Intent mStartActivity = pm.getLaunchIntentForPackage(
                            c.getPackageName()
                    );
                    if (mStartActivity != null) {
                        mStartActivity.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                        //create a pending intent so the application is restarted after System.exit(0) was called.
                        //We use an AlarmManager to call this intent in 100ms
                        int mPendingIntentId = 223344;
                        PendingIntent mPendingIntent = PendingIntent
                                .getActivity(c, mPendingIntentId, mStartActivity,
                                        PendingIntent.FLAG_CANCEL_CURRENT);
                        AlarmManager mgr = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
                        mgr.set(AlarmManager.RTC, System.currentTimeMillis() + 100, mPendingIntent);
                        //kill the application
                        System.exit(0);
                    } else {
                        Log.e(TAG, "Was not able to restart application, mStartActivity null");
                    }
                } else {
                    Log.e(TAG, "Was not able to restart application, PM null");
                }
            } else {
                Log.e(TAG, "Was not able to restart application, Context null");
            }
        } catch (Exception ex) {
            Log.e(TAG, "Was not able to restart application");
        }
    }

    public native void setGroundStationIP(String host);

    public native int receiveInit();

    public native int receiveStop();

    public native int heartBeatInit();

    public native int heartBeatStop();

    /* Displays a string from C code */
    public native String stringFromJNI();

    public native int sendProtocol();

    public native void setHeadingDegrees(double hdg);

    public native void sendAttitude(float roll, float pitch/*, float heading*/);

    public native void setBattery(int voltage, int level);

    public native void sendGlobalPosition(double lat, double lon, double alt, double relativeAlt);

    /* Called from native code. This sets the content of the TextView from the UI thread. */
    private void setMessage(final String message, boolean blink) {
        main.update.updateConversationHandler.post(new UpdateFromOutsideThread(main.text_feedback, message, false));
    }

    private void setButtons(final String message, boolean blink) {
        main.update.updateConversationHandler.post(new UpdateFromOutsideThread(main.text_feedback, message, false));
    }

    private void setAddress(final String message, boolean blink) {
        main.update.updateConversationHandler.post(new UpdateFromOutsideThread(main.text_feedback, message, false));
    }

    private void setLog(final String message) {
        //Log.d(TAG, message);
        System.out.println(message);
    }

    private void restartApp() {
        doRestart(main);
    }

    private void setRCchannels(short x, short y, short z, short r) {
        /* pitch, roll, thrust, yaw */

        /** elevator, servo 2 (installed facing right)
         * 500 - highest position
         * 2400 - lowest position */
        if (!main.autopilot.hold_pitch) {
            main.elevator = x;
            main.seekbarELEV.setProgress(x / 20 + 50);
            PWM.oneArray(main.outputsElevator, main.elevator, 100, main.ch340commObject.servoElevatorMinPW, main.ch340commObject.servoElevatorMaxPW);
        }

        if (!main.autopilot.hold_roll) {
            main.ailerons = y;
            main.seekbarAIL.setProgress(y / 20 + 50);
            PWM.oneArray(main.outputsAileron, main.ailerons, 100, main.ch340commObject.servoElevonMinPW, main.ch340commObject.servoElevonMaxPW);
        }

        /** when virtual joystick or "full down is zero throttle" option is enabled:
         * 500 is 0% throttle: 1000 microseconds pulse width
         * 1000 is 100% throttle: 2000 microseconds pulse width */
        if (!r2pressed) {
            main.throttle = (short) (z * 4 - 3000);
            main.seekbarTHROT.setProgress(z / 5 - 100);
        }

        /** rudder, servo 4 (installed facing left)
         * 500 - all the way left
         * 2400 - all the way right */
        main.rudder = r;
        main.seekbarRDR.setProgress(r / 20 + 50);
        PWM.oneArray(main.outputsRudder, main.rudder, 100, main.ch340commObject.servoRudderMinPW, main.ch340commObject.servoRudderMaxPW);

        /** flying wing:
         * left elevon, servo 6 (installed facing left)
         * 500, lowest position
         * 2400, highest position
         *
         * right elevon, servo 5 (installed facing right)
         * 500, highest position
         * 2400, lowest position */
        PWM.mixElevons(main.ailerons, main.elevator);

        /** conventional arrangement
         * left flaperon servo 16 (installed facing left)
         * 500, lowest position
         * 2500, highest position
         *
         * right flaperon servo -15 (also installed facing left)
         * 500, lowest position
         * 2500, highest position */
        //PWM.mixFlaperons(main.ailerons, main.flaps);

        System.out.println("y (ailerons): " + y + "\tx (elevator): " + x + "\tz (throttle): " + z + "\tr (rudder):" + r);
    }

    private void processButton(short number, boolean status) {

        switch (number) {
            case 0: {
                System.out.println(String.format("button", "Button: Gamesir A, status: " + status));
                main.update.updateConversationHandler.post(new UpdateFromOutsideThread(main.text_feedback, "Button A " + (status ? "pressed" : "released"), false));
            }
            break;
            case 1: {
                System.out.println(String.format("button", "Button: GameSir B, status: " + status));
                main.update.updateConversationHandler.post(new UpdateFromOutsideThread(main.text_feedback, "Button B " + (status ? "pressed" : "released"), false));
            }
            break;
            case 3: {
                System.out.println(String.format("button", "Button: GameSir L1, status: " + status));
                main.update.updateConversationHandler.post(new UpdateFromOutsideThread(main.text_feedback, "Button L1 " + (status ? "pressed" : "released"), false));

                if (status == true) {
                    main.camObjectLolipop.toggleRecording();
                }
            }
            break;
            case 4: {
                System.out.println(String.format("button", "Button: GameSir analog L2, status: " + status));
                main.update.updateConversationHandler.post(new UpdateFromOutsideThread(main.text_feedback, "Button L2 " + (status ? "pressed" : "released"), false));

                if (status == true) {
                    main.flaps = 500;
                } else {
                    main.flaps = 0;
                }
            }
            break;
            case 5: {
                System.out.println(String.format("button", "Button: GameSir R1, status: " + status));
                main.update.updateConversationHandler.post(new UpdateFromOutsideThread(main.text_feedback, "Button R1 " + (status ? "pressed" : "released"), false));

                //main.camObjectLolipop.captureImage();
            }
            break;
            case 6: {
                System.out.println(String.format("button", "Button: GameSir analog R2, status: " + status));
                main.update.updateConversationHandler.post(new UpdateFromOutsideThread(main.text_feedback, "Button R2 " + (status ? "pressed" : "released"), false));

                if (status) {
                    r2pressed = true;
                    main.throttle = 1000;
                } else {
                    r2pressed = false;
                    main.throttle = -1000;
                }
            }
            break;
            default:
                System.out.println(String.format("button", "Button: " + number + ", status: " + status));
                main.update.updateConversationHandler.post(new UpdateFromOutsideThread(main.text_feedback, "Button " + number + " " + (status ? "pressed" : "released"), false));
                break;
        }
    }

    private void addMAVLinkWaypoint(double lat, double lon, double ele, int type, int frame, int index) {
        if (type == 0)
        {
            /** MAV_MISSION_TYPE_MISSION */
            if (index == 0) {
                /* if the index is 0 it means a fresh route is being sent and the old one must be flushed first */
                main.locObject.flushWaypoints();
            }
            main.locObject.addWaypoint(lat, lon, ele, frame);
        }
        else if (type == 1)
        {
            //TODO: MAV_MISSION_TYPE_FENCE
        }
        else if (type == 2)
        {
            //TODO: MAV_MISSION_TYPE_RALLY
        }
    }

    private void takePhoto(boolean value) {
        /* called from C code */
        main.camObjectLolipop.captureImage();
    }

    private void setSound(int soundID) {
        main.update.updateConversationHandler.post(new UpdateSoundThread(main, soundID));
    }
}
