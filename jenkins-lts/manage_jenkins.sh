#!/bin/bash

set -e

check_fresh_jenkins() {
    # make sure jenkins is fresh: no job has nextbuildNumber file
    if [ $(find /var/jenkins_home/jobs -name nextBuildNumber | wc -l) != 0 ]; then
        echo "ERROR jenkins installation is not fresh"
        return 1
    fi
}

start_jenkins() {
    echo "$(date) starting jenkins instance"
    export JAVA_OPTS="-Xmx1024m -Djenkins.install.runSetupWizard=false"
    export PLUGINS_FORCE_UPGRADE=true
    export TRY_UPGRADE_IF_NO_MARKER=true

    /usr/local/bin/jenkins.sh >> /jenkins.log 2>&1 &
}

wait_for_jenkins_startup() {
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

wait_for_jenkins_shutdown() {
    while [ $(cat /jenkins.log | grep "JVM is terminating"| wc -l) = 0 ]
    do
        echo "$(date) waiting for jenkins to stop"
        sleep 10
    done
    echo ""
    echo "$(date) jenkins instance stopped"
}

wait_for_jobs() {
    local incomplete="$1"
    local incomplete2
    local result
    local next

    while [ "${incomplete}" != "" ]
    do
        sleep 10
        incomplete2=
        echo "$(date) waiting for '${incomplete}' job(s) to complete"
        for job in ${incomplete}
        do
            if [ ! -e /var/jenkins_home/jobs/${job}/builds/lastCompletedBuild/build.xml ]
            then
                incomplete2=$(echo "${incomplete2} ${job}" | xargs)
            elif [ ! -e /var/jenkins_home/jobs/${job}/builds/lastSuccessfulBuild/build.xml ]
            then
                echo "$(date) ${job} did not succeed"
            fi
        done
        if [ "${incomplete}" != "${incomplete2}" ]
        then
            incomplete="${incomplete2}"
        fi
    done

    echo "$(date) job(s) '$1' all completed"
    return 0
}

check_jobs_success() {
    echo ""
    echo "$(date) checking job(s) '$1' result"

    local unsuccessful

    for job in $1
    do
        if [ ! -e /var/jenkins_home/jobs/${job}/builds/lastSuccessfulBuild/build.xml ]
        then
            unsuccessful=$(echo "${unsuccessful} ${job}" | xargs)
            echo "job '${job}' did not succeed"
        else
            # make sure only run 1 was executed
            next=$(cat /var/jenkins_home/jobs/$job/nextBuildNumber)
            if [ "${next}" != "2" ]
            then
                echo "ERROR job '${job}' next build number is not 2 (${next}), we expected only run 1 to be completed"
                return 1
            fi
            echo "${job} successful !"
        fi
    done
    if [ "${unsuccessful}" != "" ]
    then
        echo "ERROR job(s) '${unsuccessful}' did not succeed"
        return 1
    fi
}

case "$1" in
  start)
    check_fresh_jenkins
    start_jenkins
    wait_for_jenkins_startup
    ;;

  stop)
    # stop jenkins
    killall java
    wait_for_jenkins_shutdown
    ;;

  wait_for_jobs)
    wait_for_jobs "$2"
    ;;

  check_jobs_success)
    check_jobs_success "$2"
    ;;

  *)
    echo "ERROR wrong param $1"
    exit 1
    ;;
esac

