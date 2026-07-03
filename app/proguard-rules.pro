# Retrofit + kotlinx-serialization models
-keepattributes Signature, InnerClasses, EnclosingMethod, *Annotation*
-keepclassmembers class com.ga.airdrop.data.model.** {
    *** Companion;
}
-keepclasseswithmembers class com.ga.airdrop.data.model.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
