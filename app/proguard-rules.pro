# Moshi Codegen erzeugt JsonAdapter-Klassen, Reflection ist nicht nötig.
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response

# Retrofit – Service-Interfaces und Response-Bodies behalten
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-dontwarn javax.annotation.**
-dontwarn kotlin.Unit

# Moshi – generierte Adapter behalten
-keep class com.helga.android.data.remote.dto.**JsonAdapter { *; }
-keep @com.squareup.moshi.JsonClass class * { *; }

# Room – Entities und DAOs
-keep class com.helga.android.data.local.entity.** { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-keep @androidx.room.Database class * { *; }

# Hilt / Dagger
-dontwarn dagger.internal.Codegen
-keepnames @dagger.hilt.android.lifecycle.HiltViewModel class *

# Coil – Transformations und Decoder nicht entfernen
-keep class coil.** { *; }
-dontwarn coil.**

# DataStore
-keepclassmembers class * extends androidx.datastore.preferences.protobuf.GeneratedMessageLite {
    <fields>;
}

# OkHttp / Okio
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
-dontwarn okio.**

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}

# WorkManager
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.CoroutineWorker
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# Timber – kein Code im Release, aber die Klassen müssen behalten werden
-dontwarn com.jakewharton.timber.**
