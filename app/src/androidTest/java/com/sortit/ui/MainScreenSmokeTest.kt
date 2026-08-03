package com.sortit.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Smoke test ringan — tanpa ViewModel mock (menghindari NPE/final-class issue).
 * Verifikasi UI murni: PermissionGate muncul saat izin belum diberikan.
 */
@RunWith(AndroidJUnit4::class)
class MainScreenSmokeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun permissionGate_showsAndButtonWorks() {
        composeTestRule.setContent {
            PermissionGate(legacy = false, onGrant = {})
        }
        composeTestRule.onNodeWithText("Izin storage dibutuhkan").assertExists()
        composeTestRule.onNodeWithText("Buka pengaturan").assertExists()
        composeTestRule.onNodeWithText("Buka pengaturan").performClick()
    }
}
