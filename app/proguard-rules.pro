# JGit - uses reflection and service loading
-keep class org.eclipse.jgit.** { *; }
-dontwarn org.eclipse.jgit.**
-dontwarn org.slf4j.impl.StaticLoggerBinder

# Room - entities, DAOs, database
-keep class com.example.gitview.data.db.** { *; }

# Kotlin Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.example.gitview.**$$serializer { *; }
-keepclassmembers class com.example.gitview.** {
    *** Companion;
}
-keepclasseswithmembers class com.example.gitview.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Markwon
-keep class io.noties.markwon.** { *; }

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Keep model classes used in serialization/deserialization
-keep class com.example.gitview.data.crypto.EncryptionInfo { *; }
-keep class com.example.gitview.data.crypto.EncryptionLevel { *; }
