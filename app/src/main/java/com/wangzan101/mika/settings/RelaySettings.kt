package com.wangzan101.mika.settings

import com.wangzan101.mika.core.remote.RelayGateway
import com.wangzan101.mika.core.remote.RelayPairing
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

data class RelayConfig(val enabled: Boolean = false, val gateway: String? = null) {
    val paired get() = gateway != null
}

/** 网关地址和设备凭据整份加密原子保存。状态流只保留非秘密字段;读不懂即关闭。 */
class RelaySettings(private val dir: File, private val cipher: KeyCipher) {
    private val file = File(dir, "remote-relay.bin")
    private val mutable = MutableStateFlow(runCatching { read()?.let { config(it) } }.getOrNull() ?: RelayConfig())
    val config = mutable.asStateFlow()
    init {
        runCatching {
            read()?.takeIf { "clientToken" in it }?.let { old ->
                val next = config(old)
                write(RelayPairing(string(old, "gateway"), string(old, "deviceToken")), next.enabled)
            }
        }.onFailure { mutable.value = RelayConfig() }
    }

    @Synchronized fun pairing(): RelayPairing? = runCatching {
        val o = read() ?: return null
        if (config(o) != config.value) return null
        RelayPairing(string(o, "gateway"), string(o, "deviceToken"))
    }.getOrNull()

    @Synchronized fun save(pairing: RelayPairing) { write(pairing, false) }

    @Synchronized fun setEnabled(on: Boolean) {
        try {
            val p = pairing()
            if (p == null) { check(!on) { "Pair the gateway first" }; clear(); return }
            write(p, on)
        } catch (e: Exception) {
            if (!on) { mutable.value = RelayConfig(); file.delete() }
            throw e
        }
    }

    @Synchronized fun clear() {
        check(!file.exists() || file.delete()) { "Cannot remove pairing" }
        mutable.value = RelayConfig()
    }

    private fun config(o: JsonObject): RelayConfig {
        val gateway = RelayGateway.parse(string(o, "gateway")).toString()
        check(Regex("[A-Za-z0-9_-]{43}").matches(string(o, "deviceToken")))
        return RelayConfig((o["enabled"] as? JsonPrimitive)?.booleanOrNull == true, gateway)
    }

    private fun read(): JsonObject? {
        if (!file.exists()) return null
        val bytes = cipher.decrypt(file.readBytes())
        return try { Json.parseToJsonElement(bytes.toString(Charsets.UTF_8)) as JsonObject } finally { bytes.fill(0) }
    }

    private fun write(pairing: RelayPairing, enabled: Boolean) {
        val o = buildJsonObject {
            put("enabled", enabled); put("gateway", pairing.gateway)
            put("deviceToken", pairing.deviceToken)
        }
        val next = config(o)
        val bytes = o.toString().toByteArray(Charsets.UTF_8)
        dir.mkdirs(); val tmp = File(dir, file.name + ".tmp")
        try {
            tmp.writeBytes(cipher.encrypt(bytes))
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            mutable.value = next
        } finally { bytes.fill(0); tmp.delete() }
    }

    private fun string(o: JsonObject, name: String) = (o.getValue(name) as JsonPrimitive).content
}
