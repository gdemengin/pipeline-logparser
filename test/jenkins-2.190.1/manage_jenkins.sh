#!/bin/bash

set -e

function interrupt() {
    echo "SIGNAL CAUGHT: exit manage_jenkins.sh"
    exit 1
}

trap 'interrupt;' TERM INT

function check_fresh_jenkins() {
    # make sure jenkins is fresh: no job has nextbuildNumber file
    if [ $(find /var/jenkins_home/jobs -name nextBuildNumber | wc -l) != 0 ]; then
        echo "ERROR jenkins installation is not fresh"
        return 1
    fi
}

function start_jenkins() {
    echo "$(date) starting jenkins instance"
    export JAVA_OPTS="-Xmx1024m -Djenkins.install.runSetupWizard=false -Dhudson.plugins.git.GitSCM.ALLOW_LOCAL_CHECKOUT=true"
    export PLUGINS_FORCE_UPGRADE=true
    export TRY_UPGRADE_IF_NO_MARKER=true

    rm -rf ${GITHUB_WORKSPACE}/.version
    mkdir -p ${GITHUB_WORKSPACE}/.version

    /usr/local/bin/jenkins.sh >> /jenkins.log 2>&1 &

    # wait for startup
    while [ $(cat /jenkins.log | grep "Completed initialization" | wc -l) = 0 ]
    do
        if [ $(cat /jenkins.log | grep "Jenkins stopped" | wc -l) != 0 ]
        then
            echo "jenkins has stopped instead of starting"
            return 1
        fi
        echo "$(date) waiting for jenkins to complete startup"
        sleep 10
    done
    echo ""
    echo "$(date) jenkins instance started"
}

function stop_jenkins() {
    echo "$(date) stopping jenkins instance"
    sleep 1

    # wait for shutdown
    while [ $(ps -efla | grep java | grep -v grep | wc -l) != 0 ]; do
        echo "$(date) waiting for jenkins to stop"
        ps -efla | grep java | grep -v grep
        killall java
        sleep 10
    done
    echo ""
    echo "$(date) jenkins instance stopped"
}

# wait for run (should be run #1) to be completed
function wait_for_jobs() {
    local incomplete="$1"
    local still_incomplete

    while [ "${incomplete}" != "" ]
    do
        sleep 10
        still_incomplete=
        echo "$(date) waiting for '${incomplete}' job(s) to complete"

        for job in ${incomplete}
        do
            local permalink="/var/jenkins_home/jobs/${job}/builds/permalinks"
            if [ ! -e ${permalink} ]; then
                still_incomplete=$(echo "${still_incomplete} ${job}" | xargs)
            else
                cat ${permalink}
                # if one of permalinks is not -1 then at least one run ended
                if [ $(egrep -v " -1$" ${permalink} | wc -l) == 0 ]; then
                    still_incomplete=$(echo "${still_incomplete} ${job}" | xargs)
                fi
            fi
        done
        incomplete="${still_incomplete}"
    done

    # sleep to let logs of jobs time to flush in stdout
    echo "$(date) job(s) '$1' all completed"
    return 0
}

# check status of run #1
function check_jobs_success() {
    echo ""
    echo "$(date) checking job(s) '$1' result"

    local unsuccessful

    for job in $1
    do
        local build="/var/jenkins_home/jobs/${job}/builds/1/build.xml"
        if [ ! -e ${build} ] || \
           [ "$(xpath -q -e '/flow-build/result/text()' ${build})" != "SUCCESS" ]; then
            unsuccessful=$(echo "${unsuccessful} ${job}" | xargs)
            echo "$(date) job '${job}' did not succeed"
        else
            echo "$(date) job '${job}' successful !"
        fi
    done
    if [ "${unsuccessful}" != "" ]
    then
        echo "$(date) ERROR job(s) '${unsuccessful}' did not succeed"
        return 1
    fi
}

# start local agent
function local_agent() {
    [ "$1" == "" ] && echo "ERROR missing argument root dir for local_agent" && return 1
    mkdir -p $1
    cd $1
    local ret=1
    while [ "${ret}" != "0" ]; do
        curl --url  http://localhost:8080/jnlpJars/agent.jar --insecure --output agent.jar
        {
            /opt/java/openjdk/bin/java -jar agent.jar
            ret=$?
        } || ret=1
    done
}

case "$1" in
  start)
    check_fresh_jenkins
    start_jenkins
    ;;
  stop)
    stop_jenkins
    ;;
  wait_for_jobs)
    wait_for_jobs "$2"
    ;;
  check_jobs_success)
    check_jobs_success "$2"
    ;;
  local_agent)
    local_agent "$2"
    ;;
  *)
    echo "ERROR wrong param $1"
    exit 1
    ;;
esac

