package at.leoway.spike;

import android.content.Context;
import android.content.SharedPreferences;

/** Persists the current alarm so it can be restored after reboot (alarms do not survive one). */
final class AlarmStore {
    private static SharedPreferences p(Context c) {
        return c.getSharedPreferences("leoalarm", Context.MODE_PRIVATE);
    }
    static long time(Context c) { return p(c).getLong("time", 0); }
    static int version(Context c) { return p(c).getInt("version", 0); }
    static String mode(Context c) { return p(c).getString("mode", "alarmClock"); }
    static long firedAt(Context c) { return p(c).getLong("firedAt", 0); }
    static String log(Context c) { return p(c).getString("log", ""); }

    static void save(Context c, long time, int version, String mode) {
        p(c).edit().putLong("time", time).putInt("version", version).putString("mode", mode).apply();
    }
    static void setFired(Context c, long at) { p(c).edit().putLong("firedAt", at).apply(); }

    /** Keeps the last 20 events; shown in the test page so reboot/Doze results are visible. */
    static void log(Context c, String line) {
        String[] old = log(c).isEmpty() ? new String[0] : log(c).split("\n");
        StringBuilder sb = new StringBuilder();
        int from = Math.max(0, old.length - 19);
        for (int i = from; i < old.length; i++) sb.append(old[i]).append('\n');
        sb.append(System.currentTimeMillis()).append(' ').append(line);
        p(c).edit().putString("log", sb.toString()).apply();
        android.util.Log.i("LeoAlarm", line);
    }
}
