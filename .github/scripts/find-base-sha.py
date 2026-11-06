"""
Prints the head commit of the last successful push run of a workflow that is
still part of the checked-out branch, to diff the build matrix against.
"""

import json
import os
import subprocess
import sys

from github_utils import run_gh

EMPTY_TREE = "4b825dc642cb6eb9a060e54bf8d69288fbee4904"
PER_PAGE = 100
MAX_PAGES = 10


def is_on_branch(sha: str) -> bool:
    if (
        subprocess.run(
            ["git", "cat-file", "-e", f"{sha}^{{commit}}"],
            capture_output=True,
            check=False,
        ).returncode
        != 0
    ):
        return False

    result = subprocess.run(
        ["git", "merge-base", "--is-ancestor", sha, "HEAD"],
        capture_output=True,
        encoding="utf-8",
        check=False,
    )
    if result.returncode not in (0, 1):
        raise RuntimeError(f"git merge-base failed for {sha}: {result.stderr.strip()}")
    return result.returncode == 0


def find_base_sha(repo: str, workflow: str, branch: str) -> str:
    seen_runs = 0
    for page in range(1, MAX_PAGES + 1):
        runs = json.loads(
            run_gh(
                "api",
                f"repos/{repo}/actions/workflows/{workflow}/runs"
                f"?branch={branch}&event=push&status=success"
                f"&per_page={PER_PAGE}&page={page}",
            )
        )["workflow_runs"]

        for run in runs:
            if is_on_branch(run["head_sha"]):
                print(f"Last successful run: {run['html_url']}", file=sys.stderr)
                return run["head_sha"]

        seen_runs += len(runs)
        if len(runs) < PER_PAGE:
            break

    print(
        f"No successful run on '{branch}' among {seen_runs} runs; "
        "falling back to the empty tree (full rebuild)",
        file=sys.stderr,
    )
    return EMPTY_TREE


def main() -> None:
    _, workflow = sys.argv
    base = find_base_sha(
        os.environ["GITHUB_REPOSITORY"],
        workflow,
        os.getenv("GITHUB_REF_NAME", "main"),
    )
    print(base)


if __name__ == "__main__":
    main()
