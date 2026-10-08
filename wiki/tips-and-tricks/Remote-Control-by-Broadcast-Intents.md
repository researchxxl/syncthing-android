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

With "Service Control by Broadcast" enabled, `${applicationId}.action.STATE_CHANGED`
reports two string extras:

* `mode`: `FOLLOW`, `FORCE_START` or `FORCE_STOP` (the user's control setting).
* `run_state`: `STARTING`, `RUNNING`, `STOPPED` or `ERROR` (the runtime state).

`STATE_CHANGED` is sent when either value changes. `REQUEST_STATE` always sends a
fresh response, even if nothing changed, without starting Syncthing or changing
the control mode. Register a receiver before requesting the state.

The mode is not an acknowledgement that a command has finished. After `STOP`,
wait for `run_state=STOPPED`, which is published after the native process thread
has exited. The Android service can remain alive to monitor run conditions.
During a restart, `run_state` is `STARTING`. Neither `RUNNING` nor `STOPPED`
indicates that synchronization is complete.

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
