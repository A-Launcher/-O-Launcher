#!/usr/bin/env python3
"""Audit Android native dependencies for the Mojo LWJGL2 bridge.

Usage:
  python3 native/tools/audit_mojo_lwjgl2_dependencies.py liblwjgl64.so
  python3 native/tools/audit_mojo_lwjgl2_dependencies.py liblwjgl64.so --readelf llvm-readelf

This is a static packaging audit. It deliberately does not claim that EGL/GLFW
symbols resolve at runtime or that a device can render a frame.
"""
from __future__ import annotations
import argparse
import re
import shutil
import subprocess
import sys
from pathlib import Path

ANDROID_SYSTEM_LIBS = {
    "libandroid.so", "libc.so", "libdl.so", "libEGL.so", "libGLESv1_CM.so",
    "libGLESv2.so", "libjnigraphics.so", "liblog.so", "libm.so",
    "libOpenSLES.so", "libvulkan.so", "libz.so",
}
EXPECTED_BRIDGE_LIBS = {
    "libglfw.so", "libgl4es_114.so", "libEGL.so", "libGLESv2.so",
}

def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("library", type=Path)
    ap.add_argument("--readelf", default="llvm-readelf")
    ap.add_argument("--allow-extra", action="append", default=[])
    args = ap.parse_args()
    if not args.library.is_file():
        ap.error(f"library not found: {args.library}")
    tool = shutil.which(args.readelf)
    if not tool:
        ap.error(f"readelf tool not found: {args.readelf}")
    p = subprocess.run([tool, "-d", str(args.library)], text=True,
                       stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    if p.returncode:
        print(p.stderr.strip() or "readelf failed", file=sys.stderr)
        return p.returncode
    needed = set(re.findall(r"Shared library: \[(.*?)\]", p.stdout))
    allowed = ANDROID_SYSTEM_LIBS | set(args.allow_extra)
    unexpected = sorted(needed - allowed)
    print("DT_NEEDED:")
    for name in sorted(needed):
        print(f"  {name}")
    if unexpected:
        print("Dependency audit: FAIL (unresolved/non-system dependencies require explicit packaging)")
        for name in unexpected:
            print(f"  not allowlisted: {name}")
        return 1
    print("Dependency audit: PASS (all dependencies are Android system libraries or explicitly allowlisted)")
    print("Bridge note: this does not prove GLFW window ownership, GL4ES context sharing, or runtime symbol resolution.")
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
