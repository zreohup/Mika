package com.wangzan101.mika.core.uistate

import com.wangzan101.mika.core.access.AppAccessDenied
import com.wangzan101.mika.core.access.AppAccessPolicy
import kotlinx.coroutines.delay

/** 手机端口的权限边界;窗口探针只读归属,不采集内容,也不为检查创建屏幕。 */
class AppAccessScreenPort(
    private val inner: ScreenPort,
    override val appAccess: AppAccessPolicy,
    private val packages: suspend () -> List<String?>,
    private val protectedUi: () -> Boolean = { false },
    private val ownPackage: String? = null,
) : ScreenPort by inner {
    override suspend fun checkAppAccess() {
        var state = appAccess.current()
        if (!state.available) throw AppAccessDenied("app_access_unavailable", null)
        if (state.blocked.isEmpty() && !protectedUi()) return
        var targets = packages()
        if (state.blocked.isNotEmpty() && targets.any { it.isNullOrBlank() }) {
            delay(100)
            targets = packages()
            state = appAccess.current()
        }
        if (!state.available) throw AppAccessDenied("app_access_unavailable", null)
        for (pkg in targets) {
            if (protectedUi() && ownPackage != null && pkg == ownPackage)
                throw AppAccessDenied("app_access_protected", pkg)
            state.denial(pkg)?.let { throw it }
        }
    }

    private fun target(packageName: String) {
        appAccess.current().denial(packageName)?.let { throw it }
    }

    private suspend fun <T> read(body: suspend () -> T): T {
        checkAppAccess()
        val value = body()
        checkAppAccess()
        return value
    }

    override suspend fun snapshot() = read { inner.snapshot() }
    override suspend fun peek() = read { inner.peek() }
    override suspend fun screenshotPng() = read { inner.screenshotPng() }
    override suspend fun readVisibleText() = read { inner.readVisibleText() }
    override suspend fun frameGrid() = read { inner.frameGrid() }
    override suspend fun isProtected() = read { inner.isProtected() }
    override suspend fun checkScreen() = read { inner.checkScreen() }
    override suspend fun tapRef(ref: String, snapshotId: String) = read { inner.tapRef(ref, snapshotId) }
    override suspend fun tapXY(x: Int, y: Int) = read { inner.tapXY(x, y) }
    override suspend fun doubleTapRef(ref: String, snapshotId: String) = read { inner.doubleTapRef(ref, snapshotId) }
    override suspend fun doubleTapXY(x: Int, y: Int) = read { inner.doubleTapXY(x, y) }
    override suspend fun typeText(ref: String?, snapshotId: String?, text: String) = read { inner.typeText(ref, snapshotId, text) }
    override suspend fun clearText(ref: String?, snapshotId: String?) = read { inner.clearText(ref, snapshotId) }
    override suspend fun typeAt(x: Int, y: Int, text: String) = read { inner.typeAt(x, y, text) }
    override suspend fun clearAt(x: Int, y: Int) = read { inner.clearAt(x, y) }
    override suspend fun scroll(ref: String?, snapshotId: String?, down: Boolean) = read { inner.scroll(ref, snapshotId, down) }
    override suspend fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Long) = read { inner.swipe(x1, y1, x2, y2, durationMs) }
    override suspend fun idleScroll(fraction: Double) = read { inner.idleScroll(fraction) }
    override suspend fun gesture(paths: List<List<Pair<Int, Int>>>, durationMs: Long) = read { inner.gesture(paths, durationMs) }
    override suspend fun back() = read { inner.back() }
    override suspend fun pressKey(keyCode: Int) = read { inner.pressKey(keyCode) }
    override suspend fun setWheel(ref: String, snapshotId: String, value: String) = read { inner.setWheel(ref, snapshotId, value) }
    override suspend fun waitStable(timeoutMs: Long) = read { inner.waitStable(timeoutMs) }
    override suspend fun awaitChange(timeoutMs: Long) = read { inner.awaitChange(timeoutMs) }
    override suspend fun waitFor(condition: String, value: String?, timeoutMs: Long) = read { inner.waitFor(condition, value, timeoutMs) }
    override suspend fun nextEvent(kind: String, packageName: String?, timeoutMs: Long) = read { inner.nextEvent(kind, packageName, timeoutMs) }
    override suspend fun watchEvents(kind: String, packageName: String?, durationMs: Long, maxEvents: Int) = read { inner.watchEvents(kind, packageName, durationMs, maxEvents) }
    override suspend fun openLink(action: String?, uri: String?, packageName: String?): ActionResult {
        packageName?.let(::target)
        return inner.openLink(action, uri, packageName)
    }

    override suspend fun openApp(packageName: String, cold: Boolean): ActionResult {
        target(packageName)
        return inner.openApp(packageName, cold)
    }
    override suspend fun openAppCopy(packageName: String, userId: Int, cold: Boolean): ActionResult {
        target(packageName)
        return inner.openAppCopy(packageName, userId, cold)
    }
    override suspend fun shareText(packageName: String, text: String): ActionResult {
        target(packageName)
        return inner.shareText(packageName, text)
    }
}
