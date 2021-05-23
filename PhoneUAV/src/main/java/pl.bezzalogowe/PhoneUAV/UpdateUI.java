package pl.bezzalogowe.PhoneUAV;

import android.graphics.Color;
import android.os.Handler;
import android.view.View;
import android.widget.CheckBox;
import android.widget.SeekBar;
import android.widget.Toast;
import android.widget.TextView;

public class UpdateUI {
    public Handler updateConversationHandler;

    public UpdateUI() {
    }
}

class updateAccelerometerThread implements Runnable {
    private TextView tv1, tv2, tv3;
    private float x, y, z;

    public updateAccelerometerThread(TextView argtv1, TextView argtv2, TextView argtv3, float xaxis, float yaxis, float zaxis) {

        this.tv1 = argtv1;
        this.tv2 = argtv2;
        this.tv3 = argtv3;

        this.x = xaxis;
        this.y = yaxis;
        this.z = zaxis;
    }

    @Override
    public void run() {
        tv1.setText(String.format("%.01f", x));
        tv2.setText(String.format("%.01f", y));
        tv3.setText(String.format("%.01f", z));
    }
}

class updateGravitySensorThread implements Runnable {
    private TextView tv4, tv5;
    private double angle_pitch, target_pitch, angle_roll;

    public updateGravitySensorThread(TextView argtv4, TextView argtv5, double angle1, double angle2, double angle3) {

        this.tv4 = argtv4;
        this.tv5 = argtv5;

        this.angle_pitch = angle1;
        this.target_pitch = angle2;
        this.angle_roll = angle3;
    }

    @Override
    public void run() {
        tv4.setText(String.format("%.01f", angle_pitch) + "˚\n" + String.format("%.01f", target_pitch) + "˚");
        tv5.setText(String.format("%.01f", angle_roll) + "˚");
    }
}

class updateProgressThread implements Runnable {
    private SeekBar view;
    private int pos;

    public updateProgressThread(SeekBar element, int progress) {
        this.view = element;
        this.pos = progress;
    }

    @Override
    public void run() {
        view.setProgress(pos);
    }
}

class updateToastThread implements Runnable {
    private MainActivity context;
    private String msg;
    private int length;

    public updateToastThread(MainActivity activity, String str) {
        this.context = activity;
        this.msg = str;
    }

    public updateToastThread(MainActivity activity, String str, boolean iflong) {
        this.context = activity;
        this.msg = str;

        if (iflong == true) {
            this.length = Toast.LENGTH_LONG;
        } else {
            this.length = Toast.LENGTH_SHORT;
        }
    }

    @Override
    public void run() {
        Toast.makeText(context, msg, length).show();
    }
}

class updateTextThread implements Runnable {
    private TextView view;
    private String msg;

    public updateTextThread(TextView element, String str) {
        this.view = element;
        this.msg = str;
    }

    @Override
    public void run() {
        view.setText(msg + "\n");
    }
}

class updateTextColorThread implements Runnable {
    private TextView view;
    private int colorRed, colorGreen, colorBlue;

    public updateTextColorThread(TextView element, int red, int green, int blue) {
        this.view = element;
        this.colorRed = red;
        this.colorGreen = green;
        this.colorBlue = blue;
    }

    @Override
    public void run() {
        view.setTextColor(Color.rgb(colorRed, colorGreen, colorBlue));
    }
}

class updateCheckBoxThread implements Runnable {
    private CheckBox view;
    private boolean value;

    public updateCheckBoxThread(CheckBox element, boolean status) {
        this.view = element;
        this.value = status;
    }

    @Override
    public void run() {
        view.setChecked(value);
    }
}

class updateVisibilityThread implements Runnable {
    private View view;
    private int visibility;

    public updateVisibilityThread(View arg1, int arg2) {
        this.view = arg1;
        this.visibility = arg2;
    }

    @Override
    public void run() {
        view.setVisibility(visibility);
    }
}

class updateAlphaThread implements Runnable {
    private View view;
    private float alpha;

    public updateAlphaThread(View arg1, float arg2) {
        this.view = arg1;
        this.alpha = arg2;
    }

    @Override
    public void run() {
        view.setAlpha((float) alpha);
    }
}