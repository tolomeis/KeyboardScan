-keep class org.fossify.keyboard.helpers.MyKeyboard$Key { *; }

# ML Kit components are created by reflection through their no-arg constructor, R8 full mode strips it otherwise
-keep class * implements com.google.firebase.components.ComponentRegistrar { <init>(); }
# classes used from the native barcode detector through JNI
-keep class com.google.android.libraries.barhopper.** { *; }
-keep class com.google.photos.vision.barcode.** { *; }
