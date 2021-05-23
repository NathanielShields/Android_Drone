package pl.bezzalogowe.PhoneUAV;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorManager;
import android.util.Log;

public class Magnetometer {
    public float axisX;
    public float axisY;
    public float axisZ;
    public Sensor sensorInstanceMag;
    double magnetic_declination;
    double heading = 0;
    double azimuth = 0;
    MainActivity main;
    SensorManager magnetometerManager;

    public Magnetometer(MainActivity argActivity) {
        main = argActivity;
    }

    public void calculateMagnetometer(SensorEvent event) {

        float[] magVectorDevice = new float[3];
        float[] magVectorVehicle = new float[3];

        magVectorDevice[0] = event.values[0];
        magVectorDevice[1] = event.values[1];
        magVectorDevice[2] = event.values[2];

        magVectorVehicle = main.accObject.rotateAroundZ(magVectorDevice, main.device_orientation[2]);

        /*
         heading - vehicle orientation
         azimuth - direction towards next waypoint
         */

        // TODO: doesn't take Z axis into account, maybe it should?
        heading = ((Math.toDegrees(-Math.atan2((double) magVectorVehicle[0], (double) magVectorVehicle[1])) + magnetic_declination + 360) % 360);

        //TODO: make a method for radians
        main.mavLink.setHeadingDegrees(heading);

        if (main.locObject.waypointNext != null && main.locObject.recentLocation != null) {
            try {
                main.magObject.azimuth = (main.locObject.recentLocation.bearingTo(main.locObject.waypointNext) + 360) % 360;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void startMagnetometer() {
        magnetometerManager = (SensorManager) main.getSystemService(Context.SENSOR_SERVICE);
        if (magnetometerManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD) != null) {

            sensorInstanceMag = magnetometerManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
            magnetometerManager.registerListener(main, sensorInstanceMag, SensorManager.SENSOR_DELAY_NORMAL);
        } else {
            Log.d("onCreate", "Sensor error!");
        }
    }
}