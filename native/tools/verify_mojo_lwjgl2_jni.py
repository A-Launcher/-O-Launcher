#!/usr/bin/env python3
"""Verify the pinned Mojo LWJGL2 JNI ABI in an Android shared library.

Usage:
  python3 native/tools/verify_mojo_lwjgl2_jni.py path/to/liblwjgl64.so
  python3 native/tools/verify_mojo_lwjgl2_jni.py path/to/liblwjgl64.so --nm llvm-nm

The check is intentionally stricter than ELF/ABI validation: it proves the JNI
entry points declared by the pinned Mojo LWJGL2 Java port are exported. It does
not prove that dependent GLFW symbols resolve or that a device can render.
"""
from __future__ import annotations

import argparse
import re
import shutil
import subprocess
import sys
from pathlib import Path

REQUIRED_PREFIXES = (
    "Java_org_lwjgl_opengl_GLFWDisplay_sInit",
    "Java_org_lwjgl_opengl_GLFWDisplay_nInit",
    "Java_org_lwjgl_opengl_GLFWDisplay_nGetVideoModes",
    "Java_org_lwjgl_opengl_GLFWDisplay_nGoFullscreen",
    "Java_org_lwjgl_opengl_GLFWDisplay_nReshape",
    "Java_org_lwjgl_opengl_GLFWDisplay_nAttachWindow",
    "Java_org_lwjgl_opengl_GLFWDisplay_nDetachWindow",
    "Java_org_lwjgl_opengl_GLFWDisplay_nSetTitle",
    "Java_org_lwjgl_opengl_GLFWDisplay_nPollEvents",
    "Java_org_lwjgl_opengl_GLFWDisplay_nEnableGrab",
    "Java_org_lwjgl_opengl_GLFWContextImplementation_nCreateContext",
    "Java_org_lwjgl_opengl_GLFWContextImplementation_nSwapBuffers",
    "Java_org_lwjgl_opengl_GLFWContextImplementation_nMakeCurrent",
    "Java_org_lwjgl_opengl_GLFWContextImplementation_nIsCurrent",
    "Java_org_lwjgl_opengl_GLFWContextImplementation_nSetSwapInterval",
    "Java_org_lwjgl_opengl_GLFWContextImplementation_nDestroy",
    "Java_org_lwjgl_opengl_GLFWPeerInfo_createHandle",
)

def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("library", type=Path)
    parser.add_argument("--nm", default="llvm-nm")
    args = parser.parse_args()
    if not args.library.is_file():
        parser.error(f"shared library not found: {args.library}")
    nm = shutil.which(args.nm)
    if not nm:
        parser.error(f"symbol tool not found: {args.nm}")
    proc = subprocess.run(
        [nm, "-D", "--defined-only", str(args.library)],
        text=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
    )
    if proc.returncode:
        print(proc.stderr.strip() or "nm failed", file=sys.stderr)
        return proc.returncode
    exported = set()
    for line in proc.stdout.splitlines():
        fields = line.split()
        if fields:
            exported.add(fields[-1].split("@", 1)[0])
    missing = [prefix for prefix in REQUIRED_PREFIXES
               if not any(symbol == prefix or symbol.startswith(prefix + "__")
                          for symbol in exported)]
    # LWJGL's direct-buffer accessors are native too; require at least four
    # exported methods on GLFWDisplay in addition to the named lifecycle calls.
    display_exports = [s for s in exported if re.fullmatch(
        r"Java_org_lwjgl_opengl_GLFWDisplay_n[A-Za-z0-9_]+", s)]
    if len(display_exports) < 14:
        missing.append(f"GLFWDisplay native export count >= 14 (found {len(display_exports)})")
    if missing:
        print("Mojo LWJGL2 JNI ABI: FAIL")
        for symbol in missing:
            print(f"  missing: {symbol}")
        return 1
    print(f"Mojo LWJGL2 JNI ABI: PASS ({len(exported)} defined dynamic symbols)")
    print("Note: dependency resolution, EGL ownership, and on-device rendering remain separate gates.")
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
