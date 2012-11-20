Blitz4j
=====

Blitz4j is a logging framework built on top of log4j to reduce contention and enable highly scalable logging without affecting application performance characteristics. 

At Netflix, Blitz4j is used to log billions of events for monitoring, business intelligence reporting, debugging and other purposes. Blitz4j overcomes traditional log4j
bottlenecks and comes built with a highly scalable and customizable asynchronous framework. Blitz4j comes with the ability to convert the existing log4j appenders to use
the asynchronous model without changing the existing log4j configurations.

Blitz4j uses archaius (http://www.github.com/Netflix/archaius) to reconfigure log4j dynamically and servo (http://www.github.com/Netflix/servo) to track the performance metrics
regarding logging. Blitz4j also tries to mitigate data loss and provides a way to summarize the log information during log storms.


BUILD
-------

./gradlew clean build



Support
----------
[Blitz4j Google Group] (https://groups.google.com/forum/?fromgroups#!forum/netflix_blitz4j)


Documentation
--------------
Please see [wiki] (https://github.com/Netflix/blitz4j/wiki) for detailed documentation.
