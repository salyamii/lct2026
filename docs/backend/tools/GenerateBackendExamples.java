import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import kotlin.Unit;
import kotlinx.serialization.KSerializer;
import kotlinx.serialization.json.Json;
import kotlinx.serialization.json.JsonKt;
import ru.nksk.lctapp.domain.analytics.SkillId;
import ru.nksk.lctapp.domain.backend.AnalyticsContractKt;
import ru.nksk.lctapp.domain.backend.AnalyticsUploadRequest;
import ru.nksk.lctapp.domain.backend.AnalyticsUploadResponse;
import ru.nksk.lctapp.domain.backend.AckParentRewardsRequest;
import ru.nksk.lctapp.domain.backend.CreateParentRewardRequest;
import ru.nksk.lctapp.domain.backend.PullParentRewardsRequest;
import ru.nksk.lctapp.domain.backend.RegisterProfileRequest;
import ru.nksk.lctapp.domain.backend.RegisterProfileResponse;
import ru.nksk.lctapp.domain.backend.SkillAssessmentsRequest;
import ru.nksk.lctapp.domain.backend.SnapshotDownloadRequest;
import ru.nksk.lctapp.domain.backend.SnapshotContractKt;
import ru.nksk.lctapp.domain.backend.SnapshotDownloadResponse;
import ru.nksk.lctapp.domain.backend.SnapshotUploadRequest;
import ru.nksk.lctapp.domain.backend.SnapshotUploadResponse;
import ru.nksk.lctapp.domain.content.StoryContent;
import ru.nksk.lctapp.domain.engine.EngineRules;
import ru.nksk.lctapp.domain.engine.GameCatalog;
import ru.nksk.lctapp.domain.engine.MealDefinition;
import ru.nksk.lctapp.domain.game.GameState;
import ru.nksk.lctapp.domain.history.AuditEntry;
import ru.nksk.lctapp.domain.history.GameSnapshot;
import ru.nksk.lctapp.domain.history.HistoryCodec;
import ru.nksk.lctapp.domain.history.WorldSnapshot;
import ru.nksk.lctapp.domain.history.WorldSnapshotCodec;
import ru.nksk.lctapp.domain.history.CloudWorldKt;
import ru.nksk.lctapp.domain.timemachine.GameCatalogFingerprint;

/** Documentation artifact generator. No Android, repository, network or test runner is involved. */
public final class GenerateBackendExamples {
    private static final String DEVICE_ID = "9f1c2d3e4a5b6078";
    private static final String RUN_ID = "73d831fd-cf83-4a8c-9e99-973fe16fef4e";
    private static final String UPLOAD_ID = "851818aa-f937-4a24-b588-00a36b6085a0";
    private static final String BATCH_ID = "5e06cb94-48c8-4a14-9c9c-c0f7c0a40720";
    private static final String RULES_ID = "ryzhik-2026-09-19-v1";
    private static final Json WIRE = JsonKt.Json(Json.Default, builder -> {
        builder.setEncodeDefaults(true);
        builder.setPrettyPrint(true);
        builder.setClassDiscriminator("_type");
        return Unit.INSTANCE;
    });

    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Expected output examples directory");
        Path output = Path.of(args[0]).toAbsolutePath().normalize();
        Files.createDirectories(output);

        // Deliberately synthetic: no content references, choices or invented learning evidence.
        // The supplied rule ID and numeric defaults match the packaged initial day.
        GameState state = HistoryCodec.INSTANCE.decodeState("""
            {
              "pet": {"selectedLookId":"PLAIN","visualState":"NORMAL","name":"Рыжик","age":"CUB","color":"COPPER"},
              "economy": {"plan":{"needs":35,"wants":20,"savings":20,"reserve":25},"unallocated":0,"planning":null,"availableBalance":100,"savingsBalance":0},
              "story": {"currentDayId":null,"nextScriptPosition":null,"activeEventId":null,"decisions":[]},
              "satiety":0,"fatigue":0,"ownedItems":[],
              "engine": {"rulesId":"%s","revision":0,"day":1,"phase":"RUNNING","steps":0,"energy":5,"ateToday":false,
                         "nextMorningEnergy":null,"openingBalance":100,"events":[],"deeds":[],"openingEnergy":5}
            }
            """.formatted(RULES_ID));
        String stateJson = HistoryCodec.INSTANCE.encodeState(state);
        AuditEntry initialized = HistoryCodec.INSTANCE.decodeEntry("""
            {"id":"%s:initialized","sequence":1,"runId":"%s","type":"INITIALIZED","after":%s}
            """.formatted(RUN_ID, RUN_ID, stateJson));
        GameSnapshot snapshot = HistoryCodec.INSTANCE.snapshot(RUN_ID, state, List.of(initialized), List.of());
        WorldSnapshot world = CloudWorldKt.toCloudWorldRead(snapshot).getWorld();
        String archive = WorldSnapshotCodec.INSTANCE.encode(world);
        require(world.equals(WorldSnapshotCodec.INSTANCE.decode(archive)), "World did not round-trip");

        StoryContent content = new StoryContent();
        GameCatalog fixtureCatalog = new GameCatalog(content, Map.of(), Map.of(),
            new EngineRules(RULES_ID, 5, 3, 1, 100L),
            List.of(new MealDefinition("docs-basic-meal", 5L, null, null, 0)),
            "docs-day", "docs-introduction", List.of(), List.of(), Set.of(), List.of(), null, Map.of());
        String fingerprint = GameCatalogFingerprint.INSTANCE.compute(fixtureCatalog);
        SnapshotUploadRequest upload = SnapshotContractKt.snapshotUploadRequest(DEVICE_ID, world, UPLOAD_ID, null, fingerprint);
        SnapshotUploadResponse uploadResponse = new SnapshotUploadResponse(UPLOAD_ID, RUN_ID, 1L, world.getChecksum());
        SnapshotDownloadResponse download = new SnapshotDownloadResponse(RUN_ID, 1L, fingerprint, archive, 1, "CURRENT_WORLD");
        AnalyticsUploadRequest analytics = AnalyticsContractKt.analyticsUploadRequest(DEVICE_ID, BATCH_ID, snapshot, content);
        AnalyticsUploadResponse analyticsResponse = new AnalyticsUploadResponse(BATCH_ID, RUN_ID,
            snapshot.getHistorySequence(), analytics.getFacts().stream().map(fact -> fact.getEventId()).toList(), 1);
        require(analytics.getSkills().size() == SkillId.values().length, "Incomplete skill evidence list");
        require(analytics.getFacts().isEmpty(), "The initialized fixture must not invent learning facts");

        String uploadJson = encode(SnapshotUploadRequest.Companion.serializer(), upload);
        String downloadJson = encode(SnapshotDownloadResponse.Companion.serializer(), download);
        SnapshotUploadRequest decodedUpload = WIRE.decodeFromString(SnapshotUploadRequest.Companion.serializer(), uploadJson);
        SnapshotDownloadResponse decodedDownload = WIRE.decodeFromString(SnapshotDownloadResponse.Companion.serializer(), downloadJson);
        require(archive.equals(decodedUpload.getSnapshotJson()) && archive.equals(decodedDownload.getSnapshotJson()),
            "Transport serialization changed the opaque archive");
        require(world.equals(WorldSnapshotCodec.INSTANCE.decode(decodedDownload.getSnapshotJson())),
            "Downloaded archive did not validate");

        // No trailing newline: this file is byte-for-byte the UTF-8 value of snapshotJson.
        Files.writeString(output.resolve("world-snapshot.json"), archive, StandardCharsets.UTF_8);
        write(output, "snapshot-upload.json", uploadJson);
        write(output, "snapshot-upload-response.json", encode(SnapshotUploadResponse.Companion.serializer(), uploadResponse));
        write(output, "snapshot-download-response.json", downloadJson);
        write(output, "analytics-upload.json", encode(AnalyticsUploadRequest.Companion.serializer(), analytics));
        write(output, "analytics-upload-response.json", encode(AnalyticsUploadResponse.Companion.serializer(), analyticsResponse));
        validateExample(output, "register-profile.json", RegisterProfileRequest.Companion.serializer());
        validateExample(output, "register-profile-response.json", RegisterProfileResponse.Companion.serializer());
        validateExample(output, "snapshot-download-request.json", SnapshotDownloadRequest.Companion.serializer());
        validateExample(output, "skill-assessments-request.json", SkillAssessmentsRequest.Companion.serializer());
        validateExample(output, "pull-parent-rewards-request.json", PullParentRewardsRequest.Companion.serializer());
        validateExample(output, "ack-parent-rewards-request.json", AckParentRewardsRequest.Companion.serializer());
        validateExample(output, "create-parent-reward-coins.json", CreateParentRewardRequest.Companion.serializer());
        validateExample(output, "create-parent-reward-accessory.json", CreateParentRewardRequest.Companion.serializer());
        System.out.println("Generated six synthetic backend examples; WorldSnapshot checksum: " + world.getChecksum());
        System.out.println("Validated eight deviceId transport fixtures against current Kotlin serializers.");
        System.out.println("History sequence: " + snapshot.getHistorySequence() + "; skills: " + analytics.getSkills().size());
    }

    private static <T> String encode(KSerializer<T> serializer, T value) {
        return WIRE.encodeToString(serializer, value);
    }

    private static void write(Path output, String filename, String json) throws Exception {
        Files.writeString(output.resolve(filename), json + "\n", StandardCharsets.UTF_8);
    }

    private static <T> void validateExample(Path output, String filename, KSerializer<T> serializer) throws Exception {
        WIRE.decodeFromString(serializer, Files.readString(output.resolve(filename), StandardCharsets.UTF_8));
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
