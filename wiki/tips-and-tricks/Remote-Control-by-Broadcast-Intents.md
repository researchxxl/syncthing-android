**Important Notice**
Remote control by broadcast intents has to be enabled under "Settings" > "Behaviour" before Syncthing listens to broadcast intents sent by third-party automation apps.

Syncthing can be controlled externally by sending Broadcast-Intents. Applications like **Tasker**, **Llama** or **Automate** now can _start_ or _stop_ Syncthing on behalf of the user.
Use cases would be to run Syncthing only in special conditions - like at home and charging, or once every night, ...

${applicationId} = com.github.catfriend1.syncthingfork

The following intent actions are available:
* Let Syncthing Follow Run Conditions
`adb shell am broadcast -a ${applicationId}.action.FOLLOW -p ${applicationId}`

* Force Start Syncthing
`adb shell am broadcast -a ${applicationId}.action.START -p ${applicationId}`

* Force Stop Syncthing
`adb shell am broadcast -a ${applicationId}.action.STOP -p ${applicationId}`

* Request current service state
`adb shell am broadcast -a ${applicationId}.action.REQUEST_STATE -p ${applicationId}`

When remote control by broadcast is enabled, Syncthing broadcasts its current control mode and runtime state using:

`${applicationId}.action.STATE_CHANGED`

The broadcast contains the following string extras:

* `mode`: `FOLLOW`, `FORCE_START` or `FORCE_STOP`
* `run_state`: `STARTING`, `RUNNING`, `STOPPED` or `ERROR`

It also contains raw integer synchronization counters derived from the state already tracked by the Android wrapper:

* `folders_idle_count` - folders currently idle/up to date
* `folders_scanning_count` - folders currently scanning
* `folders_syncing_count` - folders currently synchronizing
* `folders_cleaning_count` - folders currently cleaning versions
* `folders_errored_count` - folders with an error or failed items
* `folders_starting_count` - folders waiting/preparing to scan, sync or clean
* `devices_connected_count` - connected, non-paused remote devices
* `devices_syncing_count` - connected remote devices with remaining sync work
* `devices_pending_count` - disconnected remote devices with remaining sync work

These counters are intentionally raw observations. Syncthing-Fork does not turn them into a definitive `SYNCED` state; receiving applications can apply their own completion policy and stability/timeout rules.

After startup or configuration changes, folder/device state is kept conservative until the wrapper has received real state and completion information from Syncthing. Cached default values are not treated as proof that synchronization has completed.

`STATE_CHANGED` is sent when the control mode, runtime state or any synchronization counter changes. Sending `REQUEST_STATE` broadcasts the current state and counters even if they have not changed.

The intents should be set to 'broadcast' rather than starting an activity of service. Note that some apps, e.g. **Llama**, are sensitive to trailing spaces so be careful not to leave any when entering the action.

Tasker example action to start Syncthing:
* Action: Send Intent
```
Action: ${applicationId}.action.START
Type: None
Mime type: [ leave empty ]
Data: [ leave empty ]
Extra: [ leave empty ]
Package: ${applicationId}
Class: [ leave empty ]
Target: Broadcast Receiver
Description: Start Syncthing
```

For the **Automate** app there is an example-flow available in the Automate-Community that demonstrates the start- and the stop-intent. Search for *Syncthing*.
