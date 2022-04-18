#!/bin/bash

set -e

cd $(dirname $0)

docker build -t jenkins-lts .
export GITHUB_WORKSPACE=/workspace
export GITHUB_SHA=$(git rev-parse --verify HEAD)

docker run -it --rm -e GITHUB_SHA -e GITHUB_WORKSPACE -v "$(pwd -P)/../../":"/workspace" -it jenkins-lts
