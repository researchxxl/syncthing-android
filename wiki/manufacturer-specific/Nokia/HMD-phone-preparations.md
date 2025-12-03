HMD Global phones have been reported to kill apps with a manufacturer-specific implementation called "DuraSpeed". DuraSpeed is built-into the OS and normally cannot be disabled by the user. This causes Syncthing-Fork to silently cease syncing when DuraSpeed kills the app.

To work around this, advanced users may attempt to disable DuraSpeed system-wide for all apps. This can be achieved by using third party apps that offer the ability to write secure settings.

Java code example:
```
Settings.Global.putInt(context.getContentResolver(), "setting.duraspeed.enabled", -1);
Settings.Global.putInt(context.getContentResolver(), "setting.duraspeed.enabled", 0);
```

Related:
- [Details from the urbandroid-team tracker](https://github.com/urbandroid-team/dont-kill-my-app/issues/57)
