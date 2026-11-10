#!/usr/bin/env python3
import json
import shutil
import sys
from pathlib import Path

BASE_DIR = Path(__file__).resolve().parent.parent.parent
STAGING_DIR = BASE_DIR / "staging"


def find_icon(module_dir: Path, theme: str | None) -> Path | None:
    candidates = [
        module_dir / "src" / "main" / "res" / "mipmap-xxxhdpi" / "ic_launcher.png",
        module_dir / "res" / "mipmap-xxxhdpi" / "ic_launcher.png",
    ]
    if theme:
        candidates.append(
            BASE_DIR / "lib-multisrc" / theme / "res" / "mipmap-xxxhdpi" / "ic_launcher.png"
        )
    candidates.append(
        BASE_DIR / "core" / "src" / "main" / "res" / "mipmap-xxxhdpi" / "ic_launcher.png"
    )
    for candidate in candidates:
        if candidate.is_file():
            return candidate
    return None


def main() -> None:
    modules: list[str] = json.loads(sys.argv[1]) if len(sys.argv) > 1 else []
    if STAGING_DIR.exists():
        shutil.rmtree(STAGING_DIR)
    STAGING_DIR.mkdir(parents=True, exist_ok=True)

    collected = 0
    for module in modules:
        parts = module.lstrip(":").split(":")
        if len(parts) != 3 or parts[0] != "src":
            continue
        _, lang, slug = parts
        module_dir = BASE_DIR / "src" / lang / slug
        info_file = module_dir / "build" / "hikari-source-info.json"
        if not info_file.is_file():
            print(f"Warning: {info_file} not found, skipping {module}")
            continue

        info = json.loads(info_file.read_text(encoding="utf-8"))
        out_dir = STAGING_DIR / f"{lang}.{slug}"
        out_dir.mkdir(parents=True, exist_ok=True)

        shutil.copy2(info_file, out_dir / "hikari-source-info.json")

        apk_name = info["apkFileName"]
        apk_src = module_dir / "build" / "outputs" / "apk" / "release" / apk_name
        if apk_src.is_file():
            shutil.copy2(apk_src, out_dir / apk_name)
        else:
            print(f"Warning: APK {apk_src} not found for {module}")

        icon_name = info["iconFileName"]
        icon_src = find_icon(module_dir, info.get("theme"))
        if icon_src is not None:
            shutil.copy2(icon_src, out_dir / icon_name)

        collected += 1

    print(f"Staged artifacts for {collected} module(s) in {STAGING_DIR}")


if __name__ == "__main__":
    main()
