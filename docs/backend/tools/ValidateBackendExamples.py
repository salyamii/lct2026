"""Validate handoff schemas/fixtures/links; optionally rebuild its ZIP. No app or tests."""
from pathlib import Path
import argparse
import json
import re
import zipfile

import yaml
from jsonschema import Draft202012Validator, FormatChecker
from referencing import Registry, Resource

BASE = Path(__file__).resolve().parents[1]
DEVICE = "9f1c2d3e4a5b6078"
documents = {
    name: yaml.safe_load((BASE / name).read_text(encoding="utf-8"))
    for name in ("openapi.yaml", "analytics.openapi.yaml")
}
registry = Registry().with_resources((
    (BASE / name).as_uri(), Resource.from_contents({"$schema": "https://json-schema.org/draft/2020-12/schema", **document})
) for name, document in documents.items())


def resolve(ref, filename):
    external, fragment = ref.split("#", 1)
    target = external.removeprefix("./") or filename
    value = documents[target]
    for part in fragment.lstrip("/").split("/"):
        value = value[part.replace("~1", "/").replace("~0", "~")]
    return value


def check_refs(value, filename):
    if isinstance(value, dict):
        for key, item in value.items():
            if key == "$ref": resolve(item, filename)
            else: check_refs(item, filename)
    elif isinstance(value, list):
        for item in value: check_refs(item, filename)


for filename, document in documents.items():
    check_refs(document, filename)
    for schema in document["components"]["schemas"].values():
        Draft202012Validator.check_schema(schema)

api = documents["openapi.yaml"]
expected_paths = {
    "/api/pets": "post", "/v1/profiles/snapshot": "put",
    "/v1/profiles/snapshot/download": "post", "/v1/profiles/analytics": "post",
    "/v1/profiles/skills/query": "post", "/v1/profiles/rewards/pull": "post",
    "/v1/profiles/rewards/ack": "post", "/v1/parent-profiles/rewards": "post",
}
assert set(api["paths"]) == set(expected_paths)
assert "security" not in api and "securitySchemes" not in api["components"]
for path, method in expected_paths.items():
    operation = api["paths"][path][method]
    assert "security" not in operation
    schema = resolve(operation["requestBody"]["content"]["application/json"]["schema"]["$ref"], "openapi.yaml")
    assert "deviceId" in schema["required"], path
    assert not any(p.get("in") in ("path", "query") for p in operation.get("parameters", [])), path

schema_map = {
    "ack-parent-rewards-request.json": "AckParentRewardsRequest",
    "ack-parent-rewards-response.json": "AckParentRewardsResponse",
    "analytics-empty-request.json": "AnalyticsUploadRequest",
    "analytics-empty-response.json": "AnalyticsUploadResponse",
    "analytics-upload.json": "AnalyticsUploadRequest",
    "analytics-upload-response.json": "AnalyticsUploadResponse",
    "create-parent-reward-accessory.json": "CreateParentRewardRequest",
    "create-parent-reward-coins.json": "CreateParentRewardRequest",
    "parent-reward.json": "ParentRewardDto",
    "parent-rewards-response.json": "ParentRewardsResponse",
    "pull-parent-rewards-request.json": "PullParentRewardsRequest",
    "register-profile.json": "RegisterProfileRequest",
    "register-profile-response.json": "RegisterProfileResponse",
    "skill-assessments-empty-response.json": "SkillAssessmentsResponse",
    "skill-assessments-fixture-response.json": "SkillAssessmentsResponse",
    "skill-assessments-request.json": "SkillAssessmentsRequest",
    "skill-assessments-response.json": "SkillAssessmentsResponse",
    "snapshot-download-request.json": "SnapshotDownloadRequest",
    "snapshot-download-response.json": "SnapshotDownloadResponse",
    "snapshot-upload.json": "SnapshotUploadRequest",
    "snapshot-upload-response.json": "SnapshotUploadResponse",
}
examples = {path.name: json.loads(path.read_text(encoding="utf-8")) for path in (BASE / "examples").glob("*.json")}
assert set(examples) == set(schema_map) | {"world-snapshot.json"}
for filename, schema_name in schema_map.items():
    document_name = "openapi.yaml" if schema_name in api["components"]["schemas"] else "analytics.openapi.yaml"
    Draft202012Validator({"$ref": (BASE / document_name).as_uri() + "#/components/schemas/" + schema_name},
                        registry=registry, format_checker=FormatChecker()).validate(examples[filename])
    if "deviceId" in examples[filename]: assert examples[filename]["deviceId"] == DEVICE
    if "profileId" in examples[filename]: assert examples[filename]["profileId"] == DEVICE

archive = (BASE / "examples/world-snapshot.json").read_text(encoding="utf-8")
assert examples["snapshot-upload.json"]["snapshotJson"] == archive
assert examples["snapshot-download-response.json"]["snapshotJson"] == archive
assert examples["snapshot-upload-response.json"]["checksum"] == examples["world-snapshot.json"]["checksum"]
assert (BASE / "examples/profile-id-qr.txt").read_text(encoding="utf-8") == DEVICE
for path in BASE.rglob("*.md"):
    for target in re.findall(r"\]\(([^)]+)\)", path.read_text(encoding="utf-8")):
        if "://" in target or target.startswith("#"): continue
        assert (path.parent / target.split("#", 1)[0]).exists(), (path, target)

for path in BASE.rglob("*"):
    if path.suffix in (".md", ".json", ".yaml", ".java", ".ps1"):
        text = path.read_text(encoding="utf-8")
        forbidden = (r'Authorization\s*:|"installationId"\s*:|<deviceCredential>|parent-links/claim|profile-recoveries|recovery-grants|claim-parent-link'
                     if path.suffix == ".md" else
                     r"Bearer|Authorization|deviceCredential|installationId|parent-links/claim|profile-recoveries|recovery-grants|claim-parent-link")
        assert not re.search(forbidden, text), path
print(f"Validated {len(schema_map)} transport JSON examples, archive equality, all schema references and local Markdown links.")

args = argparse.ArgumentParser()
args.add_argument("--package", action="store_true")
if args.parse_args().package:
    destination = BASE / "lct-backend-contract-v1.zip"
    files = sorted(path for path in BASE.rglob("*") if path.is_file() and path.suffix != ".zip" and "__pycache__" not in path.parts)
    with zipfile.ZipFile(destination, "w", zipfile.ZIP_DEFLATED) as archive_zip:
        for path in files: archive_zip.write(path, path.relative_to(BASE).as_posix())
    with zipfile.ZipFile(destination) as archive_zip:
        assert archive_zip.testzip() is None
        assert len(archive_zip.namelist()) == len(files)
        for path in files: assert archive_zip.read(path.relative_to(BASE).as_posix()) == path.read_bytes()
    print(f"Packaged {len(files)} current files: {destination.name}, {destination.stat().st_size} bytes.")
