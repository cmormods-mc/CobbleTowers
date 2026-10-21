#!/bin/sh
# The shipped fonts are Latin subsets in WOFF2 (the full TTFs in fonts/source/ are 215 KB, the subsets 60 KB). Needs: pip install fonttools brotli
# The OFL allows this (no Reserved Font Name is declared by either font); the licence texts ship beside them.
cd "$(dirname "$0")/../fonts" || exit 1
U="U+0020-007E,U+00A0-00FF,U+0100-017F,U+2010-2027,U+2030-203A,U+20AC,U+2122,U+2190-2193,U+2212,U+2713,U+00D7"
for f in Outfit-Variable Jersey15-Regular; do
  pyftsubset "source/$f.ttf" --unicodes="$U" --flavor=woff2 --layout-features='*' --output-file="$f.woff2"
done
