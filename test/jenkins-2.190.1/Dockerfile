FROM jenkins/jenkins:2.190.1

# switch to root, let the entrypoint drop back to jenkins
USER root

# patch sources.list for old debian versions
# https://stackoverflow.com/questions/76094428/debian-stretch-repositories-404-not-found
RUN sed -i s/deb.debian.org/archive.debian.org/g /etc/apt/sources.list \
 && sed -i 's|security.debian.org|archive.debian.org/debian-security/|g' /etc/apt/sources.list \
 && sed -i '/stretch\/updates/d' /etc/apt/sources.list \
 && sed -i '/stretch-updates/d' /etc/apt/sources.list

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
     libxml-xpath-perl \
 && apt-get clean \
 && rm -rf /var/lib/apt/lists/*

# remove expired certificates
RUN grep X3 /etc/ca-certificates.conf \
 && grep X1 /etc/ca-certificates.conf \
 && sed -i '/^mozilla\/DST_Root_CA_X3/s/^/!/' /etc/ca-certificates.conf && update-ca-certificates -f

# install gosu for a better su+exec command
ARG GOSU_VERSION=1.12
RUN dpkgArch="$(dpkg --print-architecture | awk -F- '{ print $NF }')" \
 && wget -O /usr/local/bin/gosu "https://github.com/tianon/gosu/releases/download/$GOSU_VERSION/gosu-$dpkgArch" \
 && chmod +x /usr/local/bin/gosu \
 && gosu nobody true

COPY plugins.txt /usr/share/jenkins/ref/plugins.txt
RUN /usr/local/bin/install-plugins.sh < /usr/share/jenkins/ref/plugins.txt

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
