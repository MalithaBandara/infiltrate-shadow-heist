# R8 keep rules for the release build. Add a rule only for code reached by a name R8 can't see.

# StoreBilling.android.kt finds this class by name and calls setApplication/getApplication through
# reflection, inside a try/catch - if R8 renamed it, the failure would only be a logged warning.
-keep class com.revenuecat.purchases.kmp.di.AndroidProvider { *; }
