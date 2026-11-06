#!/usr/bin/env python3
import json
import shutil
from pathlib import Path

BASE_DIR = Path(__file__).resolve().parent.parent.parent
REPO_DIR = BASE_DIR / "repo"
REPO_APK_DIR = REPO_DIR / "apk"
REPO_ICON_DIR = REPO_DIR / "icon"
SRC_DIR = BASE_DIR / "src"

RAW_BASE = "https://raw.githubusercontent.com/HikariCollection/extensions/repo"
SIGNING_KEY = "956e72c8d1dc56c663b07c09d537e10614c25620650015b0f3e22a3d3a2e2e32"

def main():
    REPO_APK_DIR.mkdir(parents=True, exist_ok=True)
    REPO_ICON_DIR.mkdir(parents=True, exist_ok=True)

    extension_items = []

    # Auto-discover built extensions via hikari-source-info.json
    info_files = sorted(SRC_DIR.glob("*/*/build/hikari-source-info.json"))

    if not info_files:
        print("No hikari-source-info.json found. Run assembleRelease first.")
        return

    for info_file in info_files:
        with open(info_file, "r", encoding="utf-8") as f:
            info = json.load(f)

        module_dir = info_file.parent.parent
        apk_name = info.get("apkFileName")
        icon_name = info.get("iconFileName")

        apk_src = info_file.parent / "outputs" / "apk" / "release" / apk_name
        apk_dest = REPO_APK_DIR / apk_name
        if apk_src.exists():
            shutil.copy2(apk_src, apk_dest)

        icon_src = module_dir / "src" / "main" / "res" / "mipmap-xxxhdpi" / "ic_launcher.png"
        if not icon_src.exists():
            icon_src = module_dir / "res" / "mipmap-xxxhdpi" / "ic_launcher.png"
        if not icon_src.exists() and info.get("theme"):
            icon_src = BASE_DIR / "lib-multisrc" / info["theme"] / "res" / "mipmap-xxxhdpi" / "ic_launcher.png"
        icon_dest = REPO_ICON_DIR / icon_name
        if icon_src.exists():
            shutil.copy2(icon_src, icon_dest)

        sources_data = []
        for s in info.get("sources", []):
            sources_data.append({
                "id": s["id"],
                "name": s["name"],
                "language": s["lang"],
                "homeUrl": s["baseUrl"],
                "mirrorUrls": s.get("mirrorUrls", []),
                "message": None,
            })

        extension_items.append({
            "name": info["name"],
            "packageName": info["packageName"],
            "resources": {
                "apkUrl": f"{RAW_BASE}/apk/{apk_name}",
                "iconUrl": f"{RAW_BASE}/icon/{icon_name}",
            },
            "extensionLib": info.get("extensionLib", "1.1"),
            "versionCode": info.get("versionCode", 1),
            "versionName": info.get("versionName", "1.1.1"),
            "contentWarning": info.get("contentWarning", "SAFE"),
            "sources": sources_data,
        })

    extension_items.sort(key=lambda x: x["name"])

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

    print(f"Discovered {len(extension_items)} extension(s) and generated index in {REPO_DIR}")

if __name__ == "__main__":
    main()
