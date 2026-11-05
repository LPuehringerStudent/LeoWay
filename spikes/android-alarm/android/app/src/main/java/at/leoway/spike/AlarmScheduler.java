package at.leoway.spike;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

final class AlarmScheduler {
    static final String OK = "scheduled", OLDER = "ignoredOlderVersion", NEEDS_PERMISSION = "needsPermission";

    static boolean canExact(Context c) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true; // Android 10/11: no permission
        return ((AlarmManager) c.getSystemService(Context.ALARM_SERVICE)).canScheduleExactAlarms();
    }

    /** Spec 5.2: ignore pushes with an older version; a newer one replaces the old alarm. */
    static String set(Context c, long time, int version, String mode) {
        if (version <= AlarmStore.version(c)) {
            AlarmStore.log(c, "ignored push v" + version + " (have v" + AlarmStore.version(c) + ")");
            return OLDER;
        }
        AlarmStore.save(c, time, version, mode);   // store even without permission, to retry later
        return apply(c, time, version, mode);
    }

    /** Used after reboot / permission change: re-arm what is stored. */
    static String reschedule(Context c) {
        long t = AlarmStore.time(c);
        if (t == 0) return "nothingStored";
        if (t < System.currentTimeMillis()) {
            AlarmStore.log(c, "stored alarm already in the past (missed while off)");
            return "missed";
        }
        return apply(c, t, AlarmStore.version(c), AlarmStore.mode(c));
    }

    private static String apply(Context c, long time, int version, String mode) {
        if (!canExact(c)) {
            AlarmStore.log(c, "no exact-alarm permission, v" + version + " not armed");
            return NEEDS_PERMISSION;
        }
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        PendingIntent fire = PendingIntent.getBroadcast(c, 1,
                new Intent(c, AlarmReceiver.class), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        am.cancel(fire);   // same PendingIntent = the old alarm is replaced
        try {
            if ("exactIdle".equals(mode)) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, fire);
            } else {
                PendingIntent show = PendingIntent.getActivity(c, 2,
                        new Intent(c, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
                am.setAlarmClock(new AlarmManager.AlarmClockInfo(time, show), fire);
            }
        } catch (SecurityException e) {
            AlarmStore.log(c, "SecurityException: " + e.getMessage());
            return NEEDS_PERMISSION;
        }
        AlarmStore.log(c, "armed v" + version + " mode=" + mode + " for " + time);
        return OK;
    }
}
