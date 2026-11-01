package com.wangzan101.mika.core.remote

import com.wangzan101.mika.core.device.DeviceAccess
import com.wangzan101.mika.core.mcp.McpRpc
import com.wangzan101.mika.core.mcp.McpTaskTools
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.InetAddress

/**
 * 已认证连接共用的协议入口。调用方先完成各自的认证:局域网由 RemoteAccess,出站隧道由已配对的 HTTPS 网关。
 * 不监听、不发网络请求、不判定来源可信度;两种传输共用任务用例与近期活动。
 */
class RemoteEndpoints(tasks: RemoteTasks, serverVersion: String, private val clock: () -> Long = System::currentTimeMillis,
    device: DeviceAccess? = null) {
    private val service = RemoteTaskService(tasks)
    private val api = RemoteTaskApi(service, serverVersion, device)
    private val rpc = McpRpc(McpTaskTools(service, device), serverVersion) { remote, name -> seen(remote, name) }
    private val clients = LinkedHashMap<String, Long>()
    private val mutable = MutableStateFlow(RemoteActivity())
    val activity = mutable.asStateFlow()

    fun handle(req: HttpRequest): HttpResponse {
        if (req.path == McpRpc.PATH) return rpc.handle(req)
        if (req.path == RemoteTaskApi.ROOT || req.path.startsWith("${RemoteTaskApi.ROOT}/")) {
            seen(req.remote, req.header("x-mika-client")?.take(64))
            return api.handle(req)
        }
        return RemoteTaskApi.error(404, "not_found", "No such endpoint")
    }

    private fun seen(remote: InetAddress, name: String?) {
        val now = clock()
        synchronized(this) {
            val who = remote.hostAddress ?: "?"
            val key = if (name != null) "$who|$name" else clients.keys.lastOrNull { it.startsWith("$who|") } ?: "$who|"
            clients.remove(key); clients[key] = now
            clients.entries.removeAll { now - it.value > RemoteHost.RECENT_MS }
            mutable.value = RemoteActivity(clients.size, now)
        }
    }
}
