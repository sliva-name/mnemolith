#!/usr/bin/env python3
"""The armory set is part of the unified pipeline.

    python tools/art/build.py --only armory
"""
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))

if __name__ == '__main__':
    os.execv(sys.executable, [sys.executable, os.path.join(HERE, 'build.py'), '--only', 'armory'])
