# R8 rules of the release build, added to proguard-android-optimize.txt and to the rules
# which the libraries (AndroidX, Room, coroutines...) bring with them

# Readable stack traces: the line numbers stay, and build/outputs/mapping/release/mapping.txt
# gives back the names (Google Play gets it inside the bundle)
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# JSch creates its ciphers, key exchanges, signatures... from the class names of its
# configuration, and looks some methods up by name: it is kept whole
-keep class com.jcraft.jsch.** { *; }
# its optional dependencies, used only when they are there (and they are not on Android)
-dontwarn com.sun.jna.**
-dontwarn org.apache.logging.log4j.**
-dontwarn org.bouncycastle.**
-dontwarn org.ietf.jgss.**
-dontwarn org.newsclub.net.unix.**
-dontwarn org.slf4j.**

# The loggers are named after their classes, and give the tags of the logs (see TimberLogHandler)
-keepclasseswithmembernames class * {
    static java.util.logging.Logger *;
}

# The details of a server are passed to ServerDetailsFragment as a Serializable, and kept in
# the saved state of the fragment
-keepclassmembers class com.github.jksiezni.xpra.config.ServerDetails {
    static final long serialVersionUID;
    !static !transient <fields>;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}
