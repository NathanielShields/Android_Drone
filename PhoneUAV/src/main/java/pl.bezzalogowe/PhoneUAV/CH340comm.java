package pl.bezzalogowe.PhoneUAV;

import android.hardware.usb.UsbManager;
import android.os.Handler;
import android.os.Message;

import cn.wch.ch34xuartdriver.CH34xUARTDriver;

/* http://www.wch.cn/download/CH341SER_ANDROID_ZIP.html */

/*
UsbDevice[mName=/dev/bus/usb/001/002,mVendorId=6790,mProductId=29987,mClass=255,mSubclass=0,mProtocol=0,mManufacturerName=null,mProductName=TOROBOT Virtual COM Portl,mVersion=1.16,mSerialNumber=null,mConfigurations=[
UsbConfiguration[mId=1,mName=null,mAttributes=128,mMaxPower=48,mInterfaces=[
UsbInterface[mId=0,mAlternateSetting=0,mName=null,mClass=255,mSubclass=1,mProtocol=2,mEndpoints=[
UsbEndpoint[mAddress=130,mAttributes=2,mMaxPacketSize=32,mInterval=0]
UsbEndpoint[mAddress=2,mAttributes=2,mMaxPacketSize=32,mInterval=0]
UsbEndpoint[mAddress=129,mAttributes=3,mMaxPacketSize=8,mInterval=1]]]]
*/

public class CH340comm {
    public static final String ACTION_USB_PERMISSION = "cn.wch.wchusbdriver.USB_PERMISSION";
    public static CH34xUARTDriver driver;
    public byte[] writeBuffer = new byte[512];
    public byte[] readBuffer = new byte[512];
    public readThread handlerThread;
    public boolean isOpen;

    /* 500 to 2400 μs pulse width range for servos, 1000 to 2000 μs for ESC */
    public int servoElevatorMinPW = 500;
    public int servoElevatorMaxPW = 2400;
    public int servoRudderMinPW = 500;
    public int servoRudderMaxPW = 2400;
    public int servoElevonMinPW = 500;
    public int servoElevonMaxPW = 2400;
    public int throttleMinPW = 1000;
    public int throttleMaxPW = 2000;

    MainActivity main;
    Handler handler = new Handler() {
        public void handleMessage(Message msg) {
            /** Prints feedback from device in a text widget. */
            //main.update.updateConversationHandler.post(new updateTextThread(main.text_feedback, msg.toString()));
        }
    };

    public CH340comm(MainActivity argActivity) {
        main = argActivity;
    }

    private static byte[] toByteArray(String arg) {
        if (arg != null) {
            /* First remove the String ' ', and then convert the String to a char array */
            char[] NewArray = new char[1000];
            char[] array = arg.toCharArray();
            int length = 0;
            for (int i = 0; i < array.length; i++) {
                if (array[i] != ' ') {
                    NewArray[length] = array[i];
                    length++;
                }
            }
            NewArray[length] = 0x0D;
            NewArray[length + 1] = 0x0A;
            length += 2;

            byte[] byteArray = new byte[length];
            for (int i = 0; i < length; i++) {
                byteArray[i] = (byte) NewArray[i];
            }
            return byteArray;
        }
        return new byte[]{};
    }

    public void open() {
        int retval;
        if (!isOpen) {
            driver = new CH34xUARTDriver((UsbManager) main.getSystemService(main.USB_SERVICE), main, ACTION_USB_PERMISSION);
            retval = driver.ResumeUsbList();
            if (retval == -1) {
                main.logGPX.saveComment("error: " + "Open device failed!");

                main.update.updateConversationHandler.post(new updateTextThread(main.text_server, "Open device failed!"));
                driver.CloseDevice();
            } else if (retval == 0) {
                if (!driver.UartInit()) {
                    main.logGPX.saveComment("error: " + "Device initialization failed!");

                    main.update.updateConversationHandler.post(new updateTextThread(main.text_server, "Device initialization failed!"));
                    return;
                }
                main.update.updateConversationHandler.post(new updateTextThread(main.text_server, "Opened the device successfully!"));
                try {
                    config();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                main.usbButton.setText("USB\n\"0\"");
                isOpen = true;
                new readThread().start();

                /** sends feedback */
                Thread feedbackC340Thread = new Thread(new Wrap());
                feedbackC340Thread.start();
            } else {
                main.logGPX.saveComment("error: " + "ResumeUsbList == " + retval);

                main.update.updateConversationHandler.post(new updateTextThread(main.text_server, "ResumeUsbList == " + retval));
            }
        }
    }

    public void close() {
        if (isOpen) {
            main.logGPX.saveComment("error: " + "Disconnected");

            main.update.updateConversationHandler.post(new updateTextThread(main.text_server, "Disconnected"));
            main.usbButton.setText("USB\n\"1\"");
            driver.CloseDevice();
            driver = null;
            isOpen = false;
        }
    }

    public void config() {
        /* 9600,14400,19200,28800,33600,38400,56000,57600,76800,115200,128000,153600,230400,460800,921600,1500000,2000000 */
        int baudRate = 115200;
        byte dataBit = 8;
        byte stopBit = 1;
        byte parity = 0;
        byte flowControl = 0;

        if (driver.SetConfig(baudRate, dataBit, stopBit, parity, flowControl)) {
            main.update.updateConversationHandler.post(new updateTextThread(main.text_server, "Serial settings are successful!"));
            main.outputMode = main.USC16;
        } else {
            main.logGPX.saveComment("error: " + "Serial settings failed!");

            main.update.updateConversationHandler.post(new updateTextThread(main.text_server, "Serial settings failed!"));
        }
    }

    public void write(String command) {
        byte[] to_send = toByteArray(command);
        if (driver != null) {
            int retval = driver.WriteData(to_send, to_send.length);
            if (retval < 0)
                main.update.updateConversationHandler.post(new updateTextThread(main.text_server, "Write failed!"));
        }
    }

    public void SetPositionArray(int[] channels, int width, int time) {
        /** Sets position of one array of servos */

        String message = "";

        if (channels.length > 0) {
            for (int i = 0; i < channels.length; i++) {
                if (channels[i] < 0) {
                    int inverted = 3000 - width;
                    message += "#" + Math.abs(channels[i]) + "P" + inverted;
                } else {
                    message += "#" + channels[i] + "P" + width;
                }
            }
            message += "T" + time;
            this.write(message);
            main.update.updateConversationHandler.post(new updateTextThread(main.text_feedback, message));
        }
    }

    public void SetPositionArrayRange(int[] channels, int width, int time, int min, int max) {
        /** Sets position of one array of servos, with range of movement given */

        float multiplier = (float) (max - min) / 2000;
        String message = "";

        if (channels.length > 0) {
            for (int i = 0; i < channels.length; i++) {
                if (channels[i] < 0) {
                    int compensated = (int) ((max - width) * multiplier + min);
                    message += "#" + Math.abs(channels[i]) + "P" + compensated;
                } else {
                    int compensated = (int) ((width - min) * multiplier + min);
                    message += "#" + channels[i] + "P" + compensated;
                }
            }
            message += "T" + time;
            this.write(message);
            main.update.updateConversationHandler.post(new updateTextThread(main.text_feedback, message));
        }
    }

    public void SetPositionsRAW(int channel1, int value1, int channel2, int value2, int time) {
        /** Sets two different positions of two servos */

        String message;

        if (channel1 < 0) {
            int inverted = 3000 - value1;
            message = "#" + Math.abs(channel1) + "P" + inverted;
        } else {
            message = "#" + channel1 + "P" + value1;
        }

        if (channel2 < 0) {
            int inverted = 3000 - value2;
            message = message + "#" + Math.abs(channel2) + "P" + inverted + "T" + time;
        } else {
            message = message + "#" + channel2 + "P" + value2 + "T" + time;
        }
        this.write(message);
        main.update.updateConversationHandler.post(new updateTextThread(main.text_feedback, message));
    }

    public void SetTwoPositionsArraysRAW(int[] channels1, int value1, int[] channels2, int value2, int time) {
        /** Sets two different positions to two arrays of servos */

        String message = "";

        if (channels1.length > 0 && channels2.length > 0) {
            for (int i = 0; i < channels1.length; i++) {
                if (channels1[i] < 0) {
                    int inverted = 3000 - value1;
                    message += "#" + Math.abs(channels1[i]) + "P" + inverted;
                } else {
                    message += "#" + channels1[i] + "P" + value1;
                }
            }

            for (int j = 0; j < channels2.length; j++) {
                if (channels2[j] < 0) {
                    int inverted = 3000 - value2;
                    message += "#" + Math.abs(channels2[j]) + "P" + inverted;
                } else {
                    message += "#" + channels2[j] + "P" + value2;
                }
            }
            message += "T" + time;
            this.write(message);
            main.update.updateConversationHandler.post(new updateTextThread(main.text_feedback, message));
        }
    }

    public void SetTwoPositionsArraysRange(int[] channels1, int value1, int[] channels2, int value2, int time, int min, int max) {
        /** Sets two different positions to two arrays of servos, with range of movement given */

        float multiplier = (float) (max - min) / 2000;
        String message = "";

        if (channels1.length > 0 && channels2.length > 0) {
            for (int i = 0; i < channels1.length; i++) {
                if (channels1[i] < 0) {
                    int compensated = (int) ((max - value1) * multiplier + min);
                    message += "#" + Math.abs(channels1[i]) + "P" + compensated;
                } else {
                    int compensated = (int) ((value1 - min) * multiplier + min);
                    message += "#" + channels1[i] + "P" + compensated;
                }
            }

            for (int j = 0; j < channels2.length; j++) {
                if (channels2[j] < 0) {
                    int compensated = (int) ((max - value2) * multiplier + servoElevatorMinPW);
                    message += "#" + Math.abs(channels2[j]) + "P" + compensated;
                } else {
                    int compensated = (int) ((value2 - min) * multiplier + servoElevatorMinPW);
                    message += "#" + channels2[j] + "P" + compensated;
                }
            }
            message += "T" + time;
            this.write(message);
            main.update.updateConversationHandler.post(new updateTextThread(main.text_feedback, message));
        }
    }

    public void SetThrottle(int[] channels, int time) {
        String message = "";

        if (channels.length > 0) {
            for (int i = 0; i < channels.length; i++) {
                if (channels[i] < 0) {
                    int inverted = 2000 - main.throttle;
                    message += "#" + Math.abs(channels[i]) + "P" + inverted;
                } else {
                    message += "#" + channels[i] + "P" + main.throttle + 1000;
                }
            }
            message += "T" + time;
            this.write(message);
            main.update.updateConversationHandler.post(new updateTextThread(main.text_feedback, message));
        }
    }

    public void SetPositionMAVLink(int[] channels, int position, int time, int min, int max) {
        /** Sets position of an array of servos, with range of movement given, input in -1000 to 1000 range */

        float multiplier = (float) (max - min) / 2000;
        String message = "";
        int out;

        if (channels.length > 0) {
            for (int i = 0; i < channels.length; i++) {
                if (channels[i] < 0) {
                    /** scale then offset to (~500, ~2500) range */
                    out = (int) (-position * multiplier + (max + min) / 2);
                    message += "#" + Math.abs(channels[i]) + "P" + out;
                } else {
                    out = (int) (position * multiplier + (max + min) / 2);
                    message += "#" + channels[i] + "P" + out;
                }
            }
        }

        message += "T" + time;
        this.write(message);
        main.update.updateConversationHandler.post(new updateTextThread(main.text_feedback, message));
    }

    public void SetPositionsMAVLink(int[] channels1, int in1, int[] channels2, int in2, int time, int min, int max) {
        /** Sets positions of two arrays of servos, with range of movement given, input in -1000 to 1000 range */

        float multiplier = (float) (max - min) / 2000;
        String message = "";
        int out1 = 1500;
        int out2 = 1500;

        if (channels1.length > 0 && channels2.length > 0) {
            /* left elevon */
            for (int i = 0; i < channels1.length; i++) {
                if (channels1[i] < 0) {
                    out1 = (int) ((-in1 * multiplier + (max + min) / 2));
                    message += "#" + Math.abs(channels1[i]) + "P" + out1;
                } else {
                    out1 = (int) ((in1 * multiplier + (max + min) / 2));
                    message += "#" + channels1[i] + "P" + out1;
                }
            }

            /* right elevon */
            for (int i = 0; i < channels2.length; i++) {
                if (channels2[i] < 0) {
                    out2 = (int) (-in2 * multiplier + (max + min) / 2);
                    message += "#" + Math.abs(channels2[i]) + "P" + out2;
                } else {
                    out2 = (int) (in2 * multiplier + (max + min) / 2);
                    message += "#" + channels2[i] + "P" + out2;
                }
            }

            message += "T" + time;
            this.write(message);
            main.update.updateConversationHandler.post(new updateTextThread(main.text_feedback, message));
        }
    }

    public void SetPositions3(int[] channels1, int in1, int[] channels2, int in2, int[] channels3, int in3, int time, int min, int max, int minThrottle, int maxThrottle) {
        /** Sets positions of two arrays of servos, with range of movement given, input in -1000 to 1000 range */

        String message = "";
        int out1 = 1500;
        int out2 = 1500;
        int out3 = 1000;

        float multiplier = (float) (max - min) / 2000;
        float throttleMultiplier = (float) (maxThrottle - minThrottle) / 2000;

        if (channels1.length > 0 && channels2.length > 0) {
            /* left elevon */
            for (int i = 0; i < channels1.length; i++) {
                if (channels1[i] < 0) {
                    out1 = (int) ((-in1 * multiplier + (max + min) / 2));
                    message += "#" + Math.abs(channels1[i]) + "P" + out1;
                } else {
                    out1 = (int) ((in1 * multiplier + (max + min) / 2));
                    message += "#" + channels1[i] + "P" + out1;
                }
            }

            /* right elevon */
            for (int i = 0; i < channels2.length; i++) {
                if (channels2[i] < 0) {
                    out2 = (int) (-in2 * multiplier + (max + min) / 2);
                    message += "#" + Math.abs(channels2[i]) + "P" + out2;
                } else {
                    out2 = (int) (in2 * multiplier + (max + min) / 2);
                    message += "#" + channels2[i] + "P" + out2;
                }
            }

            /* throttle */
            for (int i = 0; i < channels3.length; i++) {
                if (channels3[i] < 0) {
                    out3 = (int) (-in3 * throttleMultiplier + (maxThrottle + minThrottle) / 2);
                    message += "#" + Math.abs(channels3[i]) + "P" + out3;
                } else {
                    out3 = (int) (in3 * throttleMultiplier + (maxThrottle + minThrottle) / 2);
                    message += "#" + channels3[i] + "P" + out3;
                }
            }

            System.out.println("main.throttle PW:" + out3);
            message += "T" + time;
            this.write(message);

            main.update.updateConversationHandler.post(new updateTextThread(main.text_feedback, message));
        }
    }

    private class readThread extends Thread {
        public void run() {
            byte[] buffer = new byte[4096];
            while (true) {
                Message msg = Message.obtain();
                if (!isOpen) {
                    break;
                }
                int length = driver.ReadData(buffer, 4096);
                if (length > 0) {
                    String recv = new String(buffer, 0, length);
                    msg.obj = recv;
                    handler.sendMessage(msg);
                }
            }
        }
    }

    class Wrap implements Runnable {
        @Override
        public void run() {
            try {
                main.sendTelemetry((byte) 12, isOpen ? true : false);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
