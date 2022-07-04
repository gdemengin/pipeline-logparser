#!/bin/bash

set -e

# update jenkins-last version with .version
# must be run AFTER running jenkins-lts test

cd $(dirname $0)/..

[ ! -e .version ] && echo "ERROR please run jenkins-lts workflow first" && exit 1

rm -rf ./test/jenkins-last
cp -r ./test/jenkins-lts ./test/jenkins-last
cp -f .version/plugins.txt ./test/jenkins-last/
cp -f .version/versions.txt ./test/jenkins-last/

NEW_BADGE="[![test/jenkins-last/versions.txt](https://img.shields.io/badge/jenkins-$(cat .version/version)-blue.svg)](test/jenkins-last/versions.txt)"
sed "s|\[\!\[test/jenkins-last/versions.txt\].*$|${NEW_BADGE}|" README.md > README.md.tmp
mv README.md.tmp README.md
sed "s|FROM jenkins/jenkins:lts|FROM jenkins/jenkins:$(cat .version/version)|" ./test/jenkins-last/Dockerfile > ./test/jenkins-last/Dockerfile.tmp
mv ./test/jenkins-last/Dockerfile.tmp ./test/jenkins-last/Dockerfile
sed "s|jenkins-lts|jenkins-last|" ./test/jenkins-last/run.sh > ./test/jenkins-last/run.sh.tmp
mv ./test/jenkins-last/run.sh.tmp ./test/jenkins-last/run.sh
chmod 755 ./test/jenkins-last/run.sh

exit 0
