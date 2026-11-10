import itertools
import json
import os
import re
import subprocess
import sys
from pathlib import Path

EXTENSION_REGEX = re.compile(r"^src/(?P<lang>[^/]+)/(?P<extension>[^/]+)")
MODULE_REGEX = re.compile(r"^:src:(?P<lang>[^:]+):(?P<extension>[^:]+)$")
PKG_NAME_REGEX = re.compile(r"""pkgName\s*(?:=|\.set\()\s*["']([^"']+)["']""")
CORE_FILES_REGEX = re.compile(
    r"^(common/|core/|gradle/|lib-multisrc/|build\.gradle\.kts|gradle\.properties|settings\.gradle\.kts|\.github/scripts/)"
)


def run_command(command: str) -> str:
    result = subprocess.run(command, capture_output=True, text=True, shell=True)
    if result.returncode != 0:
        print(result.stderr.strip())
        sys.exit(result.returncode)
    return result.stdout.strip()


def resolve_module_suffix(ref: str, lang: str, extension: str) -> str:
    build_file = Path("src", lang, extension, "build.gradle.kts")
    content = None
    if build_file.is_file():
        content = build_file.read_text("utf-8")
    elif ref:
        result = subprocess.run(
            f"git show {ref}:src/{lang}/{extension}/build.gradle.kts",
            capture_output=True,
            text=True,
            shell=True,
            check=False,
        )
        if result.returncode == 0:
            content = result.stdout

    if content:
        match = PKG_NAME_REGEX.search(content)
        if match:
            return match.group(1)

    return f"{lang}.{extension}"


def get_all_modules(ref: str) -> tuple[list[str], list[str]]:
    modules = []
    deleted = []
    for lang in Path("src").iterdir():
        if not lang.is_dir():
            continue
        for extension in lang.iterdir():
            if not (extension / "build.gradle.kts").is_file():
                continue
            modules.append(f":src:{lang.name}:{extension.name}")
            deleted.append(resolve_module_suffix(ref, lang.name, extension.name))
    return sorted(modules), sorted(deleted)


def get_module_list(ref: str, force_full: bool = False) -> tuple[list[str], list[str], list[str], bool]:
    if force_full:
        all_modules, all_deleted = get_all_modules(ref)
        return all_modules, all_deleted, [":core"], True

    diff_output = run_command(f"git diff --name-status {ref}").splitlines()

    changed_files = [
        file
        for line in diff_output
        for file in line.split("\t", 2)[1:]
    ]

    modules = set()
    deleted = set()
    core_files_changed = False

    for file in map(lambda x: Path(x).as_posix(), changed_files):
        if CORE_FILES_REGEX.search(file):
            core_files_changed = True
        elif match := EXTENSION_REGEX.search(file):
            lang = match.group("lang")
            extension = match.group("extension")
            if Path("src", lang, extension, "build.gradle.kts").is_file():
                modules.add(f":src:{lang}:{extension}")
            deleted.add(resolve_module_suffix(ref, lang, extension))

    if core_files_changed:
        all_modules, all_deleted = get_all_modules(ref)
        modules.update(all_modules)
        deleted.update(all_deleted)
        return sorted(modules), sorted(deleted), [":core"], True

    return sorted(modules), sorted(deleted), [":core"], False


def create_matrix(modules: list[str]) -> dict:
    chunk_size = int(os.getenv("CI_CHUNK_SIZE", 65))
    chunks = [modules[i:i + chunk_size] for i in range(0, len(modules), chunk_size)] if modules else []
    return {
        "chunk": [
            {
                "number": i + 1,
                "modules": chunk,
                "tasks": " ".join(f"{m}:assembleRelease" for m in chunk),
            }
            for i, chunk in enumerate(chunks)
        ]
    }


def main() -> None:
    _, ref = sys.argv
    force_full = os.getenv("FULL_REBUILD", "").lower() == "true"
    modules, deleted, lint_modules, is_full_rebuild = get_module_list(ref, force_full=force_full)

    matrix = create_matrix(modules)

    print(
        f"Module chunks to build:\n{json.dumps(matrix, indent=2)}\n\n"
        f"Modules to lint:\n{json.dumps(lint_modules, indent=2)}\n\n"
        f"Module to delete:\n{json.dumps(deleted, indent=2)}"
    )

    if os.getenv("CI") == "true":
        with open(os.getenv("GITHUB_OUTPUT"), "a", encoding="utf-8") as out_file:
            out_file.write(f"matrix={json.dumps(matrix)}\n")
            out_file.write(f"has_work={'true' if modules else 'false'}\n")
            out_file.write(f"has_delete={'true' if deleted else 'false'}\n")
            out_file.write(f"lint_modules={json.dumps(lint_modules)}\n")
            out_file.write(f"delete={json.dumps(deleted)}\n")
            out_file.write(f"full_rebuild={'true' if is_full_rebuild else 'false'}\n")


if __name__ == "__main__":
    main()
