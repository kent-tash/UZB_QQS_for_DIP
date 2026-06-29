package com.example.uzb_qqs_for_dip.network

import com.example.uzb_qqs_for_dip.data.model.PaymentType
import com.example.uzb_qqs_for_dip.util.DateFormat
import com.example.uzb_qqs_for_dip.util.MoneyFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/**
 * Распарсенный чек, полученный по URL из QR.
 */
data class ParsedReceipt(
    val qrUrl: String,
    val purchasedAt: Long?,
    val sellerName: String?,
    val totalAmountTiyin: Long?,
    val vatAmountTiyin: Long?,
    val paymentType: PaymentType,
    val fiscalSign: String?,
    val address: String? = null,
    val tin: String? = null,
    val terminalId: String? = null,
    val receiptNumber: String? = null,
    val nkmName: String? = null,
    val sn: String? = null,
    val rawSnippet: String?
) {
    val isValid: Boolean
        get() = purchasedAt != null && !sellerName.isNullOrBlank() &&
            totalAmountTiyin != null && vatAmountTiyin != null
}

/**
 * Загружает страницу электронного чека Узбекистана (my.soliq.uz, ofd.soliq.uz, ofd.multicard.uz, …)
 * и извлекает: дату/время покупки, продавца, итоговую сумму и НДС.
 *
 * Стратегия многоуровневая: лейблы (от более специфичных к общим) → таблица → свободный текст
 * → параметры URL чека (для даты — самый надёжный источник).
 */
class ReceiptParser(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()
) {

    suspend fun fetchAndParse(qrPayload: String): Result<ParsedReceipt> = withContext(Dispatchers.IO) {
        runCatching {
            val url = qrPayload.trim()
            require(url.startsWith("http://") || url.startsWith("https://")) {
                "QR не содержит ссылку на чек: $url"
            }

            // ofd.soliq.uz с июня 2026 г. отдаёт SPA без данных — чек берём из JSON API.
            if (OfdPaymentApi.isSupportedUrl(url)) {
                OfdPaymentApi.tryFetch(client, url)?.let { apiParsed ->
                    if (apiParsed.isValid) return@runCatching apiParsed
                }
            }

            val req = Request.Builder()
                .url(url)
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 14; QQS-Scanner) AppleWebKit/537.36 " +
                        "(KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"
                )
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "ru,uz;q=0.9,en;q=0.8")
                .build()

            client.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    error("HTTP ${resp.code} при загрузке страницы чека")
                }
                val htmlParsed = parseHtml(url, body)
                if (htmlParsed.isValid) {
                    htmlParsed
                } else if (OfdPaymentApi.isSupportedUrl(url)) {
                    OfdPaymentApi.tryFetch(client, url)
                        ?: htmlParsed
                } else {
                    htmlParsed
                }
            }
        }
    }

    /** Открыто для тестов: разбор HTML без сетевого вызова. */
    fun parseHtml(qrUrl: String, html: String): ParsedReceipt {
        val doc = Jsoup.parse(html)
        val text = normalizeText(doc.text())

        val date = extractDate(doc, text) ?: extractDateFromUrl(qrUrl)
        val seller = extractSeller(doc, text)
        val total = extractTotal(doc, text)
        val vat = extractVat(doc, text)
        val paymentType = extractPaymentType(text)
        val fiscalSign = extractFiscalSign(doc, text) ?: extractFiscalFromUrl(qrUrl)

        // Новые поля для детального заголовка
        val address = extractAddress(doc, text)
        // ИНН/STIR продавца (9 цифр) — отдельным полем, рядом с названием организации
        val tin = extractTin(doc, text)
        // Терминал может начинаться на разные префиксы (VG, EP, YZ, LG, …)
        val termId = extractTerminalId(doc, text)
        val recNum = extractByLabel(doc, text, listOf("Chek raqami", "Номер чека", "Receipt number", "Check number"))
        // Marketpleys nomi — специфичный лейбл для Uzum и других площадок
        val nkm = extractByLabel(doc, text, listOf("Marketpleys nomi", "Onlayn NKM nomi", "NKM nomi", "Название НКМ"))
        val snNum = extractByLabel(doc, text, listOf("SN", "Zavod raqami", "Серийный номер", "Serial number"))

        return ParsedReceipt(
            qrUrl = qrUrl,
            purchasedAt = date,
            sellerName = seller,
            totalAmountTiyin = total,
            vatAmountTiyin = vat,
            paymentType = paymentType,
            fiscalSign = fiscalSign,
            address = address,
            tin = tin,
            terminalId = termId,
            receiptNumber = recNum,
            nkmName = nkm,
            sn = snNum,
            rawSnippet = text.take(2000)
        )
    }

    private fun extractAddress(doc: Document, text: String): String? {
        // 1. Поиск по явным меткам в DOM
        val labels = listOf("Manzil", "Адрес", "Address", "Адрес торговой точки", "Манзил")
        labelValue(doc, labels)?.let { return cleanValue(it) }

        // 2. Специфичная логика для ofd.soliq.uz и EPI: адрес после названия организации
        val headings = doc.select("h1, h2, h3, b, strong, .company-name, .org-name, .seller-name")
        for (h in headings) {
            val hText = h.text().trim().lowercase()
            if (hText.isEmpty() || hText.contains("savdo cheki") || hText.contains("sotuv")) continue
            
            // Проверяем следующие за заголовком элементы. Ищем адрес и возможный индекс.
            var next = h.nextSibling()
            var count = 0
            while (next != null && count < 8) {
                val t = when (next) {
                    is org.jsoup.nodes.TextNode -> next.text().trim()
                    is Element -> next.text().trim()
                    else -> ""
                }
                
                if (t.length >= 5) {
                    if (isLikelyAddress(t)) {
                        // Нашли базовую часть адреса. Теперь ищем почтовый индекс (6 цифр) в следующих узлах.
                        // ИНН (9 цифр) намеренно НЕ присоединяем — он извлекается отдельным полем.
                        var fullAddress = t
                        var subNext = next.nextSibling()
                        var subCount = 0
                        while (subNext != null && subCount < 4) {
                            val st = when (subNext) {
                                is org.jsoup.nodes.TextNode -> subNext.text().trim()
                                is Element -> subNext.text().trim()
                                else -> ""
                            }
                            // Почтовый индекс (6 цифр)
                            if (st.matches(Regex("\\d{6}"))) {
                                fullAddress = "$fullAddress $st"
                            } else if (st.matches(Regex("\\d{9}"))) {
                                // ИНН — пропускаем, это отдельное поле
                                subNext = subNext.nextSibling()
                                subCount++
                                continue
                            } else if (st.isNotEmpty() && !st.contains(Regex("\\d{10,}"))) {
                                // Если это какой-то короткий текст (напр. номер дома), тоже берем
                                if (st.length < 20) fullAddress = "$fullAddress $st"
                                else break
                            }
                            subNext = subNext.nextSibling()
                            subCount++
                        }
                        return cleanValue(fullAddress)
                    }
                }
                next = next.nextSibling()
                count++
            }
        }

        // 3. Поиск по регулярному выражению (лейбл: значение)
        val r = Regex("(?i)(?:Manzil|Адрес|Address|Адрес торговой точки)\\s*[:\\-]?\\s*([^\\n\\r]{5,250})")
        r.find(text)?.let { return cleanValue(it.groupValues[1]) }
        
        // 4. Поиск в свободном тексте по маркерам. Собираем строку с ИНН/индексом.
        val lines = text.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
        for (i in lines.indices) {
            if (isLikelyAddress(lines[i])) {
                var res = lines[i]
                // Проверяем следующую строку на почтовый индекс (6 цифр). ИНН (9 цифр) не добавляем.
                if (i + 1 < lines.size) {
                    val nextLine = lines[i+1]
                    if (nextLine.matches(Regex("\\d{6}"))) {
                        res += " $nextLine"
                    }
                }
                return cleanValue(res)
            }
        }

        return null
    }

    private fun isLikelyAddress(s: String): Boolean {
        val lower = s.lowercase()
        // Маркеры адреса, включая сокращённые узбекские формы латиницей (sh — shahar,
        // tum — tumani, MFY — mahalla, ko'chasi/kochasi — улица, uy — дом, xonadon — квартира)
        // и узбекскую кириллицу (тумани, кўчаси, маҳалла, бозори, дўкони, худуди и т.п.).
        val addressMarkers = listOf(
            // латиница
            "tumani", "tuman", " tum", "ko'chasi", "koʻchasi", "kuchasi", "kochasi",
            "ko'cha", "koʻcha", "kocha", "mfy", "mahalla", "shahar", "shahri",
            " sh,", " sh.", "viloyat", "xonadon", " uy", " uy.", "mavzesi", "mavze",
            "bozori", "do'koni", "doʻkoni", "dokoni", "hududi",
            // кириллица (узбекская/русская)
            "тумани", "туман", " тум", "вилоят", "шаҳар", "шахар", "шаҳри", "шахри",
            "кўчаси", "кучаси", "куча", "кўча", "маҳалла", "махалла", "мфй",
            "бозори", "дўкони", "дукони", "дўкон", "дукон", "худуди", "хонадон",
            "уй", "дом", "кв.", "улица", "район", "проспект", "кават", "қават",
            // русские сокращения
            "р-он", "р-н", "ул.", "ул ", "просп", "пр-т", "пр-кт", "мкр", "кв-л",
            "проезд", "шоссе", "тупик"
        )
        val hasMarker = addressMarkers.any { lower.contains(it) }
        // Не должен быть техническим ID (ИНН 9 цифр, Терминал 12 цифр)
        val notTechnical = !s.matches(Regex("\\d{9,}")) && !s.startsWith("VG") && !s.startsWith("EP")
        return hasMarker && notTechnical && s.length in 8..300
    }

    /**
     * Извлекает ИНН/STIR продавца — девятизначное число, которое на чеке стоит
     * сразу ПОСЛЕ адреса организации (часто в теге <i>), без явной подписи.
     *
     * Стратегия: сначала явные метки STIR/ИНН/TIN, затем — главный случай ofd.soliq.uz:
     * находим блок с названием организации, доходим до строки адреса и берём первое
     * 9-значное число, идущее после неё.
     */
    private fun extractTin(doc: Document, text: String): String? {
        // 1. Явные метки STIR/ИНН/TIN.
        val labels = listOf("STIR", "ИНН", "INN", "TIN")
        for (lab in labels) {
            val r = Regex("(?i)\\b$lab\\b[^0-9]{0,20}(\\d{9})\\b")
            r.find(text)?.let { return it.groupValues[1] }
        }

        // 2. Девятизначное число сразу после адреса в блоке заголовка организации.
        tinAfterAddress(doc)?.let { return it }

        // 3. Фолбэк: отдельно стоящий 9-значный токен среди заголовочных элементов.
        for (el in doc.select("i, b, span, h3, h4")) {
            val t = el.ownText().trim()
            if (t.matches(Regex("\\d{9}"))) return t
        }

        return null
    }

    /**
     * Ищет 9-значный ИНН, стоящий после строки адреса. Сканирует контейнеры
     * с названием организации: внутри каждого проходит по дочерним узлам по порядку
     * и, как только встретит узел-адрес, возвращает первое следующее 9-значное число.
     */
    private fun tinAfterAddress(doc: Document): String? {
        val nineDigits = Regex("\\d{9}")
        val containers = doc.select("td, div, p, h1, h2, h3, h4").mapNotNull { it.parent() }.toSet() +
            doc.select("td, div, p")
        for (container in containers) {
            var addressSeen = false
            for (node in container.childNodes()) {
                val t = when (node) {
                    is org.jsoup.nodes.TextNode -> node.text().trim()
                    is Element -> node.text().trim()
                    else -> ""
                }
                if (t.isEmpty()) continue
                if (!addressSeen) {
                    if (isLikelyAddress(t)) addressSeen = true
                } else {
                    if (nineDigits.matches(t)) return t
                    // Внутри значения адреса может встретиться индекс (6 цифр) — пропускаем.
                    if (t.matches(Regex("\\d{6}"))) continue
                    // Любой другой существенный текст означает, что ИНН рядом не стоит.
                    if (t.length > 3 && !t.contains(nineDigits)) break
                    nineDigits.find(t)?.let { return it.value }
                }
            }
        }
        return null
    }

    /**
     * Извлекает идентификатор терминала/онлайн-ККМ. На чеках soliq.uz он стоит
     * отдельным жирным токеном сразу под названием/адресом организации и имеет
     * формат «2 заглавные буквы + 8–20 цифр» (VG, EP, YZ, LG, … — список открытый).
     */
    private fun extractTerminalId(doc: Document, text: String): String? {
        val pattern = Regex("^[A-Z]{2}\\d{8,20}$")
        // 1. Отдельный токен в DOM (терминал идёт по документу раньше, чем SN/штрихкоды).
        for (el in doc.select("b, td, span, h3, h4")) {
            val t = el.ownText().trim()
            if (pattern.matches(t)) return t
        }
        // 2. Явные метки (если терминал подписан).
        extractByLabel(doc, text, listOf("Terminal ID", "NKM ID", "Терминал ID"))?.let {
            if (Regex("^[A-Z]{2}\\d{8,20}$").matches(it)) return it
        }
        // 3. Фолбэк: токен «2 буквы + 8–20 цифр» в свободном тексте.
        Regex("\\b([A-Z]{2}\\d{8,20})\\b").find(text)?.let { return it.groupValues[1] }
        return null
    }

    private fun extractByLabel(doc: Document, text: String, labels: List<String>): String? {
        // 1. Поиск точного совпадения в DOM (th/td, dt/dd).
        //    Значение-дата отбраковывается: ни terminalId, ни SN, ни номер чека
        //    не могут быть датой (это защищает от случая пустого SN, когда следом идёт дата).
        labelValue(doc, labels)?.let {
            val v = cleanValue(it)
            if (v.isNotEmpty() && !isDateLike(v)) return formatValueIfNeeded(v, labels, text)
        }
        
        // 2. Специфичный фолбэк для EPI/Soliq: данные часто в ячейках <td> или <span>
        // без явных <th>, просто текстом.
        if (labels.contains("VG") || labels.contains("EP")) {
            // Ищем строку "Терминал ID: EP..." или "VG..." или просто "EP..."
            val rTerm = Regex("(?i)\\b((?:VG|EP)\\d{5,30})\\b")
            rTerm.find(text)?.let { return it.groupValues[1].uppercase() }
            
            // Если префикса нет, ищем 12-значный номер терминала
            val rDigitOnly = Regex("(?i)(?:Terminal ID|ID|Терминал|NKM ID)\\s*[:\\-]?\\s*(\\d{12})")
            rDigitOnly.find(text)?.let { return "VG${it.groupValues[1]}" }
        }

        for (lab in labels) {
            // Специальный случай для префиксов: они часто идут как часть значения
            if (lab == "VG" || lab == "EP") {
                val rPrefix = Regex("(?i)\\b($lab\\d{5,30})\\b")
                rPrefix.find(text)?.let { return it.groupValues[1].uppercase() }
            }
            
            val r = Regex("(?i)(?:$lab)\\s*[:\\-]?\\s*([^\\n\\r]{1,100})")
            r.find(text)?.let { 
                val valPart = it.groupValues[1].trim()
                // Берем первое слово или всю строку если слов нет
                val v = cleanValue(valPart.split(' ').firstOrNull() ?: "")
                // Для имен маркетплейсов берем больше слов
                val resultValue = if (lab.contains("nomi", ignoreCase = true)) cleanValue(valPart) else v
                // Дата/время — не валидное значение для SN/terminalId/номера чека.
                if (resultValue.isNotEmpty() && !isDateLike(resultValue)) {
                    return formatValueIfNeeded(resultValue, labels, text)
                }
            }
        }
        
        // 3. Фолбэк для специфичных полей
        if (labels.contains("VG") || labels.contains("EP")) {
            val rTerm = Regex("\\b((?:VG|EP)\\d{10,25})\\b")
            rTerm.find(text)?.let { return it.groupValues[1] }
            val rDigitOnly = Regex("\\b(\\d{12,14})\\b")
            rDigitOnly.find(text)?.let { return "VG${it.groupValues[1]}" }
        }
        
        if (labels.contains("SN")) {
            // Ищем SN, который НЕ является датой (не содержит точек в формате даты)
            val rSN = Regex("(?i)SN\\s*[:\\-]?\\s*([a-zA-Z0-9_-]{4,32})")
            rSN.findAll(text).forEach { m ->
                val v = m.groupValues[1]
                if (!v.contains(Regex("\\d{2}\\.\\d{2}\\.\\d{4}"))) {
                    return v
                }
            }
        }

        return null
    }

    private fun formatValueIfNeeded(v: String, labels: List<String>, fullText: String): String {
        // Если это ID терминала и он состоит только из цифр, добавляем VG. 
        // Но если в тексте рядом есть EP, то это EP.
        if ((labels.contains("VG") || labels.contains("EP")) && v.all { it.isDigit() }) {
            return if (fullText.contains("EP$v", ignoreCase = true)) "EP$v" else "VG$v"
        }
        return v
    }

    private fun cleanValue(v: String): String = v.trim().trimEnd(',', '.', ';', ':')

    /** true, если строка похожа на дату и/или время (dd.mm.yyyy, hh:mm и т.п.). */
    private fun isDateLike(s: String): Boolean =
        Regex("\\d{1,2}[./-]\\d{1,2}[./-]\\d{2,4}").containsMatchIn(s) ||
            Regex("\\d{1,2}:\\d{2}").containsMatchIn(s)

    private fun extractFiscalFromUrl(url: String): String? {
        val q = url.substringAfter('?', "")
        if (q.isEmpty()) return null
        val params = q.split('&').mapNotNull {
            val idx = it.indexOf('=')
            if (idx <= 0) null else it.substring(0, idx).lowercase() to it.substring(idx + 1)
        }.toMap()
        return params["s"] ?: params["f"] ?: params["fs"] ?: params["fp"] ?: params["code"]
    }

    private fun extractFiscalSign(doc: Document, text: String): String? {
        val labels = listOf("Fiskal belgi", "Фискальный признак", "FP", "FB", "Fiskal belgisi")
        for (lab in labels) {
            val r = Regex("(?i)(?:$lab)\\s*[:\\-]?\\s*([0-9]{6,20})")
            r.find(text)?.let { return it.groupValues[1].trim() }
        }
        return labelValue(doc, labels)
    }

    private fun extractPaymentType(text: String): PaymentType {
        // Узбекские чеки часто пишут «Naqd pul» (наличные) или «Bank kartasi» (карта).
        // Если в тексте есть упоминание карты или терминала — считаем картой.
        val lower = text.lowercase()
        return if (lower.contains("karta") || lower.contains("terminal") || lower.contains("uzcard") || lower.contains("humo")) {
            PaymentType.CARD
        } else if (lower.contains("naqd")) {
            PaymentType.CASH
        } else {
            // По умолчанию для современных чеков (особенно электронных по QR) чаще всего карта.
            PaymentType.CARD
        }
    }

    private fun normalizeText(s: String): String =
        s.replace("\u00A0", " ").replace(Regex("[\\t\\r]+"), " ").replace(Regex(" {2,}"), " ")

    // -------- Date/time --------

    private fun extractDate(doc: Document, text: String): Long? {
        // 1) Метки типа "Sana", "Дата", "Vaqt"
        val labelRegexes = listOf(
            Regex("(?i)(?:Дата(?:\\s+и\\s+время)?(?:\\s+покупки)?|Sana(?:\\s+va\\s+vaqt)?|Vaqt|Sana/Vaqt|Date(?:\\s+and\\s+time)?)\\s*[:\\-]?\\s*([0-9]{1,2}[./\\-][0-9]{1,2}[./\\-][0-9]{2,4}(?:(?:,\\s*|\\s+|T)[0-9]{1,2}:[0-9]{2}(?::[0-9]{2})?)?)"),
            Regex("(?i)Чек\\s+от\\s*[:\\-]?\\s*([0-9./\\-: T,]{8,25})")
        )
        for (regex in labelRegexes) {
            val m = regex.find(text) ?: continue
            DateFormat.tryParseReceiptDate(m.groupValues[1].trim())?.let { return it }
        }

        // 2) Поиск по DOM-меткам.
        labelValue(doc, listOf("Дата", "Sana", "Vaqt", "Sana/Vaqt", "Date", "Дата и время"))
            ?.let { DateFormat.tryParseReceiptDate(it)?.let { ts -> return ts } }

        // 3) В страницах ofd.soliq.uz/epi дата часто стоит в тэге <i> или <b> вида "29.03.2026, 18:58"
        //    — сканируем все короткие текстовые узлы и берём максимально полную дату+время.
        val candidates = mutableListOf<Long>()
        doc.select("i, b, span, td, p").forEach { el ->
            val t = el.ownText().trim().replace("\u00A0", " ")
            if (t.length in 8..30 && t.contains(Regex("\\d{1,2}[./\\-]\\d{1,2}[./\\-]\\d{2,4}"))) {
                DateFormat.tryParseReceiptDate(t)?.let { candidates.add(it) }
            }
        }
        if (candidates.isNotEmpty()) {
            // Предпочитаем кандидата, у которого есть ненулевое время (минуты/часы).
            return candidates.maxByOrNull { ts ->
                val cal = java.util.Calendar.getInstance(TimeZone.getTimeZone("Asia/Tashkent"))
                cal.timeInMillis = ts
                cal.get(java.util.Calendar.HOUR_OF_DAY) * 3600 + cal.get(java.util.Calendar.MINUTE) * 60
            }
        }

        // 4) Общий поиск даты в свободном тексте (включая разделитель ", ").
        val freeRegex = Regex(
            "\\b(\\d{1,2}[./\\-]\\d{1,2}[./\\-]\\d{2,4}(?:(?:,\\s*|\\s+|T)\\d{1,2}:\\d{2}(?::\\d{2})?)?)\\b"
        )
        val all = freeRegex.findAll(text).mapNotNull { DateFormat.tryParseReceiptDate(it.groupValues[1]) }.toList()
        return all.maxByOrNull { it }
    }

    /** Извлекает дату из параметра c= в URL чека, формат YYYYMMDDHHMMSS. */
    private fun extractDateFromUrl(url: String): Long? {
        val raw = parseQuery(url)["c"] ?: return null
        if (raw.length < 8) return null
        val sdf = SimpleDateFormat(
            when {
                raw.length >= 14 -> "yyyyMMddHHmmss"
                raw.length >= 12 -> "yyyyMMddHHmm"
                raw.length >= 10 -> "yyyyMMddHH"
                else -> "yyyyMMdd"
            },
            Locale.US
        ).apply {
            timeZone = TimeZone.getTimeZone("Asia/Tashkent")
            isLenient = false
        }
        return try {
            sdf.parse(raw.take(14))?.time
        } catch (_: Exception) {
            null
        }
    }

    private fun parseQuery(url: String): Map<String, String> {
        val q = url.substringAfter('?', "")
        if (q.isEmpty()) return emptyMap()
        return q.split('&').mapNotNull {
            val eq = it.indexOf('=')
            if (eq <= 0) null else it.substring(0, eq).lowercase() to it.substring(eq + 1)
        }.toMap()
    }

    // -------- Seller --------

    private fun extractSeller(doc: Document, text: String): String? {
        val labels = listOf(
            "Юридическое лицо", "Юр. лицо", "Юр.лицо", "Наименование юр. лица",
            "Наименование организации", "Наименование", "Продавец",
            "Yuridik shaxs", "Yuridik shaxs nomi", "Sotuvchi", "Tashkilot",
            "Korxona", "Tashkilot nomi", "Seller", "Legal entity", "Company"
        )
        // 1) Точная подпись «Юр. лицо: ...» в DOM (th/td, dt/dd, label-классы).
        labelValue(doc, labels)?.let { return cleanSeller(it) }

        // 2) Заголовок: страница ofd.soliq.uz/epi и ofd.soliq.uz/check выводит
        //    название организации в <h1>/<h2>/<h3> (первый «Savdo cheki/Sotuv» исключаем).
        //    Этот блок намеренно идёт ПЕРЕД свободным текстовым regex-ом ниже:
        //    в названиях вида «"ANGLESEY FOOD" ... XORIJIY KORXONA» слово «Korxona»
        //    подставляется как часть юрлица, и regex ниже ошибочно считает его «лейблом».
        extractSellerFromHeadings(doc)?.let { return cleanSeller(it) }

        // 3) Свободный текст вида «Sotuvchi: ...». Требуем явный разделитель ":" / "-"
        //    и word-boundary вокруг лейбла, чтобы не цепляться за хвостовые слова
        //    в названии организации (XORIJIY KORXONA, ZAO, …).
        val regex = Regex(
            "(?i)(?:^|[^\\p{L}\\p{Nd}])(?:Юридическое\\s+лицо|Юр\\.?\\s*лицо|" +
                "Наименование(?:\\s+организации|\\s+юр\\.?\\s*лица)?|Продавец|Sotuvchi|" +
                "Yuridik\\s+shaxs(?:\\s+nomi)?|Tashkilot(?:\\s+nomi)?|Korxona|Seller|Company)" +
                "(?![\\p{L}\\p{Nd}])\\s*[:\\-]\\s*([^\\n\\r]{2,160})"
        )
        regex.find(text)?.let { return cleanSeller(it.groupValues[1]) }

        return null
    }

    /**
     * Ищем юрлицо среди заголовков (h1…h4). Первый «Savdo cheki/Sotuv»
     * и подобные технические заголовки исключаем, остальные считаем
     * кандидатами в наименование организации.
     */
    private fun extractSellerFromHeadings(doc: Document): String? {
        val excluded = setOf(
            "savdo cheki/sotuv", "savdo cheki / sotuv", "savdo cheki", "kassa cheki",
            "soliq", "yuklab olish", "online check", "электронный чек", "qr-чек",
            "электронный фискальный чек", "fiskal chek", "чек", "продажа", "sotuv",
            "xaridingiz uchun rahmat", "xaridingiz uchun rahmat!", "rahmat", "rahmat!",
            "savdo", "savdo чеки"
        )
        val orgSuffixes = listOf(" AJ", " MCHJ", " OOO", " OAJ", " AO", " ZAO", " JSh", " ИП", " UE", " QK", " ChP")
        // Маркеры узбекских организационно-правовых форм, которые встречаются
        // прямо в названии (без кавычек) — например, «MAS`ULIYATI CHEKLANGAN JAMIYAT
        // XORIJIY KORXONA». На них тоже ориентируемся.
        val orgKeywords = Regex(
            "(?i)\\b(?:MAS[\\u02BB`'’]ULIYATI\\s+CHEKLANGAN\\s+JAMIYAT|XORIJIY\\s+KORXONA|" +
                "AKSIYADORLIK\\s+JAMIYATI|YOPIQ\\s+AKSIYADORLIK)\\b"
        )
        for (h in doc.select("h1, h2, h3, h4")) {
            val t = h.text().trim()
            if (t.length < 3 || t.length > 200) continue
            if (excluded.any { it.equals(t, ignoreCase = true) }) continue
            val looksLikeOrg = t.contains('"') || t.contains('«') ||
                orgSuffixes.any { t.endsWith(it, ignoreCase = true) } ||
                orgKeywords.containsMatchIn(t)
            if (looksLikeOrg) return t
        }
        // Если ни один h* не похож на юрлицо — берём первый не-«технический» заголовок.
        for (h in doc.select("h1, h2, h3")) {
            val t = h.text().trim()
            if (t.isNotEmpty() && !excluded.any { it.equals(t, ignoreCase = true) }) {
                return t
            }
        }
        return null
    }

    private fun cleanSeller(raw: String): String {
        var s = raw.trim().trimEnd(',', '.', ';')
        // Обрезаем хвост вида "ИНН ...", "STIR ...", "ИКПУ ..." и т.п.
        s = s.replace(Regex("(?i)\\b(?:ИНН|STIR|TIN|ИКПУ|MFO|MXIK|расчётный\\s+счёт|Адрес|Manzil)\\b.*"), "").trim()
        // Если в строке есть адрес (например, район, улица), попробуем обрезать его.
        // Узбекские адреса часто содержат " tumani", " ko'chasi", " koʻchasi", " kuchasi", " viloyati", " shahri", " sh.", " v."
        val addressMarkers = listOf(" tumani", " ko'chasi", " koʻchasi", " kuchasi", " viloyati", " shahri", " sh.", " v.")
        for (m in addressMarkers) {
            val idx = s.indexOf(m, ignoreCase = true)
            if (idx > 10) { // Оставляем хотя бы 10 символов названия
                s = s.substring(0, idx).trim()
            }
        }
        return s.ifEmpty { raw.trim() }
    }

    // -------- Money labels (общая логика для total и vat) --------

    /** Регексы по упорядоченному списку меток: первый, давший число — победил.
     *  Шаблон значения должен начинаться с цифры — иначе одиночный " " после метки
     *  «съел» бы значение и дал MoneyFormat.toTiyin = 0. */
    private fun firstMoneyMatch(text: String, labels: List<String>): Long? {
        // \b плохо работает с не-ASCII (НДС): после "С" \b может не стоять, поэтому
        // используем явные альтернативы границ — конец строки или не-буквенно-цифровой
        // символ. Это покрывает и кириллические метки.
        for (lab in labels) {
            val r = Regex("(?i)(?:^|[^\\p{L}\\p{Nd}])(?:$lab)(?![\\p{L}\\p{Nd}])\\s*[:\\-]?\\s*([\\d][\\d \\u00A0',.\\-]*)")
            val m = r.find(text) ?: continue
            val v = MoneyFormat.toTiyin(m.groupValues[1])
            if (v > 0) return v
        }
        return null
    }

    // -------- Total amount --------

    private fun extractTotal(doc: Document, text: String): Long? {
        // Метки в порядке приоритета: специфичные → общие.
        val labels = listOf(
            "Jami\\s+to[\\u02BB`'’]lov",
            "Jami\\s+to'lov",
            "Jami\\s+to\\u02BBlov",
            "Jami\\s+tolov",
            "Итоговая\\s+сумма\\s+покупки",
            "Сумма\\s+к\\s+оплате",
            "Итоговая\\s+сумма",
            "Итого\\s+к\\s+оплате",
            "Итого",
            "Jami\\s+summa",
            "Jami",
            "Grand\\s+total",
            "Total\\s+amount",
            "Total"
        )
        firstMoneyMatch(text, labels)?.let { return it }

        // Доп.фолбэк через структурный матчер по DOM.
        labelValue(doc, listOf(
            "Jami to`lov", "Jami to'lov", "Jami toʻlov", "Jami tolov",
            "Итого", "Итоговая сумма", "Сумма к оплате", "Total", "Grand total"
        ))?.let {
            val v = MoneyFormat.toTiyin(it)
            if (v > 0) return v
        }
        return null
    }

    // -------- VAT --------

    private fun extractVat(doc: Document, text: String): Long? {
        // Метки в порядке приоритета — самые специфичные первыми.
        val labels = listOf(
            "Umumiy\\s+QQS\\s+qiymati",
            "QQS\\s+summasi",
            "Soliq\\s+summasi",
            "Итоговый\\s+НДС",
            "Итог\\s+НДС",
            "Total\\s+VAT",
            "VAT\\s+total",
            "НДС\\s+\\(QQS\\)",
            "НДС"
            // Внимание: "QQS" и "Soliq" в одиночку НЕ включаем — это даст ложные срабатывания
            // на "QQS qiymati"/"QQS foizi" внутри отдельных позиций или на бренд "SOLIQ".
        )
        firstMoneyMatch(text, labels)?.let { return it }

        // DOM-фолбэк.
        labelValue(doc, listOf(
            "Umumiy QQS qiymati", "QQS summasi", "Soliq summasi",
            "Итоговый НДС", "Итог НДС", "НДС"
        ))?.let {
            val v = MoneyFormat.toTiyin(it)
            if (v > 0) return v
        }

        // Если есть только построчные QQS qiymati по позициям — суммируем их.
        val perItem = Regex("(?i)QQS\\s+qiymati\\s*[:\\-]?\\s*([\\d][\\d \\u00A0',.\\-]*)")
            .findAll(text)
            .map { MoneyFormat.toTiyin(it.groupValues[1]) }
            .filter { it > 0 }
            .toList()
        if (perItem.isNotEmpty()) {
            return perItem.sum()
        }
        return null
    }

    // -------- Helpers --------

    /**
     * Ищет значение по подписи в типовой DOM-разметке: <th>label</th><td>value</td>,
     * <dt>label</dt><dd>value</dd>, либо в ячейках/абзацах строкой "label: value".
     */
    private fun labelValue(doc: Document, labels: List<String>): String? {
        val lowerLabels = labels.map { it.lowercase() }

        fun matchesExact(el: Element): Boolean {
            val t = el.ownText().trim().lowercase().trimEnd(':', '.', ' ')
            return lowerLabels.any { t == it }
        }

        // 1) Подпись в <th>/<dt>/.label-классах с парным значением в соседе.
        for (th in doc.select("th, td.label, div.label, span.label, dt, .receipt-label, .row-label")) {
            if (!matchesExact(th)) continue
            val tag = th.tagName().lowercase()
            val sibling: Element? = when (tag) {
                "th" -> th.parent()?.children()?.firstOrNull { it.tagName().equals("td", true) && it !== th }
                    ?: th.nextElementSibling()
                "dt" -> th.nextElementSibling()
                else -> th.nextElementSibling() ?: th.parent()?.children()?.firstOrNull { it !== th }
            }
            val value = sibling?.text()?.trim()
            if (!value.isNullOrBlank()) return value
        }

        // 2) Строки таблиц: <tr><td>Label</td><td>Value</td></tr>
        for (tr in doc.select("tr")) {
            val tds = tr.children().filter { it.tagName().equals("td", true) }
            if (tds.size < 2) continue
            val firstText = tds.first().text().trim().trimEnd(':', '.', ' ')
            if (lowerLabels.any { it.equals(firstText, ignoreCase = true) }) {
                val value = tds.last().text().trim()
                if (value.isNotBlank()) return value
            }
        }

        // 3) Свободные строки "label: value" в одном элементе.
        for (row in doc.select("li, p, div, span")) {
            val txt = row.text().trim()
            if (txt.length > 250) continue
            for (label in labels) {
                val regex = Regex("(?i)^${Regex.escape(label)}\\s*[:\\-]\\s*(.+)$")
                regex.find(txt)?.let { return it.groupValues[1].trim() }
            }
        }
        return null
    }
}
