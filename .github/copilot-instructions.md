# Copilot Instructions

## Building the Project

There is no Gradle wrapper (`gradlew`) checked in. Use the Gradle distribution cached by Android Studio instead.

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:ANDROID_HOME = "C:\Users\marlobell\AppData\Local\Android\Sdk"
$gradle = "C:\Users\marlobell\.gradle\wrapper\dists\gradle-9.4.1-bin\arn2x92ynaizyzdaamcbpbhtj\gradle-9.4.1\bin\gradle.bat"
```

### Debug build (fast verification)
```powershell
& $gradle assembleDebug --no-daemon -q
```

### Release build (produces APK)
```powershell
& $gradle assembleRelease --no-daemon -q
```

The release APK is output to `app\build\outputs\apk\release\app-release-unsigned.apk`.
