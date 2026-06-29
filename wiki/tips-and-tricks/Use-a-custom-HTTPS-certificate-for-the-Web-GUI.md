### Use your own HTTPS certificate for the Web GUI

By default, Syncthing creates its own **self-signed** certificate for the local Web GUI / API, and
the app trusts exactly that certificate. If you'd rather use a certificate signed by your own
Certificate Authority (CA) — for example one issued by a private CA such as
[Smallstep](https://smallstep.com/), an internal company CA, or a public CA — you can replace the
built-in certificate and the app will still be able to talk to Syncthing.

This is an advanced, optional setup. The default self-signed certificate is perfectly secure for
local use, so only do this if you specifically need a CA-signed certificate.

---

### Before you start

You will need:

1. **A certificate and its private key** for the Syncthing Web GUI, in PEM format.
2. **The full certificate chain** in the certificate file. The file must contain your server
   (leaf) certificate **followed by any intermediate certificate(s)**, in order. If you only put
   the leaf certificate in the file, the app (and browsers) can't link it back to your root CA and
   the connection will be rejected.
3. **Your root CA installed and trusted on the phone.** Install the root CA under
   *Android Settings → Security → Encryption & credentials → Install a certificate → CA certificate*
   (the exact path varies by device). After installing, it appears under your phone's
   **user-trusted** certificates. The app trusts both the built-in system CAs and the CAs **you**
   install here.

> The hostname in the certificate does **not** matter. The app always connects to your local
> Syncthing on the loopback address (`127.0.0.1`), and it does not require the certificate to be
> issued for `127.0.0.1`. (If you also use Syncthing's "Remote access" feature from other devices,
> add the relevant hostnames/IPs to the certificate's SAN for those devices — that is independent
> of the app on the phone.)

---

### Method A — in the app (recommended)

The app has a built-in screen that validates your certificate before applying it and restarts
Syncthing for you, rolling back automatically if anything goes wrong.

1. Install your **root CA** on the phone if you haven't already (see *Before you start*, step 3).
2. In the app, go to **Settings → Syncthing Options → Web GUI → Custom HTTPS certificate**.
3. Tap **Select certificate (.pem)** and choose your certificate file (leaf + full chain), then
   **Select private key (.pem)** and choose the matching key. (If you pick them in the wrong order,
   the app sorts it out automatically.)
4. Review the validation checklist:
   - **Certificate chain** — is the full chain present?
   - **Trusted by this device** — will the app accept it? (self-signed, or chains to a CA your
     device trusts)
   - **Validity period** — not expired / not yet valid.
   - **Private key** — does the key match the certificate?
5. Tap **Apply certificate**. Syncthing restarts with the new certificate. If it doesn't come back
   online, the previous certificate is restored automatically and you'll see an error.

To go back to the default, use **Reset to auto-generated certificate** on the same screen — Syncthing
creates a fresh self-signed certificate.

> This screen only appears when the Web UI is served over HTTPS (it is on all modern Android
> versions).

---

### Method B — manually via config export/import

Use this if you prefer editing files directly.

1. In the app, go to **Settings → Import and Export** and **export** your configuration. This
   creates `config.zip` (by default at
   `/storage/emulated/0/backups/syncthing/config.zip`). See also
   [How can I access the config and key files?](Access-Syncthing-config-and-key-files.md).
2. Open `config.zip` and replace these two files with your own:
   - `https-cert.pem` — your certificate **including the full chain** (leaf + intermediate(s)).
   - `https-key.pem` — the matching **private key**.

   Leave everything else (`config.xml`, `cert.pem`, `key.pem`, the database, etc.) unchanged.
3. Put the updated files back into `config.zip` (keep the same file names and the same archive
   layout), and copy it back to the export location.
4. Install your **root CA** on the phone if you haven't already (see *Before you start*, step 3).
5. In the app, go to **Settings → Import and Export** and **import** the configuration.
6. Start/restart Syncthing. The app should now connect normally, and the local Web GUI should open
   using your certificate.

---

### If the app can't connect after the change

If Syncthing is clearly running and syncing, you can open the Web GUI in a normal browser, but the
**app itself** never finishes loading (it shows your folders/devices but stays stuck, or the API
seems unreachable), check the following — almost always it's one of these:

- **The root CA isn't installed (or got removed) on the phone.** Re-check
  *Settings → Security → … → Trusted credentials → User*. Without it, the app cannot verify your
  certificate.
- **The certificate file is missing the chain.** `https-cert.pem` must contain the intermediate
  certificate(s) after the leaf, not just the leaf on its own.
- **The wrong key was used.** `https-key.pem` must be the private key that matches the leaf
  certificate in `https-cert.pem`.
- **The archive layout changed.** When repacking `config.zip`, keep the original file names and
  structure so the import recognizes the files.

A log line containing `BAD_SIGNATURE` during startup is the classic symptom of a missing/untrusted
root CA or an incomplete chain.

Method A (the in-app screen) catches the first three of these before applying and won't let you
apply a certificate the app wouldn't be able to trust, so it's the easier path if you're unsure.

---

### Good to know (security & privacy)

- The app only ever talks to the Syncthing instance **on the same phone**, over the loopback
  interface (`127.0.0.1`). Your API key and configuration never leave the device for these calls,
  even if you enable "Remote access".
- The app first checks for Syncthing's built-in self-signed certificate. Only if that doesn't match
  does it fall back to the certificates trusted by Android — including the CA **you** installed.
  This keeps the default setup unchanged for everyone who doesn't use a custom certificate.
- Trusting your installed CA for the app is scoped to the **local** connection only; it does not
  change how Syncthing verifies the other devices you sync with (that uses Syncthing's own
  device-ID-based security and is unaffected).
- Reverting is easy: use **Reset to auto-generated certificate** (Method A), import an unmodified
  `config.zip`, and the app goes back to the default self-signed certificate.
