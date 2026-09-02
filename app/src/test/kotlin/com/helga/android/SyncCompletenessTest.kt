package com.helga.android

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Bewacht das wiederkehrende Muster aus dem sync-Interview: eine neue Room-Entity wird in
 * AppDatabase eingetragen, der Sync-Anschluss (DTO + Mapper in SyncEngine) aber vergessen –
 * genau das ist WeekplanTemplateEntity/OffProductEntity über Monate passiert, unbemerkt bis
 * zum Interview. Reiner Quelltext-Abgleich statt Reflection auf die echte Room-Datenbank, damit
 * der Test wie [SyncLwwTest] ohne Android-Runtime läuft.
 */
class SyncCompletenessTest {

    // Entities, die absichtlich NICHT synchronisiert werden – mit Begründung als Kommentar.
    // Jede neue Entity muss entweder hier eingetragen sein oder ein toDto()/toEntity()-Mapper-
    // Paar in SyncEngine.kt haben, sonst schlägt der Test fehl.
    private val intentionallyLocalOnly = emptySet<String>()

    private fun projectRoot(): File {
        var dir = File("").absoluteFile
        while (!File(dir, "settings.gradle.kts").exists()) {
            dir = dir.parentFile ?: error("settings.gradle.kts nicht gefunden – Testumgebung unerwartet")
        }
        return dir
    }

    @Test
    fun `every entity in AppDatabase is wired into SyncEngine or explicitly excluded`() {
        val appModule = File(projectRoot(), "app")
        val databaseSource = File(appModule, "src/main/kotlin/com/helga/android/data/local/AppDatabase.kt").readText()
        val syncEngineSource = File(appModule, "src/main/kotlin/com/helga/android/data/sync/SyncEngine.kt").readText()

        val entitiesBlock = Regex("""entities\s*=\s*\[(.*?)]""", RegexOption.DOT_MATCHES_ALL)
            .find(databaseSource)?.groupValues?.get(1)
            ?: error("Konnte den entities = [...] Block in AppDatabase.kt nicht finden")

        val entityNames = Regex("""(\w+)Entity::class""").findAll(entitiesBlock)
            .map { it.groupValues[1] }
            .toList()
        assertTrue("Keine Entities gefunden – Regex kaputt?", entityNames.isNotEmpty())

        val unwired = entityNames.filter { name ->
            if (name in intentionallyLocalOnly) return@filter false
            val hasToDto = syncEngineSource.contains("fun ${name}Entity.toDto(): ${name}Dto")
            val hasToEntity = syncEngineSource.contains("fun ${name}Dto.toEntity(): ${name}Entity")
            !(hasToDto && hasToEntity)
        }

        assertTrue(
            "Diese Entities sind weder an SyncEngine angebunden noch in " +
                "intentionallyLocalOnly (mit Begründung) eingetragen: $unwired",
            unwired.isEmpty(),
        )
    }
}
