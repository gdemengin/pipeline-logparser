#!/bin/bash

set -e

docker build -t jenkins-lts .
export GITHUB_SHA=$(git rev-parse --verify HEAD)
docker run --rm --name jenkins-lts -p 8080:8080 -e GITHUB_SHA -v "/var/run/docker.sock":"/var/run/docker.sock" -it jenkins-lts
