# Codex: regole R8 dell'app. Le librerie moderne portano le proprie regole nell'AAR; qui stanno solo
# le eccezioni che il progetto conosce.

# Stack trace leggibili nei report di crash.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# kotlinx.serialization: le classi @Serializable e i loro serializer generati.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class dev.pampa.codex.**$$serializer { *; }
-keepclassmembers class dev.pampa.codex.** { *** Companion; }
-keepclasseswithmembers class dev.pampa.codex.** { kotlinx.serialization.KSerializer serializer(...); }

# Bouncy Castle (API lightweight, nessun provider JCA registrato).
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**
-dontwarn javax.naming.**

# Le tracce di lavoro non escono dal laboratorio.
#
# `Log.d` e `Log.v` restano nel bytecode anche in release: sono misure di fotogrammi, nomi di
# contatti, identificativi di gruppo. Non sono segreti -- il registro di sistema, da Android 10, lo
# legge solo chi ha il telefono in mano e gli strumenti -- ma non c'e' nessun motivo perche' ci
# siano. R8 le toglie insieme alle stringhe che le costruiscono.
#
# Vale solo per `d` e `v`: `w` ed `e` servono a capire un guasto vero, e restano.
-assumenosideeffects class android.util.Log {
  public static int d(...);
  public static int v(...);
}
