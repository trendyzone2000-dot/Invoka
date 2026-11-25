package com.invoka.app

import android.graphics.Color as AColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(AColor.TRANSPARENT, AColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(AColor.TRANSPARENT, AColor.TRANSPARENT)
        )
        setContent { InvokaApp() }
    }
}

@Composable
fun InvokaApp(vm: AppViewModel = viewModel()) {
    val lang = vm.lang
    CompositionLocalProvider(
        LocalLang provides lang,
        LocalLayoutDirection provides if (lang == "ar") LayoutDirection.Rtl else LayoutDirection.Ltr
    ) {
        InvokaTheme {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                var splash by remember { mutableStateOf(true) }
                LaunchedEffect(Unit) {
                    delay(900)
                    splash = false
                    if (!vm.onboarded) vm.push(Screen.Business)
                }
                if (splash) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Image(
                            painterResource(R.drawable.logo_full), contentDescription = null,
                            modifier = Modifier.width(260.dp).padding(16.dp), contentScale = ContentScale.FillWidth
                        )
                    }
                } else {
                    BackHandler(enabled = vm.stack.size > 1) { vm.pop() }
                    when (val s = vm.stack.last()) {
                        Screen.Home -> HomeScreen(vm)
                        is Screen.Editor -> EditorScreen(vm, s.invoiceId)
                        is Screen.Detail -> DetailScreen(vm, s.invoiceId)
                        Screen.Business -> BusinessScreen(vm)
                    }
                }
            }
        }
    }
}
