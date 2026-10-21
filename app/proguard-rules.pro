# R8/ProGuard rules for the release build.

# ZXing embedded scanning: the CaptureActivity is kept via the merged manifest,
# but the library performs reflective lookups inside its decoder classes.
-keep class com.journeyapps.barcodescanner.** { *; }
-keep class com.google.zxing.** { *; }

# AboutLibraries loads its bundled license metadata and model classes at runtime.
-keep class com.mikepenz.aboutlibraries.** { *; }

# Gson model classes
-keep class com.nutomic.syncthingandroid.model.** { *; }

# Preserve generic type signatures used by Guava TypeToken
-keepattributes Signature,InnerClasses,EnclosingMethod

# Keep TypeToken and anonymous subclasses, including generic superclass signatures
-keep class com.google.common.reflect.TypeToken { *; }
-keep class * extends com.google.common.reflect.TypeToken { *; }
