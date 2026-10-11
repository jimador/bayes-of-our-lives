package com.embabel.example.travel.agent

import com.embabel.tripper.TripperApplication
import com.embabel.tripper.agent.DicePoiPreferenceService
import com.embabel.tripper.config.OwnedNeo4j
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ContextConfiguration

/** Explicit opt-in integration; the initializer owns and closes one disposable container. */
@EnabledIfSystemProperty(named = "tripper.dice-smoke", matches = "true")
@ContextConfiguration(initializers = [OwnedNeo4j::class])
@SpringBootTest(classes = [TripperApplication::class], properties = [
    "spring.main.web-application-type=none",
    "tripper.dice.enabled=true",
    "spring.security.oauth2.client.registration.google.client-id=unused",
    "spring.security.oauth2.client.registration.google.client-secret=unused",
])
class DicePreferenceSmokeTest @Autowired constructor(private val preference: DicePoiPreferenceService) {
    @Test fun `fictional preference round trips through owned Dice graph`() {
        val stored = preference.read()
        assertEquals("art", stored.keyword)
        assertEquals(0.15, stored.weight)
        println("TRIPPER_DICE_SMOKE source=owned-neo4j keyword=${stored.keyword} weight=${stored.weight}")
    }
}
