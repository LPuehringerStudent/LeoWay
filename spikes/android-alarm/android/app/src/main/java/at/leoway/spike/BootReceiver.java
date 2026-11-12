package at.leoway.spike;

import android.app.AlarmManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Re-arms the alarm after reboot, app update, and when the exact-alarm permission is granted again. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        String a = i.getAction();
        AlarmStore.log(c, "received " + a);
        // LOCKED_BOOT_COMPLETED arrives right after boot; BOOT_COMPLETED only after the first unlock.
        if (Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(a)
                || Intent.ACTION_BOOT_COMPLETED.equals(a)
                || Intent.ACTION_MY_PACKAGE_REPLACED.equals(a)
                || AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED.equals(a)) {
            AlarmStore.log(c, "reschedule -> " + AlarmScheduler.reschedule(c));
        }
    }
}
