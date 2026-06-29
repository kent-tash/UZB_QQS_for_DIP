package com.example.uzb_qqs_for_dip.network

import com.example.uzb_qqs_for_dip.data.model.PaymentType
import com.example.uzb_qqs_for_dip.util.DateFormat
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URI
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.roundToLong

/**
 * Клиент JSON API ofd.soliq.uz (new-ofd.soliq.uz/api/payment).
 *
 * С июня 2026 г. страницы /check и /epi отдают пустую SPA-оболочку без данных чека;
 * фактические данные загружаются через этот POST-эндпоинт (как в веб-приложении soliq).
 */
internal object OfdPaymentApi {

    private const val API_URL = "https://new-ofd.soliq.uz/api/payment"
    private const val SECRET_KEY = "thisIsPaymentSecretKey123@#"
    private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

    /** URL чеков ofd.soliq.uz, для которых доступен JSON API. */
    fun isSupportedUrl(url: String): Boolean {
        val lower = url.lowercase()
        if (!lower.contains("ofd.soliq.uz")) return false
        return lower.contains("/check") || lower.contains("/epi")
    }

    /**
     * Загружает чек через API. Возвращает null при ошибке сети, подписи или отсутствии данных.
     */
    fun tryFetch(client: OkHttpClient, qrUrl: String): ParsedReceipt? {
        val params = parseQuery(qrUrl)
        val terminalId = params["t"] ?: return null
        val paymentNo = params["r"] ?: return null
        val paymentDate = params["c"] ?: return null
        val fiscalSign = params["s"] ?: return null
        val paymentType = paymentTypeFromPath(qrUrl)

        val timestamp = System.currentTimeMillis() / 1000L
        val signature = hmacSha256Hex(SECRET_KEY, "$terminalId:$paymentNo:$timestamp")

        val bodyJson = JSONObject()
            .put("terminalId", terminalId)
            .put("paymentNo", paymentNo)
            .put("paymentDate", paymentDate)
            .put("fiscalSign", fiscalSign)
            .put("paymentType", paymentType)
            .toString()

        val req = Request.Builder()
            .url(API_URL)
            .post(bodyJson.toRequestBody(JSON_MEDIA))
            .header("Accept", "application/json")
            .header("X-Timestamp", timestamp.toString())
            .header("X-Signature", signature)
            .build()

        return try {
            client.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) return null
                parseResponse(qrUrl, text)
            }
        } catch (_: Exception) {
            null
        }
    }

    /** Разбор ответа API (доступен для unit-тестов). */
    fun parseResponse(qrUrl: String, json: String): ParsedReceipt? {
        val root = JSONObject(json)
        if (!root.optBoolean("success", false)) return null
        val data = root.optJSONObject("data") ?: return null

        val extra = data.optJSONObject("extraInfo")
        val sellerName = extra?.optString("companyName")?.takeIf { it.isNotBlank() }
        val address = extra?.optString("address")?.takeIf { it.isNotBlank() }

        val cashTotal = data.optDouble("cashTotal", 0.0)
        val cardTotal = data.optDouble("cardTotal", 0.0)
        val totalSoum = cashTotal + cardTotal
        val vatSoum = data.optDouble("vatTotal", 0.0)

        val tin = data.opt("tin")?.let { value ->
            when (value) {
                is Number -> value.toLong().toString().padStart(9, '0').takeLast(9)
                else -> value.toString().filter { it.isDigit() }.takeIf { it.length == 9 }
            }
        } ?: extra?.opt("merchantTin")?.let { value ->
            when (value) {
                is Number -> value.toLong().toString().padStart(9, '0').takeLast(9)
                else -> value.toString().filter { it.isDigit() }.takeIf { it.length == 9 }
            }
        }

        val paymentDateRaw = data.optString("paymentDate").takeIf { it.isNotBlank() }
        val purchasedAt = DateFormat.tryParseReceiptDate(paymentDateRaw)
            ?: extractDateFromUrlParam(data.optString("paymentDate"), qrUrl)

        val paymentType = when {
            cardTotal > 0.0 && cashTotal <= 0.0 -> PaymentType.CARD
            cashTotal > 0.0 && cardTotal <= 0.0 -> PaymentType.CASH
            cardTotal > cashTotal -> PaymentType.CARD
            else -> PaymentType.CASH
        }

        val fiscalSign = parseQuery(qrUrl)["s"]

        return ParsedReceipt(
            qrUrl = qrUrl,
            purchasedAt = purchasedAt,
            sellerName = sellerName,
            totalAmountTiyin = soumToTiyin(totalSoum),
            vatAmountTiyin = soumToTiyin(vatSoum),
            paymentType = paymentType,
            fiscalSign = fiscalSign,
            address = address,
            tin = tin,
            terminalId = data.optString("terminalId").takeIf { it.isNotBlank() },
            receiptNumber = data.optString("paymentNo").takeIf { it.isNotBlank() },
            nkmName = data.optString("kkmName").takeIf { it.isNotBlank() },
            sn = data.optString("kkmSerialNumber").takeIf { it.isNotBlank() },
            rawSnippet = buildRawSnippet(data, extra)
        )
    }

    private fun buildRawSnippet(data: JSONObject, extra: JSONObject?): String {
        val parts = buildList {
            extra?.optString("companyName")?.takeIf { it.isNotBlank() }?.let { add(it) }
            extra?.optString("address")?.takeIf { it.isNotBlank() }?.let { add(it) }
            data.optString("paymentDate").takeIf { it.isNotBlank() }?.let { add(it) }
            val total = data.optDouble("cashTotal", 0.0) + data.optDouble("cardTotal", 0.0)
            if (total > 0) add("Jami to`lov: $total")
            val vat = data.optDouble("vatTotal", 0.0)
            if (vat > 0) add("Umumiy QQS qiymati: $vat")
        }
        return parts.joinToString(" ").take(2000)
    }

    private fun soumToTiyin(soum: Double): Long? {
        if (soum <= 0.0) return null
        return (soum * 100.0).roundToLong()
    }

    private fun extractDateFromUrlParam(apiDate: String?, qrUrl: String): Long? {
        if (!apiDate.isNullOrBlank()) {
            DateFormat.tryParseReceiptDate(apiDate)?.let { return it }
        }
        val raw = parseQuery(qrUrl)["c"] ?: return null
        if (raw.length < 8) return null
        return try {
            val fmt = when {
                raw.length >= 14 -> "yyyyMMddHHmmss"
                raw.length >= 12 -> "yyyyMMddHHmm"
                else -> "yyyyMMdd"
            }
            java.text.SimpleDateFormat(fmt, java.util.Locale.US).apply {
                timeZone = java.util.TimeZone.getTimeZone("Asia/Tashkent")
                isLenient = false
            }.parse(raw.take(14))?.time
        } catch (_: Exception) {
            null
        }
    }

    private fun paymentTypeFromPath(url: String): String {
        val path = URI(url).path?.lowercase().orEmpty()
        return when {
            path.contains("/epi/avans") -> "AVANS"
            path.contains("/epi/kredit") -> "CREDIT"
            path.contains("/epi") -> "CHECK_EPI"
            else -> "CHECK"
        }
    }

    private fun parseQuery(url: String): Map<String, String> {
        val q = url.substringAfter('?', "")
        if (q.isEmpty()) return emptyMap()
        return q.split('&').mapNotNull { part ->
            val eq = part.indexOf('=')
            if (eq <= 0) null else part.substring(0, eq).lowercase() to part.substring(eq + 1)
        }.toMap()
    }

    private fun hmacSha256Hex(key: String, message: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return mac.doFinal(message.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }
    }
}
