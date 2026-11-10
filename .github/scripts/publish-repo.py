#!/usr/bin/env python3
import argparse
import gzip
import json
import shutil
import sys
from pathlib import Path

BASE_DIR = Path(__file__).resolve().parent.parent.parent
RAW_BASE = "https://raw.githubusercontent.com/HikariCollection/extensions/repo"
SIGNING_KEY = "956e72c8d1dc56c663b07c09d537e10614c25620650015b0f3e22a3d3a2e2e32"

CONTENT_WARNING_MAP = {
    "SAFE": 1,
    "MIXED": 2,
    "NSFW": 3,
}


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Publish built extensions and generate repo index files."
    )
    parser.add_argument(
        "--delete",
        default="[]",
        help="JSON list of module suffixes to replace or remove.",
    )
    parser.add_argument(
        "--full-rebuild",
        default="false",
        help="Whether this is a full rebuild ('true' or 'false').",
    )
    parser.add_argument(
        "--artifacts-dir",
        type=Path,
        default=BASE_DIR / "apk-artifacts",
        help="Directory containing downloaded chunk artifacts.",
    )
    parser.add_argument(
        "--repo-dir",
        type=Path,
        default=BASE_DIR / "repo",
        help="Target repo output directory.",
    )
    return parser.parse_args()


def load_existing_extensions(index_path: Path) -> dict[str, dict]:
    if not index_path.is_file():
        return {}
    with index_path.open("r", encoding="utf-8") as f:
        data = json.load(f)
    extensions = data.get("extensionList", {}).get("extensions", [])
    return {ext["packageName"]: ext for ext in extensions if "packageName" in ext}


def matches_module(package_name: str, module: str) -> bool:
    return package_name == f"app.hikari.extension.{module}" or package_name.endswith(
        f".{module}"
    )


def write_protobuf_index(repo_dir: Path, final_extensions: list[dict]) -> None:
    try:
        import index_pb2
    except ImportError as exc:
        print(
            f"Error: Failed to import index_pb2 / protobuf ({exc}). "
            "Install 'protobuf' before running publish-repo.py.",
            file=sys.stderr,
        )
        sys.exit(1)

    pb_extensions = [
        index_pb2.Extension(
            name=ext["name"],
            packageName=ext["packageName"],
            resources=index_pb2.Resources(
                apkUrl=ext["resources"]["apkUrl"],
                iconUrl=ext["resources"]["iconUrl"],
            ),
            extensionLib=ext["extensionLib"],
            versionCode=int(ext["versionCode"]),
            versionName=ext["versionName"],
            contentWarning=CONTENT_WARNING_MAP.get(ext.get("contentWarning", "SAFE"), 1),
            sources=[
                index_pb2.Source(
                    id=int(source["id"]),
                    name=source["name"],
                    language=source["language"],
                    homeUrl=source["homeUrl"],
                    mirrorUrls=source.get("mirrorUrls", []),
                    message=source.get("message"),
                )
                for source in ext.get("sources", [])
            ],
        )
        for ext in final_extensions
    ]

    pb_index = index_pb2.Index(
        name="Hikari Extensions",
        badgeLabel="Official",
        signingKey=SIGNING_KEY,
        contact=index_pb2.Contact(
            website="https://github.com/HikariCollection",
            discord=None,
        ),
        extensionList=index_pb2.ExtensionList(extensions=pb_extensions),
    )

    with (repo_dir / "index.pb").open("wb") as f:
        f.write(gzip.compress(pb_index.SerializeToString(deterministic=True), mtime=0))


def main() -> None:
    args = parse_args()
    to_delete: list[str] = json.loads(args.delete)
    full_rebuild = args.full_rebuild.lower() == "true"
    artifacts_dir: Path = args.artifacts_dir
    repo_dir: Path = args.repo_dir
    apk_dir = repo_dir / "apk"
    icon_dir = repo_dir / "icon"

    if full_rebuild:
        if apk_dir.exists():
            shutil.rmtree(apk_dir)
        if icon_dir.exists():
            shutil.rmtree(icon_dir)
        existing_extensions: dict[str, dict] = {}
    else:
        existing_extensions = load_existing_extensions(repo_dir / "index.json")

    apk_dir.mkdir(parents=True, exist_ok=True)
    icon_dir.mkdir(parents=True, exist_ok=True)

    removed_entries: list[dict] = []
    if not full_rebuild and to_delete:
        for pkg_name in list(existing_extensions.keys()):
            if any(matches_module(pkg_name, mod) for mod in to_delete):
                removed_entries.append(existing_extensions.pop(pkg_name))

    rebuilt_packages: set[str] = set()
    if artifacts_dir.exists():
        info_files = sorted(artifacts_dir.rglob("hikari-source-info.json"))
    else:
        info_files = []

    for info_file in info_files:
        with info_file.open("r", encoding="utf-8") as f:
            info = json.load(f)

        package_name = info["packageName"]
        apk_name = info["apkFileName"]
        icon_name = info["iconFileName"]

        apk_src = info_file.parent / apk_name
        if not apk_src.is_file():
            apk_src = info_file.parent / "outputs" / "apk" / "release" / apk_name
        if apk_src.is_file():
            shutil.copy2(apk_src, apk_dir / apk_name)

        icon_src = info_file.parent / icon_name
        if icon_src.is_file():
            shutil.copy2(icon_src, icon_dir / icon_name)

        sources_data = [
            {
                "id": int(s["id"]),
                "name": s["name"],
                "language": s["lang"],
                "homeUrl": s["baseUrl"],
                "mirrorUrls": s.get("mirrorUrls", []),
                "message": None,
            }
            for s in info.get("sources", [])
        ]

        existing_extensions[package_name] = {
            "name": info["name"],
            "packageName": package_name,
            "resources": {
                "apkUrl": f"{RAW_BASE}/apk/{apk_name}",
                "iconUrl": f"{RAW_BASE}/icon/{icon_name}",
            },
            "extensionLib": info.get("extensionLib", "1.1"),
            "versionCode": int(info.get("versionCode", 1)),
            "versionName": info.get("versionName", "1.1.1"),
            "contentWarning": info.get("contentWarning", "SAFE"),
            "sources": sources_data,
        }
        rebuilt_packages.add(package_name)

    for removed in removed_entries:
        if removed["packageName"] not in rebuilt_packages:
            old_apk = removed.get("resources", {}).get("apkUrl", "").rsplit("/", 1)[-1]
            old_icon = removed.get("resources", {}).get("iconUrl", "").rsplit("/", 1)[-1]
            if old_apk and (apk_dir / old_apk).is_file():
                (apk_dir / old_apk).unlink()
            if old_icon and (icon_dir / old_icon).is_file():
                (icon_dir / old_icon).unlink()

    final_extensions = sorted(
        existing_extensions.values(), key=lambda x: x["name"].lower()
    )

    index_data = {
        "name": "Hikari Extensions",
        "badgeLabel": "Official",
        "signingKey": SIGNING_KEY,
        "contact": {
            "website": "https://github.com/HikariCollection",
            "discord": None,
        },
        "extensionList": {
            "extensions": final_extensions,
        },
        "extensionListUrl": None,
    }

    with (repo_dir / "index.json").open("w", encoding="utf-8") as f:
        json.dump(index_data, f, indent=2)

    with (repo_dir / "index.min.json").open("w", encoding="utf-8") as f:
        json.dump(index_data, f, separators=(",", ":"))

    write_protobuf_index(repo_dir, final_extensions)

    print(
        f"Published {len(rebuilt_packages)} updated/new extension(s); "
        f"total {len(final_extensions)} extension(s) in {repo_dir}"
    )


if __name__ == "__main__":
    main()
