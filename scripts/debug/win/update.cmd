@echo off
setlocal enabledelayedexpansion
::
SET "SCRIPT_PATH=%~dp0"
SET "PROJECT_ROOT=%SCRIPT_PATH%..\..\.."
SET /P PACKAGE_NAME=< "%PROJECT_ROOT%\scripts\debug\package_id.txt"
SET "APK_FULLFN=%PROJECT_ROOT%\app\build\outputs\apk\debug\app-debug.apk"
::
dir "%APK_FULLFN%"
IF NOT EXIST "%APK_FULLFN%" pause
adb install -r "%APK_FULLFN%"
::
adb shell dumpsys deviceidle whitelist +%PACKAGE_NAME%
adb shell pm grant "%PACKAGE_NAME%" android.permission.ACCESS_BACKGROUND_LOCATION
adb shell pm grant "%PACKAGE_NAME%" android.permission.ACCESS_COARSE_LOCATION
adb shell pm grant "%PACKAGE_NAME%" android.permission.ACCESS_FINE_LOCATION
adb shell pm grant "%PACKAGE_NAME%" android.permission.CAMERA
adb shell pm grant "%PACKAGE_NAME%" android.permission.WRITE_EXTERNAL_STORAGE
::
goto :eof
