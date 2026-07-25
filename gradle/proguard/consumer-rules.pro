# Argos — R8/ProGuard consumer rules.
#
# The same rules an AAR would ship via consumer-rules.pro, published here so
# JVM and Android consumers can apply them today. Android apps consuming Argos
# should add this file to their release build:
#
#   android {
#       buildTypes {
#           release {
#               proguardFiles(
#                   getDefaultProguardFile('proguard-android-optimize.txt'),
#                   'path/to/gradle/proguard/consumer-rules.pro',
#               )
#           }
#       }
#   }

# kotlinx.serialization models: keep the generated serializers and their
# descriptors reachable, otherwise R8 strips them and decoding fails at runtime.
-keepattributes *Annotation*, InnerClasses, Signature, ExceptionSignature, EnclosingMethod

-keep,includedescriptorclasses class it.hydr4.argo.**$$serializer { *; }
-keepclassmembers class it.hydr4.argo.models.** {
    *** Companion;
}
-keepclasseswithmembers class it.hydr4.argo.models.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Annotation metadata used by the custom-endpoint registry must survive R8.
-keep,includedescriptorclasses @it.hydr4.argo.annotations.ArgoEndpoint class ** { *; }

# OkHttp/Okio pull in reflection-based internals; silence noisy warnings.
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn kotlinx.serialization.**
