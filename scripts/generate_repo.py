#!/usr/bin/env python3
import json
import os
import shutil
from pathlib import Path

BASE_DIR = Path(__file__).resolve().parent.parent
REPO_DIR = BASE_DIR / "repo"
REPO_APK_DIR = REPO_DIR / "apk"
REPO_ICON_DIR = REPO_DIR / "icon"

RAW_BASE = "https://raw.githubusercontent.com/HikariCollection/extensions/repo"
SIGNING_KEY = "956e72c8d1dc56c663b07c09d537e10614c25620650015b0f3e22a3d3a2e2e32"

EXTENSIONS = [
    {
        "name": "Arena Scans",
        "lang": "en",
        "slug": "arenascans",
        "pkgName": "app.hikari.extension.en.arenascans",
        "contentWarning": "MIXED",
        "sources": [
            {
                "id": 8861274191478178487,
                "name": "Arena Scans",
                "language": "en",
                "homeUrl": "https://arenascan.com",
            }
        ],
    },
    {
        "name": "Rolia Scan",
        "lang": "en",
        "slug": "roliascan",
        "pkgName": "app.hikari.extension.en.roliascan",
        "contentWarning": "SAFE",
        "sources": [
            {
                "id": 3390167717659783669,
                "name": "Rolia Scan",
                "language": "en",
                "homeUrl": "https://roliascan.com",
            }
        ],
    },
    {
        "name": "MG Komik",
        "lang": "id",
        "slug": "mgkomik",
        "pkgName": "app.hikari.extension.id.mgkomik",
        "contentWarning": "SAFE",
        "sources": [
            {
                "id": 385263422541320229,
                "name": "MG Komik",
                "language": "id",
                "homeUrl": "https://web1.mgkomik.cc",
            }
        ],
    },
    {
        "name": "Shinigami",
        "lang": "id",
        "slug": "shinigami",
        "pkgName": "app.hikari.extension.id.shinigami",
        "contentWarning": "SAFE",
        "sources": [
            {
                "id": 3411809758861089969,
                "name": "Shinigami",
                "language": "id",
                "homeUrl": "https://11.shinigami.asia",
            }
        ],
    },
    {
        "name": "Wurmz",
        "lang": "id",
        "slug": "wurmz",
        "pkgName": "app.hikari.extension.id.wurmz",
        "contentWarning": "MIXED",
        "sources": [
            {
                "id": 925868633785419090,
                "name": "Wurmz",
                "language": "id",
                "homeUrl": "https://wurmz.net",
            }
        ],
    },
]

def main():
    REPO_APK_DIR.mkdir(parents=True, exist_ok=True)
    REPO_ICON_DIR.mkdir(parents=True, exist_ok=True)

    extension_items = []

    for ext in EXTENSIONS:
        lang = ext["lang"]
        slug = ext["slug"]
        module_dir = BASE_DIR / "src" / lang / slug

        apk_src = module_dir / "build" / "outputs" / "apk" / "release" / f"hikari-{lang}-{slug}-release.apk"
        apk_dest = REPO_APK_DIR / f"hikari-{lang}-{slug}-release.apk"
        if apk_src.exists():
            shutil.copy2(apk_src, apk_dest)

        icon_src = module_dir / "src" / "main" / "res" / "mipmap-xxxhdpi" / "ic_launcher.png"
        icon_dest = REPO_ICON_DIR / f"hikari-{lang}-{slug}-release.png"
        if icon_src.exists():
            shutil.copy2(icon_src, icon_dest)

        sources_data = []
        for s in ext["sources"]:
            sources_data.append({
                "id": s["id"],
                "name": s["name"],
                "language": s["language"],
                "homeUrl": s["homeUrl"],
                "mirrorUrls": [],
                "message": None,
            })

        extension_items.append({
            "name": ext["name"],
            "packageName": ext["pkgName"],
            "resources": {
                "apkUrl": f"{RAW_BASE}/apk/hikari-{lang}-{slug}-release.apk",
                "iconUrl": f"{RAW_BASE}/icon/hikari-{lang}-{slug}-release.png",
            },
            "extensionLib": "1.1",
            "versionCode": 1,
            "versionName": "1.1.1",
            "contentWarning": ext["contentWarning"],
            "sources": sources_data,
        })

    index_data = {
        "name": "Hikari Extensions",
        "badgeLabel": "Official",
        "signingKey": SIGNING_KEY,
        "contact": {
            "website": "https://github.com/HikariCollection",
            "discord": None,
        },
        "extensionList": {
            "extensions": extension_items,
        },
        "extensionListUrl": None,
    }

    with open(REPO_DIR / "index.json", "w", encoding="utf-8") as f:
        json.dump(index_data, f, indent=2)

    with open(REPO_DIR / "index.min.json", "w", encoding="utf-8") as f:
        json.dump(index_data, f, separators=(",", ":"))

    print(f"Generated index and artifacts in {REPO_DIR}")

if __name__ == "__main__":
    main()
