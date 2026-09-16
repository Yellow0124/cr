package com.example.myapplication

import java.text.Normalizer
import java.util.GregorianCalendar
import java.util.Calendar
import java.util.Locale

internal data class OcrTicketFields(
    val title: String = "", val date: String = "", val time: String = "",
    val place: String = "", val seat: String = "", val cast: String = "",
    val price: String = "", val platform: String = ""
) {
    val hasCoreData get() = listOf(title, date, time, place).count { it.isNotBlank() } >= 3
}

/** Conservative extraction: unknown fields stay editable and empty rather than guessed. */
internal object TicketTextParser {
    private val metadata = Regex("^(日期|時間|演出日期|演出時間|入場|開演|票價|金額|座位|區域|場館|場地|地點|地址|訂單|訂購|購票|取票|列印|票號|序號|注意|date\\b|time\\b|seat\\b|venue\\b|price\\b|order\\b|section\\b|row\\b)", RegexOption.IGNORE_CASE)
    private val nonShow = Regex("購票|訂購|訂單|取票|列印|開賣|售票時間|入場|進場|purchase|order|printed|doors|admission", RegexOption.IGNORE_CASE)
    private val show = Regex("演出|開演|活動日期|表演|show|performance|starts", RegexOption.IGNORE_CASE)
    private val labels = "節目名稱|活動名稱|節目|EVENT|演出日期|演出時間|日期|時間|地點|場地|場館|VENUE|LOCATION|座位|SEAT|票價|PRICE|演出者|藝人"

    fun parse(text: String): OcrTicketFields {
        val lines = Normalizer.normalize(text, Normalizer.Form.NFKC).replace('\u00a0', ' ')
            .lines().map { it.trim().replace(Regex("[ \\t]+"), " ") }.filter { it.isNotEmpty() }
        fun value(vararg names: String): String {
            val pattern = Regex("^(?:${names.joinToString("|") { Regex.escape(it) }})(?:\\s*[:：]\\s*|\\s+|$)", RegexOption.IGNORE_CASE)
            for ((i, line) in lines.withIndex()) {
                val match = pattern.find(line) ?: continue
                val rest = line.substring(match.range.last + 1).trim()
                val result = rest.ifBlank { lines.getOrNull(i + 1)?.takeUnless { metadata.containsMatchIn(it) }.orEmpty() }
                if (result.isNotBlank()) return result.split(Regex("\\s+(?:$labels)\\s*:", RegexOption.IGNORE_CASE)).first().trim()
            }
            return ""
        }
        val showLines = lines.filterNot { nonShow.containsMatchIn(it) }.sortedByDescending { show.containsMatchIn(it) }
        val date = showLines.firstNotNullOfOrNull { date(it).takeIf(String::isNotBlank) }.orEmpty()
        val time = showLines.firstNotNullOfOrNull { time(it).takeIf(String::isNotBlank) }.orEmpty()
        val place = value("地點", "場地", "場館", "演出地點", "Venue", "Location", "Place").ifBlank {
            lines.firstOrNull { !nonShow.containsMatchIn(it) && Regex("巨蛋|音樂中心|音樂廳|體育館|展覽館|河濱公園|Arena|Dome|Music Center|Concert Hall|Zepp", RegexOption.IGNORE_CASE).containsMatchIn(it) }.orEmpty()
        }
        val seatValue = value("座位", "座位資訊", "Seat")
        val englishSeat = listOf("Section", "Row").mapNotNull { label -> value(label).takeIf { it.isNotBlank() }?.let { "$label $it" } }
        val seat = (if (englishSeat.isNotEmpty()) (englishSeat + listOfNotNull(seatValue.takeIf { it.isNotBlank() }?.let { "Seat $it" })).joinToString(" ") else seatValue).ifBlank {
            lines.filter { !Regex("地址|路|街|訂單|票號").containsMatchIn(it) }
                .mapNotNull { Regex("(?:[\\p{L}\\d-]+區\\s*)?(?:\\d+排\\s*)(?:\\d+(?:號|座))|[\\p{L}\\d-]+區(?:\\s*(?:站席|自由座))?").find(it)?.value }
                .distinct().joinToString(" ")
                .ifBlank { lines.filter { Regex("^(section|row|seat)\\s*[: ]", RegexOption.IGNORE_CASE).containsMatchIn(it) }.joinToString(" ") }
        }
        val title = value("節目名稱", "活動名稱", "節目", "Event", "Title").ifBlank {
            val candidates = lines.filter { it.length >= 4 && !metadata.containsMatchIn(it) && date(it).isBlank() && time(it).isBlank() &&
                it != place && it != seat && !Regex("KKTIX|拓元|Ticketmaster|FamilyMart|ibon|NT\\$|TWD|https?://|禁止|不得|須知", RegexOption.IGNORE_CASE).containsMatchIn(it) }
            candidates.firstOrNull { Regex("演唱會|音樂會|見面會|演奏會|TOUR|LIVE|CONCERT", RegexOption.IGNORE_CASE).containsMatchIn(it) }
                ?: candidates.firstOrNull().orEmpty()
        }
        val priceText = value("票價", "售價", "金額", "Price", "Amount")
        val money = Regex("(?:NT\\$|NTD|TWD|\\$)\\s*([\\d,]+)(?:\\.00)?", RegexOption.IGNORE_CASE)
        val price = if (priceText.isNotBlank()) Regex("[\\d,]+(?:\\.00)?").find(priceText)?.value.orEmpty()
            else lines.firstNotNullOfOrNull { money.find(it)?.groupValues?.get(1) }.orEmpty()
        val full = lines.joinToString("\n")
        val platform = listOf("KKTIX", "Ticketmaster", "拓元", "ibon", "FamiTicket", "寬宏", "年代", "OPENTIX")
            .firstOrNull { full.contains(it, ignoreCase = true) }.orEmpty()
        return OcrTicketFields(title, date, time, place, seat,
            value("演出者", "藝人", "Artist", "Performer"), price.replace(",", ""), platform)
    }

    private fun date(text: String): String {
        val m = Regex("(?<!\\d)(20\\d{2}|1\\d{2})\\s*[-/.年]\\s*(\\d{1,2})\\s*[-/.月]\\s*(\\d{1,2})(?!\\d)").find(text)
        val us = if (m == null) Regex("(?<!\\d)(\\d{1,2})\\s*[-/.]\\s*(\\d{1,2})\\s*[-/.]\\s*(20\\d{2})(?!\\d)").find(text) else null
        if (m == null && us == null) return ""
        val rawYear = (m?.groupValues?.get(1) ?: us!!.groupValues[3]).toInt()
        val year = if (rawYear < 1000) rawYear + 1911 else rawYear
        val month = (m?.groupValues?.get(2) ?: us!!.groupValues[1]).toInt()
        val day = (m?.groupValues?.get(3) ?: us!!.groupValues[2]).toInt()
        return runCatching {
            GregorianCalendar().apply { isLenient = false; clear(); set(year, month - 1, day); get(Calendar.DAY_OF_MONTH) }
            String.format(Locale.ROOT, "%04d-%02d-%02d", year, month, day)
        }.getOrDefault("")
    }

    private fun time(text: String): String {
        val m = Regex("(?<!\\d)(\\d{1,2})\\s*[:時]\\s*([0-5]\\d)(?!\\d)").find(text) ?: return ""
        var hour = m.groupValues[1].toInt()
        val pm = Regex("下午|晚上|\\bPM\\b", RegexOption.IGNORE_CASE).containsMatchIn(text)
        val am = Regex("上午|早上|\\bAM\\b", RegexOption.IGNORE_CASE).containsMatchIn(text)
        if ((pm || am) && hour !in 1..12) return ""
        if (pm && hour < 12) hour += 12
        if (am && hour == 12) hour = 0
        return if (hour in 0..23) String.format(Locale.ROOT, "%02d:%s", hour, m.groupValues[2]) else ""
    }
}
