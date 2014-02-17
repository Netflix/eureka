#!/bin/bash

AWS_REGION=ap-southeast-2
EUREKA_DOMAIN=eureka.od-dev.aws.uc-inf.net

export JAVA_OPTS="-Xmx256m -Deureka.waitTimeInMsWhenSyncEmpty=5000 -Deureka.appinfo.replicate.interval=1 -Deureka.region=$AWS_REGION -Deureka.eurekaServer.domainName=$EUREKA_DOMAIN -Deureka.datacenter=cloud -Deureka.eurekaServer.context=eureka/v2 -Deureka.environment=prod -Deureka.validateInstanceId=false -Deureka.port=8080 -Deureka.eurekaServer.port=8080 -Deureka.serviceUrl.default=http://localhost:8080/eureka/v2/"
export CATALINA_BASE=/var/lib/tomcat7
export CATALINA_HOME=/usr/share/tomcat7

/usr/share/tomcat7/bin/catalina.sh run
