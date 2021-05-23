package pl.bezzalogowe.PhoneUAV;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbAccessory;
import android.hardware.usb.UsbManager;
import android.os.ParcelFileDescriptor;
import android.widget.Toast;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

/*********************************** FT311 GPIO interface class ***********************************/

public class FT311PWMInterface extends Activity {
    private static final String ACTION_USB_PERMISSION = "com.GPIODemo.USB_PERMISSION";
    /* 4.4 Default Mode Strings */
    public static String ManufacturerString = "mManufacturer=FTDI";
    public static String ModelString = "mModel=FTDIPWMDemo";
    public static String VersionString = "mVersion=1.0";
    public UsbManager usbmanager;
    public UsbAccessory usbaccessory;
    public PendingIntent mPermissionIntent;
    public ParcelFileDescriptor filedescriptor;
    public FileInputStream inputstream;
    public FileOutputStream outputstream;
    public boolean mPermissionRequestPending = false;
    public Context global_context;

    /************************************* USB broadcast receiver *************************************/
    private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    UsbAccessory accessory = (UsbAccessory) intent
                            .getParcelableExtra(UsbManager.EXTRA_ACCESSORY);
                    if (intent.getBooleanExtra(
                            UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        Toast.makeText(global_context, "Allow USB Permission",
                                Toast.LENGTH_SHORT).show();
                        OpenAccessory(accessory);
                    } else {
                        Toast.makeText(global_context, "Deny USB Permission",
                                Toast.LENGTH_SHORT).show();
                        // Log.d("LED", "permission denied for accessory " + accessory);
                    }
                    mPermissionRequestPending = false;
                }
            } else if (UsbManager.ACTION_USB_ACCESSORY_DETACHED.equals(action)) {
                CloseAccessory();
            } else {
                // Log.d("LED", "....");
            }
        }
    };
    private byte[] usbdata;
    private byte[] writeusbdata;
    private int readcount;

    /* constructor */
    public FT311PWMInterface(Context context) {
        super();
        global_context = context;
        /* shall we start a thread here or what */
        usbdata = new byte[64];
        writeusbdata = new byte[64];

/****************************************** USB handling ******************************************/
        usbmanager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
        // Log.d("LED", "usbmanager" + usbmanager);
        mPermissionIntent = PendingIntent.getBroadcast(context, 0, new Intent(ACTION_USB_PERMISSION), 0);
        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        filter.addAction(UsbManager.ACTION_USB_ACCESSORY_DETACHED);
        context.registerReceiver(mUsbReceiver, filter);

        inputstream = null;
        outputstream = null;
    }

    /* period value in milliseconds */
    public void SetPeriod(int period) {
        // FT31XD_Android_programmer_guide(FT_000532) 5.1.1
        /* take care of zero */
        if (period < 1) {
            period = 1;
        }
        /* take care of MAX value */
        if (period > 250) {
            period = 250;
        }

        /* create the packet */
        writeusbdata[0] = 0x21;
        writeusbdata[1] = 0x00;
        writeusbdata[2] = (byte) (period & 0xff);
        writeusbdata[3] = (byte) ((period >> 8) & 0xff);
        /* send the packet over the USB */
        SendPacket(4);
    }

    /* duty cycle in percentage */
    public void SetDutyCycle(byte pwmChannel, byte dutyCycle) {
        // FT31XD_Android_programmer_guide(FT_000532)
        // 5.1.2

        if (dutyCycle < 5) {
            dutyCycle = 5;
        }

        if (dutyCycle > 95) {
            dutyCycle = 95;
        }

        /* create the packet */
        writeusbdata[0] = 0x22;
        writeusbdata[1] = (byte) (Math.abs(pwmChannel) - 1);
        writeusbdata[2] = (byte) ((pwmChannel >= 0) ? dutyCycle : (100 - dutyCycle));

        writeusbdata[3] = 0x00;

        /* send the packet over the USB */
        SendPacket(4);
    }

    /* reset method */
    public void Reset() {
        // FT31XD_Android_programmer_guide(FT_000532)
        // 5.1.3
        writeusbdata[0] = 0x23;
        writeusbdata[1] = 0x00;
        writeusbdata[2] = 0x00;
        writeusbdata[3] = 0x00;
        writeusbdata[4] = 0x00;

        /* send the packet over the USB */
        SendPacket(4);
    }

    /* method to send on USB */
    private void SendPacket(int numBytes) {

        try {
            if (outputstream != null) {
                outputstream.write(writeusbdata, 0, numBytes);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /* resume accessory */
    public void ResumeAccessory() {
        // Intent intent = getIntent();
        if (inputstream != null && outputstream != null) {
            return;
        }

        UsbAccessory[] accessories = usbmanager.getAccessoryList();
        if (accessories != null) {
            Toast.makeText(global_context, "Accessory Attached", Toast.LENGTH_SHORT).show();
        }

        UsbAccessory accessory = (accessories == null ? null : accessories[0]);
        if (accessory != null) {
            if (-1 == accessory.toString().indexOf(ManufacturerString)) {
                Toast.makeText(global_context, "Manufacturer is not matched!", Toast.LENGTH_SHORT).show();
                return;
            }

            if (-1 == accessory.toString().indexOf(ModelString)) {
                Toast.makeText(global_context, "Model is not matched!", Toast.LENGTH_SHORT).show();
                Toast.makeText(global_context, accessory.toString(), Toast.LENGTH_LONG).show();
                return;
            }

            if (-1 == accessory.toString().indexOf(VersionString)) {
                Toast.makeText(global_context, "Version is not matched!", Toast.LENGTH_SHORT).show();
                return;
            }

            Toast.makeText(global_context, "Strings are matched! (PWM mode)", Toast.LENGTH_SHORT).show();

            if (usbmanager.hasPermission(accessory)) {
                OpenAccessory(accessory);
            } else {
                synchronized (mUsbReceiver) {
                    if (!mPermissionRequestPending) {
                        Toast.makeText(global_context, "Request USB Permission", Toast.LENGTH_SHORT).show();
                        usbmanager.requestPermission(accessory, mPermissionIntent);
                        mPermissionRequestPending = true;
                    }
                }
            }
        }
    }

    /* destroy accessory */
    public void DestroyAccessory() {
        Reset();
        SetDutyCycle((byte) 1, (byte) 0);
        SetDutyCycle((byte) 2, (byte) 0);
        SetDutyCycle((byte) 3, (byte) 0);
        SetDutyCycle((byte) 4, (byte) 0);
        CloseAccessory();
    }

    /**************************************** Helper routines *****************************************/
    public void OpenAccessory(UsbAccessory accessory) {
        filedescriptor = usbmanager.openAccessory(accessory);
        if (filedescriptor != null) {
            usbaccessory = accessory;

            FileDescriptor fd = filedescriptor.getFileDescriptor();

            inputstream = new FileInputStream(fd);
            outputstream = new FileOutputStream(fd);
            /* check if any of them are null */
            if (inputstream == null || outputstream == null) {
                return;
            }
        }
    }

    private void CloseAccessory() {
        try {
            if (filedescriptor != null)
                filedescriptor.close();

        } catch (IOException e) {
        }

        try {
            if (inputstream != null)
                inputstream.close();
        } catch (IOException e) {
        }

        try {
            if (outputstream != null)
                outputstream.close();

        } catch (IOException e) {
        }
        /* FIXME, add the notification also to close the application */

        filedescriptor = null;
        inputstream = null;
        outputstream = null;

        System.exit(0);
    }
}
