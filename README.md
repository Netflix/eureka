Blitz4j
=====

Blitz4j is a logging framework built on top of log4j to reduce contention and enable highly scalable logging without affecting application performance characteristics. 

At Netflix, Blitz4j is used to log billions of events for monitoring, business intelligence reporting, debugging and other purposes. Blitz4j overcomes traditional log4j
bottlenecks and comes built with a highly scalable and customizable asynchronous framework. Blitz4j comes with the ability to convert the existing log4j appenders to use
the asynchronous model without changing the existing log4j configurations.

Blitz4j uses archaius (http://www.github.com/Netflix/archaius) that allows log4j dynamic configurations for a server or a group of servers. Blitz4j also tries to mitigate
data loss and provides a way to summarize the log information during log storms.


BUILD
-------

./gradlew clean build



Support
----------
[Blitz4j Google Group] (https://groups.google.com/forum/?fromgroups#!forum/eureka_netflix)


Documentation
--------------
Please see [wiki] (https://github.com/Netflix/blitz4j/wiki) for detailed documentation.
