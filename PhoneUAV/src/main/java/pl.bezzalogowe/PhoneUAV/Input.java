package pl.bezzalogowe.PhoneUAV;

import android.os.Build;
import android.os.Message;
import android.util.Log;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Input {
    private static final String TAG = "controller";
    MainActivity main;
    int GIMBAL_Y_MIN = -700;
    int GIMBAL_Y_MAX = 700;

    int controllerGimbalXvalue, controllerGimbalYvalue;
    int gimbalXpulse, gimbalYpulse, gimbalStep;

    public int scaleDown(int value) {
        return value / 20 + 50;
    }

    public void startController(MainActivity argActivity) {
        main = argActivity;
        controllerGimbalXvalue = 0;
        controllerGimbalYvalue = 0;

        gimbalXpulse = 0;
        gimbalYpulse = 0;
        gimbalStep = 5;

        startGimbal(main);
    }

    private void setProportional(double value) {
        main.autopilot.proportional = main.autopilot.proportional + value;
        try {
            if (main.autopilot.pid != null) {
                main.autopilot.pid.setP(main.autopilot.proportional);
            } else {
                main.update.updateConversationHandler.post(new updateTextThread(main.text_server, "pid == null"));
            }
        } catch (Exception e) {
            e.printStackTrace();
            main.update.updateConversationHandler.post(new updateTextThread(main.text_server, "Error pid: " + e.toString()));
        }
        Log.d("autopilot", "P: " + main.autopilot.proportional);
        double pArray[] = {main.autopilot.proportional};
        main.sendTelemetry(13, pArray);
    }

    private void setIntegral(double value) {
        main.autopilot.integral = main.autopilot.integral + value;
        try {
            if (main.autopilot.pid != null) {
                main.autopilot.pid.setI(main.autopilot.integral);
            } else {
                main.update.updateConversationHandler.post(new updateTextThread(main.text_server, "pid == null"));
            }
        } catch (Exception e) {
            e.printStackTrace();
            main.update.updateConversationHandler.post(new updateTextThread(main.text_server, "Error pid: " + e.toString()));
        }
        Log.d("autopilot", "I: " + main.autopilot.integral);
        double iArray[] = {main.autopilot.proportional};
        main.sendTelemetry(14, iArray);
    }

    private void setDerivative(double value) {
        main.autopilot.derivative = main.autopilot.derivative + value;
        try {
            if (main.autopilot.pid != null) {
                main.autopilot.pid.setD(main.autopilot.derivative);
            } else {
                main.update.updateConversationHandler.post(new updateTextThread(main.text_server, "pid == null"));
            }
        } catch (Exception e) {
            e.printStackTrace();
            main.update.updateConversationHandler.post(new updateTextThread(main.text_server, "Error pid: " + e.toString()));
        }
        Log.d("autopilot", "D: " + main.autopilot.derivative);
        double dArray[] = {main.autopilot.proportional};
        main.sendTelemetry(15, dArray);
    }

    public void startGimbal(final MainActivity argActivity) {
        ScheduledExecutorService executor;
        executor = Executors.newSingleThreadScheduledExecutor();
        main = argActivity;
        final int outputsGimbalX[] = {11};
        final int outputsGimbalY[] = {12};
        executor.scheduleAtFixedRate(new Runnable() {
            public void run() {
                boolean gimbalXchanged = false;
                boolean gimbalYchanged = false;

                // DS4: touchpad X
                // numpad 4 and 6
                if (controllerGimbalXvalue > 0) {
                    if (gimbalXpulse >= -1000 + gimbalStep) {
                        gimbalXpulse -= gimbalStep;
                        gimbalXchanged = true;
                    }
                } else if (controllerGimbalXvalue < 0) {
                    if (gimbalXpulse <= 1000 - gimbalStep) {
                        gimbalXpulse += gimbalStep;
                        gimbalXchanged = true;
                    }
                }

                // DS4: touchpad Y
                // numpad 8 and 2
                if (controllerGimbalYvalue < 0) {
                    if (gimbalYpulse >= GIMBAL_Y_MIN + gimbalStep) {
                        gimbalYpulse -= gimbalStep;
                        gimbalYchanged = true;
                    }
                } else if (controllerGimbalYvalue > 0) {
                    if (gimbalYpulse <= GIMBAL_Y_MAX - gimbalStep) {
                        gimbalYpulse += gimbalStep;
                        gimbalYchanged = true;
                    }
                }

                if (gimbalXchanged || gimbalYchanged) {
                    PWM.twoArrays(outputsGimbalX, gimbalXpulse, outputsGimbalY, gimbalYpulse, 100, 500, 2500);
                    main.update.updateConversationHandler.post(new updateTextThread(main.text_feedback,
                            "values: " + gimbalXpulse + ", " + gimbalYpulse + " changed: " + Boolean.valueOf(gimbalXchanged) + ", " + Boolean.valueOf(gimbalYchanged)));
                }
            }
        }, 20, 20, TimeUnit.MILLISECONDS);
    }

    private void processAileron(short value) {
        /** roll, x-input: L2, DS4: right knob X */

        /*
        minimum value is -32767 (-2^15 +1)
        maximum value is 32767 (2^15 -1)
        */

        main.ailerons = (short) (-value / 32.767);
        PWM.oneArray(main.outputsAileron, main.ailerons, 100, 750, 2250);
/*
        if (main.outputMode == main.USC16) {
            main.ch340commObject.SetPositionMAVLink(main.outputsAileron, main.ailerons, 100, 750, 2250);
        } else if (main.outputMode == main.FT311D_PWM) {
            main.pwmInterface.SetDutyCycle((byte) main.outputsAileron[0], (byte) ((main.ailerons + 1000) / 20));
        } else if (main.outputMode == main.FT311D_UART) {
            main.sk18commObject.SetPositionSK18((byte) main.outputsAileron[0], (main.ailerons + 1000) / 2, (byte) 20);
        }
*/
        /* flying wing */
        PWM.mixElevons(main.ailerons, main.elevator);

        main.seekbarAIL.setProgress(scaleDown(main.ailerons));
        main.update.updateConversationHandler.post(new updateTextThread(main.text_server, "AIL (CH" + main.outputsAileron[0] + "): " + Short.toString(value) + ", " + main.ailerons + 1500 + " μs"));
        main.update.updateConversationHandler.post(new updateTextThread(main.dutyCycleAIL, Short.toString(value)));
    }

    private void processFlaps(short value) {
        //TODO: adjust scale
    }

    private void processElevator(short value) {
        /** pitch, x-input: R2, DS4: right knob Y */
        main.elevator = (short) (-value / 32.767);

        if (main.autopilot.hold_pitch == false) {
            PWM.oneArray(main.outputsElevator, main.elevator, 100, main.ch340commObject.servoElevatorMinPW, main.ch340commObject.servoElevatorMaxPW);

            /* flying wing */
            PWM.mixElevons(main.ailerons, main.elevator);

            main.seekbarELEV.setProgress(scaleDown(main.elevator));
            main.update.updateConversationHandler.post(new updateTextThread(main.text_server, "ELE (CH" + main.outputsElevator[0] + ") " + Long.toString(value, 10)));
            main.update.updateConversationHandler.post(new updateTextThread(main.dutyCycleELEV, Short.toString(value)));
        }

    }

    private void processRudder(short value) {
        /** yaw, x-input: X1 */
        main.rudder = (short) (value / 32.767);
        PWM.oneArray(main.outputsRudder, (int) (main.rudder - main.rudderTrim), 100, main.ch340commObject.servoRudderMinPW, main.ch340commObject.servoRudderMaxPW);
        main.seekbarRDR.setProgress(scaleDown(main.rudder));
        main.update.updateConversationHandler.post(new updateTextThread(main.text_server, "RDR (CH" + main.outputsRudder[0] + ") " + Integer.toString(value, 10)));
        main.update.updateConversationHandler.post(new updateTextThread(main.dutyCycleRDR, Short.toString(value)));
    }

    private void processThrottle(short value) {
        main.throttle = (short) (value / 32.767);
        /* in: from -32767 to 32767, out: -1000 to 1000 */
        PWM.oneArray(main.outputsThrottle, main.throttle, (byte) 100, main.ch340commObject.throttleMinPW, main.ch340commObject.throttleMaxPW);
    }

    void processButton(byte data) {
        byte number = (byte) (data / 2);
        if (data % 2 == 1) {
            /* button pressed, value = true */
            switch (number) {
                case 0: {
                    // DS4 "cross" - autopilot feature 1 disabled
                    main.update.updateConversationHandler.post(new updateCheckBoxThread(main.checkboxAutopilotFeature1, false));
                    main.autopilot.stopAutopilot(main);
                }
                break;
                case 1: {
                    // DS4 "circle" - autopilot feature 2 disabled
                    main.autopilot.stopFollowingWaypoints(main);
                    main.update.updateConversationHandler.post(new updateCheckBoxThread(main.checkboxAutopilotFeature2, false));
                }
                break;
                case 2: {
                    // DS4 "triangle" - autopilot feature 2 enabled
                    main.autopilot.startFollowingWaypoints(main);
                    main.update.updateConversationHandler.post(new updateCheckBoxThread(main.checkboxAutopilotFeature2, true));
                }
                break;
                case 3: {
                    // DS4 "square" - autopilot feature 1  enabled
                    main.update.updateConversationHandler.post(new updateCheckBoxThread(main.checkboxAutopilotFeature1, true));
                    main.autopilot.startAutopilot(main);
                }
                break;
                case 4:
                    // DS4 L1 button
                    main.camObjectLolipop.toggleRecording();
                    break;
                case 5:
                    // DS4 R1 button
                    /* Camera API 1 */
                    if (Build.VERSION.SDK_INT <= 20 /*Build.VERSION_CODES.LOLLIPOP*/) {
                        /* API≤20 */
                    //FIXME: double check
                    /*
                    if (!main.camAPIobjectKitkat.isRecording)
                    {main.camAPIobjectKitkat.captureImage();}
                    */
                    } else {/* API>20 */
                        main.camObjectLolipop.captureImage();
                        main.update.updateConversationHandler.post(new updateTextThread(main.text_server, "Shutter pressed"));
                    }
                    main.sendTelemetry(5, true);
                    break;
                case 6:
                    // not actually DS4 L2 "button"
                    break;
                case 7:
                    // not actually DS4 R2 "button"
                    try {
                        /*
                        if (main.outputMode == main.USC16) {
                            main.ch340commObject.startPWM(main, main.ch340commObject.throttleMin);
                        }
                        */
                        if (main.outputMode == main.FT311D_UART) {
                            main.sk18commObject.startPWM(main, main.ch340commObject.throttleMinPW);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        main.update.updateConversationHandler.post(new updateTextThread(main.text_server, "Button 7 error: " + e.toString()));
                    }
                    break;
                case 8:
                    // DS4 Share button
                    if (Build.VERSION.SDK_INT <= 20 /* Build.VERSION_CODES.KITKAT_WATCH */) {
                        main.camObjectKitkat.turnOnTorch();
                    } else {
                        try {
                            final Message msg = new Message();
                            new Thread() {
                                public void run() {
                                    msg.arg1 = 1;
                                    main.camObjectLolipop.handlerTorch.sendMessage(msg);
                                }
                            }.start();
                        } catch (Exception e) {
                            e.printStackTrace();
                            System.out.println("handlerTorch error: " + e.toString() + "\n");
                        }
                    }
                    main.sendTelemetry(8, true);
                    break;
                case 9:
                    // DS4 Options button
                    main.sendTelemetry(9, true);
                    main.update.updateConversationHandler.post(new updateTextThread(main.text_server, "Arrestor hook retracted"));
                    break;
                case 10:
                    // DS4 Play Station button press
                    break;
                case 11:
                    // DS4 left knob press
                    break;
                case 12:
                    // DS4 right knob press
                    //TODO:
                    //main.sendTelemetry((byte) 12, true);
                    //main.ch340commObject.open();
                    break;
                /** DualShock 4 has 13 buttons (0 through 12) */
                case 13:
                    setProportional(1);
                    break;
                case 14:
                    setIntegral(1);
                    break;
                case 15:
                    setDerivative(1);
                    break;
                default:
                    main.update.updateConversationHandler.post(new updateTextThread(main.text_server, "button " + number + " pressed"));
                    break;
            }
        } else {
            /* button released, value = false */
            switch (number) {
                case 4:
                    main.camObjectLolipop.toggleRecording();
                    break;
                case 5:
                    main.update.updateConversationHandler.post(new updateTextThread(main.text_server, "Shutter released"));
                    main.sendTelemetry(5, false);
                    break;
                case 6:
                    // not actually DS4 L2 "button"
                    break;
                case 7:
                    // not actually DS4 R2 "button"
                    try {
                        if (main.outputMode == main.USC16) {
                            main.pwmObject.stopPWM(main);
                        } else if (main.outputMode == main.FT311D_UART) {
                            main.sk18commObject.stopPWM(main);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        main.update.updateConversationHandler.post(new updateTextThread(main.text_server, "Button 7 error: " + e.toString()));
                    }
                    break;
                case 8:
                    // DS4 Share button
                    if (Build.VERSION.SDK_INT <= 20 /* Build.VERSION_CODES.KITKAT_WATCH */) {
                        main.camObjectKitkat.turnOffTorch();
                    } else {
                        try {
                            final Message msg = new Message();
                            new Thread() {
                                public void run() {
                                    msg.arg1 = 0;
                                    main.camObjectLolipop.handlerTorch.sendMessage(msg);
                                }
                            }.start();
                        } catch (Exception e) {
                            e.printStackTrace();
                            System.out.println("handlerTorch error: " + e.toString() + "\n");
                        }
                    }
                    main.sendTelemetry(8, false);
                    break;
                case 9:
                    // DS4 Options button
                    main.sendTelemetry(9, false);
                    main.update.updateConversationHandler.post(new updateTextThread(main.text_server, "Arrestor hook extended"));
                    break;
                case 10:
                    break;
                case 11:
                    break;
                case 12:
                    //TODO:
                    //main.sendTelemetry((byte) 12, false);
                    //main.ch340commObject.close();
                    break;
                /** DualShock 4 has 13 buttons (0 through 12) */
                case 13:
                    setProportional(-1);
                    break;
                case 14:
                    setIntegral(-1);
                    break;
                case 15:
                    setDerivative(-1);
                    break;
                default:
                    main.update.updateConversationHandler.post(new updateTextThread(main.text_server, "button " + number + " released"));
                    break;
            }
        }
    }

    void processStick(byte data[]) {
        short value;
        value = (short) ((data[1] & 0xFF) + ((data[2] & 0xFF) << 8));
        //value = (short) (data[1] + data[2] * 256);
        switch (data[0]) {
            case 40:
                processRudder(value);
                break;
            case 41:
                // x-input: Y1 (throttle, knob)
                processElevator(value);
                break;
            case 42:
                processAileron(value);
                //processFlaperons(value);
                break;
            case 43:
                // x-input: right knob X, DS4: L2
                processFlaps(value);
                break;
            case 44:
                // x-input: right knob Y, DS4: R2
                // recalculates full analog stick value range to throttleMin - throttleMax range
                processThrottle(value);
                break;
            case 45:
                // does nothing
                break;
            case 46:
                // x-input: D-Pad X
                if (value == 0) {
                    // do nothing
                } else {
                    /** set increase or decrease trim */
                    if (value < 0 && main.rudderTrim >= -250) {
                        main.rudderTrim -= 5;
                        main.sendTelemetry(16, (short) main.rudderTrim);
                    }
                    if (value > 0 && main.rudderTrim <= 250) {
                        main.rudderTrim += 5;
                        main.sendTelemetry(16, (short) main.rudderTrim);
                    }

                    /** move rudder after change of trim */
                    if (main.outputMode == main.FT311D_UART) {
                        main.sk18commObject.SetPositionSK18((byte) main.outputsRudder[0], (main.rudder + 1000 - main.rudderTrim) / 2, (byte) 20);
                        main.update.updateConversationHandler.post(new updateTextThread(main.text_server, "rudder-trim: " + main.rudderTrim / 2 + " μs"));
                    } else if (main.outputMode == main.USC16) {
                        main.ch340commObject.SetPositionMAVLink(main.outputsRudder, main.rudder - main.rudderTrim, 100, main.ch340commObject.servoRudderMinPW, main.ch340commObject.servoRudderMaxPW);
                        main.update.updateConversationHandler.post(new updateTextThread(main.text_server, "rudder-trim: " + main.rudderTrim + " μs"));
                    } else {
                        main.update.updateConversationHandler.post(new updateTextThread(main.text_server, "rudder-trim: " + main.rudderTrim + " μs"));
                    }

                    main.update.updateConversationHandler.post(new updateTextThread(main.dutyCycleRDR, Short.toString(value)));
                    main.seekbarRDR.setProgress(scaleDown(main.rudder));
                }
                break;
            case 47:
                // x-input: D-Pad Y
                if (value == 0) {
                    // do nothing
                } else {
                    if (value > 0 && main.elevatorTrim <= 245) {
                        main.elevatorTrim += 100;
                        main.sendTelemetry(17, (short) main.elevatorTrim);
                    }
                    if (value < 0 && main.elevatorTrim >= -245) {
                        main.elevatorTrim -= 100;
                        main.sendTelemetry(17, (short) main.elevatorTrim);
                    }
                }
                break;
            /** DualShock 4 has 8 axes (0 through 7) */
            case 48:
                // numpad 4 and 6
                controllerGimbalXvalue = value;
                break;
            case 49:
                // numpad 8 and 2
                controllerGimbalYvalue = value;
                break;
            default:
                break;
        }
    }

    void process(byte data[]) {
        if (data.length == 3 && 0x28 <= data[0] && data[0] <= 0x32) {
            /* three bytes received */
            /* joystick axis */
            main.inputObject.processStick(data);
        } else if (data.length == 1 && data[0] <= 0x1f) {
            /* one byte received */
            /* decimal less or equal 31 - joystick button */
            main.inputObject.processButton(data[0]);
            Log.d("button", "process button: " + data[0]);
        } else if (data[0] == 0x21) {
            /* decimal 33 - SetPeriod */
            System.out.println(String.format("%05X", data[0] & 0x0FFFFF) + ", " + data[1] + " SetPeriod");
            main.period = (int) (data[1] + 128);
            try {
                main.pwmInterface.SetPeriod(main.period);
            } catch (Exception e) {
                e.printStackTrace();
                Log.d(TAG, "Error: " + e);
            }
            main.savePeriodPreference();
        } else if (data[0] == 0x23) {
            /* decimal 35 - Reset */
            System.out.println(String.format("%05X", data[0] & 0x0FFFFF) + " Reset");
            try {
                main.resetFT311();
            } catch (Exception e) {
                e.printStackTrace();
                Log.d(TAG, "Reset FT311D error: " + e);
            }
        } else if (data[0] == 0x32) {
            /* decimal 50 */
            //TODO stop heartheat?
            System.out.println("Pinging remote address stopped");
        } else if (data[0] == 0x33) {
            /* decimal 51 */
            //byte[] ipAddr = new byte[]{data[1], data[2], data[3], data[4]};
            //TODO start heartbeat ipAddr?
            System.out.println("Pinging remote machine started, video device: " + data[5]);
        }
        /*
        // decimal 52 - add waypoint
        else if (data[0] == 0x34) {
            byte[] header = new byte[]{data[0], data[1], data[2], data[3]};
            byte[] lat_array = new byte[]{data[4], data[5], data[6], data[7], data[8], data[9], data[10], data[11]};
            byte[] lon_array = new byte[]{data[12], data[13], data[14], data[15], data[16], data[17], data[18], data[19]};
            byte[] ele_array = new byte[]{data[20], data[21], data[22], data[23], data[24], data[25], data[26], data[27]};
            Log.d(TAG, "Waypoint " +
                    Arrays.toString(lat_array) + "\n" +
                    Arrays.toString(lon_array) + "\n" +
                    Arrays.toString(ele_array) + " received");

            double lat = main.locObject.bytesArray2Double(lat_array);
            double lon = main.locObject.bytesArray2Double(lon_array);
            double ele = main.locObject.bytesArray2Double(ele_array);

            main.locObject.addWaypoint(lat, lon, ele);
        }
        */
        /*
        // decimal 53 - skip waypoint
        else if (data[0] == 0x35) {
            byte[] header = new byte[]{data[0], data[1], data[2], data[3]};
            main.update.updateConversationHandler.post(new updateTextThread(main.text_server, Arrays.toString(header)));
            main.locObject.nextWaypoint();
        }
        */
        /** add more types of datagrams here */
    }
}
