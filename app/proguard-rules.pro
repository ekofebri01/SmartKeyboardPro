# R8 / ProGuard rules for Smart Keyboard Pro.
# Release builds are minified, so anything deserialized reflectively must be kept.

# Firestore deserializes model classes reflectively (no-arg constructor + fields).
-keep class com.smartkeyboard.pro.DictionaryEntry { *; }
-keep class com.smartkeyboard.pro.AutoTextEntry { *; }
-keep class com.smartkeyboard.pro.ThemeConfig { *; }
-keep class com.smartkeyboard.pro.KeyboardTheme { *; }

-keepattributes Signature, *Annotation*, InnerClasses, EnclosingMethod
-keepclassmembers class com.smartkeyboard.pro.** {
    <init>(...);
    <fields>;
}

# Firebase / Google Play services
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**

# AndroidX Credentials + Google Identity (Credential Manager)
-keep class androidx.credentials.** { *; }
-dontwarn androidx.credentials.**
-keep class com.google.android.libraries.identity.googleid.** { *; }
-dontwarn com.google.android.libraries.identity.googleid.**

# Compose keeps its own runtime metadata; only silence known warnings.
-dontwarn org.jetbrains.annotations.**
