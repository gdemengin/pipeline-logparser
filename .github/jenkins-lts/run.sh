#!/bin/bash

set -e

cd $(dirname $0)

docker build -t jenkins-lts .
export GITHUB_WORKSPACE=/workspace
export GITHUB_SHA=$(git rev-parse --verify HEAD)

export KIND_CLUSTER_NAME=jenkins-lts
kind delete cluster --name=${KIND_CLUSTER_NAME} || echo ""

kind create cluster --name="${KIND_CLUSTER_NAME}"

kubectl config use-context kind-${KIND_CLUSTER_NAME}
kubectl cluster-info
kind get clusters
kubectl get service --all-namespaces
docker ps -a

docker network rm jenkins-lts-net || echo ""
docker network create jenkins-lts-net
docker network connect jenkins-lts-net --alias kubernetes.default.svc ${KIND_CLUSTER_NAME}-control-plane

rm -rf kubeconfig
mkdir kubeconfig
kind get kubeconfig | sed "s/server: .*/server: https:\/\/${KIND_CLUSTER_NAME}-control-plane:6443/g" > kubeconfig/config.kube
cat ./kubeconfig/config.kube | grep server
docker run -it -p 8080:8080 --rm -e KIND_CLUSTER_NAME -e GITHUB_SHA -e GITHUB_WORKSPACE -v $(pwd -P)/kubeconfig:/kubeconfig -v "$(pwd -P)/../../":"/workspace" -v "/var/run/docker.sock":"/var/run/docker.sock" --network jenkins-lts-net jenkins-lts $*

kind delete cluster --name=${KIND_CLUSTER_NAME}
rm -rf kubeconfig jenkins-lts.kube
docker network rm jenkins-lts-net
