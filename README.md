Eureka
======
[![build](https://github.com/Netflix/eureka/actions/workflows/nebula-snapshot.yml/badge.svg)](https://github.com/Netflix/eureka/actions/workflows/nebula-snapshot.yml)

Eureka is a RESTful (Representational State Transfer) service that is primarily used in the AWS cloud for the purpose of
discovery, load balancing and failover of middle-tier servers. It plays a critical role in Netflix mid-tier infra.

Building
--------
The build requires `java8` because of some required libraries that are `java8` (`servo`), but the source and target
compatibility are still set to `1.7`. Note that tags should be checked out to perform a build.

Contributing
------------
For any non-trivial change (or a large LoC-wise change), please open an issue first to make sure there's alignment on
the scope, the approach, and the viability.

Support
----------
Community-driven mostly, feel free to open an issue with your question, the maintainers are looking over these
periodically. Issues with the most minimal repro possible have the highest chance of being answered.


Documentation
--------------
Please see [wiki](https://github.com/Netflix/eureka/wiki) for detailed documentation.
