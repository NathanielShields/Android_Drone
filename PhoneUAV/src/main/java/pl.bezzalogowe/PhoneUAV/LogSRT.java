package pl.bezzalogowe.PhoneUAV;

import android.os.Build;
import android.os.Environment;
import android.util.Log;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.LocalDateTime;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.util.Date;
import java.util.TimeZone;

/* Saves GPS speed to subtitle file, work in progress. */
public class LogSRT {

    MainActivity main;

    String fileName;
    File file;

    StringWriter writer;

    boolean saveSRT = true;

    long videoStartTime;
    long lastTimestamp;

    double lastRecordedSpeed;

    long trackpointNumber;

    public LogSRT(MainActivity argActivity) {
        main = argActivity;
    }

    public void startLog() {
        if (saveSRT) {

            //FIXME: Why is there 1 hour shift, is it because of DST?
            videoStartTime = System.currentTimeMillis();
            trackpointNumber = 0;

            try {
                /** creates subtitle file with the same name as video file */
                fileName = main.camObjectLolipop.videoFilePath;
                fileName = fileName.substring(0, Math.max(fileName.length() - 14, 10)) + ".srt";
                file = new File(fileName);
                Log.d("LogSRT", fileName + " created");

                if (!(file.exists())) {
                    file.createNewFile();
                }

            } catch (FileNotFoundException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }

        /*long opener = System.currentTimeMillis();
          String openerString = new java.text.SimpleDateFormat("HH:mm:ss,SSS").format(opener);
          append(openerString + "\r\n");*/
        }
    }

    public void saveTrackpoint(long time, double heading, double speed) throws IOException {
        if (saveSRT && main.camObjectLolipop.isRecording) {
            if (trackpointNumber > 0) {
                if (Math.abs((int) speed - (int) lastRecordedSpeed) >= 1) {
                    //TODO: move '}' down for less repetitions

                    long currentTimestamp = System.currentTimeMillis() - videoStartTime;

                    String lastTimestampString = new java.text.SimpleDateFormat("HH:mm:ss,SSS").format(lastTimestamp);
                    String currentTimestampString = new java.text.SimpleDateFormat("HH:mm:ss,SSS").format(currentTimestamp);

                    String text = trackpointNumber + "\r\n" +
                            lastTimestampString + " --> " + currentTimestampString + "\r\n" +
                            /* String.format("%.0f", heading) + "° " + */ String.format("%.0f", lastRecordedSpeed * 3.6) + " km/h\r\n";
                    append(text);

                    Log.d("LogSRT", lastTimestampString + " --> " + currentTimestampString);

                    lastTimestamp = currentTimestamp;
                    trackpointNumber++;
                    //TODO: move '}' up for more repetitions
                }
            } else {
                //lastTimestamp = new java.util.Date(System.currentTimeMillis()).getTime() - videoStartTime;

                /** calculates difference between current time and time when video started */
/*
    try {
        long info;

        if (Build.VERSION.SDK_INT >= 24) {
        info = Long.sum(System.currentTimeMillis(), - videoStartTime);
        }
        else
        {info = System.currentTimeMillis() - videoStartTime;}

        String text2 = "difference: " + Long.toString(info) + "\r\n";
        append(text2);
    } catch (Exception e) {
        e.printStackTrace();
        append(e.toString()+ "\r\n");
    }
*/
                //FIXME: why is this workaround needed?
                try {
                    if (Build.VERSION.SDK_INT >= 24) {
                        //append("API>=24\r\n");
                        int val = Long.compare(System.currentTimeMillis(), Long.sum(videoStartTime, 3600000L));
                        if (val < 0) {
                            videoStartTime = Long.sum(videoStartTime, 3600000L);
                        }
                    } else {
                        //append("API<24\r\n");
                        if (System.currentTimeMillis() < videoStartTime + 3600000L) {
                            videoStartTime += 3600000L;
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    append(e.toString() + "\r\n");
                }

                lastTimestamp = System.currentTimeMillis() - videoStartTime;
                trackpointNumber++;
            }
            lastRecordedSpeed = speed;
        }
    }

    public void stopLog(final MainActivity pwmDemoActivity) {
        if (saveSRT) {
            String text = "\n";
            append(text);
            Log.d("LogSRT", fileName + " saved");
        }
    }

    public void append(String string) {
        BufferedWriter bw = null;
        try {
            bw = new BufferedWriter(new FileWriter(file, true));
            bw.write(string);
            bw.newLine();
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