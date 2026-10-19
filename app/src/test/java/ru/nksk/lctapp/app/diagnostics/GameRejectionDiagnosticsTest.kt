package ru.nksk.lctapp.app.diagnostics

import org.junit.Assert.*
import org.junit.Test
import ru.nksk.lctapp.domain.engine.BlockReason
import ru.nksk.lctapp.domain.engine.ContentFailureSource
import ru.nksk.lctapp.domain.engine.EngineCommand
import ru.nksk.lctapp.domain.engine.RejectedGameCommand
import ru.nksk.lctapp.domain.engine.RejectionOrigin

class GameRejectionDiagnosticsTest {
    @Test fun fixedFailureAndOriginalReleaseFramesRemainAvailableForRetracing() {
        val frames = listOf("a.b.c(Unknown Source:487)", "a.b.d(SourceFile:922)")
        val fields = RejectedGameCommand(EngineCommand.FinishDay,
            BlockReason.InvalidContent("Failed requirement."),
            ContentFailureSource("java.lang.IllegalArgumentException", frames)).diagnosticFields()
        assertEquals("FinishDay", fields["last_rejected_action"])
        assertEquals("COMMAND", fields["last_rejected_origin"])
        assertEquals("InvalidContent", fields["last_rejected_reason"])
        assertEquals("Failed requirement.", fields["last_rejected_detail"])
        assertEquals(frames[0], fields["last_rejected_source_0"])
        assertEquals(frames[1], fields["last_rejected_source_1"])
        assertEquals("none", fields["last_rejected_source_2"])
    }

    @Test fun disabledButtonFailureIsIdentifiedAsAGuardCheckInExportedContext() {
        val fields = RejectedGameCommand(EngineCommand.FinishDay,
            BlockReason.InvalidContent("Failed requirement."),
            ContentFailureSource("java.lang.IllegalArgumentException", listOf("a.b.c(SourceFile:42)")),
            RejectionOrigin.GUARD_CHECK).diagnosticFields()
        assertEquals("FinishDay", fields["last_rejected_action"])
        assertEquals("GUARD_CHECK", fields["last_rejected_origin"])
        assertEquals("InvalidContent", fields["last_rejected_reason"])
        assertEquals("Failed requirement.", fields["last_rejected_detail"])
        assertEquals("a.b.c(SourceFile:42)", fields["last_rejected_source_0"])
    }

    @Test fun openIdentifiersAndUserCommandPayloadsCannotEnterTheReport() {
        val rejection = RejectedGameCommand(EngineCommand.RenamePet("private old name", "private new name"),
            BlockReason.InvalidContent("Unknown event: private-content-id"))
        val fields = rejection.diagnosticFields()
        assertEquals("RenamePet", fields["last_rejected_action"])
        assertEquals("unknown_event (id omitted)", fields["last_rejected_detail"])
        assertFalse(fields.toString().contains("private"))
        val arbitrary = rejection.copy(reason = BlockReason.InvalidContent("private custom detail")).diagnosticFields()
        assertFalse(arbitrary.toString().contains("private"))
    }

    @Test fun ordinaryCareGuardsDoNotClearTheLastContentFailure() {
        val context = RejectedGameCommand(EngineCommand.FinishDay,
            BlockReason.MissingCarriedLore).diagnosticFields().toMutableMap()
        context.putAll(RejectedGameCommand(EngineCommand.Feed("private-meal"), BlockReason.MustEat).diagnosticFields())
        assertEquals("FinishDay", context["last_rejected_action"])
        assertEquals("MissingCarriedLore", context["last_rejected_reason"])
        assertEquals("carried_plan_mismatch", context["last_rejected_detail"])
    }
}
