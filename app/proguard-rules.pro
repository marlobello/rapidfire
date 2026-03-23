# Add project specific ProGuard rules here.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Keep update data class used with JSON parsing
-keep class com.rapidfire.game.update.UpdateInfo { *; }
