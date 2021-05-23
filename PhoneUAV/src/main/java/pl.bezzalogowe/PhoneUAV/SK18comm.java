package pl.bezzalogowe.PhoneUAV;

import android.content.SharedPreferences;
import android.util.Log;
import android.widget.LinearLayout;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class SK18comm {
    MainActivity main;
    int baudRate;
    byte stopBit;
    byte dataBit;
    byte parity;
    byte flowControl;

    ScheduledExecutorService executor;
    ScheduledExecutorService executorUp;
    ScheduledExecutorService executorDown;

    public static byte[] calculatePacket(byte number, short position, byte speed) {
        byte[] packet = new byte[4];

        packet[0] = (byte) 255;
        packet[1] = number;
        byte posHigh = (byte) (position >> 2);
        byte posLow = (byte) (position << 6);
        packet[2] = posHigh;
        packet[3] = (byte) (posLow | speed);

        return packet;
    }

    public void getPreference(SharedPreferences sharePrefSettings) {
        sharePrefSettings.getString("configed", "TRUE");
        baudRate = sharePrefSettings.getInt("baudRate", 9600);
        stopBit = (byte) sharePrefSettings.getInt("stopBit", 1);
        dataBit = (byte) sharePrefSettings.getInt("dataBit", 8);
        parity = (byte) sharePrefSettings.getInt("parity", 0);
        flowControl = (byte) sharePrefSettings.getInt("flowControl", 0);
        Log.d("getPreference",
                "baudRate: " + baudRate +
                "\nstopBit: " + stopBit +
                "\ndataBit: " + dataBit +
                "\nparity: " + parity +
                "\nflowControl: " + flowControl);
    }

    public void startPWM(MainActivity argActivity, int value) {
        executor = Executors.newSingleThreadScheduledExecutor();
        main = argActivity;
        main.startPWMminButton.setVisibility(LinearLayout.INVISIBLE);
        main.startPWMmaxButton.setVisibility(LinearLayout.INVISIBLE);

        main.seekbarTHROT.setAlpha((float) 1);
        main.seekbarTHROT.setProgress((value - 500) / 20);

        executor.scheduleAtFixedRate(new Runnable() {
            public void run() {
                /* 1 through 1000 range */
                SetPositionSK18(main.outputsThrottle[0], main.throttle + 1000, (byte) 4);
                /* Servos and ESCs need pulses every 20 ms (50 Hz).
				 * With SK18 servocontroller setting one channel value sends a pulse for all channels. */
            }
        }, 20, 20, TimeUnit.MILLISECONDS);
        main.sendTelemetry(7, 1);
    }

    public void stopPWM(MainActivity argActivity) {
        main = argActivity;
        main.seekbarTHROT.setAlpha((float) 0.5);

        SetPositionSK18(main.outputsThrottle[0], 1, (byte) 4);
        executor.shutdownNow();
        try {
            if (executor.isShutdown()) {
                main.update.updateConversationHandler.post(new updateTextThread(main.text_server, "ESC stopped"));
            }
        } catch (Exception e) {
            main.update.updateConversationHandler.post(new updateTextThread(main.text_server, "ESC not stopped"));
        }
        executor = null;
        main.sendTelemetry(7, 0);
    }

    public void SetPositionSK18(int channel, int position, byte speed) {
        byte[] writeBuffer = new byte[4];
        try {
            if (channel < 0)
            {writeBuffer = SK18comm.calculatePacket((byte) Math.abs(channel), (short) (1001 - position), (byte) 10);}
            else
            {writeBuffer = SK18comm.calculatePacket((byte) channel, (short) (position), (byte) 10);}
            if (main.uartInterface.SendData(4, writeBuffer) != 0) {
                Log.d("SK18comm", "SendData");
            }
        } catch (Exception e) {
            Log.d("SK18comm", "CalculatePacket");
            e.printStackTrace();
        }
    }

    public void startExampleThread(MainActivity argActivity) {
        executorUp = Executors.newSingleThreadScheduledExecutor();
        main = argActivity;
        Thread example = new Thread(new ExampleThread());
        example.start();
    }

    class ExampleThread implements Runnable {
        @Override
        public void run() {
            try {
                main.update.updateConversationHandler.post(new updateTextThread(main.text_server, "Thread started."));
                main.logGPX.saveComment("Thread started.");
                while (true /*condition*/) {
                    Thread.sleep(20);
                }
                //main.update.updateConversationHandler.post(new updateTextThread(main.text_server, "Thread finished."));
                //main.logObject.saveComment("Thread finished.");
            } catch (Exception e) {
                Log.d("SK18comm", "error up: " + e);
            }
        }
    }
}
