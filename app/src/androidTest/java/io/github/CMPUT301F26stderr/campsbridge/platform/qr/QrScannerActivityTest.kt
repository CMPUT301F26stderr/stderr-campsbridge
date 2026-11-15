package io.github.CMPUT301F26stderr.campsbridge.platform.qr

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.pm.PackageManager
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.content.ContextCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises permission delivery and a real initialized scanner on a fresh disposable app. */
@RunWith(AndroidJUnit4::class)
class QrScannerActivityTest {
    /** Denial returns a platform outcome; granting access permits native startup and ordinary Back. */
    @Test fun cameraDenialThenGrantedCancellation() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val automation = instrumentation.uiAutomation
        automation.serviceInfo =
            automation.serviceInfo.apply {
                flags = flags or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
            }
        val contract = QrScanContract()
        assertEquals(
            PackageManager.PERMISSION_DENIED,
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA),
        )
        ActivityScenario
            .launchActivityForResult<QrScannerActivity>(
                contract.createIntent(context, Unit),
            ).use { scenario ->
                awaitCondition {
                    findDenyButton(automation.rootInActiveWindow)
                        ?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
                }
                val result = scenario.result
                assertEquals(QrScanResult.PermissionDenied, contract.parseResult(result.resultCode, result.resultData))
            }
        automation.grantRuntimePermission(context.packageName, Manifest.permission.CAMERA)
        ActivityScenario
            .launchActivityForResult<QrScannerActivity>(
                contract.createIntent(context, Unit),
            ).use { scenario ->
                awaitCondition {
                    var ready = false
                    scenario.onActivity { ready = it.cameraScan != null }
                    ready
                }
                scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
                val result = scenario.result
                assertEquals(QrScanResult.Cancelled, contract.parseResult(result.resultCode, result.resultData))
            }
    }

    /** Waits for a real platform callback with a bounded timeout.
     * @param condition Observable platform condition, never a replacement for the camera adapter.
     */
    private fun awaitCondition(condition: () -> Boolean) {
        val deadline = System.nanoTime() + 15_000_000_000L
        while (System.nanoTime() < deadline) {
            if (condition()) return
            Thread.sleep(100)
        }
        val root = InstrumentationRegistry.getInstrumentation().uiAutomation.rootInActiveWindow
        assertTrue("Scanner platform callback did not arrive: ${describeWindow(root)}", condition())
    }

    /** Finds the controller's actual resource, whose namespace can differ from its application package.
     * @param node Current active accessibility subtree.
     */
    private fun findDenyButton(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.viewIdResourceName?.endsWith(":id/permission_deny_button") == true) return node
        for (index in 0 until node.childCount) {
            findDenyButton(node.getChild(index))?.let { return it }
        }
        return null
    }

    /** Includes real system-window evidence when platform synchronization fails.
     * @param node Current active accessibility subtree, or null while no window is accessible.
     */
    private fun describeWindow(node: AccessibilityNodeInfo?): String {
        if (node == null) return "no accessible window"
        return "${node.packageName}/${node.viewIdResourceName}: ${node.text}" +
            (0 until node.childCount).joinToString(prefix = "[", postfix = "]") { describeWindow(node.getChild(it)) }
    }
}
