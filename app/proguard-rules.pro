# Add project specific ProGuard rules here.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Keep update data classes used with JSON parsing
-keep class com.rapidfire.game.update.UpdateInfo { *; }
-keep class com.rapidfire.game.update.UpdateResult { *; }
-keep class com.rapidfire.game.update.UpdateResult$* { *; }
