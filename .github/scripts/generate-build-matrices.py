import itertools
import json
import os
import re
import subprocess
import sys
from pathlib import Path

EXTENSION_REGEX = re.compile(r"^src/(?P<lang>\w+)/(?P<extension>\w+)")
MODULE_REGEX = re.compile(r"^:src:(?P<lang>\w+):(?P<extension>\w+)$")
PKG_NAME_REGEX = re.compile(r"""pkgName\s*(?:=|\.set\()\s*["']([^"']+)["']""")
CORE_FILES_REGEX = re.compile(
    r"^(common/|core/|gradle/|build\.gradle\.kts|gradle\.properties|settings\.gradle\.kts|\.github/scripts/)"
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
            if not extension.is_dir():
                continue
            modules.append(f":src:{lang.name}:{extension.name}")
            deleted.append(resolve_module_suffix(ref, lang.name, extension.name))
    return sorted(modules), sorted(deleted)


def get_module_list(ref: str) -> tuple[list[str], list[str], list[str], bool]:
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
            if Path("src", lang, extension).is_dir():
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
            {"number": i + 1, "modules": chunk}
            for i, chunk in enumerate(chunks)
        ]
    }


def main() -> None:
    _, ref = sys.argv
    modules, deleted, lint_modules, is_full_rebuild = get_module_list(ref)

    matrix = create_matrix(modules)

    print(
        f"Module chunks to build:\n{json.dumps(matrix, indent=2)}\n\n"
        f"Modules to lint:\n{json.dumps(lint_modules, indent=2)}\n\n"
        f"Module to delete:\n{json.dumps(deleted, indent=2)}"
    )

    if os.getenv("CI") == "true":
        with open(os.getenv("GITHUB_OUTPUT"), "a") as out_file:
            out_file.write(f"matrix={json.dumps(matrix)}\n")
            out_file.write(f"lint_modules={json.dumps(lint_modules)}\n")
            out_file.write(f"delete={json.dumps(deleted)}\n")
            if is_full_rebuild:
                out_file.write("full_rebuild=true\n")


if __name__ == "__main__":
    main()
