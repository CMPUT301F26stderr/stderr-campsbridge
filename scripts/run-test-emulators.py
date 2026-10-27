#!/usr/bin/env python3
"""Reserve independent service ports for concurrent test jobs on one Linux host."""

from contextlib import ExitStack, contextmanager
from copy import deepcopy
import errno
import fcntl
import json
import os
from pathlib import Path
import random
import socket
import subprocess
import sys
import tempfile

ROOT = Path(__file__).resolve().parents[1]
# All runner workspaces use the same advisory locks, even with different TMPDIR values.
LOCK_DIR = Path("/tmp/campsbridge-emulator-ports")
FIREBASE_SERVICES = ("auth", "firestore", "storage", "hub", "logging", "websocket")


@contextmanager
def lease_port(lock_dir, candidates=None, width=1):
    """Keep a free port or adjacent pair reserved against other test jobs until exit."""
    lock_dir.mkdir(parents=True, exist_ok=True)
    choices = list(candidates) if candidates is not None else [0] * 128
    random.SystemRandom().shuffle(choices)
    for candidate in choices:
        with ExitStack() as lease:
            try:
                with ExitStack() as probes:
                    first = probes.enter_context(socket.socket())
                    first.bind(("127.0.0.1", candidate))
                    port = first.getsockname()[1]
                    for offset in range(width):
                        if offset:
                            neighbor = probes.enter_context(socket.socket())
                            neighbor.bind(("127.0.0.1", port + offset))
                        lock = lease.enter_context((lock_dir / f"{port + offset}.lock").open("a"))
                        fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
            except OSError as failure:
                if failure.errno not in (errno.EADDRINUSE, errno.EACCES, errno.EAGAIN):
                    raise
                continue
            # Close probe sockets before the service binds; retain advisory ownership throughout.
            yield port
            return
    raise RuntimeError("No available emulator ports")


def firebase_config(source, ports):
    """Apply this run's loopback addresses without changing rules or checked-in defaults."""
    config = deepcopy(source)
    for service in FIREBASE_SERVICES[:-1]:
        config["emulators"][service] = {"host": "127.0.0.1", "port": ports[service]}
    config["emulators"]["firestore"]["websocketPort"] = ports["websocket"]
    config["emulators"]["ui"] = {"enabled": False}
    return config


def run_firebase(root):
    """Start disposable Firebase services with unique ports, config and hub locator files."""
    source = root / "firebase/firebase.json"
    with ExitStack() as lifetime:
        ports = {name: lifetime.enter_context(lease_port(LOCK_DIR)) for name in FIREBASE_SERVICES}
        work = Path(lifetime.enter_context(tempfile.TemporaryDirectory(prefix="campsbridge-firebase.")))
        config = lifetime.enter_context(tempfile.NamedTemporaryFile(
            mode="w", prefix=".emulators-", suffix=".json", dir=source.parent,
        ))
        json.dump(firebase_config(json.loads(source.read_text()), ports), config)
        config.flush()
        environment = {**os.environ, "TMPDIR": str(work)}
        print("Firebase test ports: " + ", ".join(f"{name}={port}" for name, port in ports.items()), flush=True)
        # The CLI supplies the actual service addresses to the test command using its standard env vars.
        return subprocess.call([
            "firebase", "emulators:exec", "--project", "demo-stderr", "--config", config.name,
            "--only", "auth,firestore,storage", "python3 scripts/run-firebase-tests.py",
        ], cwd=root, env=environment)


def run_android(root, arguments):
    """Reserve one Android device port pair and a gRPC port for the UI script."""
    with ExitStack() as lifetime:
        console = lifetime.enter_context(lease_port(LOCK_DIR, range(5554, 5683, 2), width=2))
        grpc = lifetime.enter_context(lease_port(LOCK_DIR))
        environment = {
            **os.environ,
            "CAMPSBRIDGE_UI_PORT": str(console),
            "CAMPSBRIDGE_UI_GRPC_PORT": str(grpc),
        }
        print(f"Android test ports: console={console}, device-adb={console + 1}, grpc={grpc}", flush=True)
        return subprocess.call(["bash", "scripts/check-android-ui.sh", *arguments], cwd=root, env=environment)


def main():
    """Dispatch the existing verification entrypoints with per-run port ownership."""
    if len(sys.argv) < 2 or sys.argv[1] not in ("firebase", "android"):
        raise SystemExit("Usage: run-test-emulators.py firebase|android [Android test arguments]")
    if sys.argv[1] == "firebase":
        return run_firebase(ROOT)
    return run_android(ROOT, sys.argv[2:])


if __name__ == "__main__":
    sys.exit(main())
