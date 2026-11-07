package br.com.inforprimetecnologia.vaidebuzao

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.viewmodel.compose.viewModel
import br.com.inforprimetecnologia.vaidebuzao.ui.AppRoot
import br.com.inforprimetecnologia.vaidebuzao.ui.state.AppViewModel
import br.com.inforprimetecnologia.vaidebuzao.ui.state.Gate
import br.com.inforprimetecnologia.vaidebuzao.ui.state.TransitViewModel
import br.com.inforprimetecnologia.vaidebuzao.ui.theme.ThemeMode
import br.com.inforprimetecnologia.vaidebuzao.ui.theme.VaiDeBuzaoTheme
import br.com.inforprimetecnologia.vaidebuzao.ui.theme.isAppInDarkTheme

class MainActivity : ComponentActivity() {
    private var keepSplash = true

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen().setKeepOnScreenCondition { keepSplash }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as VaiDeBuzaoApp).container
        setContent {
            val app: AppViewModel = viewModel { AppViewModel(container) }
            val transit: TransitViewModel = viewModel { TransitViewModel(container) }
            val settings by app.settings.collectAsState()
            val gate by app.gate.collectAsState()
            keepSplash = gate == Gate.Loading
            val dark = isAppInDarkTheme(settings?.themeMode ?: ThemeMode.SYSTEM)
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
                    navigationBarStyle = if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
                )
                onDispose {}
            }
            VaiDeBuzaoTheme(darkTheme = dark) {
                AppRoot(app, transit, container)
            }
        }
    }
}
