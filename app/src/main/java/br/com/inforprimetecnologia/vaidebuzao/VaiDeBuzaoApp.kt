package br.com.inforprimetecnologia.vaidebuzao

import android.app.Application
import org.maplibre.android.MapLibre

class VaiDeBuzaoApp : Application() {
    override fun onCreate() {
        super.onCreate()
        MapLibre.getInstance(this)
    }
}
