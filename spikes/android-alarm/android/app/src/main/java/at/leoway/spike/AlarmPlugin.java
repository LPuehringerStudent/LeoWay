package at.leoway.spike;

import android.Manifest;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationManagerCompat;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

@CapacitorPlugin(name = "LeoAlarm")
public class AlarmPlugin extends Plugin {

    @PluginMethod
    public void getStatus(PluginCall call) {
        JSObject r = new JSObject();
        r.put("sdk", Build.VERSION.SDK_INT);
        r.put("canExact", AlarmScheduler.canExact(getContext()));
        r.put("notificationsOn", NotificationManagerCompat.from(getContext()).areNotificationsEnabled());
        r.put("time", AlarmStore.time(getContext()));
        r.put("version", AlarmStore.version(getContext()));
        r.put("firedAt", AlarmStore.firedAt(getContext()));
        r.put("log", AlarmStore.log(getContext()));
        call.resolve(r);
    }

    /** time = epoch millis (UTC), version = increasing number from the backend, mode = alarmClock | exactIdle */
    @PluginMethod
    public void setAlarm(PluginCall call) {
        long time = call.getDouble("time").longValue();
        int version = call.getInt("version");
        String mode = call.getString("mode", "alarmClock");
        JSObject r = new JSObject();
        r.put("result", AlarmScheduler.set(getContext(), time, version, mode));
        call.resolve(r);
    }

    @PluginMethod
    public void openExactAlarmSettings(PluginCall call) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Intent i = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                    Uri.parse("package:" + getContext().getPackageName()));
            getActivity().startActivity(i);
        }
        call.resolve();
    }

    @PluginMethod
    public void requestNotifications(PluginCall call) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(getActivity(), new String[]{Manifest.permission.POST_NOTIFICATIONS}, 7);
        }
        call.resolve();
    }
}
