package pl.bezzalogowe.PhoneUAV;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.usb.UsbAccessory;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;

import pl.bezzalogowe.mavlink.*;

public class MainActivity extends Activity implements SensorEventListener {
    public static final int REQUEST_CAMERA_PERMISSION_RESULT = 0;
    public static final int REQUEST_WRITE_EXTERNAL_STORAGE_PERMISSION_RESULT = 1;
    final MainActivity main = this;
    final int FORMAT_ASCII = 0;
    final int FORMAT_HEX = 1;
    final int FORMAT_DEC = 2;
    final int REQUEST_CODE = 200;
    /* declare FT311 interface variables */
    public FT311PWMInterface pwmInterface;
    public FT311UARTInterface uartInterface;
    /* thread to read the data */
    public handler_thread handlerThread;
    public byte FT311D_PWM = 0x00;
    public byte FT311D_UART = 0x01;
    public byte USC16 = 0x02;
    public UpdateUI update = new UpdateUI();
    public UpdateFromOutsideThread updateOutside = new UpdateFromOutsideThread();
    public PWM pwmObject = new PWM(this);
    public CH340comm ch340commObject;
    /* API≤20 */
    public CameraAPI camObjectKitkat = new CameraAPI(this);
    /* API>20 */
    public Camera2API camObjectLolipop = new Camera2API(this);
    public Autopilot autopilot = new Autopilot();
    public TextView text_feedback;
    public byte outputMode, protocol;
    public SeekBar seekbarRDR, seekbarAIL, seekbarELEV, seekbarTHROT, seekbarELEV_L, seekbarELEV_R, seekbarTEST;
    /**
     * USC-16: output channels for each control surface
     * You can use more than one servo per surface
     */

    public int[] outputsAileron,
            outputsFlaperonLeft,
            outputsFlaperonRight,
            outputsElevator,
            outputsThrottle,
            outputsRudder,
            outputsElevonLeft,
            outputsElevonRight;
    //int ail, ele, thr, rdr, flaps;
    public short elevator, ailerons, throttle, rudder, flaps;
    int inputFormat = FORMAT_DEC;
    final Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            for (int i = 0; i < uartInterface.actualNumBytes[0]; i++) {
                uartInterface.readBufferToChar[i] = (char) uartInterface.readBufferFB[i];
            }
            appendData(uartInterface.readBufferToChar, uartInterface.actualNumBytes[0]);
        }
    };
    ServerUDP serverUDP = new ServerUDP(main);
    ServerTCP serverTCP = new ServerTCP(main);
    Input inputObject = new Input();
    MAVLinkClass mavLink = new MAVLinkClass(this);
    SK18comm sk18commObject = new SK18comm();
    /* needed for USB device permission */
    PendingIntent mPermissionIntent;
    /* camera number */
    String cameraID;
    double[] device_orientation = new double[3];
    Accelerometer accObject = new Accelerometer(this);
    Gravity gravityObject = new Gravity(this);
    Magnetometer magObject = new Magnetometer(this);
    Barometer pressureObject = new Barometer(this);
    Location locObject = new Location(this);
    LogGPX logGPX = new LogGPX(this);
    /* needed for USB device permission */
    public final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ch340commObject.ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    UsbDevice device = (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);

                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if (device != null) {
                            main.update.updateConversationHandler.post(new updateTextThread(main.text_server, "permission granted to " + device.getProductName()));
                            /** set up device communication */
                            try {
                                ch340commObject.open();
                            } catch (Exception e) {
                                main.logGPX.saveComment("error: " + e.toString());
                                e.printStackTrace();
                            }
                        }
                    } else {
                        main.update.updateConversationHandler.post(new updateTextThread(main.text_server, "permission denied to " + device.getProductName()));
                    }
                }
            }
        }
    };
    // saves trackpoints to csv file
    // LogCSV logObject = new LogCSV();
    LogSRT logSubRip = new LogSRT(this);
    NetworkInformation networkObject = new NetworkInformation(this);
    String serverpath, mavlinkGCShost;
    /* layout components */
    TextView dutyCycleRDR, dutyCycleAIL, dutyCycleELEV, dutyCycleTHROT, dutyCycleELEV_L, dutyCycleELEV_R, dutyCycleTEST,
            axis_text_x, axis_text_y, axis_text_z,
            angle_text_pitch, angle_text_roll, angle_text_heading, altitude_text, text_server;
    Button resetButton, usbButton, torchButton, photoButton, videoButton;
    Button startPWMminButton, startPWMmaxButton, throttleUpButton, throttleDownButton, throttleStopButton;
    Button throttleMinButton, throttleMaxButton;
    CheckBox checkboxAutopilotFeature1, checkboxAutopilotFeature2;
    SharedPreferences settings;
    int period;
    short elevatorTrim = 0;
    short rudderTrim = 0;
    /* https://stackoverflow.com/questions/3291655/get-battery-level-and-state-in-android */
    private BroadcastReceiver mBatInfoReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context ctxt, Intent intent) {
            /** sending battery voltage info */
            int voltage = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0); // Millivolts
            int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0); // percent
            mavLink.setBattery(voltage, level);
        }
    };

    public static void powerOTG(boolean flipBool) {
        /** http://stackoverflow.com/questions/20932102/execute-shell-command-from-android */
        try {
            Process su = Runtime.getRuntime().exec("su");
            DataOutputStream outputStream = new DataOutputStream(su.getOutputStream());

            String flipString = flipBool ? "1" : "0";
            outputStream.writeBytes("echo \"" + flipString + "\" >> /sys/kernel/debug/regulator/8226_smbbp_otg/enable\n");
            outputStream.flush();

            outputStream.writeBytes("exit\n");
            outputStream.flush();

            try {
                su.waitFor();
            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                outputStream.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /* Called when the activity is first created. */
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.main);

        if (Build.VERSION.SDK_INT >= 23) {
            askPermissions();
        }

        int result = getApplicationContext().checkCallingPermission(Manifest.permission.INTERNET);
        if (result == PackageManager.PERMISSION_GRANTED) {
            Log.d("permissions", "Internet granted");
        } else if (result == PackageManager.PERMISSION_DENIED) {
            Log.d("permissions", "Internet denied");
        }

/** Path to preferences file: /data/data/pl.bezzalogowe.PhoneUAV/shared_prefs/
 Root privilege required to create or modify file by hand */

        settings = PreferenceManager.getDefaultSharedPreferences(main);

/** https://www.ef3m.pl/pl/blog/Nadajnik-i-odbiornik-2,4GHz/12 */

        outputsAileron = getOutputs("outputs-aileron", "1");
        outputsElevator = getOutputs("outputs-elevator", "2");
        outputsThrottle = getOutputs("outputs-throttle", "3");
        outputsRudder = getOutputs("outputs-rudder", "4");

        /* both values positive when servos are mounted in opposite directions */
        outputsElevonLeft = getOutputs("outputs-elevon-left", "6");
        outputsElevonRight = getOutputs("outputs-elevon-right", "5");

        /* both values positive when servos are mounted in opposite directions,
           one value negative when servos are mounted in the same direction */
        outputsFlaperonLeft = getOutputs("outputs-flaperon-left", "16");
        outputsFlaperonRight = getOutputs("outputs-flaperon-right", "-15");

        autopilot.proportional = Double.valueOf(settings.getString("autopilot-proportional", "-100"));
        autopilot.integral = Double.valueOf(settings.getString("autopilot-integral", "1"));
        autopilot.derivative = Double.valueOf(settings.getString("autopilot-derivative", "1"));

/** If flaperon or elevon servos are installed in opposite directions, one flaperons' (elevons') channels should be negative */

        Log.d("getPreference", "channel order: " + outputsAileron[0] + ",\t" + outputsFlaperonLeft[0] + ",\t" + outputsFlaperonRight[0] + ",\n" + outputsElevator[0] + ",\t" + outputsElevonLeft[0] + ",\t" + outputsElevonRight[0] + ",\n" + outputsThrottle[0] + ",\t" + outputsRudder[0] + "\n");

        /* angle between the device and vehicle orientation */

/*
90 degr: <
180 degr: <<
270 degr: >
*/

        String orientation_string = null;
        try {
            orientation_string = settings.getString("orientation", "0");
        } catch (NumberFormatException e) {
            e.printStackTrace();
        }

        if (!orientation_string.isEmpty()) {
            if (orientation_string.equals("180"))
                device_orientation[2] = 180;
            else
                device_orientation[2] = 0;
        }

        int orientation_int = (int) device_orientation[2];
        switch (orientation_int) {
            case 270:
                this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE);
                break;
            case 180:
                this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT);
                break;
            case 90:
                this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                break;
            default:
                this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }

        /** https://codepoints.net/ */

        try {
            serverpath = settings.getString("server-path", "http://bezzalogowe.pl/uav/");
        } catch (NumberFormatException e) {
            e.printStackTrace();
        }

        /* TCP or UDP */
        if (settings.getString("protocol", "UDP").equals("TCP")) {
            protocol = 0x01;
        } else {
            protocol = 0x00;
        }

        /** https://developer.android.com/reference/android/hardware/camera2/CameraCharacteristics.html#LENS_FACING */
        /* LENS_FACING_BACK or LENS_FACING_FRONT assuming that back has id 0 and front has id 1 */
        if (settings.getString("camera-facing", "1").equals("2")) {
            cameraID = "2";
        } else if (settings.getString("camera-facing", "1").equals("0")) {
            cameraID = "1";
        } else {
            cameraID = "0";
        }

        try {
            mavlinkGCShost = settings.getString("mavlink-gcs-host", "");
        } catch (NumberFormatException e) {
            e.printStackTrace();
        }

        resetButton = (Button) findViewById(R.id.resetButton);
        usbButton = (Button) findViewById(R.id.usbButton);
        torchButton = (Button) findViewById(R.id.torchButton);
        photoButton = (Button) findViewById(R.id.photoButton);
        videoButton = (Button) findViewById(R.id.videoStartButton);

        dutyCycleRDR = (TextView) findViewById(R.id.DutyCycle1Text);
        dutyCycleAIL = (TextView) findViewById(R.id.DutyCycle2Text);
        dutyCycleELEV = (TextView) findViewById(R.id.DutyCycle3Text);
        dutyCycleTHROT = (TextView) findViewById(R.id.DutyCycle4Text);
        dutyCycleELEV_L = (TextView) findViewById(R.id.DutyElevonLeftText);
        dutyCycleELEV_R = (TextView) findViewById(R.id.DutyCycleElevonRightText);

        seekbarRDR = (SeekBar) findViewById(R.id.Seekbar1);
        seekbarAIL = (SeekBar) findViewById(R.id.Seekbar2);
        seekbarELEV = (SeekBar) findViewById(R.id.Seekbar3);
        seekbarTHROT = (SeekBar) findViewById(R.id.Seekbar4);
        seekbarTHROT.setAlpha((float) 0.5);
        seekbarELEV_L = (SeekBar) findViewById(R.id.SeekbarElevonLeft);
        seekbarELEV_R = (SeekBar) findViewById(R.id.SeekbarElevonRight);

        startPWMminButton = (Button) findViewById(R.id.StartPWMminButton);
        startPWMmaxButton = (Button) findViewById(R.id.StartPWMmaxButton);

        throttleStopButton = (Button) findViewById(R.id.ThrottleStopButton);
        throttleStopButton.setAlpha((float) 0.5);

        /** starts PWM thread with throttle set to minimum */
        startPWMminButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                if (outputMode == USC16) {
                    pwmObject.startPWM(ch340commObject.throttleMinPW);
                } else if (outputMode == FT311D_UART) {
                    sk18commObject.startPWM(main, ch340commObject.throttleMinPW);
                }
                /* PWM */
            }
        });

        /** starts PWM thread with throttle set to maximum */
        startPWMmaxButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                if (outputMode == USC16) {
                    pwmObject.startPWM(ch340commObject.throttleMaxPW);
                } else if (outputMode == FT311D_UART) {
                    sk18commObject.startPWM(main, ch340commObject.throttleMaxPW);
                }
                /* PWM */
            }
        });

        /** sets throttle to minimum */
        throttleMinButton = (Button) findViewById(R.id.ThrottleMinButton);
        throttleMinButton.setText("\u22a2");
        throttleMinButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                PWM.oneArray(outputsThrottle, ch340commObject.throttleMinPW, (byte) 100, ch340commObject.throttleMinPW, ch340commObject.throttleMaxPW);
                seekbarTHROT.setProgress(inputObject.scaleDown(ch340commObject.throttleMinPW));
            }
        });

        /** sets throttle to maximum */
        throttleMaxButton = (Button) findViewById(R.id.ThrottleMaxButton);
        throttleMaxButton.setText("\u22a3");
        throttleMaxButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                PWM.oneArray(outputsThrottle, ch340commObject.throttleMaxPW, (byte) 100, ch340commObject.throttleMinPW, ch340commObject.throttleMaxPW);
                seekbarTHROT.setProgress(inputObject.scaleDown(ch340commObject.throttleMaxPW));
            }
        });

        throttleStopButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                if (outputMode == USC16) {
                    pwmObject.stopPWM(main);
                } else if (outputMode == FT311D_UART) {
                    sk18commObject.stopPWM(main);
                }
                /* PWM */
            }
        });

        text_server = findViewById(R.id.text_server);
        text_feedback = findViewById(R.id.text_feedback);
        axis_text_x = findViewById(R.id.Axis0Text);
        axis_text_y = findViewById(R.id.Axis1Text);
        axis_text_z = findViewById(R.id.Axis2Text);

        text_feedback.setTextColor(Color.rgb(0, 255, 0));

        angle_text_pitch = findViewById(R.id.Angle0Text);
        angle_text_roll = findViewById(R.id.Angle1Text);
        angle_text_heading = findViewById(R.id.Angle2Text);
        altitude_text = findViewById(R.id.AltitudeText);

        checkboxAutopilotFeature1 = (CheckBox) findViewById(R.id.checkboxAutopilot1);
        checkboxAutopilotFeature2 = (CheckBox) findViewById(R.id.checkboxAutopilot2);

        /*
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        */

        if (protocol == 0x01) {
            serverTCP.startServer();
        } else {
            serverUDP.startServer();
        }

        update.updateConversationHandler = new Handler();
        updateOutside.updateConversationHandler = new Handler();

        pwmInterface = new FT311PWMInterface(this);
        resetFT311();
        sk18commObject = new SK18comm();
        SharedPreferences sharePrefSettings = getSharedPreferences("UARTLBPref", 0);
        sk18commObject.getPreference(sharePrefSettings);
        uartInterface = new FT311UARTInterface(this, sharePrefSettings);
        initiateFT311();
        ch340commObject = new CH340comm(this);

        /* needed for USB device permission */
        mPermissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(ch340commObject.ACTION_USB_PERMISSION), 0);
        IntentFilter filter = new IntentFilter(ch340commObject.ACTION_USB_PERMISSION);
        registerReceiver(mUsbReceiver, filter);

        Thread delayedInitiateThread = new Thread(new DelayedInitiateDeviceThread());
        delayedInitiateThread.start();

        handlerThread = new handler_thread(handler);
        handlerThread.start();

        //FIXME: Not all ports are free - this may cause problems.
        serverTCP.server_port = Integer.valueOf(settings.getString("server-port", "6000"));
        period = Integer.valueOf(settings.getString("period", "3"));

        ch340commObject.servoElevatorMinPW = Integer.valueOf(settings.getString("servo-elevator-min", "500")); //800
        ch340commObject.servoElevatorMaxPW = Integer.valueOf(settings.getString("servo-elevator-max", "2400")); //2200

        ch340commObject.servoRudderMinPW = Integer.valueOf(settings.getString("servo-rudder-min", "750")); //740
        ch340commObject.servoRudderMaxPW = Integer.valueOf(settings.getString("servo-rudder-max", "2150")); //2260

        ch340commObject.servoElevonMinPW = Integer.valueOf(settings.getString("servo-elevon-min", "700")); //400, 500, 483
        ch340commObject.servoElevonMaxPW = Integer.valueOf(settings.getString("servo-elevon-max", "2200")); //2400, 2500, 2443

        ch340commObject.throttleMinPW = Short.valueOf(settings.getString("throttle-min", "1000"));
        ch340commObject.throttleMaxPW = Short.valueOf(settings.getString("throttle-max", "2000"));

        startPWMminButton.setText(Integer.toString(ch340commObject.throttleMinPW));
        startPWMmaxButton.setText(Integer.toString(ch340commObject.throttleMaxPW));

        //FIXME: execute SetPeriod() after ResumeAccessory, not after fixed time.
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                pwmInterface.SetPeriod(period);
            }
        }, 2000);

        rudderTrim = Short.valueOf(settings.getString("rudder-trim", "0"));
        Log.d("rudder-trim", "read: " + rudderTrim);

        magObject.magnetic_declination = Double.valueOf(settings.getString("magnetic-declination", "0,0").replaceAll(",", "."));
        Log.d("magnetic-declination", "read: " + magObject.magnetic_declination);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);

        pressureObject.pressure_mean_sealevel = Float.valueOf(settings.getString("pressure-mean-sealevel", Float.toString(SensorManager.PRESSURE_STANDARD_ATMOSPHERE)).replaceAll(",", "."));
        Log.d("pressure-mean-sealevel", "read: " + pressureObject.pressure_mean_sealevel);

        inputObject.startController(this);
        accObject.startAccelerometer();
        gravityObject.startGravity();
        magObject.startMagnetometer();
        pressureObject.startBarometer();
        locObject.startLocation();
        logGPX.startLog();

        Log.d("getOutputs", "Attempting to open MAVLink port...");

        /* https://stackoverflow.com/questions/10294515/how-do-i-find-in-c-that-a-port-is-free-to-use */
        try {
            mavLink.classInit();
            mavLink.setGroundStationIP(mavlinkGCShost);

            /* takes longer if you wait for result of gethostbyname */
            int dynDNSresult = mavLink.receiveInit();
            if (dynDNSresult == 0) {
                System.out.println("dynDNS Host OK");
            } else {
                System.out.println("dynDNS Host NOT OK, gethostbyname returned NULL");
            }

            mavLink.heartBeatInit();

            //This can be moved to a new thread
            /*
            Thread concurrentSocketOpeningThread = new Thread(new ConcurrentSocketOpeningThread());
            concurrentSocketOpeningThread.start();
            */
        } catch (Exception e) {
            e.printStackTrace();
        }

        this.registerReceiver(this.mBatInfoReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));

        if (camObjectLolipop.hasCameraPermission()) {
            if (Build.VERSION.SDK_INT <= 20 /*Build.VERSION_CODES.KITKAT_WATCH*/) {
                /* API≤20 */
                camObjectKitkat.cameraInit();
            } else {
                /* API>20 */
                //TODO: make the method return true on success, false if something goes wrong
                camObjectLolipop.cameraInit();
                camObjectLolipop.cameraInitialised = true;
            }
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQUEST_CODE);
            show_permissions_dialog();
        }

        setupWidgets();
        serverTCP.displayAddress();

        //TODO: do this when the UAV is armed, stop thread when it's disarmed
        if (outputMode == USC16) {
            pwmObject.startPWM(ch340commObject.throttleMinPW);
        }
    }

    public void savePeriodPreference() {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(main);
        SharedPreferences.Editor editor = settings.edit();
        editor.putString("period", String.valueOf(period));
        editor.commit();
    }

    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_menu, menu);
        return true;
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        switch (item.getItemId()) {
            case R.id.switch_orientation:
                switchOrientation();
                break;
            case R.id.power_to_usb:
                powerToUSB();
                break;
            case R.id.get_info:
                networkObject.showWiFiInfo(this);
                break;
            case R.id.toggle_alt_spoof:
                toggle_altitude_spoofing();
                break;
            case R.id.options:
                showOptions();
                break;
            case R.id.help:
                openPage();
                break;
            case R.id.close:
                onBackPressed();
                break;
            default:
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    public int[] getOutputs(String key, String defaults) {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(main);
        String outputsAsSting = settings.getString(key, defaults);
        if (outputsAsSting.length() > 0) {
            String[] tokens = outputsAsSting.split(",");
            int[] outputs = new int[tokens.length];
            for (int i = 0; i < tokens.length; i++) {
                outputs[i] = Integer.valueOf(tokens[i]);
            }

            for (int i = 0; i < outputs.length; i++) {
                Log.d("getOutputs", key + ": " + outputs[i]);
            }
            return outputs;
        } else {
            return null;
        }
    }

    public void initiateCH340() {
        UsbManager usbmanager = (UsbManager) this.getSystemService(Context.USB_SERVICE);
        HashMap devicesList = usbmanager.getDeviceList();
        if (devicesList.size() > 0) {
            Object[] devices = devicesList.values().toArray();
            for (Object device : devices) {
                if (device.toString().indexOf("mVendorId=6790") != -1 &&
                        (device.toString().indexOf("mProductId=29987") != -1 ||
                                device.toString().indexOf("mProductId=21795") != -1)) {
                    /* needed for USB device permission */
                    usbmanager.requestPermission((UsbDevice) device, mPermissionIntent);
                }
            }
        }
    }

    private void initiateFT311() {
        /** Detects "mModel=FTDIPWMDemo" or "mModel=FTDIUARTDemo". */
        UsbManager usbmanager = (UsbManager) this.getSystemService(Context.USB_SERVICE);
        UsbAccessory[] usbAccessories = usbmanager.getAccessoryList();
        if (usbAccessories != null) {
            for (UsbAccessory object : usbAccessories) {
                update.updateConversationHandler.post(new updateTextThread(text_server, "accessory: " + object.toString()));
            }

            try {
                String model = usbAccessories[0].getModel();
                if (model.equals("FTDIPWMDemo")) {
                    pwmInterface.ResumeAccessory();
                    update.updateConversationHandler.post(new updateTextThread(text_server, "PWM mode"));
                    Log.d("initiateFT311", "PWM mode");
                    outputMode = FT311D_PWM;
                } else if (model.equals("FTDIUARTDemo")) {
                    if (uartInterface.ResumeAccessory() == 0) {
                        update.updateConversationHandler.post(new updateTextThread(text_server, "UART mode"));
                        Log.d("initiateFT311", "UART mode");
                        uartInterface.SetConfig(sk18commObject.baudRate, sk18commObject.dataBit, sk18commObject.stopBit, sk18commObject.parity, sk18commObject.flowControl);
                        outputMode = FT311D_UART;
                    } else
                        update.updateConversationHandler.post(new updateTextThread(text_server, "UART mode FAIL"));
                    Log.d("initiateFT311", "UART mode FAIL");
                } else {
                    update.updateConversationHandler.post(new updateTextThread(text_server, "unknown accessory: " + model));
                    Log.d("initiateFT311", "unknown accessory");
                }
            } catch (Exception e) {
                update.updateConversationHandler.post(new updateTextThread(text_server, "error: " + e.toString()));
                e.printStackTrace();
            }

        } else {
            update.updateConversationHandler.post(new updateTextThread(text_server, "accessory not attached"));
            Log.d("initiateFT311", "accessory not attached");
        }
    }

    protected void resetFT311() {
        pwmInterface.Reset();
        seekbarRDR.setProgress(50);
        seekbarAIL.setProgress(50);
        seekbarELEV.setProgress(50);
        seekbarTHROT.setProgress(0);
        seekbarELEV_L.setProgress(50);
        seekbarELEV_R.setProgress(50);

        //TODO: there must at least one channel assigned, this should not be obligatory
        try {
            if (outputMode == FT311D_PWM) {
                pwmInterface.SetDutyCycle((byte) (outputsRudder[0]), (byte) seekbarRDR.getProgress());
                pwmInterface.SetDutyCycle((byte) (outputsAileron[0]), (byte) seekbarAIL.getProgress());
                pwmInterface.SetDutyCycle((byte) (outputsElevator[0]), (byte) seekbarELEV.getProgress());
                pwmInterface.SetDutyCycle((byte) (outputsThrottle[0]), (byte) seekbarTHROT.getProgress());
                pwmInterface.SetDutyCycle((byte) (outputsElevonLeft[0]), (byte) seekbarELEV_L.getProgress());
                pwmInterface.SetDutyCycle((byte) (outputsElevonRight[0]), (byte) seekbarELEV_R.getProgress());
            } else if (outputMode == FT311D_UART) {
                sk18commObject.SetPositionSK18((byte) outputsRudder[0], seekbarRDR.getProgress() * 10, (byte) 10);
                sk18commObject.SetPositionSK18((byte) outputsAileron[0], seekbarAIL.getProgress() * 10, (byte) 10);
                sk18commObject.SetPositionSK18((byte) outputsElevator[0], seekbarELEV.getProgress() * 10, (byte) 10);
                sk18commObject.SetPositionSK18((byte) outputsThrottle[0], seekbarTHROT.getProgress() * 10, (byte) 10);
                sk18commObject.SetPositionSK18((byte) outputsElevonLeft[0], seekbarELEV_L.getProgress() * 10, (byte) 10);
                sk18commObject.SetPositionSK18((byte) outputsElevonRight[0], seekbarELEV_R.getProgress() * 10, (byte) 10);
            } else {
                //TODO: USC-16
            }
        } catch (Exception e) {
            e.printStackTrace();
            Log.d("resetFT311", "some output channels are undefined");
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        camObjectLolipop.cameraResume();
    }

    @Override
    protected void onResume() {
        // Ideally should implement onResume() and onPause() to take appropriate action when the activity looses focus
        Log.d("called", "onResume");
        super.onResume();
    }

    @Override
    protected void onPause() {
        // Ideally should implement onResume() and onPause() to take appropriate
        // action when the activity looses focus
        Log.d("called", "onPause");
        super.onPause();
    }

    @Override
    protected void onStop() {
        Log.d("called", "onStop");
        super.onStop();
        camObjectLolipop.cameraPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        switch (event.sensor.getType()) {
            case Sensor.TYPE_ACCELEROMETER:
                accObject.processAccelerometer(event);
                break;
            case Sensor.TYPE_GRAVITY:
                gravityObject.processGravity(event);
                break;
            case Sensor.TYPE_MAGNETIC_FIELD:
                magObject.calculateMagnetometer(event);
                break;
            case Sensor.TYPE_PRESSURE:
                pressureObject.calculateAltitude(event);
                break;
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // do nothing
    }

    private void setupWidgets() {
        usbButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                if (ch340commObject.isOpen) {
                    ch340commObject.close();
                } else {
                    ch340commObject.open();
                }
            }
        });

        resetButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                // resetButton.setBackgroundResource(drawable.start);
                resetFT311();
            }
        });
/*
        periodButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (periodValue.length() == 0x00) {
                    periodValue.setText("3");
                }
                period = Integer.parseInt(periodValue.getText().toString());

                if (period < 1) {
                    period = 1;
                    periodValue.setText("1");
                }

                if (period > 250) {
                    period = 250;
                    periodValue.setText("250");
                }

                periodValue.setSelection(periodValue.getText().length());
                pwmInterface.SetPeriod(period);

                savePUsbManager.getInstanceeriodPreference();
            }
        });
*/

        checkboxAutopilotFeature1.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    autopilot.startAutopilot(main);
                } else {
                    autopilot.stopAutopilot(main);
                }
            }
        });

        checkboxAutopilotFeature2.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    main.autopilot.startFollowingWaypoints(main);
                } else {
                    main.autopilot.stopFollowingWaypoints(main);
                }
            }
        });

        /** rudder */
        seekbarRDR.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onStopTrackingTouch(SeekBar seekBar) {
            }

            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    dutyCycleRDR.setText(Integer.toString(progress) + "%");
                    PWM.oneArray(main.outputsRudder, progress * 20 - 1000, 100, main.ch340commObject.servoRudderMinPW, main.ch340commObject.servoRudderMaxPW);
                } else {
                    dutyCycleRDR.setText("A " + progress + "%");
                }
            }
        });

        /** aileron */
        seekbarAIL.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onStopTrackingTouch(SeekBar seekBar) {
            }

            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    dutyCycleAIL.setText(Integer.toString(progress) + "%");
                    main.ailerons = (short) (progress * 20 - 1000);
                    //PWM.oneArray(main.outputsAileron, main.ailerons, 100, main.ch340commObject.servoElevonMinPW, main.ch340commObject.servoElevonMaxPW);
                    PWM.mixElevons(main.ailerons, main.elevator);
                    //PWM.flyingWing(main.ailerons, main.elevator, main.throttle);
                } else {
                    dutyCycleAIL.setText("A " + progress + "%");
                }
            }
        });

        /** elevator */
        seekbarELEV.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onStopTrackingTouch(SeekBar seekBar) {
            }

            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    dutyCycleELEV.setText(Integer.toString(progress) + "%");
                    main.elevator = (short) (progress * 20 - 1000);
                    //PWM.oneArray(main.outputsElevator, main.elevator, 100, main.ch340commObject.servoElevatorMinPW, main.ch340commObject.servoElevatorMaxPW);
                    PWM.mixElevons(main.ailerons, main.elevator);
                    //PWM.flyingWing(main.ailerons, main.elevator, main.throttle);
                } else {
                    dutyCycleELEV.setText("A " + progress + "%");
                }
            }
        });

        /** throttle */
        seekbarTHROT.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onStopTrackingTouch(SeekBar seekBar) {
            }

            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    dutyCycleTHROT.setText(Integer.toString(progress) + "%");
                    //TODO: check scale
                    throttle = (short) (20 * progress - 1000);
                    PWM.oneArray(main.outputsThrottle, throttle, 100, main.ch340commObject.throttleMinPW, main.ch340commObject.throttleMaxPW);
                } else {
                    dutyCycleTHROT.setText("A " + progress + "%");
                }
            }
        });

/** elevons (flying wing) */

        /** left elevon */
        seekbarELEV_L.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onStopTrackingTouch(SeekBar seekBar) {
            }

            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    dutyCycleELEV_L.setText(Integer.toString(progress));
                    PWM.oneArray(main.outputsElevonLeft, progress * 20 - 1000, 100, main.ch340commObject.servoElevonMinPW, main.ch340commObject.servoElevonMaxPW);
                } else {
                    // not fromUser
                }
            }
        });

        /** right elevon */
        seekbarELEV_R.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onStopTrackingTouch(SeekBar seekBar) {
            }

            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    dutyCycleELEV_R.setText(Integer.toString(progress));
                    PWM.oneArray(main.outputsElevonRight, progress * 20 - 1000, 100, main.ch340commObject.servoElevonMinPW, main.ch340commObject.servoElevonMaxPW);
                } else {
                    // not fromUser
                }
            }
        });
    }

    /* https://stackoverflow.com/a/12764310 */
    private void switchOrientation() {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(main);
        SharedPreferences.Editor editor = settings.edit();

        if (device_orientation[2] == 180) {
            device_orientation[2] = 0;
            this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

            editor.putString("orientation", String.valueOf("0"));
            editor.commit();

        } else if (device_orientation[2] == 0) {
            device_orientation[2] = 180;
            this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT);

            editor.putString("orientation", String.valueOf("180"));
            editor.commit();
        } else {
            device_orientation[2] = (device_orientation[2] + 180) % 360;
            if (device_orientation[2] >= 270 || device_orientation[2] <= 90)
                this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            else
                this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT);
        }
    }

    public void powerToUSB() {
        new AlertDialog.Builder(this).setIcon(android.R.drawable.ic_menu_preferences)
                .setTitle(getResources().getString(R.string.power_to_usb_otg_title))
                .setMessage(getResources().getString(R.string.power_to_usb_otg_msg))
                .setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        try {
                            powerOTG(true);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }).setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                try {
                    powerOTG(false);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).setNeutralButton(R.string.cancel, null).show();
    }

    @Override
    public void onBackPressed() {
        new AlertDialog.Builder(this).setIcon(android.R.drawable.ic_menu_close_clear_cancel)
                .setTitle(getResources().getString(R.string.confirmation_title))
                .setMessage(getResources().getString(R.string.confirmation_msg))
                .setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        closeApplication();
                    }
                }).setNegativeButton(R.string.no, null).show();
    }

    /* https://code.tutsplus.com/tutorials/android-sdk-intercepting-physical-key-events--mobile-10379 */
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int action = event.getAction();
        int keyCode = event.getKeyCode();
        switch (keyCode) {
            case KeyEvent.KEYCODE_VOLUME_UP:
                if (action == KeyEvent.ACTION_DOWN) {
                    //TODO
                    main.update.updateConversationHandler.post(new updateToastThread(this, "Volume up pressed"));
                }
                return true;
            case KeyEvent.KEYCODE_VOLUME_DOWN:
                if (action == KeyEvent.ACTION_DOWN) {
                    //TODO
                    main.update.updateConversationHandler.post(new updateToastThread(this, "Volume down pressed"));
                }
                return true;
            case KeyEvent.KEYCODE_HOME:
                if (action == KeyEvent.ACTION_DOWN) {
                    //TODO
                    main.update.updateConversationHandler.post(new updateToastThread(this, "Home pressed"));
                }
                return true;
            default:
                return super.dispatchKeyEvent(event);
        }
    }

    private void closeApplication() {
        logGPX.stopLog();

        mavLink.heartBeatStop();
        mavLink.receiveStop();
        /** disables flash */
        if (Build.VERSION.SDK_INT <= 20 /*Build.VERSION_CODES.KITKAT_WATCH*/) {
            try {
                if (camObjectKitkat.param.getFlashMode() != "FLASH_MODE_OFF")
                    camObjectKitkat.turnOffTorch();
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            camObjectLolipop.turnOffTorch();
        }

        /** closes device/accessory */
        if (outputMode == USC16) {
            if (ch340commObject.isOpen) {
                pwmObject.stopPWM(this);
                ch340commObject.isOpen = false;
                ch340commObject.driver.CloseDevice();
                ch340commObject.driver = null;
            }
        } else if (outputMode == FT311D_PWM) {
            pwmInterface.DestroyAccessory();
        } else if (outputMode == FT311D_UART) {
            uartInterface.DestroyAccessory(true);
        }

        /** saves rudder trim value */
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(main);
        SharedPreferences.Editor editor = settings.edit();
        editor.putString("rudder-trim", String.valueOf(rudderTrim));

        editor.putString("autopilot-proportional", Double.toString(autopilot.proportional));
        editor.putString("autopilot-integral", Double.toString(autopilot.integral));
        editor.putString("autopilot-derivative", Double.toString(autopilot.derivative));

        editor.commit();

        /** Must actually close activity and app */
        finish();
        System.exit(0);
    }

    private void showOptions() {
        //Intent optionsIntent = new Intent(this, OptionsActivity.class);
        Intent optionsIntent = new Intent(getApplicationContext(), SettingsActivity.class);
        //optionsIntent.putExtra(EXTRA_MESSAGE, "settings");
        startActivity(optionsIntent);
    }

    private void openPage() {
        Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/mareksuma1985/PhoneUAV-server"));
        startActivity(browserIntent);
    }

    protected void askPermissions() {
        String[] permissions = {
                "android.permission.INTERNET",
                "android.permission.CAMERA",
                "android.permission.RECORD_AUDIO",
                "android.permission.READ_EXTERNAL_STORAGE",
                "android.permission.WRITE_EXTERNAL_STORAGE",
                "android.permission.ACCESS_FINE_LOCATION",
                "android.permission.READ_PHONE_STATE"
        };
        ActivityCompat.requestPermissions(this, permissions, REQUEST_CODE);
    }

    public void show_permissions_dialog() {
        new AlertDialog.Builder(this).setIcon(android.R.drawable.ic_dialog_alert)
                .setTitle(getResources().getString(R.string.permission_title))
                .setMessage(getResources().getString(R.string.permission_content))
                .setNeutralButton("close", new Dialog.OnClickListener() {
                    public void onClick(DialogInterface arg0, int arg1) {
                        askPermissions();
                    }
                }).show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA_PERMISSION_RESULT) {
            if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(getApplicationContext(),
                        "Application will not run without camera services", Toast.LENGTH_SHORT).show();
            }
            if (grantResults[1] != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(getApplicationContext(),
                        "Application will not have audio on record", Toast.LENGTH_SHORT).show();
            }
        }

        if (requestCode == REQUEST_WRITE_EXTERNAL_STORAGE_PERMISSION_RESULT) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                /*if(mIsRecording || mIsTimelapse) {
                    mIsRecording = true;
                    mRecordImageButton.setImageResource(R.mipmap.btn_video_busy);
                }*/
                Toast.makeText(this,
                        "Permission successfully granted!", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this,
                        "App needs to save video to run", Toast.LENGTH_SHORT).show();
            }
        }
    }

    public void appendData(String s) {
        switch (inputFormat) {
            case FORMAT_HEX: {
                update.updateConversationHandler.post(new updateTextThread(text_feedback, "Hex"));
            }
            break;

            case FORMAT_DEC: {
                update.updateConversationHandler.post(new updateTextThread(text_feedback, "Dec"));
            }
            break;
/*
            case FORMAT_ASCII:
            break;
*/
            default:
                update.updateConversationHandler.post(new updateTextThread(text_feedback, s.toString()));
                break;
        }
    }

    public void appendData(char[] data, int len) {
        if (len >= 1)
            uartInterface.readSB.append(String.copyValueOf(data, 0, len));

        switch (inputFormat) {
            case FORMAT_HEX: {
                char[] ch = uartInterface.readSB.toString().toCharArray();
                String temp;
                StringBuilder tmpSB = new StringBuilder();
                for (int i = 0; i < ch.length; i++) {
                    temp = String.format("%02x", (int) ch[i]);

                    if (temp.length() == 4) {
                        tmpSB.append(temp.substring(2, 4));
                    } else {
                        tmpSB.append(temp);
                    }

                    if (i + 1 < ch.length) {
                        tmpSB.append(" ");
                    }
                }
                update.updateConversationHandler.post(new updateTextThread(text_feedback, tmpSB.toString()));
                tmpSB.delete(0, tmpSB.length());
            }
            break;

            case FORMAT_DEC: {
                char[] ch = uartInterface.readSB.toString().toCharArray();
                String temp;
                StringBuilder tmpSB = new StringBuilder();
                for (int i = 0; i < ch.length; i++) {
                    temp = Integer.toString((int) (ch[i] & 0xff));

                    for (int j = 0; j < (3 - temp.length()); j++) {
                        tmpSB.append("0");
                    }
                    tmpSB.append(temp);

                    if (i + 1 < ch.length) {
                        tmpSB.append(" ");
                    }
                }
                update.updateConversationHandler.post(new updateTextThread(text_feedback, tmpSB.toString()));
                tmpSB.delete(0, tmpSB.length());
            }
            break;

            case FORMAT_ASCII:
            default:
                update.updateConversationHandler.post(new updateTextThread(text_feedback, uartInterface.readSB.toString()));
                break;
        }
    }

    /* one boolean */
    void sendTelemetry(int number, boolean value) {
        if (protocol == 0x01) {
            //TODO write method for sending one boolean through TCP
        } else {
            serverUDP.send((byte) number, value);
        }
    }

    /* one short */
    void sendTelemetry(int number, short value) {
        if (protocol == 0x01) {
            //TODO: write method for sending one short through TCP
        } else {
            serverUDP.send((byte) number, value);
        }
    }

    /* two shorts */
    void sendTelemetry(int number, short short1, short short2) {
        if (protocol == 0x01) {
            serverTCP.send((byte) number, short1, short2);
        } else {
            serverUDP.send((byte) number, short1, short2);
        }
    }

    /* one float */
    void sendTelemetry(int number, float value) {
        if (protocol == 0x01) {
            serverTCP.send((byte) number, value);
        } else {
            serverUDP.send((byte) number, value);
        }
    }

    /* an array of floats */
    void sendTelemetry(int number, float[] value) {
        if (protocol == 0x01) {
            //TODO: write method for sending array of floats through TCP
        } else {
            serverUDP.send((byte) number, value);
        }
    }

    /* an array of doubles */
    void sendTelemetry(int number, double[] coordinates) {
        if (protocol == 0x01) {
            //TODO: write method for sending array of doubles through TCP
        } else {
            serverUDP.send((byte) number, coordinates);
        }
    }

    private void enable_altitude_spoofing() {
        LinearLayout mainVertical = findViewById(R.id.main_vertical);
        LinearLayout linearLayoutTest = new LinearLayout(this);
        dutyCycleTEST = new TextView(this);
        linearLayoutTest.addView(dutyCycleTEST, 0);
        WindowManager.LayoutParams paramsLayout = new WindowManager.LayoutParams();
        paramsLayout.width = WindowManager.LayoutParams.WRAP_CONTENT;
        paramsLayout.height = WindowManager.LayoutParams.WRAP_CONTENT;
        linearLayoutTest.setLayoutParams(paramsLayout);
        mainVertical.addView(linearLayoutTest, 7);
        seekbarTEST = new SeekBar(this);
        WindowManager.LayoutParams paramsSeekbar = new WindowManager.LayoutParams();
        paramsSeekbar.width = 960;
        paramsSeekbar.height = WindowManager.LayoutParams.WRAP_CONTENT;
        paramsSeekbar.gravity = android.view.Gravity.END;
        seekbarTEST.setLayoutParams(paramsSeekbar);
        seekbarTEST.setProgress(50);
        dutyCycleTEST.setText("50%");
        linearLayoutTest.addView(seekbarTEST, 1);
        autopilot.target_altitude = 150;
        autopilot.fakeAltitude = true;

        seekbarTEST.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onStopTrackingTouch(SeekBar seekBar) {
            }

            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    dutyCycleTEST.setText(Integer.toString(progress) + "%");
                    /** for testing */
                    if (autopilot.fakeAltitude) {
                        pressureObject.altitudeBarometricRecent = 100 + progress;
                    }
                } else {
                    // not fromUser
                }
            }
        });
    }

    private void disable_altitude_spoofing() {
        LinearLayout mainVertical = findViewById(R.id.main_vertical);
        LinearLayout linearLayoutTest = (LinearLayout) mainVertical.getChildAt(7);
        linearLayoutTest.setVisibility(View.GONE);
        seekbarTEST = null;
        autopilot.fakeAltitude = false;
    }

    private void toggle_altitude_spoofing() {
        if (autopilot.fakeAltitude) {
            disable_altitude_spoofing();
        } else {
            enable_altitude_spoofing();
        }
    }

    /* USB input data handler */
    private class handler_thread extends Thread {
        Handler mHandler;

        handler_thread(Handler h) {
            mHandler = h;
        }

        public void run() {
            Message msg;
            while (true) {
                try {
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                }

                uartInterface.status = uartInterface.ReadData(4096, uartInterface.readBufferFB, uartInterface.actualNumBytes);

                if (uartInterface.status == 0x00 && uartInterface.actualNumBytes[0] > 0) {
                    msg = mHandler.obtainMessage();
                    mHandler.sendMessage(msg);
                }
            }
        }
    }

    class DelayedInitiateDeviceThread implements Runnable {
        @SuppressWarnings("all")
        public void run() {
            try {
                Thread.sleep(500);
                initiateCH340();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public class ConcurrentSocketOpeningThread implements Runnable {
        @SuppressWarnings("all")
        public void run() {
        }
    }
}
