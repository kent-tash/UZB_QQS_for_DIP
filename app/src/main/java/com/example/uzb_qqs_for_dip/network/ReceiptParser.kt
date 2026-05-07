package com.example.uzb_qqs_for_dip.network

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
                parseHtml(url, body)
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

        return ParsedReceipt(
            qrUrl = qrUrl,
            purchasedAt = date,
            sellerName = seller,
            totalAmountTiyin = total,
            vatAmountTiyin = vat,
            rawSnippet = text.take(2000)
        )
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
            "электронный фискальный чек", "fiskal chek"
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
        s = s.replace(Regex("(?i)\\b(?:ИНН|STIR|TIN|ИКПУ|MFO|MXIK|расчётный\\s+счёт)\\b.*"), "").trim()
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
