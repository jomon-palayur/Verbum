package com.verbum.app.liturgy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class LiturgicalDataPackInstallerTest {
    @Test fun parsesAttributedProfileAndCanonCoordinates() {
        val pack = LiturgicalDataPackInstaller.parse(validPack("roman", "Roman Rite"))
        assertEquals("roman", pack.profiles.single().id)
        assertEquals(1, pack.readings.size)
        assertEquals("roman", pack.readings.single().profileId)
        assertEquals("49:1:1-2", pack.readings.single().gospelRef)
    }

    @Test fun acceptsMultipleCatholicTraditionsWithoutKeyCollisions() {
        val json = """{"schemaVersion":1,"profiles":[
          ${profile("roman", "Roman Rite")},
          ${profile("syro_malabar", "Syro-Malabar")}
        ]}"""
        val pack = LiturgicalDataPackInstaller.parse(json)
        assertEquals(2, pack.profiles.size)
        assertEquals(setOf("roman", "syro_malabar"), pack.readings.map { it.profileId }.toSet())
    }

    @Test fun rejectsCoordinatesOutsideTheCanonicalBookRange() {
        val invalid = validPack("roman", "Roman Rite").replace("49:1:1-2", "74:1:1-2")
        assertThrows(IllegalArgumentException::class.java) { LiturgicalDataPackInstaller.parse(invalid) }
    }

    @Test fun rejectsMissingSourceAttribution() {
        val invalid = validPack("roman", "Roman Rite").replace("\"sourceName\":\"Catholic source\",", "")
        assertThrows(IllegalArgumentException::class.java) { LiturgicalDataPackInstaller.parse(invalid) }
    }

    private fun validPack(id: String, name: String) = """{"schemaVersion":1,"profiles":[${profile(id, name)}]}"""

    private fun profile(id: String, name: String) = """{"id":"$id","name":"$name","tradition":"$name","regionCode":"001","sourceName":"Catholic source","sourceUrl":"https://example.org/source","license":"Rights-cleared","readings":[{"key":"DATE_2026-01-01","title":"Solemnity","color":"WHITE","firstReadingRef":"1:1:1-2","psalmRef":"23:1:1-2","gospelRef":"49:1:1-2"}]}"""
}
