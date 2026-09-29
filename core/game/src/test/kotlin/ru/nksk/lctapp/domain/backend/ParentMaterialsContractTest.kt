package ru.nksk.lctapp.domain.backend

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ParentMaterialsContractTest {
    @Test fun catalogueRequiresEverySkillExactlyOnce() {
        val skills = (1..12).map { material("FIN-%02d".format(it)) }
        assertEquals(12, ParentMaterialsCatalog("version", skills).skills.size)
        assertThrows(IllegalArgumentException::class.java) { ParentMaterialsCatalog("version", skills.dropLast(1)) }
        assertThrows(IllegalArgumentException::class.java) { ParentMaterialsCatalog("version", skills.dropLast(1) + skills.first()) }
    }

    @Test fun unpublishedCatalogueIsExplicitAndCannotContainDraftText() {
        val empty = ParentMaterialsCatalog("unpublished", emptyList(),
            publicationStatus = ParentMaterialsPublicationStatus.UNPUBLISHED)
        assertEquals(ParentMaterialsPublicationStatus.UNPUBLISHED, empty.publicationStatus)
        assertThrows(IllegalArgumentException::class.java) {
            empty.copy(skills = listOf(material("FIN-01")))
        }
        assertThrows(IllegalArgumentException::class.java) {
            empty.copy(publicationStatus = ParentMaterialsPublicationStatus.PUBLISHED)
        }
    }

    @Test fun materialRejectsEmptyQuestionsAndUnsafeSourceLinks() {
        val material = material("FIN-01")
        assertThrows(IllegalArgumentException::class.java) { material.copy(conversationStarters = listOf("")) }
        assertThrows(IllegalArgumentException::class.java) { material.copy(researchSources = listOf("javascript:alert(1)")) }
    }

    private fun material(id: String) = ParentSkillMaterialDto(
        id, "Цель", "История", "Вспомните пример", List(5) { "Вопрос $it?" },
        "Вывод", "Основание", listOf("https://example.org/source"),
    )
}
