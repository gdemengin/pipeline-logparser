FROM jenkins/jenkins:lts

# switch to root, let the entrypoint drop back to jenkins
USER root

# install prerequisite debian packages
RUN apt-get update \
 && DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
     apt-transport-https \
     ca-certificates \
     curl \
     gnupg2 \
     software-properties-common \
     vim \
     wget \
     procps \
     psmisc \
 && apt-get clean \
 && rm -rf /var/lib/apt/lists/*

# install gosu for a better su+exec command
ARG GOSU_VERSION=1.12
RUN dpkgArch="$(dpkg --print-architecture | awk -F- '{ print $NF }')" \
 && wget -O /usr/local/bin/gosu "https://github.com/tianon/gosu/releases/download/$GOSU_VERSION/gosu-$dpkgArch" \
 && chmod +x /usr/local/bin/gosu \
 && gosu nobody true

# install docker
# if install fails, command apt-cache madison docker-ce below shall list the list of available versions
# or you can leave it empty
ARG DOCKER_CLI_VERSION==5:20.10.8~3-0~debian-bullseye
# ARG DOCKER_CLI_VERSION=
RUN curl -fsSL https://download.docker.com/linux/debian/gpg | apt-key add - \
 && add-apt-repository \
     "deb [arch=amd64] https://download.docker.com/linux/debian \
     $(lsb_release -cs) \
     stable" \
 && apt-get update \
 && echo "lsb_release -cs=$(lsb_release -cs)" && apt-cache madison docker-ce \
 && DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
    docker-ce-cli${DOCKER_CLI_VERSION} \
 && apt-get clean \
 && rm -rf /var/lib/apt/lists/* \
 && groupadd -r docker \
 && usermod -aG docker jenkins

# install k8s + minikube + kind
RUN apt-get update \
&& curl -s https://packages.cloud.google.com/apt/doc/apt-key.gpg | apt-key add - \
&& echo "deb https://apt.kubernetes.io/ kubernetes-xenial main" | tee -a /etc/apt/sources.list.d/kubernetes.list \
&& apt-get update \
&& apt-get install -y kubectl conntrack iptables sudo \
&& curl -Lo minikube https://storage.googleapis.com/minikube/releases/latest/minikube-linux-amd64 \
&& chmod +x minikube \
&& install minikube /usr/local/bin/ \
&& curl -Lo kind https://github.com/kubernetes-sigs/kind/releases/download/0.1.0/kind-linux-amd64 \
&& chmod +x kind \
&& mv kind /usr/local/bin/


COPY plugins.txt /usr/share/jenkins/ref/plugins.txt
RUN jenkins-plugin-cli --verbose --plugin-file /usr/share/jenkins/ref/plugins.txt

ENV JENKINS_ADMIN_USERNAME jenkins
ENV JENKINS_ADMIN_PASSWORD jenkins

COPY init.groovy.d/ /usr/share/jenkins/ref/init.groovy.d/
COPY jenkins-home /var/jenkins_home
RUN chown -R jenkins:jenkins /var/jenkins_home

# entrypoint is used to update docker gid and revert back to jenkins user
COPY entrypoint.sh /entrypoint.sh
COPY manage_jenkins.sh /manage_jenkins.sh
RUN chmod +x /entrypoint.sh \
 && chmod +x /manage_jenkins.sh
ENTRYPOINT ["/entrypoint.sh"]
HEALTHCHECK CMD curl -sSLf http://localhost:8080/login >/dev/null || exit 1
