@echo off
setlocal enabledelayedexpansion
::
:: Consts.
IF NOT DEFINED EMULATOR_NAME SET "EMULATOR_NAME=Android 16"
SET "EMULATOR_NAME=%EMULATOR_NAME: =_%"
::
where emulator.exe >NUL: 2>&1 || SET "PATH=%PATH%;%ANDROID_HOME%\emulator"
IF NOT DEFINED ANDROID_AVD_HOME SET "ANDROID_AVD_HOME=%ANDROID_USER_HOME%\AVD"
::
emulator -list-avds | findstr /i "%EMULATOR_NAME%"
start "" conhost --headless emulator -avd "%EMULATOR_NAME%"
::
goto :eof
