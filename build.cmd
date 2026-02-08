@echo off
setlocal enabledelayedexpansion
::
:: Example
:: 	call build && echo OK
::
IF /I "%1" == "clean" SET PARAM_NO_BUILD_CACHE=--no-build-cache
IF /I "%1" == "oneshot" SET PARAM_NO_BUILD_CACHE=--no-daemon
::
call gradlew %PARAM_NO_BUILD_CACHE% --warning-mode all %* assembledebug
SET GRADLEW_ERRORLEVEL=%ERRORLEVEL%
::
IF "%GRADLEW_ERRORLEVEL%" == "0" call "%ANDROID_HOME%\build-tools\36.1.0\apksigner.bat" sign --ks "%ANDROID_USER_HOME%\debug.keystore" --ks-key-alias androiddebugkey --ks-pass pass:android --key-pass pass:android --out "app\build\outputs\apk\debug\app-debug.apk" "app\build\outputs\apk\debug\app-debug-unsigned.apk"
::
call scripts\debug\win\hide-folders-from-notepad++.cmd
::
endlocal & (
	where update.cmd >NUL: 2>&1 || SET "PATH=%PATH%;%~dp0scripts\debug\win"
	::
	exit /b %GRADLEW_ERRORLEVEL%
)
