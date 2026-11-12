# Exact-alarm spike: phone test (#15)

Date: 2026-11-12. Phone: Samsung Galaxy A55 5G (SM-A556B), Android 14 (API 34), One UI 6.1, build
`A556BXXS6AYB4`, Battery saver on. App: this spike, `targetSdk 36`, debug build over USB (`adb`).
Times are Vienna time; "late" is the time between the planned time and `AlarmReceiver.onReceive`.

## Results

| Test | What was done | Result |
| --- | --- | --- |
| Push moves the alarm | Alarm v3 at +90 s, push v4 moves it to +150 s, then old pushes v3 and v4 | v3/v4 repeats `ignoredOlderVersion`; fired **once**, at the v4 time, **37 ms** late |
| Permission denied | Fresh install, exact-alarm permission off, then switched on | `needsPermission`, nothing armed; on grant `SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED` re-arms it; fired **29 ms** late |
| Permission revoked while armed | Alarm armed, permission switched off, then on again | Android kills the app and drops the alarm; on re-grant it is re-armed and fired **45 ms** late |
| Doze | Screen off, unplugged (`dumpsys battery unplug`); Battery saver on for quick doze, off for light idle | `setAlarmClock` in quick doze: **68 ms** late. `setExactAndAllowWhileIdle` in light idle: **71 ms** late |
| Reboot, first version | Alarm armed, reboot, phone left locked | **Missed.** `BOOT_COMPLETED` only arrives after the first unlock; the alarm time had passed by then |
| Reboot, fixed version | Same, with the fixes below | Re-armed while still locked (`LOCKED_BOOT_COMPLETED`, 28 s after boot), fired **292 ms** late; phone first unlocked at 11:50, 46 min after the alarm |

## Found and fixed in the spike

1. **`setAlarm` rejected every whole-millisecond time.** Epoch millis arrive as a JSON long; `PluginCall.getDouble()`
   returns `null` for it. Now read as any `Number` (`AlarmPlugin`).
2. **No re-arm before the first unlock (Direct Boot).** Receivers are `directBootAware`, the alarm is kept in
   device-protected storage, and `BootReceiver` also handles `LOCKED_BOOT_COMPLETED`.
3. **The exact-alarm permission was gone after a reboot.** `SCHEDULE_EXACT_ALARM`, switched on in "Alarms &
   reminders", read `default` / `canScheduleExactAlarms() = false` after the restart on this phone. On API 33+ the app
   now uses `USE_EXACT_ALARM`, which is granted at install and cannot be revoked. `SCHEDULE_EXACT_ALARM` stays for
   Android 12 (`maxSdkVersion 32`).

## Not tested

- **Deep Doze.** The phone would not enter deep `IDLE`: `force-idle` stops at `QUICK_DOZE_DELAY` with Battery saver
  on, and at `INACTIVE` without it (motion sensor active). `setAlarmClock` is exempt from Doze by design; quick and
  light doze added no delay.
- Android 10/11 (no permission needed there) and other manufacturers (Xiaomi, Huawei kill apps harder).

## For the real alarm (#64)

- Use `setAlarmClock` (shows the alarm icon, exempt from Doze); `setExactAndAllowWhileIdle` gives no advantage.
- Declare `USE_EXACT_ALARM` on API 33+. Google Play allows it only for apps whose core function is an alarm or
  timer, so the store listing must say so; fall back to `SCHEDULE_EXACT_ALARM` with an explanation screen if Play
  rejects it.
- Mark the alarm receivers `directBootAware` and keep the next alarm in device-protected storage. Samsung's
  "Auto restart" in Device care reboots phones at night by default.
- A notification is not enough: on this phone the lock screen shows no notifications. The alarm needs sound or
  vibration and a full-screen intent (`USE_FULL_SCREEN_INTENT`), like a clock app.
- If an alarm was missed while the phone was off, tell the student when the app opens ("missed while off" is logged).
