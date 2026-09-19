package ru.nksk.lctapp.data.game.local

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/** Additive migration: existing saves and reference content are not reinterpreted or reseeded. */
internal val MIGRATION_1_2 = object : Migration(1, 2) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL("""
            CREATE TABLE IF NOT EXISTS ENGINE_STATE (
                game_state_id TEXT NOT NULL PRIMARY KEY,
                rules_id TEXT NOT NULL, revision INTEGER NOT NULL, day INTEGER NOT NULL,
                phase TEXT NOT NULL, steps INTEGER NOT NULL, energy INTEGER NOT NULL,
                ate_today INTEGER NOT NULL, next_morning_energy INTEGER,
                opening_balance INTEGER NOT NULL,
                FOREIGN KEY(game_state_id) REFERENCES GAME_STATE(id) ON UPDATE NO ACTION ON DELETE NO ACTION
            )
        """.trimIndent())
        connection.execSQL("""
            CREATE TABLE IF NOT EXISTS ENGINE_DEED (
                id TEXT NOT NULL PRIMARY KEY, game_state_id TEXT NOT NULL, position INTEGER NOT NULL,
                event_id TEXT NOT NULL, expires_day INTEGER NOT NULL,
                completed INTEGER NOT NULL,
                FOREIGN KEY(game_state_id) REFERENCES ENGINE_STATE(game_state_id) ON UPDATE NO ACTION ON DELETE NO ACTION,
                FOREIGN KEY(event_id) REFERENCES EVENT(id) ON UPDATE NO ACTION ON DELETE NO ACTION
            )
        """.trimIndent())
        connection.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_ENGINE_DEED_game_state_id_position ON ENGINE_DEED(game_state_id, position)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS index_ENGINE_DEED_event_id ON ENGINE_DEED(event_id)")
        connection.execSQL("""
            CREATE TABLE IF NOT EXISTS ENGINE_EVENT (
                id TEXT NOT NULL PRIMARY KEY, game_state_id TEXT NOT NULL, position INTEGER NOT NULL,
                event_id TEXT, status TEXT NOT NULL, deed_offer_id TEXT,
                FOREIGN KEY(game_state_id) REFERENCES ENGINE_STATE(game_state_id) ON UPDATE NO ACTION ON DELETE NO ACTION,
                FOREIGN KEY(event_id) REFERENCES EVENT(id) ON UPDATE NO ACTION ON DELETE NO ACTION,
                FOREIGN KEY(deed_offer_id) REFERENCES ENGINE_DEED(id) ON UPDATE NO ACTION ON DELETE NO ACTION
            )
        """.trimIndent())
        connection.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_ENGINE_EVENT_game_state_id_position ON ENGINE_EVENT(game_state_id, position)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS index_ENGINE_EVENT_event_id ON ENGINE_EVENT(event_id)")
        connection.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_ENGINE_EVENT_deed_offer_id ON ENGINE_EVENT(deed_offer_id)")
    }
}
