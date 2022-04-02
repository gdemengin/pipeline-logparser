#!/bin/bash

set -e

# start jenkins, run job to test and wait for it to complete, then stop kenkins
function run_job() {
    timeout 300 /manage_jenkins.sh start
    sleep 10
    timeout 300 /manage_jenkins.sh wait_for_jobs "$1"
    sleep 10
    timeout 60 /manage_jenkins.sh stop
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
    # cleanup temp filesi
    rm -rf ${GITHUB_WORKSPACE}/.tmp-test

    # sleep to capture logs in tail command
    sleep 10
}

create_local_repo
echo GITHUB_SHA=${GITHUB_SHA}

cd /

> stdout
> jenkins.log
tail -F stdout jenkins.log /var/jenkins_home/jobs/logparser/builds/1/log &

JOBS="logparser"

run_job "${JOBS}" > stdout 2<&1|| {
    return_code=$?
    clean
    echo "run_jobs failed, exiting"
    exit ${return_code}
}

clean
echo "Happy End!"
