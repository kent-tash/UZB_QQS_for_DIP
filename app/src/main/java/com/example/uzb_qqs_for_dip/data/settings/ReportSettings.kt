package com.example.uzb_qqs_for_dip.data.settings

import com.example.uzb_qqs_for_dip.util.DateFormat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Calendar
import java.util.TimeZone

/**
 * Поле сортировки таблицы отчёта. То же используется на вкладке «Чеки»
 * (она просто читает сортировку с экрана «Отчёт»).
 */
enum class SortField(val label: String) {
    DATE("Дата"),
    SELLER("Наименование организации"),
    TOTAL("Общая сумма"),
    VAT("НДС");
}

enum class SortOrder(val label: String) {
    ASC("По возрастанию"),
    DESC("По убыванию");
}

/**
 * Период по календарным кварталам. [Custom] означает «свой произвольный
 * диапазон, заданный датами from/to», без привязки к кварталу.
 */
enum class Quarter(val label: String, val firstMonth: Int, val lastMonth: Int) {
    Q1("I квартал (январь–март)", Calendar.JANUARY, Calendar.MARCH),
    Q2("II квартал (апрель–июнь)", Calendar.APRIL, Calendar.JUNE),
    Q3("III квартал (июль–сентябрь)", Calendar.JULY, Calendar.SEPTEMBER),
    Q4("IV квартал (октябрь–декабрь)", Calendar.OCTOBER, Calendar.DECEMBER),
    Custom("Свой период", -1, -1);
}

/**
 * Глобальное состояние, общее для вкладок «Отчёт» и «Чеки»: один источник
 * правды о том, какой пользователь, период и сортировка применяются. Так
 * вкладка «Чеки» автоматически отражает текущий выбор отчёта (нумерация
 * и порядок чеков совпадают с таблицей в отчёте).
 *
 * @property userId        выбранный пользователь (или null = текущий из сессии).
 * @property quarter       выбранный квартал; [Quarter.Custom] = ручные даты.
 * @property year          выбранный год (актуален при quarter != Custom).
 * @property from / to     границы периода в миллисекундах.
 * @property sortField     поле сортировки строк.
 * @property sortOrder     направление сортировки.
 */
data class ReportSettings(
    val userId: Long? = null,
    val quarter: Quarter = currentQuarter(),
    val year: Int = currentYear(),
    val from: Long = quarterStart(currentYear(), currentQuarter()),
    val to: Long = quarterEnd(currentYear(), currentQuarter()),
    val sortField: SortField = SortField.DATE,
    val sortOrder: SortOrder = SortOrder.ASC,
) {
    companion object {
        fun currentYear(): Int = Calendar.getInstance(tz()).get(Calendar.YEAR)

        fun currentQuarter(): Quarter {
            val m = Calendar.getInstance(tz()).get(Calendar.MONTH)
            return when (m) {
                in Calendar.JANUARY..Calendar.MARCH -> Quarter.Q1
                in Calendar.APRIL..Calendar.JUNE -> Quarter.Q2
                in Calendar.JULY..Calendar.SEPTEMBER -> Quarter.Q3
                else -> Quarter.Q4
            }
        }

        fun quarterStart(year: Int, q: Quarter): Long {
            if (q == Quarter.Custom) return DateFormat.startOfDay(System.currentTimeMillis())
            val cal = Calendar.getInstance(tz()).apply {
                clear()
                set(Calendar.YEAR, year)
                set(Calendar.MONTH, q.firstMonth)
                set(Calendar.DAY_OF_MONTH, 1)
            }
            return cal.timeInMillis
        }

        fun quarterEnd(year: Int, q: Quarter): Long {
            if (q == Quarter.Custom) return DateFormat.endOfDay(System.currentTimeMillis())
            val cal = Calendar.getInstance(tz()).apply {
                clear()
                set(Calendar.YEAR, year)
                set(Calendar.MONTH, q.lastMonth)
                set(Calendar.DAY_OF_MONTH, 1)
            }
            cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
            return DateFormat.endOfDay(cal.timeInMillis)
        }

        private fun tz(): TimeZone = TimeZone.getTimeZone("Asia/Tashkent")
    }
}

/**
 * Сигнглетон-холдер настроек отчёта, живёт в [com.example.uzb_qqs_for_dip.data.AppContainer].
 * ViewModel-и подписываются на [settings] и обновляют её через `setX`.
 */
class ReportSettingsHolder {
    private val _settings = MutableStateFlow(ReportSettings())
    val settings: StateFlow<ReportSettings> = _settings.asStateFlow()

    /** Полная замена состояния (например после загрузки бэкапа). */
    fun replace(settings: ReportSettings) {
        _settings.value = settings
    }

    fun update(transform: (ReportSettings) -> ReportSettings) {
        _settings.value = transform(_settings.value)
    }

    fun setUserId(userId: Long?) = update { it.copy(userId = userId) }

    /** Меняем явные даты — переводим выбор в Custom-период. */
    fun setFrom(ts: Long) = update { it.copy(from = ts, quarter = Quarter.Custom) }
    fun setTo(ts: Long) = update { it.copy(to = ts, quarter = Quarter.Custom) }

    fun setQuarter(q: Quarter) = update {
        if (q == Quarter.Custom) it.copy(quarter = q)
        else it.copy(
            quarter = q,
            from = ReportSettings.quarterStart(it.year, q),
            to = ReportSettings.quarterEnd(it.year, q),
        )
    }

    fun setYear(year: Int) = update {
        if (it.quarter == Quarter.Custom) it.copy(year = year)
        else it.copy(
            year = year,
            from = ReportSettings.quarterStart(year, it.quarter),
            to = ReportSettings.quarterEnd(year, it.quarter),
        )
    }

    fun setSortField(field: SortField) = update { it.copy(sortField = field) }
    fun setSortOrder(order: SortOrder) = update { it.copy(sortOrder = order) }

    /** Клик по заголовку колонки: первый клик — выбрать поле, повторный — инвертировать. */
    fun toggleSort(field: SortField) = update {
        if (it.sortField == field) {
            it.copy(sortOrder = if (it.sortOrder == SortOrder.ASC) SortOrder.DESC else SortOrder.ASC)
        } else {
            it.copy(sortField = field, sortOrder = SortOrder.ASC)
        }
    }
}
