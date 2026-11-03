# Regole R8 specifiche del progetto (le regole predefinite di Android/Compose bastano per ora).

# Ponte JavaScript del player YouTube: i metodi annotati devono restare con il loro nome.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
