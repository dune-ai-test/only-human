package com.only.human

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.only.human.design.OnlyHumanTheme
import com.only.human.nav.AppShell
import com.only.human.screens.Onboarding
import com.only.human.world.World

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        World.start(applicationContext)
        enableEdgeToEdge()
        setContent {
            OnlyHumanTheme {
                if (World.onboarded) {
                    AppShell()
                } else {
                    Onboarding()
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // The next launch simulates the gap between this moment and then.
        World.markAway()
    }
}
