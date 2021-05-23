package pl.bezzalogowe.PhoneUAV;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorManager;
import android.util.Log;

public class Gravity {
    public Sensor sensorInstanceGrav;
    MainActivity main;
    SensorManager gravitySensorManager;
    double angle_roll_rads, angle_pitch_rads;
    double angle_roll, angle_pitch;
    float[] gravityVectorDevice = new float[3];
    float[] gravityVectorVehicle = new float[3];

    public Gravity(MainActivity argActivity) {
        main = argActivity;
    }

    public void processGravity(SensorEvent event) {
        /** Processes data from gravity sensor event. */
        float deltaX = Math.abs(gravityVectorDevice[0] - event.values[0]);
        float deltaY = Math.abs(gravityVectorDevice[1] - event.values[1]);
        float deltaZ = Math.abs(gravityVectorDevice[2] - event.values[2]);

        if (deltaX < 0.01)
            deltaX = 0;
        if (deltaY < 0.01)
            deltaY = 0;
        if (deltaZ < 0.01)
            deltaZ = 0;

        gravityVectorDevice[0] = event.values[0];
        gravityVectorDevice[1] = event.values[1];
        gravityVectorDevice[2] = event.values[2];

        /** rotates the vector by angle between device and vehicle orientation */
        gravityVectorVehicle = rotateAroundZ(gravityVectorDevice, main.device_orientation[2]);

        if (deltaX != 0 || deltaY != 0 || deltaZ != 0) {

            /** sends the vehicle attitude vector even before calculating the angles */
            Thread feedback = new Thread(new Wrap());
            feedback.start();

            /** in radians */
            angle_roll_rads = Math.atan2((double) gravityVectorVehicle[0], (double) gravityVectorVehicle[2]);
            angle_pitch_rads = Math.atan2((double) gravityVectorVehicle[1], (double) gravityVectorVehicle[2]);

            main.mavLink.sendAttitude((float) -angle_roll_rads, (float) angle_pitch_rads);

            /** in degrees */
            angle_roll = Math.toDegrees(Math.atan2((double) gravityVectorVehicle[0], (double) gravityVectorVehicle[2]));
            angle_pitch = Math.toDegrees(Math.atan2((double) gravityVectorVehicle[1], (double) gravityVectorVehicle[2]));

            main.update.updateConversationHandler.post(new updateGravitySensorThread(
                    main.angle_text_pitch,
                    main.angle_text_roll,
                    angle_pitch,
                    main.autopilot.target_pitch,
                    angle_roll));
        }
    }

    public void startGravity() {
        gravitySensorManager = (SensorManager) main.getSystemService(Context.SENSOR_SERVICE);
        if (gravitySensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY) != null) {
            sensorInstanceGrav = gravitySensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY);
            //SENSOR_DELAY_NORMAL, SENSOR_DELAY_UI, SENSOR_DELAY_GAME, SENSOR_DELAY_FASTEST
            gravitySensorManager.registerListener(main, sensorInstanceGrav, SensorManager.SENSOR_DELAY_UI);
        } else {
            Log.d("onCreate", "Gravity sensor error!");
        }
    }

    public float[] rotateAroundZ(float[] input, double angleDegrees) {
        double angleRadians = Math.toRadians(angleDegrees);
        float[] output = new float[3];
        if (angleDegrees == 180) {
            output[0] = -input[0];
            output[1] = -input[1];
            output[2] = input[2];
        } else {
            output[0] = (float) (Math.cos(angleRadians) * input[0] - Math.sin(angleRadians) * input[1]);
            output[1] = (float) (Math.sin(angleRadians) * input[0] + Math.cos(angleRadians) * input[1]);
            output[2] = input[2];
        }
        return output;
    }

    class Wrap implements Runnable {
        @Override
        public void run() {
            try {
                //main.sendTelemetry(1, (short) angle_roll, (short) angle_pitch);
                float[] vector = {gravityVectorVehicle[0], gravityVectorVehicle[1], gravityVectorVehicle[2]};
                main.sendTelemetry(1, vector);
            } catch (Exception e) {
                Log.d("Gravity sensor", "error: " + e);
                main.logGPX.saveComment("error: " + e.toString());
            }
        }
    }
}
