#!/usr/bin/env python3
"""Run Android-independent parts regression tests with javac or ECJ_JAR/JAVA8_RT."""
import os
import re
from pathlib import Path
import shutil
import subprocess
import tempfile

root = Path(__file__).resolve().parents[2]
sources = [root / "parts/src/org/lineageos/settings/utils/ProfileUtils.java"]
sources += sorted((root / "tests/parts/stubs").rglob("*.java"))
sources += [root / "parts/src/org/lineageos/settings/refreshrate/RefreshUtils.java"]
sources += sorted(Path(__file__).parent.glob("*Test.java"))
with tempfile.TemporaryDirectory(prefix="parts-tests-") as output:
    if shutil.which("javac"):
        compiler = ["javac", "-d", output]
    else:
        compiler = ["java", "-jar", os.environ["ECJ_JAR"], "-8", "-proc:none", "-warn:none",
                    "-bootclasspath", os.environ["JAVA8_RT"], "-d", output]
    subprocess.run(compiler + [str(p) for p in sources], check=True)
    for test in sorted(Path(__file__).parent.glob("*Test.java")):
        package = re.search(r"^package ([\w.]+);", test.read_text(), re.M)
        name = (package.group(1) + "." if package else "") + test.stem
        subprocess.run(["java", "-cp", output, name], check=True)
