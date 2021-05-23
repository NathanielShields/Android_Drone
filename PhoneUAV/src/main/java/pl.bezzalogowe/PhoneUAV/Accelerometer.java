package pl.bezzalogowe.PhoneUAV;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorManager;
import android.util.Log;

public class Accelerometer {
    public Sensor sensorInstanceAcc;
    MainActivity main;
    SensorManager accelerometerManager;
    double angle_roll, angle_pitch;
    float[] accVectorDevice = new float[3];
    float[] accVectorVehicle = new float[3];

    public Accelerometer(MainActivity argActivity) {
        main = argActivity;
    }

    public void processAccelerometer(SensorEvent event) {
        /** Processes data from accelerometer event. */

        float deltaX = Math.abs(accVectorDevice[0] - event.values[0]);
        float deltaY = Math.abs(accVectorDevice[1] - event.values[1]);
        float deltaZ = Math.abs(accVectorDevice[2] - event.values[2]);

        // filter out changes below 0.01
        if (deltaX < 0.01)
            deltaX = 0;
        if (deltaY < 0.01)
            deltaY = 0;
        if (deltaZ < 0.01)
            deltaZ = 0;

        accVectorDevice[0] = event.values[0];
        accVectorDevice[1] = event.values[1];
        accVectorDevice[2] = event.values[2];

        /* rotates the vector by angle between device (smartphone/tablet) and vehicle (UAV) orientation */
        accVectorVehicle = rotateAroundZ(accVectorDevice, main.device_orientation[2]);

        angle_roll = Math.toDegrees(Math.atan2((double) accVectorVehicle[0], (double) accVectorVehicle[2]));
        angle_pitch = Math.toDegrees(Math.atan2((double) accVectorVehicle[1], (double) accVectorVehicle[2]));

        if (deltaX != 0 || deltaY != 0 || deltaZ != 0) {
            main.update.updateConversationHandler
                    .post(new updateAccelerometerThread(
                            main.axis_text_x, main.axis_text_y, main.axis_text_z, accVectorVehicle[0], accVectorVehicle[1], accVectorVehicle[2]));
        }

        /* Sending accelerometer feedback disabled in favour of gravity sensor */
    }

    public void startAccelerometer() {
        accelerometerManager = (SensorManager) main.getSystemService(Context.SENSOR_SERVICE);
        if (accelerometerManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null) {
            sensorInstanceAcc = accelerometerManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            accelerometerManager.registerListener(main, sensorInstanceAcc, SensorManager.SENSOR_DELAY_UI);
        } else {
            Log.d("onCreate", "Accelerometer error!");
        }
    }


    public float[] rotateAroundX(float[] input, double angleDegrees) {
        double angleRadians = Math.toRadians(angleDegrees);
        float[] output = new float[3];
        output[0] = input[0];
        output[1] = (float) (Math.cos(angleRadians) * input[1] - Math.sin(angleRadians) * input[2]);
        output[2] = (float) (Math.sin(angleRadians) * input[1] + Math.cos(angleRadians) * input[2]);
        return output;
    }

    public float[] rotateAroundY(float[] input, double angleDegrees) {
        double angleRadians = Math.toRadians(angleDegrees);
        float[] output = new float[3];
        output[0] = (float) (Math.cos(angleRadians) * input[0] + Math.sin(angleRadians) * input[2]);
        output[1] = input[1];
        output[2] = (float) (-Math.sin(angleRadians) * input[0] + Math.cos(angleRadians) * input[2]);
        return output;
    }

    public float[] rotateAroundZ(float[] input, double angleDegrees) {
        /** https://en.wikipedia.org/wiki/Rotation_matrix */
        double angleRadians = Math.toRadians(angleDegrees);
        float[] output = new float[3];
        if (angleDegrees == 180) {
            output[0] = -input[0];
            output[1] = -input[1];
            output[2] = input[2];
        } else {
/*
┌			   ┐┌ ┐ ┌			  ┐
│cosΦ┆-sinΦ┆ 0 ││x│ │cosΦx - sinΦy│
│sinΦ┆ cosΦ┆ 0 ││y│=│sinΦx + cosΦy│
│ 0  ┆  0  ┆ 1 ││z│ │      z	  │
└			   ┘└ ┘ └			  ┘
*/
            output[0] = (float) (Math.cos(angleRadians) * input[0] - Math.sin(angleRadians) * input[1]);
            output[1] = (float) (Math.sin(angleRadians) * input[0] + Math.cos(angleRadians) * input[1]);
            output[2] = input[2];
        }
        //TODO: add fast methods for 90 and 270 cases
        return output;
    }
}
