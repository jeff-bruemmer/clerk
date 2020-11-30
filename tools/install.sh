#!/bin/bash

clojure -M:build

chmod +x clerk

sudo cp clerk /usr/local/bin
