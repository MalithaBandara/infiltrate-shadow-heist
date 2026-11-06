# R8 keep rules for the release build. Add a rule only for code reached by a name R8 can't see.

# StoreBilling.android.kt finds this class by name and calls setApplication/getApplication through
# reflection, inside a try/catch - if R8 renamed it, the failure would only be a logged warning.
-keep class com.revenuecat.purchases.kmp.di.AndroidProvider { *; }

# WorkManager (pulled in by the ads SDK) starts at process launch and Room creates WorkDatabase_Impl
# through its no-arg constructor by reflection. Room 2.2.5's own rule keeps the class but not the
# constructor, and R8 full mode then strips it: the app crashes on every launch without this.
-keep class * extends androidx.room.RoomDatabase { <init>(); }
