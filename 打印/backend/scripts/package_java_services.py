#!/usr/bin/env python3
"""
Package Java service jars required by the Dockerfiles.

The Dockerfiles for print-service, gateway and schedule-service copy prebuilt
Spring Boot jars from target/. This helper builds the whole backend reactor
from the root aggregator pom with the root Maven wrapper and a temporary
Aliyun mirror settings file, avoiding slow Docker multi-stage Maven builds and
leaving no temporary settings in the workspace.
"""

import argparse
import subprocess
import sys
import tempfile
from pathlib import Path


SETTINGS_XML = """<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0 https://maven.apache.org/xsd/settings-1.0.0.xsd">
  <mirrors>
    <mirror>
      <id>aliyunmaven</id>
      <mirrorOf>*</mirrorOf>
      <name>Aliyun Maven</name>
      <url>https://maven.aliyun.com/repository/public</url>
    </mirror>
  </mirrors>
</settings>
"""


def configure_output_encoding() -> None:
    for stream in (sys.stdout, sys.stderr):
        reconfigure = getattr(stream, "reconfigure", None)
        if reconfigure:
            reconfigure(encoding="utf-8")


def main() -> int:
    configure_output_encoding()
    parser = argparse.ArgumentParser(description="Package Java jars for local Docker Compose startup.")
    parser.add_argument("--with-tests", action="store_true", help="Run tests while packaging. Defaults to -DskipTests.")
    args = parser.parse_args()

    backend_dir = Path(__file__).resolve().parents[1]
    wrapper = backend_dir / ("mvnw.cmd" if sys.platform.startswith("win") else "mvnw")
    if not wrapper.is_file():
        raise SystemExit(f"Maven wrapper not found: {wrapper}")

    with tempfile.TemporaryDirectory(prefix="printshop-maven-") as temp_dir:
        settings_path = Path(temp_dir) / "settings.xml"
        settings_path.write_text(SETTINGS_XML, encoding="utf-8")
        command = [str(wrapper), "-s", str(settings_path)]
        if not args.with_tests:
            command.append("-DskipTests")
        command.append("package")
        print("==> packaging printshop, gateway, schedule-service from the root reactor")
        subprocess.run(command, cwd=backend_dir, check=True)
    return 0


if __name__ == "__main__":
    sys.exit(main())
