"""Offline startup contract tests; fake only external executables, run real sh.

Linux: python3 docker/test_entrypoint.py
Windows (Git Bash installed): python docker/test_entrypoint.py --shell <bash.exe>
No real auth key, database credentials, network connection or Docker required.
"""
import argparse
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser()
parser.add_argument("--shell", default=shutil.which("sh"))
ARGS, UNIT_ARGS = parser.parse_known_args()
if not ARGS.shell:
    parser.error("A POSIX shell is required; install/use Git Bash on Windows.")

# Fixed markers are synthetic test data, not usable credentials.
AUTH_MARKER = "synthetic-auth-marker-not-a-key"
PASSWORD_MARKER = "synthetic-database-marker-not-a-password"

MOCK = r'''#!/bin/sh
set -eu
name=${0##*/}
printf '%s\n' "$name $*" >> "$CASE_DIR/calls"
case "$name" in
tailscaled)
    printf '%s\n' "$$" > "$CASE_DIR/daemon.pid"
    [ "${MODE:-}" != daemon_failure ] || exit 1
    trap 'touch "$CASE_DIR/daemon.stopped"; exit 0' TERM INT
    while :; do
        if [ "${MODE:-}" = daemon_crash ] && [ -f "$CASE_DIR/java.started" ]; then exit 2; fi
        /usr/bin/sleep 0.02
    done
    ;;
tailscale)
    if [ "$2" = up ]; then
        for arg in "$@"; do
            case "$arg" in
                --auth-key=file:*)
                    keyfile=${arg#--auth-key=file:}
                    [ "$(cat "$keyfile")" = "$AUTH_MARKER" ] || exit 9
                    printf '%s\n' "$keyfile" > "$CASE_DIR/key.path"
                    [ -z "${TS_AUTHKEY:-}" ] || exit 9
                    ;;
            esac
        done
        # Provider errors are deliberately sensitive; the wrapper must suppress them.
        if [ "${MODE:-}" = auth_failure ]; then echo "$AUTH_MARKER $DB_PASSWORD" >&2; exit 1; fi
        touch "$CASE_DIR/authenticated"
    else
        if [ "${MODE:-}" = socket_failure ]; then exit 1; fi
        if [ "${MODE:-}" = status_failure ] && [ -f "$CASE_DIR/authenticated" ]; then
            echo '{"BackendState":"NeedsMachineAuth"}'
        else
            echo '{"BackendState":"Running"}'
        fi
    fi
    ;;
nc)
    [ -f "$CASE_DIR/authenticated" ] || exit 8
    [ "${MODE:-}" != unreachable ] || exit 1
    touch "$CASE_DIR/reachable"
    ;;
java)
    [ -f "$CASE_DIR/reachable" ] || exit 8
    [ -z "${TS_AUTHKEY:-}" ] || exit 9
    keyfile=$(cat "$CASE_DIR/key.path")
    [ ! -f "$keyfile" ] || exit 9
    printf '%s\n' "${PORT:-}" > "$CASE_DIR/java.port"
    printf '%s\n' "$$" > "$CASE_DIR/java.pid"
    touch "$CASE_DIR/java.started"
    trap 'touch "$CASE_DIR/java.stopped"; exit 0' TERM INT
    case "${MODE:-}" in
        java_failure) exit 7 ;;
        signal|daemon_crash) while :; do /usr/bin/sleep 0.02; done ;;
        *) exit 0 ;;
    esac
    ;;
sleep)
    # Speed up bounded retry loops. Runtime supervision still yields to children.
    /usr/bin/sleep 0.04
    ;;
timeout)
    # Use real timeout, shortened only for deterministic deadline testing.
    shift 2
    deadline=$1
    shift
    if [ "${MODE:-}" = auth_timeout ] && [ "$1" = tailscale ] && [ "$3" = up ]; then
        exec /usr/bin/timeout -k 0.1s 0.05s /usr/bin/sleep 10
    fi
    exec /usr/bin/timeout -k 1s "$deadline" "$@"
    ;;
esac
'''


class EntrypointTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="rehab-startup-test-")
        self.addCleanup(self.temp.cleanup)
        self.folder = Path(self.temp.name)
        self.bin = self.folder / "bin"
        self.bin.mkdir()
        for name in ["tailscaled", "tailscale", "nc", "java", "sleep", "timeout"]:
            command = self.bin / name
            command.write_text(MOCK, encoding="utf-8", newline="\n")
            command.chmod(0o755)
        self.env = dict(os.environ)
        self.env.update({
            "TS_AUTHKEY": AUTH_MARKER,
            "AUTH_MARKER": AUTH_MARKER,
            "DB_URL": "jdbc:mysql://100.94.202.58:3306/rehab_r2_validation?connectionTimeZone=UTC",
            "DB_USERNAME": "synthetic-user",
            "DB_PASSWORD": PASSWORD_MARKER,
            "CASE_DIR": self.folder.as_posix(),
            "TMPDIR": self.folder.as_posix(),
            "PORT": "18765",
            "MODE": "success",
        })
        # Do not let a developer's optional configuration contaminate the tests.
        for name in ["TS_DB_HOST", "TS_DB_PORT", "TS_HOSTNAME"]:
            self.env.pop(name, None)

    def command(self):
        # Quote paths, not secrets. Git Bash supplies POSIX coreutils on Windows.
        return [ARGS.shell, "-c", 'if command -v /usr/bin/cygpath >/dev/null; then CASE_DIR=$(/usr/bin/cygpath -u "$CASE_DIR"); TMPDIR="$CASE_DIR"; export CASE_DIR TMPDIR; fi; export PATH="$CASE_DIR/bin:/usr/bin:$PATH"; printf "%s" "$$" > "$CASE_DIR/entrypoint.pid"; exec sh "$1"',
                "startup-test", (ROOT / "docker/entrypoint.sh").as_posix()]

    def run_case(self, mode="success"):
        self.env["MODE"] = mode
        result = subprocess.run(self.command(), env=self.env, capture_output=True, text=True, timeout=12)
        self.assertNotIn(AUTH_MARKER, result.stdout + result.stderr)
        self.assertNotIn(PASSWORD_MARKER, result.stdout + result.stderr)
        if (self.folder / "key.path").exists():
            key_path = (self.folder / "key.path").read_text().strip()
            if os.name == "nt":
                key_path = subprocess.check_output([ARGS.shell, "-c", '/usr/bin/cygpath -w "$1"',
                                                    "convert-path", key_path], text=True).strip()
            self.assertFalse(Path(key_path).exists())
        return result

    def assert_rejected(self, mode, message):
        result = self.run_case(mode)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn(message, result.stderr)
        self.assertFalse((self.folder / "java.started").exists())

    def test_missing_authkey(self):
        self.env.pop("TS_AUTHKEY")
        self.assert_rejected("success", "TS_AUTHKEY is missing")

    def test_missing_database_configuration(self):
        for name in ["DB_URL", "DB_USERNAME", "DB_PASSWORD"]:
            with self.subTest(variable=name):
                value = self.env.pop(name)
                self.assert_rejected("success", name + " is missing")
                self.env[name] = value

    def test_wrong_database_destination_is_rejected(self):
        self.env["DB_URL"] = "jdbc:mysql://127.0.0.1:3306/rehab_r2_validation"
        self.assert_rejected("success", "DB_URL must target")

    def test_daemon_failure_prevents_java(self):
        self.assert_rejected("daemon_failure", "tailscaled exited")

    def test_socket_deadline_prevents_java(self):
        self.assert_rejected("socket_failure", "startup deadline")

    def test_auth_failure_does_not_log_secrets(self):
        self.assert_rejected("auth_failure", "authentication failed or timed out")

    def test_auth_timeout_prevents_java(self):
        self.assert_rejected("auth_timeout", "authentication failed or timed out")

    def test_non_running_state_prevents_java(self):
        self.assert_rejected("status_failure", "authenticated Running state")

    def test_mysql_unreachable_retries_are_bounded(self):
        self.assert_rejected("unreachable", "MySQL TCP port is unreachable")
        calls = (self.folder / "calls").read_text()
        self.assertEqual(sum(line.startswith("nc ") for line in calls.splitlines()), 3)

    def test_success_uses_userspace_loopback_proxy_then_java(self):
        result = self.run_case()
        self.assertEqual(result.returncode, 0, result.stderr)
        calls = (self.folder / "calls").read_text()
        self.assertIn("--tun=userspace-networking --state=mem:", calls)
        self.assertIn("--socks5-server=127.0.0.1:1055", calls)
        self.assertIn("--auth-key=file:", calls)
        self.assertNotIn(AUTH_MARKER, calls)
        self.assertIn("-X 5 -x 127.0.0.1:1055 100.94.202.58 3306", calls)
        self.assertLess(calls.index("nc "), calls.index("java "))
        self.assertIn("--spring.datasource.hikari.data-source-properties.socksProxyHost=127.0.0.1", calls)
        self.assertIn("--spring.datasource.hikari.data-source-properties.socksProxyPort=1055", calls)
        self.assertNotIn("-DsocksProxy", calls)
        self.assertIn("-Xmx224m", calls)
        self.assertEqual((self.folder / "java.port").read_text().strip(), "18765")
        self.assertTrue((self.folder / "daemon.stopped").exists())

    def test_java_exit_status_is_preserved(self):
        result = self.run_case("java_failure")
        self.assertEqual(result.returncode, 7)
        self.assertTrue((self.folder / "daemon.stopped").exists())

    def test_daemon_crash_stops_java(self):
        result = self.run_case("daemon_crash")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("tailscaled exited while Spring Boot", result.stderr)
        self.assertTrue((self.folder / "java.stopped").exists())

    def test_sigterm_stops_both_children(self):
        import time
        self.env["MODE"] = "signal"
        process = subprocess.Popen(self.command(), env=self.env, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
        try:
            deadline = time.monotonic() + 5
            while not (self.folder / "java.started").exists() and time.monotonic() < deadline:
                time.sleep(0.02)
            self.assertTrue((self.folder / "java.started").exists())
            # Use the POSIX shell's own kill, including MSYS on Windows.
            shell_pid = (self.folder / "entrypoint.pid").read_text().strip()
            subprocess.run([ARGS.shell, "-c", 'kill -TERM "$1"', "signal-test", shell_pid], check=True)
            _, stderr = process.communicate(timeout=12)
            self.assertEqual(process.returncode, 143, stderr)
            self.assertTrue((self.folder / "daemon.stopped").exists())
            self.assertTrue((self.folder / "java.stopped").exists())
        finally:
            if process.poll() is None:
                process.kill()
                process.communicate()


if __name__ == "__main__":
    unittest.main(argv=[__file__, *UNIT_ARGS], verbosity=2)
