package br.com.inforprimetecnologia.vaidebuzao

import android.app.Application
import br.com.inforprimetecnologia.vaidebuzao.data.AppContainer
import org.maplibre.android.MapLibre

class VaiDeBuzaoApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        MapLibre.getInstance(this)
    }
}
