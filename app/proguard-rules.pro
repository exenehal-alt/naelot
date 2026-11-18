# CircleChat ProGuard / R8 Rules

# Keep line numbers & source file names for readable crash stack traces
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Keep Kotlin & Annotation metadata
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Keep @Keep annotated classes and members (including Firestore data models)
-keep class androidx.annotation.Keep
-keep @androidx.annotation.Keep class * { *; }
-keepclassmembers class * {
    @androidx.annotation.Keep *;
}

# Preserve all Firestore models, fields, and no-arg constructors for CustomClassMapper
-keepclassmembers class com.example.data.model.** {
    <init>(...);
    <fields>;
    <methods>;
}
-keep class com.example.data.model.** { *; }

# Firebase Firestore & Auth annotations
-keepattributes RuntimeVisibleAnnotations,RuntimeInvisibleAnnotations
-keepclassmembers class * {
    @com.google.firebase.firestore.DocumentId *;
    @com.google.firebase.firestore.ServerTimestamp *;
    @com.google.firebase.firestore.PropertyName *;
    @com.google.firebase.firestore.Exclude *;
}

# Credential Manager & Google Identity Services
-keep class androidx.credentials.** { *; }
-keep class com.google.android.libraries.identity.googleid.** { *; }

# CameraX
-keep class androidx.camera.** { *; }

# OkHttp & Coroutines warnings suppression
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn okhttp3.**
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }
