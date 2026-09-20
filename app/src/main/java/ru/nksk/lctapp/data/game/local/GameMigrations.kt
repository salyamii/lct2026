package ru.nksk.lctapp.data.game.local

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/** Preserve ownership and legacy item values; do not fabricate prices or award inventory. */
internal val MIGRATION_4_5 = object : Migration(4, 5) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE ITEM ADD COLUMN category TEXT NOT NULL DEFAULT 'STORY'")
        connection.execSQL("ALTER TABLE ITEM ADD COLUMN price_coins INTEGER")
    }
}

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

/** Two development branches used v2 for different schemas. Converge without resetting either save. */
internal val MIGRATION_2_3 = object : Migration(2, 3) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL("""
            CREATE TABLE IF NOT EXISTS LEGACY_EXPENSE_STATE (
                game_state_id TEXT NOT NULL PRIMARY KEY,
                expense_sequence INTEGER NOT NULL,
                expense_stage TEXT NOT NULL,
                earning_attempt INTEGER NOT NULL,
                FOREIGN KEY(game_state_id) REFERENCES GAME_STATE(id) ON UPDATE NO ACTION ON DELETE NO ACTION
            )
        """.trimIndent())
        val columns = connection.prepare("PRAGMA table_info(GAME_STATE)").use { statement ->
            buildSet { while (statement.step()) add(statement.getText(1)) }
        }
        val legacyColumns = listOf("expense_sequence", "expense_stage", "earning_attempt")
        if (legacyColumns.any { it in columns }) {
            check(legacyColumns.all { it in columns }) { "Incomplete expenses save schema" }
            connection.execSQL("""
                INSERT INTO LEGACY_EXPENSE_STATE (game_state_id, expense_sequence, expense_stage, earning_attempt)
                SELECT id, expense_sequence, expense_stage, earning_attempt FROM GAME_STATE
            """.trimIndent())
            // BundledSQLiteDriver supports DROP COLUMN. Keep the original table and all incoming FKs.
            legacyColumns.forEach { connection.execSQL("ALTER TABLE GAME_STATE DROP COLUMN $it") }
        }
        // IF NOT EXISTS preserves existing engine rows; the expenses branch has none of these tables.
        MIGRATION_1_2.migrate(connection)
        // Expenses reference tables remain intact, even though this branch does not consume them.
    }
}

/** Historical v4 split; v6 merges mini-game increments back into satiety. */
internal val MIGRATION_3_4 = object : Migration(3, 4) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE GAME_STATE ADD COLUMN hunger INTEGER NOT NULL DEFAULT 0")
        connection.execSQL("""
            CREATE TABLE IF NOT EXISTS MINI_GAME_COMPLETION (
                attempt_id TEXT NOT NULL PRIMARY KEY,
                game_state_id TEXT NOT NULL,
                FOREIGN KEY(game_state_id) REFERENCES GAME_STATE(id) ON UPDATE NO ACTION ON DELETE NO ACTION
            )
        """.trimIndent())
        connection.execSQL("CREATE INDEX IF NOT EXISTS index_MINI_GAME_COMPLETION_game_state_id ON MINI_GAME_COMPLETION(game_state_id)")
    }
}

/** Fold v4/v5 mini-game increments into the original hunger parameter (D-076). */
internal val MIGRATION_5_6 = object : Migration(5, 6) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL("UPDATE GAME_STATE SET satiety = satiety + hunger")
        // Bundled SQLite supports DROP COLUMN; preserve the parent row and its incoming FKs.
        connection.execSQL("ALTER TABLE GAME_STATE DROP COLUMN hunger")
    }
}

/** Add an optional goal choice without changing any existing game or reference rows. */
internal val MIGRATION_6_7 = object : Migration(6, 7) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL("""
            CREATE TABLE IF NOT EXISTS GOAL_SELECTION (
                game_state_id TEXT NOT NULL PRIMARY KEY,
                goal_id TEXT NOT NULL,
                FOREIGN KEY(game_state_id) REFERENCES GAME_STATE(id) ON UPDATE NO ACTION ON DELETE NO ACTION,
                FOREIGN KEY(goal_id) REFERENCES GOAL(id) ON UPDATE NO ACTION ON DELETE NO ACTION
            )
        """.trimIndent())
        connection.execSQL("CREATE INDEX IF NOT EXISTS index_GOAL_SELECTION_goal_id ON GOAL_SELECTION(goal_id)")
    }
}

/** Prior saves had no name or age: correct the old hardcoded teen artwork without resetting progress. */
internal val MIGRATION_7_8 = object : Migration(7, 8) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE GAME_STATE ADD COLUMN pet_name TEXT NOT NULL DEFAULT 'Рыжик'")
        connection.execSQL("ALTER TABLE GAME_STATE ADD COLUMN pet_age TEXT NOT NULL DEFAULT 'CUB'")
    }
}

/** Adopt D-092 for the former cub starter look; keep owned gear and all gameplay progress. */
internal val MIGRATION_8_9 = object : Migration(8, 9) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL("UPDATE GAME_STATE SET selected_look = 'PLAIN' WHERE pet_age = 'CUB' AND selected_look = 'BACKPACK'")
    }
}

/** Preserve every v9 row; previous builds could not finish a goal's story chapter. */
internal val MIGRATION_9_10 = object : Migration(9, 10) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL("""
            CREATE TABLE IF NOT EXISTS COMPLETED_GOAL_PROJECT (
                decision_id TEXT NOT NULL PRIMARY KEY,
                goal_id TEXT NOT NULL,
                FOREIGN KEY(decision_id) REFERENCES PLAYER_DECISION(id) ON UPDATE NO ACTION ON DELETE NO ACTION,
                FOREIGN KEY(goal_id) REFERENCES GOAL(id) ON UPDATE NO ACTION ON DELETE NO ACTION
            )
        """.trimIndent())
        connection.execSQL("CREATE INDEX IF NOT EXISTS index_COMPLETED_GOAL_PROJECT_goal_id ON COMPLETED_GOAL_PROJECT(goal_id)")
    }
}

/** Earlier spending cannot be reconstructed reliably; leave its journal empty, preserve the save. */
internal val MIGRATION_10_11 = object : Migration(10, 11) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE ENGINE_STATE ADD COLUMN opening_energy INTEGER")
        connection.execSQL("""
            CREATE TABLE IF NOT EXISTS DAY_JOURNAL (
                id TEXT NOT NULL PRIMARY KEY,
                game_state_id TEXT NOT NULL,
                position INTEGER NOT NULL,
                kind TEXT NOT NULL,
                source_id TEXT NOT NULL,
                money_delta INTEGER NOT NULL,
                energy_delta INTEGER NOT NULL,
                FOREIGN KEY(game_state_id) REFERENCES ENGINE_STATE(game_state_id) ON UPDATE NO ACTION ON DELETE NO ACTION
            )
        """.trimIndent())
        connection.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_DAY_JOURNAL_game_state_id_position ON DAY_JOURNAL(game_state_id, position)")
    }
}

/** Before color selection all displayed foxes used copper. Preserve every existing field and row. */
internal val MIGRATION_11_12 = object : Migration(11, 12) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE GAME_STATE ADD COLUMN pet_color TEXT NOT NULL DEFAULT 'COPPER'")
    }
}
