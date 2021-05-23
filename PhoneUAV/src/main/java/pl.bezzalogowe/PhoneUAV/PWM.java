package pl.bezzalogowe.PhoneUAV;

import android.widget.LinearLayout;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class PWM {
    static MainActivity main;
    ScheduledExecutorService executor, executorBlink;
    boolean pwmThread = false;
    boolean blink = false;
    public PWM(MainActivity argActivity) {
        main = argActivity;
    }

    public static void oneArray(int[] channels, int position, int time, int min, int max) {
        /* takes values between -1000 and 1000 */
        if (main.outputMode == main.USC16) {
            main.ch340commObject.SetPositionMAVLink(channels, position, time, min, max);
        } else if (main.outputMode == main.FT311D_PWM) {
            /* SetDutyCycle takes values between 0 and 100 */
            for (int i = 0; i < channels.length; i++) {
                main.pwmInterface.SetDutyCycle((byte) channels[i], (byte) ((position + 1000) / 20));
            }
        } else if (main.outputMode == main.FT311D_UART) {
            /* SetPositionSK18 takes values between 1 and 1000 */
            for (int i = 0; i < channels.length; i++) {
                main.sk18commObject.SetPositionSK18((byte) channels[i], (position + 1000) / 2, (byte) 20);
            }
        }
    }

    public static void twoArrays(int[] channels1, int position1, int[] channels2, int position2, int time, int min, int max) {
        if (main.outputMode == main.USC16) {
            main.ch340commObject.SetPositionsMAVLink(channels1, position1, channels2, position2, time, min, max);
        } else if (main.outputMode == main.FT311D_PWM) {
            for (int i = 0; i < channels1.length; i++) {
                main.pwmInterface.SetDutyCycle((byte) channels1[i], (byte) ((position1 + 1000) / 20));
            }
            for (int i = 0; i < channels2.length; i++) {
                main.pwmInterface.SetDutyCycle((byte) channels2[i], (byte) ((position2 + 1000) / 20));
            }
        } else if (main.outputMode == main.FT311D_UART) {
            for (int i = 0; i < channels1.length; i++) {
                main.sk18commObject.SetPositionSK18((byte) channels1[i], (position1 + 1000) / 2, (byte) 20);
            }
            for (int i = 0; i < channels2.length; i++) {
                main.sk18commObject.SetPositionSK18((byte) channels2[i], (position2 + 1000) / 2, (byte) 20);
            }
        }
    }

    public static void flyingWing(int ailerons, int elevator, int throttle) {
        /* two servos plus throttle */
        int elevonLeft, elevonRight;
        elevonLeft = -elevator - ailerons;
        elevonRight = elevator - ailerons;

        if (elevonLeft > 1000) {
            elevonLeft = 1000;
        }
        if (elevonLeft < -1000) {
            elevonLeft = -1000;
        }

        if (elevonRight > 1000) {
            elevonRight = 1000;
        }
        if (elevonRight < -1000) {
            elevonRight = -1000;
        }

        if (main.outputMode == main.USC16) {
            main.ch340commObject.SetPositions3(main.outputsElevonLeft, elevonLeft, main.outputsElevonRight, elevonRight, main.outputsThrottle, throttle, 100, main.ch340commObject.servoElevonMinPW, main.ch340commObject.servoElevonMinPW, main.ch340commObject.throttleMinPW, main.ch340commObject.throttleMaxPW);
        }
    }

    public static void mixElevons(short ailerons, short elevator) {
        int elevonLeft, elevonRight;
        elevonLeft = -elevator - ailerons;
        elevonRight = elevator - ailerons;

        if (elevonLeft > 1000) {
            elevonLeft = 1000;
        }
        if (elevonLeft < -1000) {
            elevonLeft = -1000;
        }

        if (elevonRight > 1000) {
            elevonRight = 1000;
        }
        if (elevonRight < -1000) {
            elevonRight = -1000;
        }

        PWM.twoArrays(main.outputsElevonLeft, elevonLeft, main.outputsElevonRight, elevonRight, 100, main.ch340commObject.servoElevonMinPW, main.ch340commObject.servoElevonMaxPW);
    }

    public static void mixFlaperons(short ailerons, short flaps) {
        int flaperonLeft, flaperonRight;
        flaperonLeft = -ailerons - flaps;
        flaperonRight = -ailerons + flaps;

        if (flaperonLeft > 1000) {
            flaperonLeft = 1000;
        }
        if (flaperonLeft < -1000) {
            flaperonLeft = -1000;
        }

        if (flaperonRight > 1000) {
            flaperonRight = 1000;
        }
        if (flaperonRight < -1000) {
            flaperonRight = -1000;
        }

        PWM.twoArrays(main.outputsFlaperonLeft, flaperonLeft, main.outputsFlaperonRight, flaperonRight, 100, main.ch340commObject.servoElevonMinPW, main.ch340commObject.servoElevonMaxPW);
    }

    public void startPWM(int value) {
        if (!pwmThread) {
            executor = Executors.newSingleThreadScheduledExecutor();

            main.startPWMminButton.setVisibility(LinearLayout.GONE);
            main.startPWMmaxButton.setVisibility(LinearLayout.GONE);

            main.throttleMinButton.setVisibility(LinearLayout.VISIBLE);
            main.throttleMaxButton.setVisibility(LinearLayout.VISIBLE);

            main.throttleStopButton.setAlpha((float) 1);

            executor.scheduleAtFixedRate(new Runnable() {
                public void run() {
                    /* 1000 μs through 2000 μs range */
                    //PWM.oneArray(main.outputsAileron, main.ailerons, 100, 750, 2250);
                    //PWM.oneArray(main.outputsElevator, main.elevator, 100, main.ch340commObject.servoElevatorMinPW, main.ch340commObject.servoElevatorMaxPW);
                    PWM.oneArray(main.outputsThrottle, main.throttle, 100, main.ch340commObject.throttleMinPW, main.ch340commObject.throttleMaxPW);
                    //PWM.mixElevons(main.ailerons, main.elevator);
                    //PWM.mixFlaperons(main.ailerons, main.flaps);
                    //PWM.flyingWing(main.ailerons, main.elevator, main.throttle);
                }
            }, 20, 20, TimeUnit.MILLISECONDS);

            main.update.updateConversationHandler.post(new updateTextThread(main.text_server, "PWM thread started"));
            main.sendTelemetry(7, 1);
        }
        pwmThread = true;
    }

    public void stopPWM(MainActivity argActivity) {
        if (pwmThread) {
            main = argActivity;

            main.throttleMinButton.setVisibility(LinearLayout.GONE);
            main.throttleMaxButton.setVisibility(LinearLayout.GONE);

            main.startPWMminButton.setVisibility(LinearLayout.VISIBLE);
            main.startPWMmaxButton.setVisibility(LinearLayout.VISIBLE);

            main.throttleStopButton.setAlpha((float) 0.5);

            main.ch340commObject.SetPositionMAVLink(main.outputsThrottle, main.throttle, (byte) 4, main.ch340commObject.throttleMinPW, main.ch340commObject.throttleMaxPW);

            try {
                executor.shutdownNow();
                if (executor.isShutdown()) {
                    main.update.updateConversationHandler.post(new updateTextThread(main.text_server, "PWM thread stopped"));
                }
            } catch (Exception e) {
                main.update.updateConversationHandler.post(new updateTextThread(main.text_server, "PWM thread not stopped"));
            }
            executor = null;
            main.sendTelemetry(7, 0);
        }
        pwmThread = false;
    }

    public void blink() {
        if (blink) {
            main.update.updateConversationHandler.post(new updateAlphaThread(main.seekbarTHROT, (float) 0.5));
            blink = false;
        } else {
            main.update.updateConversationHandler.post(new updateAlphaThread(main.seekbarTHROT, (float) 1));
            blink = true;
        }
    }

    public void startBlinking() {
        executorBlink = Executors.newSingleThreadScheduledExecutor();
        executorBlink.scheduleAtFixedRate(new Runnable() {
            public void run() {
                blink();
            }
        }, 500, 500, TimeUnit.MILLISECONDS);
    }

    public void stopBlinking() {
        try {
            executorBlink.shutdownNow();
            if (executorBlink.isShutdown()) {
            }
        } catch (Exception e) {
        }
        executorBlink = null;
    }
}
