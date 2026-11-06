import gzip
import hashlib
import html
import json
import math
import sys
import time
from pathlib import Path

import index_pb2
from github_utils import REPO_NAME, run_gh
from google.protobuf import json_format

ARTIFACTS_DIR = Path.home() / "apk-artifacts"
REPO_DIR = Path.cwd()

ICON_BASE_URL = "https://cdn.jsdelivr.net/gh/HikariCollection/extensions-source@main"
RELEASE_BASE_URL = f"https://github.com/{REPO_NAME}/releases/download"
ASSET_LIMIT = 495
UPLOAD_CHUNK_SIZE = 80
UPLOAD_CHUNK_INTERVAL = 30

to_delete: list[str] = json.loads(sys.argv[1]) if len(sys.argv) > 1 else []
current_sha = sys.argv[2] if len(sys.argv) > 2 else ""
current_sha_short = current_sha[:7]
full_rebuild = (sys.argv[3] == "true") if len(sys.argv) > 3 else True

index_json_path = REPO_DIR / "index.json"
if index_json_path.exists():
    with index_json_path.open() as f:
        remote_proto = json_format.Parse(f.read(), index_pb2.Index())
else:
    remote_proto = index_pb2.Index()

remote_extensions = {
    ext.packageName: ext for ext in remote_proto.extensionList.extensions
}

release_assets_path = REPO_DIR / "release-assets.json"
if release_assets_path.exists():
    with release_assets_path.open() as f:
        release_assets = json.load(f)
else:
    release_assets = {}

updated_release_assets = {
    package_name: assets
    for package_name, assets in release_assets.items()
    if not full_rebuild
    and not any(package_name.endswith(f".{module}") for module in to_delete)
}

new_extensions: list[tuple[index_pb2.Extension, Path, Path, bool]] = []

SOURCE_DIR = Path(__file__).resolve().parents[2]
ICON_FILE = "res/mipmap-xhdpi/ic_launcher.png"


def get_icon_url(module: str, theme: str | None) -> str:
    module_icon = f"src/{module.replace('.', '/')}/{ICON_FILE}"
    if (SOURCE_DIR / module_icon).exists():
        return f"{ICON_BASE_URL}/{module_icon}"
    return f"{ICON_BASE_URL}/core/src/main/{ICON_FILE}"


for info_file in ARTIFACTS_DIR.glob("**/hikari-source-info.json"):
    with info_file.open(encoding="utf-8") as f:
        info = json.load(f)
    package_name = info["packageName"]
    apk = next((info_file.parent / "outputs/apk/release").glob("*.apk"), None)
    if apk is None:
        continue

    jar = next((info_file.parent / "outputs/jar/release").glob("*.jar"), apk)

    content_hash = info.get("contentHash", "")
    remote_ext = remote_extensions.get(package_name)
    changed = (
        remote_ext is None
        or remote_ext.versionCode != info["versionCode"]
        or release_assets.get(package_name) != content_hash
    )

    updated_release_assets[package_name] = content_hash

    ext = index_pb2.Extension(
        name=info["name"],
        packageName=package_name,
        resources=index_pb2.Resources(
            iconUrl=get_icon_url(info.get("module", package_name.removeprefix("app.hikari.extension.")), None),
        ),
        extensionLib=info["extensionLib"],
        versionCode=info["versionCode"],
        versionName=info["versionName"],
        contentWarning=1 if info["contentWarning"] == "SAFE" else (2 if info["contentWarning"] == "MIXED" else 3),
        sources=[
            index_pb2.Source(
                id=int(source["id"]),
                name=source["name"],
                language=source["lang"],
                homeUrl=source["baseUrl"],
                mirrorUrls=source.get("mirrorUrls", []),
            )
            for source in info["sources"]
        ],
    )
    new_extensions.append((ext, apk, jar, changed))

new_extensions.sort(key=lambda item: item[0].packageName)

changed_extensions = [item for item in new_extensions if item[3]]
total_changed_extensions = len(changed_extensions)
release_count = (
    math.ceil(total_changed_extensions / ASSET_LIMIT)
    if total_changed_extensions
    else 0
)
ext_per_release = (
    math.ceil(total_changed_extensions / release_count) if release_count else 0
)


def get_release_tag(batch_index: int) -> str:
    return (
        f"{current_sha_short}-{batch_index}" if release_count > 1 else current_sha_short
    )


changed_index = 0
for ext, apk, jar, changed in new_extensions:
    if changed:
        tag = get_release_tag(changed_index // ext_per_release)
        ext.resources.apkUrl = f"{RELEASE_BASE_URL}/{tag}/{apk.name}"
        ext.resources.jarUrl = f"{RELEASE_BASE_URL}/{tag}/{jar.name}"
        changed_index += 1
    elif ext.packageName in remote_extensions:
        old_resources = remote_extensions[ext.packageName].resources
        ext.resources.apkUrl = old_resources.apkUrl
        ext.resources.jarUrl = old_resources.jarUrl

final_extensions = []
final_extensions.extend(
    ext
    for ext in remote_proto.extensionList.extensions
    if not full_rebuild
    and not any(ext.packageName.endswith(f".{module}") for module in to_delete)
)
final_extensions.extend(ext for ext, _, _, _ in new_extensions)
final_extensions.sort(key=lambda ext: ext.packageName)

index = index_pb2.Index(
    name="Hikari Extensions",
    badgeLabel="Official",
    signingKey="956e72c8d1dc56c663b07c09d537e10614c25620650015b0f3e22a3d3a2e2e32",
    contact=index_pb2.Contact(
        website="https://github.com/HikariCollection",
        discord=None,
    ),
    extensionList=index_pb2.ExtensionList(extensions=final_extensions),
)

with REPO_DIR.joinpath("index.json").open("w", encoding="utf-8") as f:
    f.write(
        json_format.MessageToJson(
            index,
            always_print_fields_with_no_presence=False,
            preserving_proto_field_name=True,
        )
    )

with REPO_DIR.joinpath("index.pb").open("wb") as f:
    f.write(gzip.compress(index.SerializeToString(deterministic=True), mtime=0))

with release_assets_path.open("w", encoding="utf-8") as f:
    json.dump(updated_release_assets, f, indent=2, sort_keys=True)
    f.write("\n")
