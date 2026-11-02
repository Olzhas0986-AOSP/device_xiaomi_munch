# Parts host regression tests

Run `python3 tests/parts/run.py` with a JDK providing `javac` and `java`.
Alternatively, set `ECJ_JAR` to an Eclipse Java compiler jar and `JAVA8_RT` to
an OpenJDK 8 `rt.jar`. Supplying `ECJ_JAR` also enables syntax parsing for every
production Java source in `parts/src`.

The tests compile the actual profile, refresh, thermal, HBM and file utilities
against small host Android stubs. They exercise corrupted profile data, exact
package matching, refresh transitions and process restart, raw brightness
restoration, and real buffered-write errors (`/dev/full` on Linux).

The stubs do not verify Android API compatibility, resource linking, SELinux,
sensor callbacks or real panel behavior. Build XiaomiParts in the full Android
source tree and verify these on the device as well.
