FROM    ubuntu

RUN     apt-get install -fy tomcat7

ADD     eureka-server/build/libs/eureka-server-1.1.127-SNAPSHOT.war /var/lib/tomcat7/webapps/eureka.war
ADD     run.sh /srv/run.sh

EXPOSE  8080

CMD     /srv/run.sh