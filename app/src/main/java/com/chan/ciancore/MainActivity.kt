package com.chan.ciancore

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.chan.ciancore.ui.navigation.CianCoreNavGraph
import com.chan.ciancore.ui.theme.CianCoreTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CianCoreTheme {
                CianCoreNavGraph()
            }
        }
    }
}
