package br.com.inforprimetecnologia.vaidebuzao.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import br.com.inforprimetecnologia.vaidebuzao.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "preferencias")

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val onboardingDone: Boolean = false,
    val historyEnabled: Boolean = false,
)

/** Preferências do usuário, guardadas só no aparelho (DataStore). */
class SettingsRepository(private val context: Context) {
    private object Keys {
        val THEME = stringPreferencesKey("tema")
        val ONBOARDING = booleanPreferencesKey("onboarding_concluido")
        val HISTORY = booleanPreferencesKey("historico_buscas")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            themeMode = p[Keys.THEME]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
            onboardingDone = p[Keys.ONBOARDING] ?: false,
            historyEnabled = p[Keys.HISTORY] ?: false,
        )
    }

    suspend fun setTheme(mode: ThemeMode) = context.dataStore.edit { it[Keys.THEME] = mode.name }
    suspend fun setOnboardingDone(done: Boolean) = context.dataStore.edit { it[Keys.ONBOARDING] = done }
    suspend fun setHistoryEnabled(enabled: Boolean) = context.dataStore.edit { it[Keys.HISTORY] = enabled }
    suspend fun clear() = context.dataStore.edit { it.clear() }

    /** Para exportação (portabilidade). */
    suspend fun snapshot(): Map<String, String> {
        var out = emptyMap<String, String>()
        context.dataStore.edit { p -> out = p.asMap().mapKeys { it.key.name }.mapValues { it.value.toString() } }
        return out
    }
}
