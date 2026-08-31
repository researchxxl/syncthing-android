@echo off
::
SET STMONITORED=1
start "" "%ProgramFiles%\Syncthing\syncthing.exe" --no-console --no-browser
::
goto :eof
