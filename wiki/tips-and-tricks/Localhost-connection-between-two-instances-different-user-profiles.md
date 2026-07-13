## Localhost connection between two instances in different user profiles
Android 17+ restricts apps from connecting to their other instance over localhost when they are running in different user profiles. The affects both actual users and also profiles. For example private space or work profile. For folks who run multiple instances of the app to sync files locally between users, the traffic is now forced to go through Syncthing community relays due to this restriction.

However, the cross user profile communication permission can be manually granted to allow direct connection via the local loopback interface. This requires `adb`.

1. Download the [`interact-across-profiles.sh`](./scripts/interact-across-profiles.sh) script. Use the "Raw" button in the toolbar to get the file as plain text.

2. Push the script to the device:

    ```bash
    adb push interact-across-profiles.sh /tmp/
    ```

3. Grant the permission `INTERACT_ACROSS_USERS`:

    ```bash
    adb shell sh /tmp/interact-across-profiles.sh grant
    ```

    This will grant the permission to every installed copy of the app in any user and restart the app. If the app is later installed in a new user profile, the script needs to be run again.

    To undo the changes and revoke the permission, run:

    ```bash
    adb shell sh /tmp/interact-across-profiles.sh revoke
    ```

To actually set up direct loopback connection between two app instances via localhost:

1. In each instance, go to `Settings -> Syncthing Options -> Listen Address` and set a listen address with a fixed port number, such as `tcp4://:22000`. Each instance needs a different port number.

2. After adding one instance as a remote device to another instance, go to `Devices -> <Device name> -> Click to Edit -> Listen Address` and change the value from `dynamic` to `tcp4://localhost:[port]`. This is necessary because Syncthing does not try to connect via localhost by default.

Sources:
- [Android Developer](https://developer.android.com/about/versions/17/behavior-changes-all#block-cross-profile-loopback)
- Credits to @chenxiaolong for [interact_across_users.sh](https://github.com/chenxiaolong/BasicSync/blob/master/scripts/interact_across_users.sh)
