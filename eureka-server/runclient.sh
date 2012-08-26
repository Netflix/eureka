#!/bin/bash
#Copy all libraries
TEST_CLASSPATH=
for i in testlibs/WEB-INF/lib/*
do
 if [ "$TEST_CLASSPATH" = "" ] ; then
   TEST_CLASSPATH=$i
 fi
 TEST_CLASSPATH=$TEST_CLASSPATH:$i
done
TEST_CLASSPATH=$TEST_CLASSPATH:build/classes/main:conf/sampleclient

echo CLASSPATH:$TEST_CLASSPATH
java -Deureka.region=default -Deureka.environment=test -Deureka.client.props=sample-eureka-client -cp $TEST_CLASSPATH com.netflix.eureka.SampleEurekaClient

