package dev.orgdroid

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import dev.orgdroid.ui.EditorScreen
import dev.orgdroid.ui.OrgdroidTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            OrgdroidTheme { EditorScreen() }
        }
    }
}
