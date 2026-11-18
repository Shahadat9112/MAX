package com.example

import com.example.domain.AssistantActionType
import com.example.domain.OfflineCommandEngine
import com.example.domain.WakeWordParser
import com.example.domain.WakeWordResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WakeWordAndOfflineTest {

    @Test
    fun wakeWord_acceptsMaxAndHeyMax() {
        val valid = WakeWordParser.parse("MAX, take a picture")
        assertTrue(valid is WakeWordResult.Triggered)
        assertEquals("take a picture", (valid as WakeWordResult.Triggered).command)

        val validBengali = WakeWordParser.parse("ম্যাক্স কয়টা বাজে")
        assertTrue(validBengali is WakeWordResult.Triggered)
        assertEquals("কয়টা বাজে", (validBengali as WakeWordResult.Triggered).command)

        val heyMax = WakeWordParser.parse("Hey MAX, what time is it")
        assertTrue(heyMax is WakeWordResult.Triggered)
        assertEquals("what time is it", (heyMax as WakeWordResult.Triggered).command)

        val okMax = WakeWordParser.parse("OK MAX open camera")
        assertTrue(okMax is WakeWordResult.Triggered)
        assertEquals("open camera", (okMax as WakeWordResult.Triggered).command)

        val unrelated = WakeWordParser.parse("Open YouTube please")
        assertEquals(WakeWordResult.NotDetected, unrelated)
    }

    @Test
    fun offlineCommands_parseEnglishAndBengali() {
        val photo = OfflineCommandEngine.tryParse("MAX, take a picture")
        assertNotNull(photo)
        assertEquals(AssistantActionType.TAKE_PHOTO, photo?.action)

        val callFather = OfflineCommandEngine.tryParse("MAX, call my father")
        assertNotNull(callFather)
        assertEquals(AssistantActionType.CALL_CONTACT, callFather?.action)
        assertEquals("father", callFather?.targetName)

        val bnCallRahim = OfflineCommandEngine.tryParse("রহিমকে কল করো")
        assertNotNull(bnCallRahim)
        assertEquals(AssistantActionType.CALL_CONTACT, bnCallRahim?.action)
        assertEquals("রহিম", bnCallRahim?.targetName)

        val smsRahim = OfflineCommandEngine.tryParse("MAX, send an SMS to Rahim")
        assertNotNull(smsRahim)
        assertEquals(AssistantActionType.SEND_SMS, smsRahim?.action)
        assertEquals("Rahim", smsRahim?.targetName)

        val bnSms = OfflineCommandEngine.tryParse("রহিমকে এসএমএস পাঠাও আমি আসছি")
        assertNotNull(bnSms)
        assertEquals(AssistantActionType.SEND_SMS, bnSms?.action)
        assertEquals("রহিম", bnSms?.targetName)
        assertEquals("আমি আসছি", bnSms?.messageBody)

        val bnVolume = OfflineCommandEngine.tryParse("ভলিউম বাড়াও")
        assertNotNull(bnVolume)
        assertEquals(AssistantActionType.VOLUME_UP, bnVolume?.action)
        assertEquals("bn", bnVolume?.language)

        val bnTime = OfflineCommandEngine.tryParse("কয়টা বাজে")
        assertNotNull(bnTime)
        assertEquals(AssistantActionType.GET_TIME, bnTime?.action)
        assertEquals("bn", bnTime?.language)
    }
}
