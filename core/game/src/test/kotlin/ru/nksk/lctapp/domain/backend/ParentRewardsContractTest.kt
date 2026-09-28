package ru.nksk.lctapp.domain.backend

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.fail
import org.junit.Test

class ParentRewardsContractTest {
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
        classDiscriminator = "_type"
    }

    @Test fun rewardUsesItsOwnWireDiscriminatorAndPreservesInt64() {
        val request = CreateParentRewardRequest("device", "run", ParentRewardPayload.Coins(9_007_199_254_740_993L))
        val encoded = json.encodeToString(CreateParentRewardRequest.serializer(), request)
        val reward = json.parseToJsonElement(encoded).jsonObject.getValue("reward").jsonObject
        assertEquals("COINS", reward.getValue("type").jsonPrimitive.content)
        assertEquals("9007199254740993", reward.getValue("amount").jsonPrimitive.content)
        assertFalse(reward.containsKey("_type"))
        assertEquals(request, json.decodeFromString<CreateParentRewardRequest>(encoded))
    }

    @Test fun accessoryGrantFromBackendDecodesWithoutAnyReplacementWorldState() {
        val page = json.decodeFromString<ParentRewardsResponse>("""
            {"profileId":"profile","gameRunId":"run","rewards":[
              {"rewardId":"gift","profileId":"profile","gameRunId":"run","sequence":1,
               "reward":{"type":"ACCESSORY","itemId":"cosmetic-explorer-hat-v2"},"createdAt":"2026-09-27T12:00:00Z"}
            ],"nextAfterSequence":1,"hasMore":false,"schemaVersion":1}
        """.trimIndent())
        assertEquals(ParentRewardPayload.Accessory("cosmetic-explorer-hat-v2"), page.rewards.single().reward)
        assertEquals(page, json.decodeFromString<ParentRewardsResponse>(json.encodeToString(ParentRewardsResponse.serializer(), page)))
    }

    @Test fun unknownGrantKindFailsClosedInsteadOfBecomingCoins() {
        try {
            json.decodeFromString<CreateParentRewardRequest>("""
                {"deviceId":"device","gameRunId":"run","reward":{"type":"REPLACE_BALANCE","amount":100}}
            """.trimIndent())
            fail("Unsupported grant must not become an applicable reward")
        } catch (_: SerializationException) { }
    }
}
