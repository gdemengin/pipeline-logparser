#!/bin/bash

set -e

function usage() {
    echo "usage: $0 [-keepalive] [-port <port>]"
}

KEEPALIVE_ARG=
PORT_ARG=

[ $# -gt 3 ] && echo "ERROR too many parameters" && usage && exit 1
while [ $# -gt 0 ]; do
    case "$1" in
        -keepalive) KEEPALIVE_ARG=$1; shift;;
        -port) PORT_ARG="-p $2:8080"; shift 2;;
        *) echo "ERROR unknown parameter $1"; usage; exit 1;;
    esac
done

cd $(dirname $0)

docker build -t jenkins-lts .
export GITHUB_WORKSPACE=/workspace
export GITHUB_SHA=$(git rev-parse --verify HEAD)

docker run -it --rm -e GITHUB_SHA -e GITHUB_WORKSPACE -v "$(pwd -P)/../../":"/workspace" -it ${PORT_ARG} jenkins-lts ${KEEPALIVE_ARG}
