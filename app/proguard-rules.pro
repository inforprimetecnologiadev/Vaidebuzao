# Regras do R8 para o Vai de Buzão.
# kotlinx.serialization e MapLibre já trazem suas próprias regras (consumer rules).

# Mantém nomes das classes @Serializable usadas na exportação de dados (LGPD).
-keepclassmembers @kotlinx.serialization.Serializable class br.com.inforprimetecnologia.vaidebuzao.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-dontwarn org.slf4j.**
