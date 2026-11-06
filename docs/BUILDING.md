# Build and signing

## Local prerequisites

Install Android SDK platform 25, a recent Android build-tools version, and
Java 21. The code is compiled for Java 8 bytecode for compatibility with
the target Android platform.

The build script signs the APK. Supply a keystore path and passwords as
environment variables; keep them in a local shell configuration or
password manager, never in the repository.

```sh
export HARNESS_KEYSTORE=/absolute/path/to/release.keystore
export HARNESS_KEYSTORE_PASSWORD='...'
export HARNESS_KEY_PASSWORD='...'
./build.sh
```

Use a task-specific or temporary shell session if you do not want the
values to remain in your shell environment. The generated APK and
signature files are ignored by Git.

## SDK and JDK selection

The script honours `ANDROID_HOME` first and `ANDROID_SDK_ROOT` second. If
neither is set, it uses its configured local SDK path. It honours
`JAVA_HOME`; otherwise it uses the JDK path configured in the script. If
your SDK or JDK is elsewhere, set the corresponding environment variable
before building.

## Fire TV install

Enable ADB debugging in Fire TV settings and connect to the device:

```sh
adb connect FIRE_TV_IP:5555
adb install -r build/harness-tv.apk
```

Keep using the same signing key for updates. Android rejects an in-place
update signed by a different key; uninstalling first can remove the app's
local data.

## Troubleshooting

- **SDK platform not found:** install Android SDK Platform 25 or adjust
  `PLATFORM` in `build.sh` after checking app compatibility.
- **Signing fails:** confirm that the keystore path is correct and that
  the store and key passwords match the supplied values.
- **ADB cannot connect:** check that debugging is enabled, the TV and
  computer share a network, and the TV's current IP address is correct.
- **Catalogue or playback is empty:** the harness ships with placeholder
  config (`example.com` in `SiteConfig.java`) and will never load a real
  catalogue until it is filled in — follow `docs/AGENT_GUIDE.md`. After
  that, check the device's internet connection and whether the site has
  changed its pages or playback providers.
