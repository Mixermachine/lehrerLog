package de.aarondietz.lehrerlog

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import de.aarondietz.lehrerlog.auth.initAndroidTokenStorage
import de.aarondietz.lehrerlog.logging.initAndroidLogFileWriter
import de.aarondietz.lehrerlog.logging.initAndroidLogSharing

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Initialize token storage with context
        initAndroidTokenStorage(applicationContext)
        initAndroidLogFileWriter(applicationContext)
        initAndroidLogSharing(applicationContext)

        setContent {
            App()
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}
