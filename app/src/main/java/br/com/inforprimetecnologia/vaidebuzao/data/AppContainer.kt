package br.com.inforprimetecnologia.vaidebuzao.data

import android.content.Context

/** Injeção de dependências manual (simples, sem processadores de anotação). */
class AppContainer(context: Context) {
    private val app = context.applicationContext
    val settings = SettingsRepository(app)
    private val userDb = UserDatabase(app)
    val consent = ConsentRepository(userDb)
    val favorites = FavoritesRepository(userDb)
    val recordings = RecordingRepository(userDb)
    val transit = TransitRepository(app)
    val legal = LegalRepository(app)
    val location = LocationRepository(app)
    val geocoding = GeocodingRepository()
}
