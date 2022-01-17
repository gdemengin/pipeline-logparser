#!/bin/bash

set -e

# start jenkins, run job to test and wait for it to complete, then stop kenkins
run_job() {
    timeout 300 /manage_jenkins.sh start
    sleep 10
    timeout 300 /manage_jenkins.sh wait_for_jobs "$1"
    sleep 10
    timeout 60 /manage_jenkins.sh stop
    /manage_jenkins.sh check_jobs_success "$1"
}

cd /

> stdout
> jenkins.log
tail -F stdout jenkins.log /var/jenkins_home/jobs/logparser/builds/1/log &

JOBS="logparser"

run_job "${JOBS}" > stdout 2<&1|| {
    return_code=$?
    # sleep to capture logs in tail command
    sleep 10
    echo "run_jobs failed, exiting"
    exit ${return_code}
}

# sleep to capture logs in tail command
sleep 10
echo "Happy End!"
