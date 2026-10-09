package com.example.uzb_qqs_for_dip.ui

import androidx.annotation.StringRes

enum class CameraScanStatus { SUCCESS, WARNING, DUPLICATE, ERROR }

data class CameraScanResult(
    val status: CameraScanStatus,
    @StringRes val message: Int,
    val args: List<Any> = emptyList(),
    /** Actual database receipt identity; only completed operations may supply it. */
    val completedReceiptId: Long? = null,
    val retryable: Boolean = false,
    val outOfPeriod: Boolean = false,
)

/** Main-thread owned session. Every visible code gets a turn, even in a sheet. */
class CameraScanSession(private val rearmAfterMs: Long = 1200L) {
    private val lastSeen = mutableMapOf<String, Long>()
    private val handled = mutableSetOf<String>()
    private val completed = mutableMapOf<String, Long>()
    private val counted = mutableSetOf<Long>()
    var active: String? = null
        private set
    val count: Int get() = counted.size

    fun next(codes: List<String>, nowMs: Long): String? {
        val visible = codes.map(String::trim).filter(String::isNotEmpty).distinct()
        lastSeen.entries.removeAll { (code, seen) ->
            (nowMs - seen >= rearmAfterMs).also { if (it) handled.remove(code) }
        }
        visible.forEach { lastSeen[it] = nowMs }
        if (active != null) return null
        return visible.firstOrNull { it !in handled }?.also {
            handled.add(it)
            active = it
        }
    }

    fun wasCompleted(code: String): Boolean = code in completed

    fun finish(code: String, result: CameraScanResult) {
        result.completedReceiptId?.let { completed[code] = it; counted.add(it) }
        if (active == code) active = null
    }

    fun retry(code: String) { handled.remove(code) }
    fun cancel() { active?.let(handled::remove); active = null }
}
