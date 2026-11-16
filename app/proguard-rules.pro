# R8 / ProGuard для release (isMinifyEnabled + isShrinkResources).
# Бібліотеки з consumer-правилами (Room, ML Kit, Play Services/AdMob, UMP, AndroidX, Material) свої правила
# приносять самі. Нижче лише те, що потрібне нам явно.

# ---- pdfbox-android (com.tom-roush): шрифти, кодування, фільтри, ресурси підвантажуються за іменами ----
-keep class com.tom_roush.pdfbox.** { *; }
-keep class com.tom_roush.fontbox.** { *; }
-keep class com.tom_roush.harmony.** { *; }
-keep class com.tom_roush.pdfbox.android.** { *; }
-dontwarn com.tom_roush.pdfbox.**
-dontwarn com.tom_roush.fontbox.**
# Необов'язкові залежності PdfBox, яких немає на Android
-dontwarn com.gemalto.jp2.**
-dontwarn org.bouncycastle.**
-dontwarn org.apache.**
-dontwarn javax.naming.**
-dontwarn javax.xml.**
-dontwarn org.w3c.dom.**
-dontwarn java.awt.**
-dontwarn javax.imageio.**
-dontwarn javax.swing.**

# ---- androidx.pdf (pdf-viewer-fragment): фрагменти/сервіси створюються рефлексією, AIDL ----
-keep class androidx.pdf.** { *; }
-dontwarn androidx.pdf.**

# ---- ML Kit Document Scanner, AdMob / Play Services, UMP ----
# Основні правила постачають самі бібліотеки; тут лише страховка від попереджень про відсутні класи.
-keep class com.google.mlkit.vision.documentscanner.** { *; }
-dontwarn com.google.mlkit.**
-keep class com.google.android.gms.ads.** { *; }
-keep class com.google.android.ump.** { *; }
-dontwarn com.google.android.gms.**

# Ресурси за іменами (getIdentifier/Class.forName) у коді не використовуються (перевірено пошуком), keep.xml не потрібен.

# ---- Трасування стеків для Play Console ----
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
