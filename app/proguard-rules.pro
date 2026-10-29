-keep class org.fossify.keyboard.helpers.MyKeyboard$Key { *; }

# ML Kit components are created by reflection through their no-arg constructor, R8 full mode strips it otherwise
-keep class * implements com.google.firebase.components.ComponentRegistrar { <init>(); }
# classes used from the native barcode detector through JNI
-keep class com.google.android.libraries.barhopper.** { *; }
-keep class com.google.photos.vision.barcode.** { *; }

# KeyboardScan is a renamed fork that credits Fossify in the README and keeps the original About screen,
# so skip the "modded app" warning that Fossify Commons shows to apps outside the org.fossify namespace
-assumenosideeffects class org.fossify.commons.extensions.ActivityKt {
    void showModdedAppWarning(org.fossify.commons.activities.BaseSimpleActivity);
}
-assumenosideeffects class org.fossify.commons.compose.extensions.ActivityExtensionsKt {
    void fakeVersionCheck(android.content.Context, kotlin.jvm.functions.Function0);
}
