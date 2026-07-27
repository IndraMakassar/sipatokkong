package com.antoniusindra.sipatokkong

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.antoniusindra.sipatokkong.data.model.Profile
import com.antoniusindra.sipatokkong.data.model.UserRole
import com.antoniusindra.sipatokkong.ui.home.ParentHomeScreen
import com.antoniusindra.sipatokkong.ui.theme.SipatokkongTheme

/**
 * DEBUG-ONLY entry point. Opens [ParentHomeScreen] directly with sample data so the parent
 * dashboard can be previewed on an emulator/device WITHOUT logging in.
 *
 * This activity lives in `src/debug/`, so it is never part of a release build. It does not touch
 * the real login flow at all. To remove it, delete the `app/src/debug/` folder.
 */
class DevPreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SipatokkongTheme(dynamicColor = false) {
                ParentHomeScreen(
                    profile = Profile(
                        id = "preview",
                        role = UserRole.ORANGTUA,
                        fullName = "Ibu Almair",
                    ),
                    onLogout = { finish() },
                )
            }
        }
    }
}
