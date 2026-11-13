#!/usr/bin/python3
"""Install a tested backend JAR and restore the previous release on failure."""

import fcntl
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile
import time
from zipfile import ZipFile


BASE = Path("/opt/mygamelist-api")
CURRENT = BASE / "current"
MAX_JAR_SIZE = 160 * 1024 * 1024
SERVICE = "mygamelist-api.service"
CHECK_URLS = (
    "http://127.0.0.1:8080/api/games?query=Celeste",
    "https://54.233.52.158/api/games?query=Celeste",
)


def switch(target):
    pending = BASE / ".current-next"
    pending.unlink(missing_ok=True)
    pending.symlink_to(target)
    pending.replace(CURRENT)


def restart_and_verify():
    subprocess.run(["/usr/bin/systemctl", "restart", SERVICE], check=True, timeout=120)
    deadline = time.monotonic() + 100
    while time.monotonic() < deadline:
        try:
            for url in CHECK_URLS:
                response = subprocess.check_output(
                    ["/usr/bin/curl", "-fsS", "--max-time", "5", url],
                    stderr=subprocess.DEVNULL,
                )
                data = json.loads(response)
                if not isinstance(data, dict) or not isinstance(data.get("content"), list):
                    raise ValueError("Unexpected catalog response")
            return
        except (subprocess.CalledProcessError, ValueError):
            time.sleep(2)
    raise RuntimeError("API did not pass its local and public health checks")


def migrations(jar):
    prefix = "BOOT-INF/classes/db/migration/"
    return {
        entry.filename: jar.read(entry)
        for entry in jar.infolist()
        if entry.filename.startswith(prefix) and entry.filename.endswith(".sql")
    }


def main():
    command = re.fullmatch(r"deploy ([0-9]{1,20})", os.environ.get("SSH_ORIGINAL_COMMAND", ""))
    if not command or len(sys.argv) != 1:
        raise SystemExit("Expected deploy <build run ID>")

    with (BASE / ".deploy.lock").open("a") as lock:
        fcntl.flock(lock, fcntl.LOCK_EX)
        previous = CURRENT.resolve(strict=True)
        release = Path(tempfile.mkdtemp(prefix=f"{command[1]}-", dir=BASE / "releases"))
        try:
            jar_path = release / "app.jar"
            with jar_path.open("wb") as destination:
                remaining = MAX_JAR_SIZE
                while chunk := sys.stdin.buffer.read(65536):
                    remaining -= len(chunk)
                    if remaining < 0:
                        raise ValueError("JAR exceeds the upload size limit")
                    destination.write(chunk)

            with ZipFile(jar_path) as jar, ZipFile(previous / "app.jar") as old:
                manifest = jar.read("META-INF/MANIFEST.MF")
                if b"Main-Class: org.springframework.boot.loader.launch.JarLauncher" not in manifest:
                    raise ValueError("Expected an executable Spring Boot JAR")
                if migrations(jar) != migrations(old):
                    raise ValueError("Database migrations changed. Review and deploy them separately.")

            jar_path.chmod(0o644)
            release.chmod(0o755)
        except Exception:
            shutil.rmtree(release)
            raise

        switch(release)
        try:
            restart_and_verify()
        except Exception:
            print(f"Restoring {previous.name}", file=sys.stderr)
            switch(previous)
            restart_and_verify()
            shutil.rmtree(release)
            raise

        print(f"Published {release.name}. Previous release: {previous.name}")


if __name__ == "__main__":
    main()
