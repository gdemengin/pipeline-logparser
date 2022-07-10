import jenkins.model.*
Jenkins.instance.setNumExecutors(4)
// todo: use separate agent from master
Jenkins.instance.setLabelString('test-agent')
