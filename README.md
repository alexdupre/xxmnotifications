# xxm notifications

A simple Android app to enable notifications on messages received by the Elixir `xx messenger` app.

## Permissions

The app doesn't require any permission, except for the ability to read system logs.
To grant such privilege, run the following command after installing the app on the device:

```
adb shell pm grant com.alexdupre.xxmnotifications android.permission.READ_LOGS
```