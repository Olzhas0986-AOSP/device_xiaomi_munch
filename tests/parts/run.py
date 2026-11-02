#!/usr/bin/env python3
"""Run Android-independent parts regression tests with javac or ECJ_JAR/JAVA8_RT."""
import os
from pathlib import Path
import shutil
import subprocess
import tempfile

root = Path(__file__).resolve().parents[2]
sources = [root / "parts/src/org/lineageos/settings/utils/ProfileUtils.java"]
sources += sorted(Path(__file__).parent.glob("*Test.java"))
with tempfile.TemporaryDirectory(prefix="parts-tests-") as output:
    if shutil.which("javac"):
        compiler = ["javac", "-d", output]
    else:
        compiler = ["java", "-jar", os.environ["ECJ_JAR"], "-8", "-proc:none",
                    "-bootclasspath", os.environ["JAVA8_RT"], "-d", output]
    subprocess.run(compiler + [str(p) for p in sources], check=True)
    for test in sorted(Path(__file__).parent.glob("*Test.java")):
        subprocess.run(["java", "-cp", output, test.stem], check=True)
