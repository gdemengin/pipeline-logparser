#!/bin/bash

set -e

docker build -t jenkins-lts .
export GITHUB_WORKSPACE=/workspace
export GITHUB_SHA=$(git rev-parse --verify HEAD)

docker run -it --rm --name jenkins-lts -p 8080:8080 -e GITHUB_SHA -e GITHUB_WORKSPACE -v "$(pwd -P)/../../":"/workspace" -v "/var/run/docker.sock":"/var/run/docker.sock" -it jenkins-lts
