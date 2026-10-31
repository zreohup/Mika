package com.wangzan101.mika.core.agent

import com.wangzan101.mika.core.model.Message
import com.wangzan101.mika.core.model.Role
import com.wangzan101.mika.core.model.Usage

/** 当前上下文大小:同一模型最近一次 usage(input + output)+ 其后消息的粗估;没有锚点就全量估算。 */
internal object UsageMeter {
    fun of(messages: List<Message>, usageOf: (Message) -> Usage?): Int {
        val anchor = messages.indexOfLast { it.role == Role.Assistant && usageOf(it) != null }
        if (anchor < 0) return TokenEstimate.of(messages)
        val usage = usageOf(messages[anchor])!!
        return usage.inputTokens + usage.outputTokens + TokenEstimate.of(messages.subList(anchor + 1, messages.size))
    }
}
