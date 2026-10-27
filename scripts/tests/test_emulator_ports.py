"""Verify per-run emulator ports without launching Android or Firebase."""

from contextlib import ExitStack
import importlib.util
import json
import os
from pathlib import Path
import socket
import tempfile
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location("test_emulators", ROOT / "scripts/run-test-emulators.py")
EMULATORS = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(EMULATORS)


class EmulatorPortsTest(unittest.TestCase):
    """Cover port ownership, configuration and process lifecycle for shared runners."""

    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.locks = self.root / "locks"

    def test_parallel_reservations_keep_distinct_ports_until_released(self):
        with ExitStack() as leases:
            ports = [leases.enter_context(EMULATORS.lease_port(self.locks)) for _ in range(24)]
            self.assertEqual(len(ports), len(set(ports)))
            with self.assertRaisesRegex(RuntimeError, "No available"):
                with EMULATORS.lease_port(self.locks, candidates=[ports[0]]):
                    self.fail("An active reservation must not be reused")
        with EMULATORS.lease_port(self.locks, candidates=[ports[0]]) as reused:
            self.assertEqual(ports[0], reused)

    def test_android_pair_skips_an_occupied_neighbor_and_reserves_both_ports(self):
        with socket.socket() as occupied:
            occupied.bind(("127.0.0.1", 0))
            busy = occupied.getsockname()[1]
            with self.assertRaisesRegex(RuntimeError, "No available"):
                with EMULATORS.lease_port(self.locks, candidates=[busy - 1], width=2):
                    self.fail("Both Android ports must be available")
        with EMULATORS.lease_port(self.locks) as first:
            with self.assertRaisesRegex(RuntimeError, "No available"):
                with EMULATORS.lease_port(self.locks, candidates=[first - 1], width=2):
                    self.fail("An adjacent lease must not be reused")
        with EMULATORS.lease_port(self.locks, candidates=range(5554, 5683, 2), width=2) as console:
            self.assertEqual(0, console % 2)
            self.assertIn(console, range(5554, 5683, 2))
            with self.assertRaisesRegex(RuntimeError, "No available"):
                with EMULATORS.lease_port(self.locks, candidates=[console + 1]):
                    self.fail("The ADB neighbor must remain reserved")

    def test_firebase_configuration_keeps_sources_and_assigns_all_service_ports(self):
        original = json.loads((ROOT / "firebase/firebase.json").read_text())
        ports = dict(zip(EMULATORS.FIREBASE_SERVICES, range(31000, 31006)))
        config = EMULATORS.firebase_config(original, ports)
        self.assertEqual(original["firestore"], config["firestore"])
        self.assertEqual(original["storage"], config["storage"])
        self.assertEqual(9099, original["emulators"]["auth"]["port"])
        for service in ("auth", "firestore", "storage", "hub", "logging"):
            self.assertEqual("127.0.0.1", config["emulators"][service]["host"])
            self.assertEqual(ports[service], config["emulators"][service]["port"])
        self.assertEqual(ports["websocket"], config["emulators"]["firestore"]["websocketPort"])
        self.assertFalse(config["emulators"]["ui"]["enabled"])

    def test_firebase_launch_isolates_hub_files_and_preserves_exit_status(self):
        source = self.root / "firebase"
        source.mkdir()
        (source / "firebase.json").write_text((ROOT / "firebase/firebase.json").read_text())
        observed = []

        def launch(command, **kwargs):
            config_path = Path(command[command.index("--config") + 1])
            config = json.loads(config_path.read_text())
            observed.append((config_path, Path(kwargs["env"]["TMPDIR"])))
            self.assertEqual(source, config_path.parent)
            self.assertTrue(observed[-1][1].is_dir())
            self.assertNotEqual(os.environ.get("TMPDIR"), kwargs["env"]["TMPDIR"])
            self.assertEqual(self.root, kwargs["cwd"])
            self.assertEqual("python3 scripts/run-firebase-tests.py", command[-1])
            self.assertFalse(config["emulators"]["ui"]["enabled"])
            return 17

        with patch.object(EMULATORS, "LOCK_DIR", self.locks), patch.object(
            EMULATORS.subprocess, "call", side_effect=launch,
        ):
            self.assertEqual(17, EMULATORS.run_firebase(self.root))
        self.assertFalse(observed[0][0].exists())
        self.assertFalse(observed[0][1].exists())
        self.assertEqual(["firebase.json"], [path.name for path in source.iterdir()])

    def test_android_launch_passes_distinct_adb_console_and_grpc_ports(self):
        def launch(command, **kwargs):
            env = kwargs["env"]
            console = int(env["CAMPSBRIDGE_UI_PORT"])
            adb = int(env["ANDROID_ADB_SERVER_PORT"])
            grpc = int(env["CAMPSBRIDGE_UI_GRPC_PORT"])
            self.assertEqual(4, len({console, console + 1, adb, grpc}))
            self.assertEqual(0, console % 2)
            self.assertEqual(f"tcp:{adb}", env["ADB_SERVER_SOCKET"])
            self.assertEqual(["bash", "scripts/check-android-ui.sh", "--stacktrace"], command)
            return 9

        with patch.object(EMULATORS, "LOCK_DIR", self.locks), patch.object(
            EMULATORS.subprocess, "call", side_effect=launch,
        ):
            self.assertEqual(9, EMULATORS.run_android(self.root, ["--stacktrace"]))


if __name__ == "__main__":
    unittest.main()
