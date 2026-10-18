package com.chan.ciancore

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import com.chan.ciancore.ui.MainViewModel
import com.chan.ciancore.ui.navigation.CianCoreNavGraph
import com.chan.ciancore.ui.theme.CianCoreTheme

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            vm.onVpnPermissionGranted()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CianCoreTheme {
                CianCoreNavGraph(
                    vm = vm,
                    requestVpnPermission = { intent ->
                        vpnPermissionLauncher.launch(intent)
                    }
                )
            }
        }
    }
}
