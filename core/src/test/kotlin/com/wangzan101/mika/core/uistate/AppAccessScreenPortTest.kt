package com.wangzan101.mika.core.uistate

import com.wangzan101.mika.core.access.AppAccessDenied
import com.wangzan101.mika.core.access.AppAccessPolicy
import com.wangzan101.mika.core.access.AppAccessState
import com.wangzan101.mika.core.tool.FakeScreen
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class AppAccessScreenPortTest {
    @Test fun emptyBlocklistAllowsUnknownPackageWithoutProbe() = runTest {
        assertNull(AppAccessState().denial(null))
        val screen = AppAccessScreenPort(FakeScreen(), AppAccessPolicy.ALLOW,
            packages = { error("empty blocklist must not query windows") })
        screen.snapshot()
    }

    @Test fun unknownPackageIsReadOnceMore() = runTest {
        var probes = 0
        val screen = AppAccessScreenPort(FakeScreen(), blocked(), packages = {
            if (++probes == 1) listOf(null) else listOf("allowed")
        })
        screen.checkAppAccess()
        assertEquals(2, probes)
    }

    @Test fun stillUnknownPackageIsRefusedAfterOneRetry() = runTest {
        var probes = 0
        val screen = AppAccessScreenPort(FakeScreen(), blocked(), packages = { probes++; listOf(null) })
        denied("app_access_unverified") { screen.checkAppAccess() }
        assertEquals(2, probes)
    }

    @Test fun retryCanIdentifyBlockedPackage() = runTest {
        var probes = 0
        val screen = AppAccessScreenPort(FakeScreen(), blocked(), packages = {
            if (++probes == 1) listOf(null) else listOf("bank")
        })
        denied("app_blocked") { screen.checkAppAccess() }
        assertEquals(2, probes)
    }

    @Test fun unavailableSettingsDenyEvenWhenListIsEmpty() = runTest {
        val screen = AppAccessScreenPort(FakeScreen(), AppAccessPolicy { AppAccessState(available = false) },
            packages = { error("must reject before probing") })
        denied("app_access_unavailable") { screen.checkAppAccess() }
    }

    @Test fun uncreatedDisplayDoesNotNeedAnApp() = runTest {
        val screen = AppAccessScreenPort(FakeScreen(), blocked(), packages = { emptyList() })
        screen.checkAppAccess()
    }

    @Test fun sensitiveOwnUiIsProtectedEvenWithEmptyList() = runTest {
        val screen = AppAccessScreenPort(FakeScreen(), AppAccessPolicy.ALLOW, packages = { listOf("mika") },
            protectedUi = { true }, ownPackage = "mika")
        denied("app_access_protected") { screen.snapshot() }
    }

    @Test fun blocklistIsRereadAfterWaiting() = runTest {
        var state = AppAccessState(blocked = setOf("bank"))
        var probes = 0
        val screen = AppAccessScreenPort(FakeScreen(), AppAccessPolicy { state }, packages = {
            probes++
            if (probes == 2) state = AppAccessState()
            listOf(null)
        })
        screen.checkAppAccess()
        assertEquals(2, probes)
    }

    @Test fun runtimeBlockStopsNextReadAndAllowRestoresIt() = runTest {
        var state = AppAccessState()
        var reads = 0
        val inner = object : ScreenPort by FakeScreen() {
            override suspend fun screenshotPng(): ByteArray { reads++; return byteArrayOf(1) }
        }
        val screen = AppAccessScreenPort(inner, AppAccessPolicy { state }, packages = { listOf("bank") })
        assertArrayEquals(byteArrayOf(1), screen.screenshotPng())
        state = AppAccessState(blocked = setOf("bank"), revision = 1)
        denied("app_blocked") { screen.screenshotPng() }
        assertEquals(1, reads)
        state = AppAccessState(revision = 2)
        assertArrayEquals(byteArrayOf(1), screen.screenshotPng())
    }

    @Test fun readThatCrossesIntoBlockedAppDoesNotReturnContent() = runTest {
        var pkg = "allowed"
        val inner = object : ScreenPort by FakeScreen() {
            override suspend fun screenshotPng(): ByteArray { pkg = "bank"; return byteArrayOf(1) }
        }
        val screen = AppAccessScreenPort(inner, blocked(), packages = { listOf(pkg) })
        denied("app_blocked") { screen.screenshotPng() }
    }

    @Test fun canLeaveBlockedAppButCannotLaunchBlockedTarget() = runTest {
        val screen = AppAccessScreenPort(FakeScreen(), blocked(), packages = { listOf("bank") })
        screen.openApp("allowed")
        denied("app_blocked") { screen.openApp("bank") }
        denied("app_blocked") { screen.openAppCopy("bank", 10) }
        denied("app_blocked") { screen.shareText("bank", "text") }
    }

    private fun blocked() = AppAccessPolicy { AppAccessState(blocked = setOf("bank")) }

    private suspend fun denied(code: String, body: suspend () -> Unit) {
        try {
            body()
            fail("expected $code")
        } catch (e: AppAccessDenied) {
            assertEquals(code, e.code)
        }
    }
}
