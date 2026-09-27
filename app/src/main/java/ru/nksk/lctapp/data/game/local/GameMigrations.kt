package ru.nksk.lctapp.data.game.local

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/** Transport cursors and frozen requests do not replace or rewrite any saved gameplay. */
internal val MIGRATION_20_21 = object : Migration(20, 21) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL("""
            CREATE TABLE IF NOT EXISTS BACKEND_SYNC_STATE (
                profile_id TEXT NOT NULL, backend_url TEXT NOT NULL, server_revision INTEGER,
                last_snapshot_checksum TEXT, last_analytics_sequence INTEGER NOT NULL,
                last_synced_at_epoch_ms INTEGER, skills_payload TEXT, game_run_id TEXT,
                local_generation TEXT, reward_fetch_cursor INTEGER, PRIMARY KEY(profile_id))
        """.trimIndent())
        connection.execSQL("""
            CREATE TABLE IF NOT EXISTS PENDING_BACKEND_REQUEST (
                profile_id TEXT NOT NULL, kind TEXT NOT NULL, request_id TEXT NOT NULL,
                payload TEXT NOT NULL, PRIMARY KEY(profile_id, kind))
        """.trimIndent())
    }
}

/** Upgrade the complete aggregate through the repository before exposing it; never guess past envelope expenses in SQL. */
internal val MIGRATION_19_20 = object : Migration(19, 20) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE GAME_STATE ADD COLUMN budget_model_version INTEGER NOT NULL DEFAULT 0")
    }
}

/** Existing project choices are kept as history; a purchase target requires a new explicit choice. */
internal val MIGRATION_18_19 = object : Migration(18, 19) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL("""
            CREATE TABLE IF NOT EXISTS SAVING_GOAL_SELECTION (
                game_state_id TEXT NOT NULL, item_id TEXT NOT NULL,
                PRIMARY KEY(game_state_id),
                FOREIGN KEY(game_state_id) REFERENCES GAME_STATE(id) ON UPDATE NO ACTION ON DELETE NO ACTION,
                FOREIGN KEY(item_id) REFERENCES ITEM(id) ON UPDATE NO ACTION ON DELETE NO ACTION)
        """.trimIndent())
        connection.execSQL("CREATE INDEX IF NOT EXISTS index_SAVING_GOAL_SELECTION_item_id ON SAVING_GOAL_SELECTION(item_id)")
        connection.execSQL("ALTER TABLE ONBOARDING_DRAFT ADD COLUMN saving_item_id TEXT")
        connection.execSQL("UPDATE ONBOARDING_DRAFT SET step = 'GOAL_SELECTION' WHERE step = 'INTRODUCTION'")
    }
}

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

/** Extend the latest main schema without replacing any saved identity or progress. */
internal val MIGRATION_12_13 = object : Migration(12, 13) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE GAME_STATE ADD COLUMN pet_temperament TEXT")
        connection.execSQL("""
            CREATE TABLE IF NOT EXISTS ONBOARDING_DRAFT (
                id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL,
                temperament TEXT NOT NULL, fur TEXT NOT NULL,
                step TEXT NOT NULL DEFAULT 'PROFILE',
                accessory_id TEXT NOT NULL DEFAULT 'BACKPACK'
            )
        """.trimIndent())
    }
}

/** Preserve the whole save; previous menu always showed the daytime city. */
internal val MIGRATION_13_14 = object : Migration(13, 14) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE GAME_STATE ADD COLUMN location_id TEXT NOT NULL DEFAULT 'city'")
        connection.execSQL("ALTER TABLE GAME_STATE ADD COLUMN location_lighting TEXT NOT NULL DEFAULT 'DAY'")
    }
}

/** Keep every game/profile value; old introductions now resume at the required goal chooser. */
internal val MIGRATION_14_15 = object : Migration(14, 15) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE ONBOARDING_DRAFT ADD COLUMN goal_id TEXT")
        connection.execSQL("UPDATE ONBOARDING_DRAFT SET step = 'GOAL_SELECTION' WHERE step = 'INTRODUCTION'")
    }
}

/** Convert the actual balance once; the former plan never represented additional money. */
internal val MIGRATION_15_16 = object : Migration(15, 16) {
    override suspend fun migrate(connection: SQLiteConnection) {
        // Keep the one-time technical top-up outside this day's earned/spent totals.
        connection.execSQL("ALTER TABLE ENGINE_STATE ADD COLUMN balance_adjustment INTEGER NOT NULL DEFAULT 0")
        connection.execSQL("""
            UPDATE ENGINE_STATE SET balance_adjustment = (
                SELECT MAX(35 - balance, 0) FROM GAME_STATE WHERE id = ENGINE_STATE.game_state_id
            )
        """.trimIndent())
        // Rename in place: parent identity and every referencing FK stay intact.
        connection.execSQL("ALTER TABLE GAME_STATE RENAME COLUMN balance TO unallocated")
        for (section in listOf("needs", "wants", "savings", "reserve")) {
            connection.execSQL("ALTER TABLE GAME_STATE RENAME COLUMN planned_$section TO $section")
        }
        connection.execSQL("UPDATE GAME_STATE SET unallocated = MAX(unallocated, 35), needs = 0, wants = 0, savings = 0, reserve = 0")
        connection.execSQL("""
            CREATE TABLE IF NOT EXISTS BUDGET_PLANNING (
                game_state_id TEXT NOT NULL PRIMARY KEY,
                session_id TEXT NOT NULL,
                reason TEXT NOT NULL,
                stage TEXT NOT NULL,
                income INTEGER NOT NULL,
                revision INTEGER NOT NULL,
                FOREIGN KEY(game_state_id) REFERENCES GAME_STATE(id) ON UPDATE NO ACTION ON DELETE NO ACTION
            )
        """.trimIndent())
        connection.execSQL("""
            INSERT INTO BUDGET_PLANNING (game_state_id, session_id, reason, stage, income, revision)
            SELECT id, 'migration-16-' || id, 'MIGRATION', 'ALLOCATION', 0, 0 FROM GAME_STATE
        """.trimIndent())
    }
}

/** v16 savings are real money. Preserve them; combine only spendable categories. */
internal val MIGRATION_16_17 = object : Migration(16, 17) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE GAME_STATE ADD COLUMN available_balance INTEGER NOT NULL DEFAULT 0")
        connection.execSQL("ALTER TABLE GAME_STATE ADD COLUMN savings_balance INTEGER NOT NULL DEFAULT 0")
        connection.execSQL("UPDATE GAME_STATE SET available_balance = needs + wants + reserve + unallocated, savings_balance = savings")
        for (column in listOf("draft_needs", "draft_wants", "draft_savings", "draft_reserve", "base_amount")) {
            connection.execSQL("ALTER TABLE BUDGET_PLANNING ADD COLUMN $column INTEGER")
        }
        // Already saved coins are not allocated again. The original distribution remains in GAME_STATE.
        connection.execSQL("""
            UPDATE BUDGET_PLANNING SET
                draft_needs = (SELECT needs FROM GAME_STATE WHERE id = game_state_id),
                draft_wants = (SELECT wants FROM GAME_STATE WHERE id = game_state_id),
                draft_savings = 0,
                draft_reserve = (SELECT reserve FROM GAME_STATE WHERE id = game_state_id),
                base_amount = (SELECT available_balance FROM GAME_STATE WHERE id = game_state_id)
        """.trimIndent())
        connection.execSQL("""
            CREATE TABLE IF NOT EXISTS GAME_RUN (
                game_state_id TEXT NOT NULL PRIMARY KEY, run_id TEXT NOT NULL,
                FOREIGN KEY(game_state_id) REFERENCES GAME_STATE(id) ON UPDATE NO ACTION ON DELETE NO ACTION)
        """.trimIndent())
        connection.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_GAME_RUN_run_id ON GAME_RUN(run_id)")
        connection.execSQL("INSERT INTO GAME_RUN SELECT id, 'import-17-' || lower(hex(randomblob(16))) FROM GAME_STATE")
        connection.execSQL("""
            CREATE TABLE IF NOT EXISTS GAME_AUDIT (
                id TEXT NOT NULL PRIMARY KEY, run_id TEXT NOT NULL, sequence INTEGER NOT NULL,
                type TEXT NOT NULL, format_version INTEGER NOT NULL, payload TEXT NOT NULL,
                FOREIGN KEY(run_id) REFERENCES GAME_RUN(run_id) ON UPDATE NO ACTION ON DELETE NO ACTION)
        """.trimIndent())
        connection.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_GAME_AUDIT_run_id_sequence ON GAME_AUDIT(run_id, sequence)")
        connection.execSQL("""
            CREATE TABLE IF NOT EXISTS AUDIT_FACT_ID (
                fact_id TEXT NOT NULL PRIMARY KEY, audit_id TEXT NOT NULL,
                FOREIGN KEY(audit_id) REFERENCES GAME_AUDIT(id) ON UPDATE NO ACTION ON DELETE NO ACTION)
        """.trimIndent())
        connection.execSQL("CREATE INDEX IF NOT EXISTS index_AUDIT_FACT_ID_audit_id ON AUDIT_FACT_ID(audit_id)")
        connection.execSQL("""
            CREATE TABLE IF NOT EXISTS AUDIT_OUTBOX (
                audit_id TEXT NOT NULL PRIMARY KEY, acknowledged INTEGER NOT NULL DEFAULT 0,
                FOREIGN KEY(audit_id) REFERENCES GAME_AUDIT(id) ON UPDATE NO ACTION ON DELETE NO ACTION)
        """.trimIndent())
        connection.execSQL("""
            CREATE TABLE IF NOT EXISTS FINANCIAL_PERIOD (
                id TEXT NOT NULL PRIMARY KEY, game_state_id TEXT NOT NULL, position INTEGER NOT NULL,
                goal_id TEXT NOT NULL, ordinal INTEGER NOT NULL, started_day INTEGER NOT NULL,
                opening_available INTEGER NOT NULL, opening_savings INTEGER NOT NULL, closed_day INTEGER,
                needs_provided INTEGER NOT NULL, independently_saved INTEGER NOT NULL,
                reviewed_plan INTEGER NOT NULL, imported INTEGER NOT NULL,
                income INTEGER NOT NULL, spent_available INTEGER NOT NULL, spent_savings INTEGER NOT NULL,
                deposited INTEGER NOT NULL, withdrawn INTEGER NOT NULL,
                FOREIGN KEY(game_state_id) REFERENCES GAME_STATE(id) ON UPDATE NO ACTION ON DELETE NO ACTION)
        """.trimIndent())
        connection.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_FINANCIAL_PERIOD_game_state_id_position ON FINANCIAL_PERIOD(game_state_id, position)")
        connection.execSQL("""
            CREATE TABLE IF NOT EXISTS FINANCIAL_CURSOR (
                game_state_id TEXT NOT NULL PRIMARY KEY, period_id TEXT,
                FOREIGN KEY(game_state_id) REFERENCES GAME_STATE(id) ON UPDATE NO ACTION ON DELETE NO ACTION,
                FOREIGN KEY(period_id) REFERENCES FINANCIAL_PERIOD(id) ON UPDATE NO ACTION ON DELETE NO ACTION)
        """.trimIndent())
        connection.execSQL("CREATE INDEX IF NOT EXISTS index_FINANCIAL_CURSOR_period_id ON FINANCIAL_CURSOR(period_id)")
        connection.execSQL("""
            CREATE TABLE IF NOT EXISTS BUDGET_PLAN_REVISION (
                id TEXT NOT NULL PRIMARY KEY, game_state_id TEXT NOT NULL, position INTEGER NOT NULL,
                period_id TEXT, ordinal INTEGER NOT NULL, day INTEGER NOT NULL, available_basis INTEGER NOT NULL,
                needs INTEGER NOT NULL, wants INTEGER NOT NULL, savings INTEGER NOT NULL, reserve INTEGER NOT NULL,
                reason TEXT NOT NULL, previous_id TEXT, known_needs INTEGER, cause_action_id TEXT,
                FOREIGN KEY(game_state_id) REFERENCES GAME_STATE(id) ON UPDATE NO ACTION ON DELETE NO ACTION,
                FOREIGN KEY(period_id) REFERENCES FINANCIAL_PERIOD(id) ON UPDATE NO ACTION ON DELETE NO ACTION)
        """.trimIndent())
        connection.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_BUDGET_PLAN_REVISION_game_state_id_position ON BUDGET_PLAN_REVISION(game_state_id, position)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS index_BUDGET_PLAN_REVISION_period_id ON BUDGET_PLAN_REVISION(period_id)")
        connection.execSQL("""
            CREATE TABLE IF NOT EXISTS FINANCIAL_PRACTICE (
                game_state_id TEXT NOT NULL PRIMARY KEY, task_payload TEXT NOT NULL,
                answered_option_id TEXT, used_hint INTEGER NOT NULL, attempts INTEGER NOT NULL,
                FOREIGN KEY(game_state_id) REFERENCES GAME_STATE(id) ON UPDATE NO ACTION ON DELETE NO ACTION)
        """.trimIndent())
        // Only a selected unfinished project has a known current period. No prior milestones are invented.
        connection.execSQL("""
            INSERT INTO FINANCIAL_PERIOD
            SELECT 'import-17-period-' || gs.id, gs.id, 0, selected.goal_id,
                (SELECT COUNT(*) FROM COMPLETED_GOAL_PROJECT) + 1,
                COALESCE(engine.day, 1), gs.available_balance, gs.savings_balance, NULL, 0, 0, 0, 1, 0, 0, 0, 0, 0
            FROM GAME_STATE gs INNER JOIN GOAL_SELECTION selected ON selected.game_state_id = gs.id
                LEFT JOIN ENGINE_STATE engine ON engine.game_state_id = gs.id
        """.trimIndent())
        connection.execSQL("INSERT INTO FINANCIAL_CURSOR SELECT game_state_id, id FROM FINANCIAL_PERIOD")
    }
}

/** Retain unknown past exposure/practice as unknown; never guess a previous success or date. */
internal val MIGRATION_17_18 = object : Migration(17, 18) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE FINANCIAL_PERIOD ADD COLUMN savings_practice_payload TEXT")
        connection.execSQL("ALTER TABLE FINANCIAL_PERIOD ADD COLUMN review_evidence_payload TEXT")
        connection.execSQL("""
            CREATE TABLE IF NOT EXISTS EVENT_EXPOSURE (
                game_state_id TEXT NOT NULL, event_id TEXT NOT NULL, position INTEGER NOT NULL,
                last_offered_day INTEGER, last_completed_day INTEGER,
                offer_count INTEGER NOT NULL, completion_count INTEGER NOT NULL,
                PRIMARY KEY(game_state_id, event_id),
                FOREIGN KEY(game_state_id) REFERENCES GAME_STATE(id) ON UPDATE NO ACTION ON DELETE NO ACTION,
                FOREIGN KEY(event_id) REFERENCES EVENT(id) ON UPDATE NO ACTION ON DELETE NO ACTION)
        """.trimIndent())
        connection.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_EVENT_EXPOSURE_game_state_id_position ON EVENT_EXPOSURE(game_state_id, position)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS index_EVENT_EXPOSURE_event_id ON EVENT_EXPOSURE(event_id)")
    }
}
