package com.example.uzb_qqs_for_dip.util

import android.content.Intent
import android.net.Uri

object ReceiptUrlExtractor {

    private val SUPPORTED_HOSTS = setOf(
        "ofd.soliq.uz",
        "my.soliq.uz",
        "ofd.multicard.uz"
    )

    private val URL_REGEX = Regex("""https?://[^\s<>"'«»]+""", RegexOption.IGNORE_CASE)

    private val TRAILING_PUNCTUATION = charArrayOf(
        '.', ',', ';', ':', '!', '?', ')', ']', '}', '>', '"', '\'', '»', '”', '’'
    )

    /**
     * Извлекает и валидирует URL чека из Intent (ACTION_VIEW или ACTION_SEND).
     * Возвращает очищенную ссылку с валидным хостом или null.
     */
    fun extractFromIntent(intent: Intent?): String? {
        if (intent == null) return null
        return when (intent.action) {
            Intent.ACTION_VIEW -> {
                val dataString = intent.dataString ?: intent.data?.toString()
                dataString?.let { extractUrlFromText(it) }
            }
            Intent.ACTION_SEND -> {
                val extraText = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
                if (!extraText.isNullOrBlank()) {
                    val url = extractUrlFromText(extraText)
                    if (url != null) return url
                }

                val clipData = intent.clipData
                if (clipData != null) {
                    for (i in 0 until clipData.itemCount) {
                        val item = clipData.getItemAt(i)
                        val text = item.text?.toString()
                        if (!text.isNullOrBlank()) {
                            val url = extractUrlFromText(text)
                            if (url != null) return url
                        }
                        val uri = item.uri?.toString()
                        if (!uri.isNullOrBlank()) {
                            val url = extractUrlFromText(uri)
                            if (url != null) return url
                        }
                    }
                }
                null
            }
            else -> {
                val dataString = intent.dataString ?: intent.data?.toString()
                if (!dataString.isNullOrBlank()) {
                    extractUrlFromText(dataString)
                } else null
            }
        }
    }

    /**
     * Ищет URL в произвольном тексте и возвращает первый подходящий адрес чека.
     */
    fun extractUrlFromText(text: String): String? {
        if (text.isBlank()) return null

        for (match in URL_REGEX.findAll(text)) {
            val candidate = cleanUrl(match.value)
            if (isValidReceiptUrl(candidate)) {
                return candidate
            }
        }

        val trimmedCandidate = cleanUrl(text.trim())
        if (isValidReceiptUrl(trimmedCandidate)) {
            return trimmedCandidate
        }

        if (!trimmedCandidate.startsWith("http://", ignoreCase = true) &&
            !trimmedCandidate.startsWith("https://", ignoreCase = true)
        ) {
            val withHttps = "https://$trimmedCandidate"
            if (isValidReceiptUrl(withHttps)) {
                return withHttps
            }
        }

        return null
    }

    /**
     * Удаляет хвостовые знаки препинания, попавшие в ссылку из предложения.
     */
    fun cleanUrl(raw: String): String {
        var trimmed = raw.trim()
        while (trimmed.isNotEmpty() && trimmed.last() in TRAILING_PUNCTUATION) {
            trimmed = trimmed.dropLast(1)
        }
        return trimmed
    }

    /**
     * Проверяет, что URL имеет протокол http/https и хост из списка поддерживаемых.
     */
    fun isValidReceiptUrl(url: String): Boolean {
        if (url.isBlank()) return false
        val host = runCatching {
            val uri = Uri.parse(url)
            val scheme = uri.scheme?.lowercase()
            if (scheme != "http" && scheme != "https") return false
            uri.host?.lowercase()
        }.getOrElse {
            // Fallback when android.net.Uri is not mocked in JVM unit tests
            runCatching {
                val jUri = java.net.URI(url)
                val scheme = jUri.scheme?.lowercase()
                if (scheme != "http" && scheme != "https") return false
                jUri.host?.lowercase()
            }.getOrNull()
        } ?: return false
        return host in SUPPORTED_HOSTS
    }
}
