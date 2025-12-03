### Huawei: 'device disconnected' workaround

If your desktop constantly reports your Huawei device as disconnected, open Syncthing UI of your desktop. Go to 'remote devices', expand the phone's entry, click 'Edit', switch to 'Advanced'. Put your phone IP address into 'Addresses' like this:

```
tcp4://(ipAddress), dynamic
```

Confirmed working for: Huawei P10
