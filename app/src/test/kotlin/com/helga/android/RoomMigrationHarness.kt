package com.helga.android

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import io.mockk.every
import io.mockk.mockk
import org.json.JSONObject
import org.junit.Assert.assertEquals
import java.io.File
import java.sql.Connection
import java.sql.DriverManager

/**
 * Room-Migrationstests ohne Emulator: Die Migration läuft gegen eine echte SQLite-Datenbank auf der JVM
 * (sqlite-jdbc) und wird mit dem exportierten Schema (`app/schemas/.../<version>.json`) verglichen.
 */
object RoomMigrationHarness {

    fun open(): Connection = DriverManager.getConnection("jdbc:sqlite::memory:")

    private fun schemaFile(version: Int): File {
        val roots = listOf(File("schemas"), File("app/schemas"))
        return roots.filter { it.exists() }
            .flatMap { it.walk().toList() }
            .firstOrNull { it.isFile && it.name == "$version.json" }
            ?: error("Schema $version.json nicht gefunden (Arbeitsverzeichnis: ${File(".").absolutePath})")
    }

    private fun database(version: Int): JSONObject =
        JSONObject(schemaFile(version).readText()).getJSONObject("database")

    /** Legt alle Tabellen und Indizes der Schema-Version an. */
    fun createFromSchema(conn: Connection, version: Int) {
        val entities = database(version).getJSONArray("entities")
        conn.createStatement().use { st ->
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                val table = entity.getString("tableName")
                st.execute(entity.getString("createSql").replace("\${TABLE_NAME}", table))
            }
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                val table = entity.getString("tableName")
                val indices = entity.getJSONArray("indices")
                for (j in 0 until indices.length()) {
                    st.execute(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
                }
            }
        }
    }

    /** Führt `migration.migrate` aus; jedes `execSQL` geht an die echte SQLite-Verbindung. */
    fun run(conn: Connection, migration: Migration) {
        val db = mockk<SupportSQLiteDatabase>(relaxed = true)
        every { db.execSQL(any<String>()) } answers {
            conn.createStatement().use { it.execute(firstArg<String>()) }
        }
        migration.migrate(db)
    }

    /** Vergleicht Spalten (Name, Affinität, NOT NULL, Primärschlüssel) und Indexnamen mit dem Schema. */
    fun assertMatchesSchema(conn: Connection, version: Int) {
        val entities = database(version).getJSONArray("entities")
        for (i in 0 until entities.length()) {
            val entity = entities.getJSONObject(i)
            val table = entity.getString("tableName")

            val expectedColumns = mutableMapOf<String, Triple<String, Boolean, Boolean>>()
            val fields = entity.getJSONArray("fields")
            val pkNames = entity.getJSONObject("primaryKey").getJSONArray("columnNames").let { arr ->
                (0 until arr.length()).map { arr.getString(it) }.toSet()
            }
            for (j in 0 until fields.length()) {
                val f = fields.getJSONObject(j)
                val name = f.getString("columnName")
                expectedColumns[name] = Triple(f.getString("affinity").uppercase(), f.getBoolean("notNull"), name in pkNames)
            }

            val actualColumns = mutableMapOf<String, Triple<String, Boolean, Boolean>>()
            conn.createStatement().use { st ->
                st.executeQuery("PRAGMA table_info(`$table`)").use { rs ->
                    while (rs.next()) {
                        actualColumns[rs.getString("name")] =
                            Triple(rs.getString("type").uppercase(), rs.getInt("notnull") == 1, rs.getInt("pk") > 0)
                    }
                }
            }
            assertEquals("Spalten von $table", expectedColumns.toSortedMap().toString(), actualColumns.toSortedMap().toString())

            val expectedIndices = entity.getJSONArray("indices").let { arr ->
                (0 until arr.length()).map { arr.getJSONObject(it).getString("name") }.toSet()
            }
            val actualIndices = mutableSetOf<String>()
            conn.createStatement().use { st ->
                st.executeQuery("PRAGMA index_list(`$table`)").use { rs ->
                    while (rs.next()) {
                        val name = rs.getString("name")
                        if (!name.startsWith("sqlite_autoindex")) actualIndices += name
                    }
                }
            }
            assertEquals("Indizes von $table", expectedIndices.toSortedSet(), actualIndices.toSortedSet())
        }
    }
}
