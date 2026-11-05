package at.leoway.spike;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public class AlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        long now = System.currentTimeMillis();
        long planned = AlarmStore.time(c);
        AlarmStore.setFired(c, now);
        AlarmStore.log(c, "FIRED, " + (now - planned) + " ms after planned time");

        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        String ch = "leaveby";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(new NotificationChannel(ch, "Leave-by alarm", NotificationManager.IMPORTANCE_HIGH));
        }
        Notification.Builder b = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(c, ch) : new Notification.Builder(c);
        nm.notify(42, b.setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("Leave now").setContentText("Leave-by alarm (spike)")
                .setCategory(Notification.CATEGORY_ALARM).setAutoCancel(true).build());
    }
}
