package com.example.myapplication

import org.junit.Assert.*
import org.junit.Test

class TicketTextParserTest {
    @Test fun englishSeatIncludesSectionAndRowAndUsDate() {
        val result = TicketTextParser.parse("Event: Night Concert\nDate: 12/25/2026\nSection: A\nRow: 3\nSeat: 12")
        assertEquals("2026-12-25", result.date)
        assertEquals("Section A Row 3 Seat 12", result.seat)
    }
    @Test fun chineseTicketKeepsSeatAndUsesShowDate() {
        val result = TicketTextParser.parse("""
            KKTIX
            訂購日期:2026/01/01 10:00
            節目:告五人演唱會
            演出日期:2026/09/20
            入場時間:18:00
            演出時間:19:30
            地點:臺北小巨蛋
            座位:紅2B區 3排 12號
            票價:NT$ 3,800
        """.trimIndent())
        assertEquals("告五人演唱會", result.title)
        assertEquals("2026-09-20", result.date)
        assertEquals("19:30", result.time)
        assertEquals("臺北小巨蛋", result.place)
        assertEquals("紅2B區 3排 12號", result.seat)
        assertEquals("3800", result.price)
        assertEquals("KKTIX", result.platform)
    }

    @Test fun normalizesFullWidthAndRocDatesAndNextLineLabels() {
        val result = TicketTextParser.parse("節目：\n星空音樂會\n演出日期：１１５年９月２０日\n時間：下午７：３０\n場館：\n新莊文化藝術中心")
        assertEquals("星空音樂會", result.title)
        assertEquals("2026-09-20", result.date)
        assertEquals("19:30", result.time)
        assertEquals("新莊文化藝術中心", result.place)
    }

    @Test fun rejectsImpossibleDatesAndDoesNotInventArtistsOrPrices() {
        val result = TicketTextParser.parse("節目:未知樂團音樂會\n日期:2026/02/30\n訂單:98765432\n地點:新場館")
        assertEquals("", result.date)
        assertEquals("", result.price)
        assertEquals("", result.cast)
        assertEquals("新場館", result.place)
    }

    @Test fun parsesEnglishTimeAndPreservesUnknownVenue() {
        val result = TicketTextParser.parse("Event: THE LIVE TOUR\nDate: 2026-12-01\nTime: 07:30 PM\nVenue: Small Independent Hall\nSeat: A-12\nPrice: TWD 1500")
        assertEquals("19:30", result.time)
        assertEquals("Small Independent Hall", result.place)
        assertEquals("A-12", result.seat)
        assertEquals("1500", result.price)
    }
}
