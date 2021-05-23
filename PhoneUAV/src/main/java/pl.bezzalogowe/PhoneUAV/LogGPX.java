package pl.bezzalogowe.PhoneUAV;

import android.os.Build;
import android.os.Environment;
import android.util.Log;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.sql.Timestamp;
import java.time.LocalDateTime;

public class LogGPX {

    MainActivity main;

    String fileName;
    File file;
    StringWriter writer;
    long trackpointNumber;

    public LogGPX(MainActivity argActivity) {
        main = argActivity;
    }

    public void startLog() {
        fileName = (new java.text.SimpleDateFormat("yyyy-MM-dd").format(new java.util.Date())) + "_" + (new java.text.SimpleDateFormat("HH-mm-ss").format(new java.util.Date())) + ".gpx";

        trackpointNumber = 1;
        try {
            file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM) + "/PhoneUAVmpeg4/" + fileName);
            //file = new File(main.camObjectLolipop.videoFolder.getAbsolutePath() + fileName);

            if (!(file.exists())) {
                file.createNewFile();
            }
            String text = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                    "<gpx version=\"1.0\">\n" +
                    "<trk><name>track</name><number>1</number>" + "<trkseg>\n";
            append(text);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void saveTrackpoint(double lat, double lon, double alt, double heading, double speed, float acc, long time) throws IOException {
        /** A Track Point holds the coordinates, elevation, timestamp, and metadata for a single point in a track. */
        String dateISO8601 = new java.text.SimpleDateFormat("yyyy-MM-dd").format(new java.util.Date(time));
        String timeISO8601 = new java.text.SimpleDateFormat("HH:mm:ss").format(new java.util.Date(time));
        trackpointNumber++;

        String text = "<trkpt lat=\"" + Double.toString(lat) + "\" lon=\"" + Double.toString(lon) + "\">" +
                "<ele>" + Double.toString(alt) + "</ele>" +
                "<heading>" + Double.toString(heading) + "</heading>" +
                "<speed>" + Double.toString(speed * 3.6) + "</speed>" +
                "<prec>" + Float.toString(acc) + "</prec>" +
                "<time>" + dateISO8601 + "T" + timeISO8601 + "</time></trkpt>\n";

        append(text);
        main.update.updateConversationHandler.post(new updateToastThread(main, "GPS lock"));
    }

    public void saveNode(String value) {
        append(value);
    }

    public void saveWPT(String lat, String lon, String value) {
        String text;

        if (lat != "0" && lon != "0")
            text = "<wpt lat=\"" + lat + "\" lon=\"" + lon + "\"><name>" + value + "</name></wpt>\n";
        else text = "<wpt><name>" + value + "</name></wpt>\n";
        append(text);
    }

    public void saveComment(String value) {
        String text = "<!-- " + value + " -->\n";
        append(text);
    }

    public void stopLog() {
        String text = "</trkseg>" + "</trk>\n</gpx>";
        append(text);
        Log.d("LogGPX", fileName + " saved");
    }

    public void append(String string) {
        BufferedWriter bw = null;
        try {
            bw = new BufferedWriter(new FileWriter(file, true));
            bw.write(string);
            bw.flush();
        } catch (IOException ioe) {
            ioe.printStackTrace();
        } finally {
            if (bw != null)
                try {
                    bw.close();
                } catch (IOException ioe2) {
                }
        }
    }
}
