### TL;DR
Android 11 is the first version since 4.4 that supports writing to external SD card again using Syncthing. That's why the app requests the "access all files and manage external storage" permission on first usage.

### Why SD card use together with Syncthing requires special attention
According to Google's document:

https://source.android.com/devices/storage/traditional.html

It is true if you did not format the external SD card as adoptable storage but left it as portable. Doing this prevents any native linux app from writing to this card freely. The directories on the SD card that an app can write to are restricted to the following places, for example:

* /storage/[ABCD-EFGH]/Android/data/${applicationId}/files
* /storage/[ABCD-EFGH]/Android/media/${applicationId}

In Syncthing-Fork, when you are prompted for the directory, you are automatically taken to a writable directory by the built-in directory chooser to help finding out these writable directories with ease. Just create your folder you wish to be used by Syncthing below the suggested directory that opens up on the UI.

To sum up:
* You CAN write to your internal storage and external SD card using Syncthing-Fork.
* Adoptable Storage counts as "internal" storage.
* You can NOT decide freely where Syncthing-Fork has write access due to the above mentioned Android restriction if the SD card was formatted as PORTABLE.

To ease your technical understanding of the issue and prevent the opening of tickets caused by misunderstandings:
* Syncthing-Fork consists of two components:
1. "libsyncthingnative.so" - A cross-compiled linux binary which we often call "SyncthingNative". It contains all synchronization functionality and carries out everything required to read, transfer and store files.
2. The wrapper which represents the UI. It mainly controls the start and stop of the SyncthingNative binary to save your battery and monitor health of the syncing process.

Android forces SD card access to be read only upon SyncthingNative outside the above mentioned directory paths. People often asked on the issue tracker and forum why the Syncthing-Fork app refrains from asking for SD card directory tree permission using the WRITE_EXTERNAL_STORAGE permission. It might disappoint you, but this was already tried. If the Java app (2.) asks for permission and the user grants it, Android still forces the SD card paths to be accessed read-only by SyncthingNative (1.). This is why we don't ask the user for a directory tree permission like for example file manager 3rd party apps would have done it. File manager apps are technically different from Syncthing because they run as Java code and not as Native code.

Implementing Syncthing as Java code instead of using the Native would involve a LOT of work, and so far until the time of writing this, no one stepped up to do it yet. If you want to help with this and are familiar with Go, Java and Android, read through the discussion in issues [#29](https://github.com/syncthing/syncthing-android/issues/29) and [#1008](https://github.com/syncthing/syncthing-android/issues/1008). They outline the steps to solve this issue pretty good. Short summary: SynchtingNative would have to be transformed into a gomobile library with goreverse bindings to be called from the wrapper's java code.

### Write to external sdcard workaround
If you see the UI stating that `Android only grants syncthing read-only access` to a certain folder, create a new folder using this path to gain write access:

* /storage/[ABCD-EFGH]/Android/media/${applicationId}/YOUR_FOLDER_NAME

Be careful: This folder will get wiped out by Android if you uninstall this app. Before uninstalling the app, move the data to a safe place outside the `/storage/[ABCD-EFGH]/Android` folder.
