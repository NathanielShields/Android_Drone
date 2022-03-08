package pl.bezzalogowe.PhoneUAV;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.telephony.TelephonyManager;
import android.text.format.Time;
import android.util.Base64;
import android.util.Log;

import java.nio.ByteBuffer;

import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Arrays;
import java.util.LinkedList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

public class Location extends Thread {
    private static final String TAG = "location";
    MainActivity main;
    LocationManager locationManager;
    android.location.Location recentLocation;
    android.location.Location waypointNext;
    LinkedList<Waypoint> route = new LinkedList<Waypoint>();
    String deviceIdentifier = null;

    double[] altitudesample = new double[10];
    /* estimated takeoff altitude calculated from 10 first locks */
    double takeoff_altitude;

    public Location(MainActivity argActivity) {
        main = argActivity;
    }

    /* converts one double to an array of 8 bytes */
    public static final byte[] double2Bytes(double inData) {
        long bits = Double.doubleToLongBits(inData);
        byte[] buffer = {(byte) (bits & 0xff),
                (byte) ((bits >> 8) & 0xff),
                (byte) ((bits >> 16) & 0xff),
                (byte) ((bits >> 24) & 0xff),
                (byte) ((bits >> 32) & 0xff),
                (byte) ((bits >> 40) & 0xff),
                (byte) ((bits >> 48) & 0xff),
                (byte) ((bits >> 56) & 0xff)};
        return buffer;
    }

    /* converts an array of doubles to an array of bytes */
    public static final byte[] doubleArray2Bytes(double[] inArray) {
        int j = 0;
        int length = inArray.length;
        byte[] out = new byte[length * Double.BYTES];
        for (int i = 0; i < length; i++) {
            long bits = Double.doubleToLongBits(inArray[i]);
            out[j++] = (byte) (bits & 0xff);
            out[j++] = (byte) ((bits >> 8) & 0xff);
            out[j++] = (byte) ((bits >> 16) & 0xff);
            out[j++] = (byte) ((bits >> 24) & 0xff);
            out[j++] = (byte) ((bits >> 32) & 0xff);
            out[j++] = (byte) ((bits >> 40) & 0xff);
            out[j++] = (byte) ((bits >> 48) & 0xff);
            out[j++] = (byte) ((bits >> 56) & 0xff);
        }
        return out;
    }

    /* converts an array of bytes to a double */
    public static final double bytesArray2Double(byte[] input) {
        System.out.println(Arrays.toString(input) + "\n");
        byte[] reverse = new byte[]{input[7], input[6], input[5], input[4], input[3], input[2], input[1], input[0]};
        return ByteBuffer.wrap(reverse).getDouble();
    }

    public void processLocation(MainActivity argActivity, android.location.Location location) {
        recentLocation = location;

        Log.d("location", "Lat: " + recentLocation.getLatitude() + " Lon: " + recentLocation.getLongitude() + " alt: " + recentLocation.getAltitude() + " hdg: " + argActivity.magObject.heading + " acc: " + recentLocation.getAccuracy());

/*
        8 x 3 = 24
        8 x 4 = 32
*/

/*
        byte[] temp = doubleArray2Bytes(data);
        String tempString = "";
        for (short i = 0; i < temp.length; i++)
        {
            tempString += "["+temp[i]+"]";
            if (i!=temp.length-1)
            {tempString += " ";}
        }
        System.out.println(tempString);
*/
        double[] data = {recentLocation.getLatitude(), recentLocation.getLongitude(), recentLocation.getAltitude()};
        String base64string = android.util.Base64.encodeToString(doubleArray2Bytes(data), Base64.URL_SAFE | Base64.NO_WRAP);
        Log.d("location", "base64: " + base64string);

        if (main.logGPX.trackpointNumber <= 10) {
            /* 0 through 9 */
            int pos = ((int) main.logGPX.trackpointNumber) - 1;

            altitudesample[pos] = location.getAltitude();
            double sum = 0;
            for (int i = 0; i <= pos; i++) {
                sum += altitudesample[pos];
            }
            takeoff_altitude = sum / main.logGPX.trackpointNumber;

            main.pressureObject.pressure_mean_sealevel = Barometer.getSealevelPressure((float) /*location.getAltitude()*/ takeoff_altitude, main.pressureObject.pressureBarometricRecent);
            Log.d("location", "average GPS altitude: " + takeoff_altitude +
                    "; number of samples: " + main.logGPX.trackpointNumber +
                    "; barometric pressure: " + main.pressureObject.pressureBarometricRecent +
                    "; sea level pressure: " + main.pressureObject.pressure_mean_sealevel);
        }

        if (main.pressureObject.barometer == null) {
            /* if barometer is not available */
            main.mavLink.sendGlobalPosition(recentLocation.getLatitude(), recentLocation.getLongitude(), recentLocation.getAltitude(), recentLocation.getAltitude() - takeoff_altitude);
        } else {
            /* if there is a barometer available */
            main.mavLink.sendGlobalPosition(recentLocation.getLatitude(), recentLocation.getLongitude(), main.pressureObject.altitudeBarometricRecent, main.pressureObject.altitudeBarometricRecent - takeoff_altitude);
        }

        /* saves location online */
        Time timeLocal = new Time();
        timeLocal.setToNow();

        try {
            String[] parameters = {main.serverpath,
                    String.valueOf(recentLocation.getLatitude()),
                    String.valueOf(recentLocation.getLongitude()),
                    String.valueOf(recentLocation.getAltitude()),
                    String.valueOf(main.magObject.heading),
                    String.format("%.00f", recentLocation.getAccuracy()),
                    deviceIdentifier,
                    base64string,
                    timeLocal.format("%Y-%m-%dT%H:%M:%S")
            };
            new LogOnline().execute(parameters);
        } catch (Exception e) {
            e.printStackTrace();
            Log.d(TAG, e.toString());
        }

        /* saves location to a GPX file */
        try {
            main.logGPX.saveTrackpoint(
                    recentLocation.getLatitude(),
                    recentLocation.getLongitude(),
                    recentLocation.getAltitude(),
                    main.magObject.heading,
                    recentLocation.getSpeed(),
                    recentLocation.getAccuracy(), recentLocation.getTime());
        } catch (IOException e) {
            Log.d(TAG, e.toString());
            e.printStackTrace();
        }

        /* saves data to a SRT (SubRip) file */
        try {
            main.logSubRip.saveTrackpoint(
                    recentLocation.getTime(),
                    main.magObject.heading,
                    recentLocation.getSpeed()
            );
        } catch (IOException e) {
            Log.d(TAG, e.toString());
            e.printStackTrace();
        }

        /* sends location over UDP (must be done in a separate thread) */
        Thread feedbackLocation = new Thread(new Wrap());
        feedbackLocation.start();

        if (waypointNext != null) {
            float distance = recentLocation.distanceTo(waypointNext);
            main.update.updateConversationHandler.post(new updateTextThread(main.text_server, String.format("%.02f", distance) + "m No: " + main.logGPX.trackpointNumber));

            //TODO: radius should be taken from the waypoint
            if (distance < 64) {
                nextWaypoint();
            }
        }
    }

    public void startLocation() {
        locationManager = (LocationManager) main.getSystemService(Context.LOCATION_SERVICE);
        TelephonyManager telemamanger = (TelephonyManager) main.getSystemService(main.TELEPHONY_SERVICE);
        /*
        try {
            deviceIdentifier = telemamanger.getLine1Number();
        } catch (Exception e) {
            e.printStackTrace();
        }
        */
        try {
            deviceIdentifier = telemamanger.getDeviceId();
        } catch (SecurityException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (deviceIdentifier == null)
            deviceIdentifier = "N/A";

        Log.d("location", "started");

        LocationListener locationListener = new LocationListener() {
            @Override
            public void onLocationChanged(android.location.Location location) {
                processLocation(main, location);
            }

            public void onStatusChanged(String provider, int status, Bundle extras) {
            }

            public void onProviderEnabled(String provider) {
            }

            public void onProviderDisabled(String provider) {
            }
        };

        if (ContextCompat.checkSelfPermission(main, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 1, locationListener);
        }
    }

    public void printFlightpath() {
        try {
            Log.d(TAG, "waypoints read: " + route.size());
            for (Waypoint item : route) {
                Log.d(TAG, item.lat + "\u00B0 " + item.lon + "\u00B0 " + item.ele + "m");
            }
        } catch (Exception e) {
            Log.d(TAG, e.toString());
        }
    }

    /* allows for clearing the route from MAVLink class */
    public void flushWaypoints() {
        route.clear();
    }

    public void addWaypoint(double lat, double lon, double ele, int frame) {
        /* latitude (szerokość), longitude (długość), elevation, frame of reference */
        if (frame == 0) {
            /* https://mavlink.io/en/messages/common.html#MAV_FRAME_GLOBAL */
            route.add(new Waypoint(lat, lon, ele));
        } else if (frame == 3) {
            /* https://mavlink.io/en/messages/common.html#MAV_FRAME_GLOBAL_RELATIVE_ALT */
            route.add(new Waypoint(lat, lon, ele + takeoff_altitude));
        }
        Log.d("location","Waypoint added: " + lat + "°, " + lon + "°, " + ele + " m (" + route.size() + " waypoints now)");
    }

    public int nextWaypoint() {
        int size = route.size();
        try {
            main.update.updateConversationHandler.post(new updateTextThread(main.text_server, "there are " + size + " waypoints left"));
            Waypoint read = route.remove();
            waypointNext.setLatitude(read.lat);
            waypointNext.setLongitude(read.lon);
            waypointNext.setAltitude(read.ele);
            Log.d(TAG, "Waypoint " + read.lat + ", " + read.lon + ", " + read.ele + " polled/removed, " + route.size() + " waypoints left");
        } catch (java.util.NoSuchElementException e) {
            e.printStackTrace();
            main.update.updateConversationHandler.post(new updateTextThread(main.text_server, "no more waypoints left"));
            Log.d(TAG, "no more waypoints");
        }
/*
        if (route.size() == 0)
            {}
*/
        return size;
    }

    class Waypoint {
        public double lat;
        public double lon;
        public double ele;

        /* constructor with latitude and longitude */
        public Waypoint(double argLat, double argLon) {
            super();
            this.lat = argLat;
            this.lon = argLon;
        }

        /* constructor with latitude, longitude and elevation */
        public Waypoint(double argLat, double argLon, double argElevation) {
            super();
            this.lat = argLat;
            this.lon = argLon;
            this.ele = argElevation;
        }
    }

    /* not used */
    class ReadXMLfromURL extends Thread {
        @Override
        public void run() {
            synchronized (this) {
                try {
                    URL url = new URL(main.serverpath + "route.gpx");
                    URLConnection conn = url.openConnection();

                    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                    DocumentBuilder builder = factory.newDocumentBuilder();
                    Document doc = builder.parse(conn.getInputStream());

                    // try reading route points
                    NodeList nodes = doc.getElementsByTagName("rtept");
                    // if the file doesn't contain route points, try reading track points
                    if (nodes.getLength() == 0)
                        nodes = doc.getElementsByTagName("trkpt");

                    for (int i = 0; i < nodes.getLength(); i++) {
                        Element element = (Element) nodes.item(i);

                        String parameter1value = element.getAttribute("lat");
                        String parameter2value = element.getAttribute("lon");

                        NodeList tag1 = element.getElementsByTagName("ele");
                        Element tag1content = (Element) tag1.item(0);
                        String tag1string = tag1content.getTextContent().toString();

                        route.add(new Waypoint(Double.parseDouble(parameter1value), Double.parseDouble(parameter2value), Double.parseDouble(tag1string)));
                    }
                } catch (NumberFormatException e) {
                    Log.d(TAG, "NumberFormatException");
                    e.printStackTrace();
                } catch (MalformedURLException e) {
                    Log.d(TAG, "MalformedURLException");
                    e.printStackTrace();
                } catch (DOMException e) {
                    Log.d(TAG, "DOMException");
                    e.printStackTrace();
                } catch (IOException e) {
                    Log.d(TAG, "IOException");
                    e.printStackTrace();
                } catch (ParserConfigurationException e) {
                    Log.d(TAG, "ParserConfigurationException");
                    e.printStackTrace();
                } catch (SAXException e) {
                    Log.d(TAG, "SAXException");
                    e.printStackTrace();
                }
                notify();
            }
        }
    }

    class Wrap implements Runnable {
        @Override
        public void run() {
            double[] coordinates = {recentLocation.getLatitude(), recentLocation.getLongitude()};
            try {
                main.sendTelemetry(2, coordinates);
            } catch (Exception e) {
                Log.d("Barometer", "error: " + e);
                main.logGPX.saveComment("error: " + e.toString());
            }
        }
    }
}
