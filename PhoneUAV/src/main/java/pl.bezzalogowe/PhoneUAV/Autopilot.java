package pl.bezzalogowe.PhoneUAV;

import android.location.LocationManager;

import com.stormbots.MiniPID;

/** https://github.com/tekdemo/MiniPID-Java */

public class Autopilot {
    public MiniPID pid, pidRoll;
    public boolean hold_roll = false;
    public boolean hold_pitch = false;
    MainActivity main;
    boolean auto_heading = false;
    int period = 100;
    double target_roll = 0;
    double target_pitch = 0;
    double target_altitude;
    boolean fakeAltitude = false;
    Thread altitudeHoldThread;
    double proportional = 0;
    double integral = 0;
    double derivative = 0;

    public void startAutopilot(MainActivity argActivity) {
        main = argActivity;
        hold_pitch = true;
        hold_roll = true;
        altitudeHoldThread = new Thread(new AutopilotThread());
        altitudeHoldThread.start();
    }

    public void stopAutopilot(MainActivity argActivity) {
        main = argActivity;
        hold_pitch = false;
        hold_roll = false;
        try {
            altitudeHoldThread.interrupt();
            altitudeHoldThread = null;
            main.update.updateConversationHandler.post(new updateTextThread(main.text_server, "Autopilot stopped"));
        } catch (Exception e) {
            e.printStackTrace();
            main.update.updateConversationHandler.post(new updateTextThread(main.text_server, "Autopilot NOT stopped"));
        }
    }

    public void startFollowingWaypoints(MainActivity argActivity) {
        main = argActivity;
        if (main.locObject.waypointNext != null) {
            main.autopilot.auto_heading = true;
        } else {
            main.locObject.waypointNext = new android.location.Location(LocationManager.GPS_PROVIDER);
            int size = main.locObject.nextWaypoint();
            if (size == 0) {
                main.locObject.waypointNext = null;
                /*
                main.locObject.waypointNext.setLatitude(0);
                main.locObject.waypointNext.setLongitude(0);
                main.locObject.waypointNext.setAltitude(300);
                */
            }
        }
    }

    public void stopFollowingWaypoints(MainActivity argActivity) {
        main = argActivity;
        main.autopilot.auto_heading = false;
    }

    class AutopilotThread implements Runnable {
        @SuppressWarnings("all")
        public void run() {

            /** sets target altitude to the barometric altitude at the moment of enabling autopilot */
            main.autopilot.target_altitude = main.pressureObject.altitudeBarometric;

            pid = new MiniPID(proportional, integral, derivative);
            pidRoll = new MiniPID(proportional, integral, derivative);

            while (altitudeHoldThread != null) {

                short aileronrvalue = 0;
                short elevatorvalue = 0;
                short ruddervalue = 0;

                /** PID target pitch hold */
                if (hold_pitch) {

                    if (main.pressureObject.altitudeBarometricRecent < target_altitude - 0.2) {
                        /* aircraft too low */
                        if (main.pressureObject.altitudeBarometricRecent < target_altitude - 50) {
                            target_pitch = 30;
                            main.update.updateConversationHandler.post(new updateTextThread(main.text_feedback, ">50 m too low"));
                        } else {
                            target_pitch = (target_altitude - (double) main.pressureObject.altitudeBarometricRecent) * 3 / 5;
                            main.update.updateConversationHandler.post(new updateTextThread(main.text_feedback, String.format("%.2f", target_altitude - main.pressureObject.altitudeBarometricRecent) + " m too low"));
                        }

                    } else if (main.pressureObject.altitudeBarometricRecent > target_altitude + 0.2) {
                        /* aircraft too high */
                        if (main.pressureObject.altitudeBarometricRecent > target_altitude + 50) {
                            target_pitch = -30;
                            main.update.updateConversationHandler.post(new updateTextThread(main.text_feedback, ">50 m too high"));
                        } else {
                            target_pitch = (target_altitude - (double) main.pressureObject.altitudeBarometricRecent) * 3 / 5;
                            main.update.updateConversationHandler.post(new updateTextThread(main.text_feedback, String.format("%.2f", main.pressureObject.altitudeBarometricRecent - target_altitude) + " m too high"));
                        }
                    } else {
                        /* aircraft inside 0.4 meter altitude range */
                        target_pitch = 0;
                        main.update.updateConversationHandler.post(new updateTextThread(main.text_feedback, "inside 0,4 m range"));
                    }

                    String pString = String.format("%.2f", proportional);
                    String iString = String.format("%.2f", integral);
                    String dString = String.format("%.2f", derivative);

                    main.update.updateConversationHandler.post(new updateTextThread(main.text_server, "P= " + pString + "\tI= " + iString + "\tD= " + dString));

                    /** PID elevator adjustment */
                    /* https://github.com/ArduPilot/ardupilot/blob/master/libraries/APM_Control/AP_PitchController.cpp */

                    elevatorvalue = (short) pid.getOutput((double) main.gravityObject.angle_pitch, target_pitch);

                    if (elevatorvalue > 1000) {
                        elevatorvalue = 1000;
                    }
                    if (elevatorvalue < -1000) {
                        elevatorvalue = -1000;
                    }

                    main.elevator = (short) elevatorvalue;

                    main.update.updateConversationHandler.post(new updateProgressThread(main.seekbarELEV, main.elevator / 20 + 50));
                    main.update.updateConversationHandler.post(new updateTextThread(main.dutyCycleELEV, Integer.toString(main.elevator)));
                    /** prints -32767 to 32767 value*/
                    //main.update.updateConversationHandler.post(new updateTextThread(main.dutyCycleELEV, Integer.toString(elevatorvalue)));
                }

                /** PID roll adjustment */
                /* https://github.com/ArduPilot/ardupilot/blob/master/libraries/APM_Control/AP_RollController.cpp */

                if (hold_roll) {
                    aileronrvalue = (short) pidRoll.getOutput((double) main.gravityObject.angle_roll, 0);

                    if (aileronrvalue > 1000) {
                        aileronrvalue = 1000;
                    }
                    if (aileronrvalue < -1000) {
                        aileronrvalue = -1000;
                    }

                    main.ailerons = (short) aileronrvalue;

                    main.update.updateConversationHandler.post(new updateProgressThread(main.seekbarAIL, main.ailerons / 20 + 50));
                    main.update.updateConversationHandler.post(new updateTextThread(main.dutyCycleAIL, Integer.toString(main.ailerons)));
                }

                PWM.mixElevons(main.ailerons, main.elevator);

                /** Proportional yaw adjustment */
                /* https://github.com/ArduPilot/ardupilot/blob/master/libraries/APM_Control/AP_YawController.cpp */

                if (auto_heading && main.rudder == 0 && main.locObject.waypointNext != null) {
                    /** turning with rudder, work in progress */
                /*
                    double difference = (((360 + (main.magObject.azimuth - main.magObject.heading)) % 360) - 180);

                    main.update.updateConversationHandler.post(new updateTextThread(main.angle_text_heading, (int) main.magObject.heading + "\u00B0\n" + (int) main.magObject.azimuth + "\u00B0\n" + (int) difference + "\u00B0"));

                    ruddervalue = (short) (difference * 182);

                    PWM.oneArray(main.outputsRudder, progress*20 - 1000, 100, main.ch340commObject.servoRudderMinPW, main.ch340commObject.servoRudderMaxPW);

                    main.rdr = (int) ((-ruddervalue / 32.767) + 1500);
                    main.update.updateConversationHandler.post(new updateProgressThread(main.seekbarRDR, main.inputObject.scaleDown(main.rdr)));
                    main.update.updateConversationHandler.post(new updateTextThread(main.dutyCycleRDR, Integer.toString(main.inputObject.scaleDown(main.rdr)) + "%"));
                */
                }

                try {
                    Thread.sleep(period);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
