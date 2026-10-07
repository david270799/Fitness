# Минификация выключена (личное приложение). Правила оставлены на случай включения R8.
-keepattributes *Annotation*, InnerClasses, Signature
-keep,includedescriptorclasses class com.iron.fitness.**$$serializer { *; }
-keepclassmembers class com.iron.fitness.** {
    *** Companion;
}
-keepclasseswithmembers class com.iron.fitness.** {
    kotlinx.serialization.KSerializer serializer(...);
}
