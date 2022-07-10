#!/bin/bash

set -e

function usage() {
    echo "ERROR too many parameters: usage $0 [-keepalive]"
}

KEEPALIVE=0

[ $# -gt 1 ] && echo "ERROR too many parameters" && usage && exit 1

while [ $# -gt 0 ]; do
    case "$1" in
        -keepalive) KEEPALIVE=1; shift;;
        *) echo "ERROR bad parameter $1"; usage; exit 1;;
    esac
done

function start_jenkins() {
    timeout 300 /manage_jenkins.sh start
}

function stop_jenkins() {
    timeout 60 /manage_jenkins.sh stop
}

function interrupt() {
    echo "SIGNAL CAUGHT: stop jenkins and exit"
    stop_jenkins
    # wait 5 sec to let logs flush
    sleep 5
    exit 1
}

trap 'interrupt;' TERM INT

# start jenkins, run job to test and wait for it to complete, then stop jenkins
function run_job() {
    start_jenkins
    sleep 10
    timeout 300 /manage_jenkins.sh wait_for_jobs "$1"
    sleep 10
    [ "${KEEPALIVE}" != "1" ] && stop_jenkins
    /manage_jenkins.sh check_jobs_success "$1"
}

# using the current git tree as Global Pipeline Library may fail
# if HEAD is detached (pull-request, tag, ...)
# So clone current commit in a local repo with local branch
# the test job shall use that branch
function create_local_repo() {
    local SRC=${GITHUB_WORKSPACE}/.git
    local SHA=${GITHUB_SHA}
    local DST=${GITHUB_WORKSPACE}/.tmp-test

    echo ""
    echo "building local git repository from ${SRC}"

    rm -rf ${DST}
    git clone file://${SRC} ${DST}
    cd ${DST}
    git status

    # create dummy branch to be able to use this commit
    git checkout -b ${SHA}

    # show status and last commit
    git status
    git show -q
}

function clean() {
    # cleanup temp files
    rm -rf ${GITHUB_WORKSPACE}/.tmp-test

    # sleep to capture logs in tail command
    sleep 10
}

function wait_and_clean() {
    if [ "${KEEPALIVE}" == 1 ]; then
        # let the check of tests end (~10s) to avoid mixing logs
        sleep 20
        local key=
        while [ "${key}" != "q" ]; do
            read -t 60 -n 1 -s -r -p "$(date) Press 'q' to stop jenkins instance and exit container " key || echo > /dev/null
            echo
        done
        echo "Stopping ..."
        stop_jenkins
    fi
    clean
}

echo testing commit GITHUB_SHA=${GITHUB_SHA}
create_local_repo

cd /

> stdout
> jenkins.log
tail -F stdout jenkins.log /var/jenkins_home/jobs/logparser/builds/1/log &

JOBS="logparser"

run_job "${JOBS}" > stdout 2<&1|| {
    return_code=$?
    echo "run_jobs failed with code ${return_code}"
    wait_and_clean
    exit ${return_code}
}

wait_and_clean
echo "Happy End!"
