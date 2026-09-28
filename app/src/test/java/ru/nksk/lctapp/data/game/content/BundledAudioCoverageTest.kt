package ru.nksk.lctapp.data.game.content

import java.io.File
import java.security.MessageDigest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.*
import org.junit.Test
import ru.nksk.lctapp.core.ui.media.BundledMediaCatalog
import ru.nksk.lctapp.core.ui.media.INTRO_VIDEO_ASSET
import ru.nksk.lctapp.domain.engine.EventMedia

/** Compare runtime content with recorded archive and generated sources, including legacy saves. */
class BundledAudioCoverageTest {
    private val catalog = bundledGameCatalog()
    private val mainDirectory = checkNotNull(File(checkNotNull(System.getProperty("lctapp.mainSourceDir"))).parentFile)
    private val projectDirectory = mainDirectory.resolve("../../..").normalize()
    private val manifest = Json.parseToJsonElement(
        File(projectDirectory, "docs/design/assets/media-manifest.json").readText(),
    ).jsonObject.getValue("entries").jsonArray.map { it.jsonObject }

    @Test fun everyDocumentedFileResolvesToItsVerifiedRuntimeAssetIncludingAliases() {
        val archiveEntries = manifest.filter { "archive_entry" in it }
        val generatedEntries = manifest.filter { it.textOrNull("status") == "generated" }
        assertEquals(95, archiveEntries.size)
        assertEquals(setOf("sound.telescope_adjustment"), generatedEntries.map { it.text("cue_key") }.toSet())
        assertEquals(manifest.size, archiveEntries.size + generatedEntries.size)
        assertEquals(manifest.size, manifest.map { it.text("cue_key") }.toSet().size)
        val runtimeHashes = mutableMapOf<String, String>()
        manifest.forEach { entry ->
            val cue = entry.text("cue_key")
            val path = entry.text("asset_path")
            assertEquals(cue, path, if (cue == "video.intro_fox") INTRO_VIDEO_ASSET else BundledMediaCatalog.assetPath(cue))
            val file = File(mainDirectory, "assets/$path")
            assertTrue("Missing bundled file for $cue: $file", file.isFile)
            assertEquals(cue, entry.textOrNull("runtime_bytes")?.toLong() ?: entry.text("bytes").toLong(), file.length())
            val actual = runtimeHashes.getOrPut(path) {
                val digest = MessageDigest.getInstance("SHA-256")
                file.inputStream().use { stream ->
                    val buffer = ByteArray(32 * 1024)
                    while (true) {
                        val count = stream.read(buffer)
                        if (count < 0) break
                        digest.update(buffer, 0, count)
                    }
                }
                digest.digest().joinToString("") { "%02x".format(it) }
            }
            assertEquals(cue, entry.textOrNull("runtime_sha256") ?: entry.text("sha256"), actual)
        }
        assertEquals(86, archiveEntries.map { it.text("asset_path") }.toSet().size)
        assertEquals(87, runtimeHashes.size)
    }

    @Test fun everyDocumentedAudioCueHasAnAuthoredRuntimeConsumer() {
        val documented = manifest.map { it.text("cue_key") }.filterNot { it == "video.intro_fox" }.toSet()
        val used = catalog.cards.values.flatMap { it.presentation.media.audioKeys() }.toSet()
        assertEquals("A documented cue is unassigned, or content refers to an undocumented cue", documented, used)
    }

    @Test fun allNarrationStaysAttachedToItsAuthoredEventThroughVersionReplacements() {
        val voicedEntries = manifest.filter { it.text("cue_key").startsWith("narration.") }
        assertEquals(85, voicedEntries.size)
        voicedEntries.forEach { entry ->
            val target = entry.text("authored_target")
            val originalId = when (entry.text("binding")) {
                "LoreScene.narrationCueKey" -> storyEventId(target)
                "UnexpectedCard.narrationCueKey" -> "figma-${target.replace(':', '-')}-v2"
                else -> error("Narration binding must identify its authored event: $target")
            }
            val versions = linkedSetOf(originalId)
            var current = originalId
            while (true) {
                val replacement = catalog.eventReplacements[current] ?: break
                check(versions.add(replacement)) { "Cyclic event replacement: $originalId" }
                current = replacement
            }
            versions.forEach { eventId ->
                assertEquals(eventId, entry.text("cue_key"), catalog.cards.getValue(eventId).presentation.media.narrationCueKey)
            }
        }
    }

    @Test fun currentDeedsPreserveTheirAuthoredAudioAndLegacyIntroductionHasItsVoice() {
        catalog.deedPool.forEach { eventId ->
            val originalId = eventId.removeSuffix(":balance-v2")
            assertNotEquals("Expected a rebalanced deed: $eventId", originalId, eventId)
            assertEquals(eventId, catalog.cards.getValue(originalId).presentation.media,
                catalog.cards.getValue(eventId).presentation.media)
        }
        assertEquals("narration.story.night_observation",
            catalog.cards.getValue("figma-2363-4-v1").presentation.media.narrationCueKey)
    }

    private fun JsonObject.text(key: String): String = getValue(key).jsonPrimitive.content
    private fun JsonObject.textOrNull(key: String): String? = get(key)?.jsonPrimitive?.content
    private fun EventMedia.audioKeys(): List<String> = listOfNotNull(musicCueKey, narrationCueKey,
        ambientCueKey, appearanceCueKey) + actionAudio.values.flatMap { listOfNotNull(it.soundCueKey, it.voiceCueKey) }
}
