Nexus
=====

A Gossip Based Service Discovery and Failure Detection Service.

Licensed under the Apache License, version 2.0 (http://www.apache.org/licenses/LICENSE-2.0.html).

This project requires Maven 3.x to build.  To build, cd to root directory and:

    mvn clean install

See the [project wiki](https://github.com/Hellblazer/Nexus/wiki) for design and usage.

### Maven configuration

include the hellblazer snapshot repository:

    <repository>
        <id>hellblazer-snapshots</id>
        <url>https://repository-hal900000.forge.cloudbees.com/snapshot/</url>
    </repository>
    
add as dependency:

    <dependency>
        <groupId>com.hellblazer</groupId>
        <artifactId>nexus</artifactId>
        <version>1.0.1-SNAPSHOT</version>
    </dependency>
