package io.github.brainyjongmin.forestboardfriends

import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*
import java.io.File

class GameUiTest {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    private fun home(){
        ui.waitUntil(10_000){ui.onAllNodesWithText("이름").fetchSemanticsNodes().isNotEmpty() || ui.onAllNodesWithText("오목").fetchSemanticsNodes().isNotEmpty()}
        if(ui.onAllNodesWithText("이름").fetchSemanticsNodes().isNotEmpty()){
            ui.onNode(hasSetTextAction()).performTextInput("테스트")
            ui.onNodeWithText("숲속으로 들어가기").performScrollTo().performClick()
        }
        ui.waitUntil(10_000){ui.onAllNodesWithText("오목").fetchSemanticsNodes().isNotEmpty()}
    }
    private fun screenshot(name:String){val bitmap=ui.onRoot().captureToImage().asAndroidBitmap();File(ui.activity.getExternalFilesDir(null),"$name.png").outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}}
    @Test fun continuousRotationDoesNotResetGravity(){
        home();ui.onNodeWithText("엄마의 블록 퍼즐").performScrollTo().performClick();ui.onNodeWithText("노템전").performClick()
        ui.mainClock.autoAdvance=false
        repeat(16){ui.onNodeWithText("↻").performClick();ui.mainClock.advanceTimeBy(100)}
        val row=ui.onNodeWithTag("block-board").fetchSemanticsNode().config[SemanticsProperties.StateDescription].substringAfter(":").toInt()
        assertTrue("Gravity must progress during continuous rotation, row=$row",row>=1)
    }
    @Test fun moleRespondsAndPausesAcrossLandscapeRotation(){
        home();ui.onNodeWithText("숲속 두더지 팡!").performScrollTo().performClick();ui.onNodeWithTag("mole-stage-1").performClick()
        ui.mainClock.autoAdvance=false;ui.mainClock.advanceTimeBy(3250)
        val live=ui.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,"등장"))
        assertTrue(live.fetchSemanticsNodes().isNotEmpty());live[0].performClick()
        ui.onNodeWithTag("mole-score").assertTextEquals("100점  ·  1 콤보")
        screenshot("mole-portrait")
        ui.onNodeWithText("잠깐 쉬기").performClick();val before=ui.onNodeWithTag("mole-time").fetchSemanticsNode().config[SemanticsProperties.Text].first().text
        ui.mainClock.advanceTimeBy(5000)
        ui.onNodeWithTag("mole-time").assertTextEquals(before)
        ui.runOnUiThread{ui.activity.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE}
        ui.mainClock.advanceTimeBy(400);ui.onNodeWithText("계속하기").performClick();ui.mainClock.advanceTimeBy(100)
        ui.onNodeWithTag("mole-score").assertTextEquals("100점  ·  1 콤보")
        screenshot("mole-landscape")
    }
    @Test fun microphoneButtonIsPresentAndCancellationKeepsDraft(){
        home();ui.onNodeWithText("오목").performClick();ui.onNodeWithText("게임 시작").performClick()
        ui.onNode(hasSetTextAction()).performTextInput("안녕")
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("pm grant io.github.brainyjongmin.forestboardfriends android.permission.RECORD_AUDIO").use{descriptor->android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().readText()}
        ui.onNodeWithContentDescription("음성 입력").assertExists()
        ui.onNodeWithContentDescription("음성 입력").performClick()
        if(ui.onAllNodesWithText("취소").fetchSemanticsNodes().isNotEmpty())ui.onNodeWithText("취소").performClick()
        ui.onNodeWithText("안녕").assertExists()
    }
}
