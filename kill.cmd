@echo off
::
taskkill /f /im adb.exe 2>NUL:
taskkill /f /im java.exe 2>NUL:
::
goto :eof
