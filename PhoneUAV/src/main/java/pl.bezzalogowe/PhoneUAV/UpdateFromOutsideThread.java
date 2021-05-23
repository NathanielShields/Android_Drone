package pl.bezzalogowe.PhoneUAV;

import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.graphics.Color;
import android.os.Handler;
import android.util.Log;
import android.widget.TextView;

public class UpdateFromOutsideThread implements Runnable {
    /* for updating views from outside of package */
    private final static String TAG = UpdateFromOutsideThread.class.getName();
    public Handler updateConversationHandler;
    private TextView view;
    private String msg;
    private boolean fancy;

    public UpdateFromOutsideThread() {
    }

    public UpdateFromOutsideThread(TextView element, String str, boolean blink) {
        this.view = element;
        this.msg = str;
        this.fancy = blink;
    }

    @Override
    public void run() {
        try {
            view.setText(msg);
            if (fancy) {
                manageBlinkEffect();
            }
            Log.d(TAG, msg);
        } catch (Exception e) {
            e.printStackTrace();
            Log.d(TAG, "Error: " + e);
        }
    }

    /* https://medium.com/@ssaurel/create-a-blink-effect-on-android-3c76b5e0e36b */
    // Ground station sends many messages with the same content, a blink indicates it's a new message.
    private void manageBlinkEffect() {
        ObjectAnimator anim = ObjectAnimator.ofInt(view, "textColor", Color.BLACK, Color.WHITE);
        anim.setDuration(500);
        anim.setEvaluator(new ArgbEvaluator());
        anim.setRepeatMode(ValueAnimator.REVERSE);
        anim.setRepeatCount(1);
        anim.start();
    }
}
