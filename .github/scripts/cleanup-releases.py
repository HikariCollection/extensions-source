import json
import time
from datetime import UTC, datetime, timedelta

from github_utils import REPO_NAME, run_gh

SOURCE_REPO = "HikariCollection/extensions-source"
PUBLISH_WORKFLOW = "build_push.yml"
PUBLISH_JOB = "Publish extension repo"
MIN_PUBLISH_AGE = timedelta(hours=1)
POLL_INTERVAL = 60
DELETE_INTERVAL = 1
RELEASE_RETRY_INTERVAL = 100


def get_json(endpoint: str):
    return json.loads(run_gh("api", endpoint))


def get_pages(endpoint: str) -> list[dict]:
    items = []
    page = 1
    separator = "&" if "?" in endpoint else "?"
    while True:
        batch = get_json(f"{endpoint}{separator}per_page=100&page={page}")
        items.extend(batch)
        if len(batch) < 100:
            return items
        page += 1


def get_workflow_runs() -> list[dict]:
    return get_json(
        f"repos/{SOURCE_REPO}/actions/workflows/{PUBLISH_WORKFLOW}/runs?per_page=20"
    )["workflow_runs"]


def get_latest_publish_job(runs: list[dict]) -> dict | None:
    publish_jobs = []
    for run in runs:
        jobs = get_json(
            f"repos/{SOURCE_REPO}/actions/runs/{run['id']}/jobs?per_page=100"
        )["jobs"]
        publish_jobs.extend(
            job
            for job in jobs
            if job["name"] == PUBLISH_JOB and job.get("conclusion") != "skipped"
        )

    if not publish_jobs:
        return None

    return max(publish_jobs, key=lambda job: job["completed_at"])


def wait_for_publish_window() -> None:
    while True:
        runs = get_workflow_runs()
        active_runs = [run for run in runs if run["status"] != "completed"]
        if active_runs:
            print(
                f"publish pipeline is active ({len(active_runs)} in flight), waiting..."
            )
            time.sleep(POLL_INTERVAL)
            continue

        latest_job = get_latest_publish_job(runs)
        if not latest_job:
            return

        completed = datetime.fromisoformat(
            latest_job["completed_at"].replace("Z", "+00:00")
        )
        age = datetime.now(UTC) - completed
        if age >= MIN_PUBLISH_AGE:
            return

        wait = (MIN_PUBLISH_AGE - age).total_seconds()
        print(f"last publish was {int(age.total_seconds())}s ago, waiting {int(wait)}s...")
        time.sleep(min(wait, POLL_INTERVAL))


def main() -> None:
    wait_for_publish_window()

    releases = get_pages(f"repos/{REPO_NAME}/releases")
    if len(releases) <= 1:
        print("1 or fewer releases; nothing to prune")
        return

    print(f"Total releases: {len(releases)}")


if __name__ == "__main__":
    main()
