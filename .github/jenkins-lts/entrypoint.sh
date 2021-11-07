#!/bin/bash

set -e

KEEPALIVE=
if [ "$1" = "keepalive" ]; then
   KEEPALIVE=true
   shift
fi

# In Prod, this may be configured with a GID already matching the container
# allowing the container to be run directly as Jenkins. In Dev, or on unknown
# environments, run the container as root to automatically correct docker
# group in container to match the docker.sock GID mounted from the host.
if [ "$(id -u)" = "0" ] && [ "${CLI:-false}" != "true" ]; then
  # get gid of docker socket file
  SOCK_DOCKER_GID=`ls -ng /var/run/docker.sock | cut -f3 -d' '`

  # get group of docker inside container
  CUR_DOCKER_GID=`getent group docker | cut -f3 -d: || true`

  # if they don't match, adjust
  if [ ! -z "$SOCK_DOCKER_GID" -a "$SOCK_DOCKER_GID" != "$CUR_DOCKER_GID" ]; then
    groupmod -g ${SOCK_DOCKER_GID} -o docker
  fi
  if ! groups jenkins | grep -q docker; then
    usermod -aG docker jenkins
  fi

  chmod 664 /var/run/docker.sock

  # Add call to gosu to drop from root user to jenkins user
  # when running original entrypoint
  set -- gosu jenkins "$@"
fi

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

if [ "${KEEPALIVE}" = "true" ]; then
  > jenkins.log
  tail -F jenkins.log &
  timeout 300 /manage_jenkins.sh start
  while [ 1 == 1 ]
  do
    sleep 300
    echo keepalive
  done
fi

cd /

> stdout
> jenkins.log
tail -F stdout jenkins.log /var/jenkins_home/jobs/logparser/builds/1/log | sed -r 's/\x1B\[8m.*?\x1B\[0m//g' &

JOBS="logparser"

run_job "${JOBS}" > stdout 2<&1|| {
    return_code=$?
    clean
    echo "run_jobs failed, exiting"
    exit ${return_code}
}

# clean dockerhost
clean

echo "Happy End!"
