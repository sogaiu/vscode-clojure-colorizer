#!/usr/bin/env bash

set -e

HERE=$(pwd)
TSBIN=$HERE/node_modules/.bin/tree-sitter 
TSCLJ=$HERE/node_modules/tree-sitter-clojure

# Build parsers
cd $TSCLJ && \
  $TSBIN generate && \
  npx node-gyp configure && \
  npx node-gyp rebuild && \
  cd $HERE && \
  $TSBIN build-wasm $TSCLJ

mv *.wasm parsers
