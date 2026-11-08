# Rules for the release build (code shrinking).

# Anonymous voting (D-110): libsodium is reached through JNA, which finds the native functions
# by reflection on the binding interface. Without these rules, R8 would rename or remove them and
# the voting self-test would fail on the phone. (The preview build sent to testers is not shrunk.)
-keep class com.sun.jna.** { *; }
-keepclassmembers class * extends com.sun.jna.** { public *; }
-keep class com.ionspin.kotlin.crypto.** { *; }
-dontwarn java.awt.**
