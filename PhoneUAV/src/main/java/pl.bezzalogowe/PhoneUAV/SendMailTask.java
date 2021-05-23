package pl.bezzalogowe.PhoneUAV;

import android.os.AsyncTask;
import android.util.Log;
import android.widget.Toast;

import java.util.List;

import javax.mail.MessagingException;
import javax.mail.internet.AddressException;

public class SendMailTask extends AsyncTask {

    //private Activity sendMailActivity;

    public SendMailTask() {
        //sendMailActivity = activity;
    }

    @Override
    protected Object doInBackground(Object... args) {
        try {
            Log.i("SendMailTask", "About to instantiate GMail...");
            GMail androidEmail = new GMail(args[0].toString(), (Integer) args[1], args[2].toString(), args[3].toString(), (List) args[4], args[5].toString(), args[6].toString(), (MainActivity) args[7]);
            androidEmail.createEmailMessage();
            androidEmail.sendEmail();
            Log.i("SendMailTask", "Mail Sent");
        } catch (MessagingException a) {
            a.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
            Log.i("SendMailTaskE", e.getMessage());
        }
        return null;
    }
}
