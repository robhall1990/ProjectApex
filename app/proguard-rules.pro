# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Retrofit builds its service implementations via a dynamic proxy and
# inspects generic return/parameter types at runtime (e.g. Call<List<Dto>>);
# R8 must not strip that reflective metadata.
-keepattributes Signature, InnerClasses, EnclosingMethod, AnnotationDefault, *Annotation*

# Preserve line number information for legible crash stacks, but collapse
# the source file name so it doesn't leak in released builds.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# No explicit keep rules for @Serializable DTOs (data/openf1/OpenF1Dtos.kt):
# kotlinx-serialization-core >= 1.6 ships consumer proguard rules that keep
# the generated $serializer/INSTANCE for every @Serializable class, and the
# Retrofit/OkHttp/kotlinx-serialization artifacts here (see
# gradle/libs.versions.toml) are all recent enough to carry those consumer
# rules. Revisit if a release build ever shows a SerializationException for
# a missing serializer.
