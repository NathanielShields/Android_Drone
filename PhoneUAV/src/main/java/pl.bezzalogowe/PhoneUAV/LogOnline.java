package pl.bezzalogowe.PhoneUAV;

import android.os.AsyncTask;
import android.util.Log;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

public class LogOnline extends AsyncTask<String, Void, LogOnline> {
    protected LogOnline doInBackground(String... input) {
        try {
            /** serverpath, "write.php?data=", Latitude, Longitude, Altitude, Heading, Accuracy, Device identifier, Base64, Send time */
            String address = input[0] + "write.php?data=" + input[1] + "," + input[2] + "," + input[3] + "," + input[4] + "," + input[5] + "," + input[6] + "," + input[7] + "," + input[8];
            URL url = new URL(address);
            Log.d("LogOnline-url", url.toString());
            HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();

            try {
                Long timeWeb = urlConnection.getDate();
                String dateISO8601 = new java.text.SimpleDateFormat("yyyy-MM-dd").format(new java.util.Date(timeWeb));
                String timeISO8601 = new java.text.SimpleDateFormat("HH:mm:ss").format(new java.util.Date(timeWeb));
                Log.d("LogOnline", dateISO8601 + "T" + timeISO8601);

            } catch (Exception e) {
                e.printStackTrace();
                Log.d("LogOnline", e.toString());
            } finally {
                urlConnection.disconnect();
            }
        } catch (MalformedURLException e) {
            e.printStackTrace();
            Log.d("LogOnline", "MalformedURLException" + e.toString());
        } catch (IOException e) {
            e.printStackTrace();
            Log.d("LogOnline", "IOException" + e.toString());
        }
        return null;
    }

    protected void onPostExecute(LogOnline feed) {
    }
}