#!/usr/bin/env python3
"""Reject tracked raw diagnostics and likely literal credentials.

Only file names and line numbers are printed so a failing CI run does not
repeat a credential in its own logs.
"""

from __future__ import annotations

import pathlib
import re
import subprocess
import sys


ROOT = pathlib.Path(__file__).resolve().parents[1]
FORBIDDEN_ARTIFACTS = (
    (re.compile(r"logcat", re.IGNORECASE), "raw logcat artifact"),
    (re.compile(r"^qa-", re.IGNORECASE), "raw QA artifact"),
)
SENSITIVE_PATTERNS = (
    re.compile(r"(?im)\bZ-Auth-Token\b\s*:\s*[A-Za-z0-9._~+/=-]{12,}"),
    re.compile(r"(?im)\bZ-Dev-ApiKey\b\s*:\s*[A-Za-z0-9._~+/=-]{12,}"),
    re.compile(r"\bghp_[A-Za-z0-9]{20,}\b"),
    re.compile(r"\bgithub_pat_[A-Za-z0-9_]{20,}\b"),
)


def tracked_files() -> list[pathlib.Path]:
    result = subprocess.run(
        ["git", "ls-files", "-z"],
        cwd=ROOT,
        check=True,
        capture_output=True,
    )
    return [ROOT / raw.decode("utf-8") for raw in result.stdout.split(b"\0") if raw]


def main() -> int:
    failures: list[tuple[pathlib.Path, int, str]] = []
    for path in tracked_files():
        relative = path.relative_to(ROOT)
        artifact_reason = next(
            (reason for pattern, reason in FORBIDDEN_ARTIFACTS if pattern.search(relative.name)),
            None,
        )
        if artifact_reason:
            failures.append((relative, 0, artifact_reason))
            continue
        try:
            data = path.read_bytes()
        except OSError:
            continue
        if b"\0" in data[:8192]:
            continue
        text = data.decode("utf-8", errors="ignore")
        for pattern in SENSITIVE_PATTERNS:
            match = pattern.search(text)
            if match:
                line = text.count("\n", 0, match.start()) + 1
                failures.append((relative, line, "likely literal credential"))
                break

    if not failures:
        print("Sensitive artifact check passed.")
        return 0

    for path, line, reason in failures:
        location = f"{path}:{line}" if line else str(path)
        print(f"ERROR {location}: {reason}")
    return 1


if __name__ == "__main__":
    sys.exit(main())
