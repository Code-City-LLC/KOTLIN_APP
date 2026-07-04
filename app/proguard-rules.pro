# kotlinx-serialization + Retrofit
-keepattributes Signature, InnerClasses, EnclosingMethod, *Annotation*, RuntimeVisibleAnnotations, AnnotationDefault

# Keep serializers for EVERY @Serializable class in the app. Models live in
# data.model, but @Serializable also appears in feature/** (CalculatorRepository,
# CartStore) and data/api (ApiError). Scoping the keep to data.model only would
# let R8 strip those serializers -> SerializationException in release builds.
-keepclassmembers class com.ga.airdrop.** {
    *** Companion;
}
-keepclasseswithmembers class com.ga.airdrop.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.ga.airdrop.**$$serializer { *; }

# kotlinx-serialization runtime
-dontwarn kotlinx.serialization.**

-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
