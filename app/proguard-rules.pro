# ── Phantom Messenger ProGuard Rules ──
# Production rules for R8 code shrinking and obfuscation.

# ── Preserve line numbers for crash reporting ──
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ── Kotlin Serialization ──
# Keep @Serializable classes and their generated serializers
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}

-keepclasseswithmembers class **$$serializer {
    *** INSTANCE;
}

-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}

-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}

# ── Room (Database) ──
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao class *
-dontwarn androidx.room.paging.**

# ── SQLCipher ──
-keep class net.sqlcipher.** { *; }
-dontwarn net.sqlcipher.**

# ── Moshi ──
-keep class com.squareup.moshi.** { *; }
-keepclassmembers class ** {
    @com.squareup.moshi.FromJson *;
    @com.squareup.moshi.ToJson *;
}
-keep @com.squareup.moshi.JsonQualifier @interface *

# ── Retrofit ──
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

# ── OkHttp ──
-dontwarn okhttp3.internal.platform.**
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**

# ── Ktor Client ──
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**

# ── Supabase ──
-keep class io.github.jan.supabase.** { *; }
-dontwarn io.github.jan.supabase.**

# ── Google Identity / Credentials ──
-keep class com.google.android.libraries.identity.googleid.** { *; }
-keep class androidx.credentials.** { *; }

# ── Firebase ──
-keep class com.google.firebase.** { *; }

# ── Coil (Image Loading) ──
-dontwarn coil.**

# ── Phantom Network Models ──
# Keep all @SerialName annotated fields in network payloads
-keep class com.example.phantom.data.network.** { *; }

# ── Hilt / Dagger ──
-dontwarn dagger.hilt.**
