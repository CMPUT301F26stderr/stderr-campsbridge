#!/usr/bin/env python3
"""Exercise Halfway snapshot/reset contracts with three isolated Firebase emulators."""

from contextlib import ExitStack
import importlib.util
import json
import os
from pathlib import Path
import shlex
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parent.parent


def main():
    """Lease service ports and discard only this check's temporary emulator state."""
    spec = importlib.util.spec_from_file_location("ports", ROOT / "scripts/run-test-emulators.py")
    port_tools = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(port_tools)
    with ExitStack() as lifetime:
        ports = {
            name: lifetime.enter_context(port_tools.lease_port())
            for name in ("auth", "firestore", "storage", "hub", "logging", "websocket")
        }
        work = Path(lifetime.enter_context(tempfile.TemporaryDirectory(prefix="halfway-reset-contracts-")))
        config = {
            "firestore": {"rules": str(ROOT / "firebase/firestore.rules")},
            "storage": {"rules": str(ROOT / "firebase/storage.rules")},
            "emulators": {
                name: {"host": "127.0.0.1", "port": ports[name]}
                for name in ("auth", "firestore", "storage", "hub", "logging")
            },
        }
        config["emulators"].update(ui={"enabled": False}, singleProjectMode=True)
        config["emulators"]["firestore"]["websocketPort"] = ports["websocket"]
        config_path = work / "firebase.json"
        config_path.write_text(json.dumps(config))
        environment = {**os.environ, "GCLOUD_PROJECT": "demo-stderr", "TMPDIR": str(work)}
        print("Isolated Firebase ports:", ports, flush=True)
        command = shlex.join(["node", "--test", str(ROOT / "firebase/halfway-demo/demo.test.cjs")])
        return subprocess.call([
            "firebase", "emulators:exec", "--only", "auth,firestore,storage",
            "--project", "demo-stderr", "--config", str(config_path), command,
        ], cwd=work, env=environment)


if __name__ == "__main__":
    raise SystemExit(main())
