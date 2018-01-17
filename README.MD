* [Java Developer Kit (JDK) version 8](http://www.oracle.com/technetwork/java/javase/downloads/jdk8-downloads-2133151.html)
* [Maven (https://maven.apache.org/download.cgi)](https://maven.apache.org/download.cgi) (a widely used build tool for Java projects)
* A text editor or IDE of your choice



## Understanding the Maven `pom.xml` configuration file

### Properties

Maven allows you to define project-level values called *properties* in a `pom.xml` file. Placing the following text after the `<repositories>` section will add two global properties to the configuration:

```xml
<properties>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    <heron.version>0.14.6</heron.version>
</properties>
```

These values are used in different sections of the `pom.xml`. For example, when specifying the version of the Heron libraries, we can use `${heron.version}` instead of hardcoding a value.

### Dependencies

Below are the dependencies for Heron components in the `pom.xml` 

```xml
<dependency>
    <groupId>com.twitter.heron</groupId>
    <artifactId>heron-storm</artifactId>
    <version>${heron.version}</version>
</dependency>
```

### Coap dependecies
Below are the coap related dependencies from californium

```xml
 <dependency>
	    <groupId>org.eclipse.californium</groupId>
	    <artifactId>californium-core</artifactId>
	    <version>2.0.0-M5</version>
	</dependency>
```

### Build configuration

Maven plug-ins allow you to customize the build stages of the project, for example how the project is compiled or how to package it into a JAR file. General POM files follow this convention:

```xml
<build>
    <plugins>
    </plugins>
    <resources>
    </resources>
</build>
```

This section is used to add plug-ins, resources, and other build configuration options. For a full reference for `pom.xml` files, see the [Maven documentation](http://maven.apache.org/pom.html).

### Plug-ins

For Heron topologies implemented in Java, the [Exec Maven Plugin](http://www.mojohaus.org/exec-maven-plugin/) is useful because it allows you to easily run the topology locally in your development environment. The Exec Maven plugin is included in the `<plugins>` section of the `pom.xml` like this:

```xml
<plugin>
    <groupId>org.codehaus.mojo</groupId>
    <artifactId>exec-maven-plugin</artifactId>
    <version>1.4.0</version>
    <executions>
    <execution>
    <goals>
        <goal>exec</goal>
    </goals>
    </execution>
    </executions>
    <configuration>
        <executable>java</executable>
        <includeProjectDependencies>true</includeProjectDependencies>
        <includePluginDependencies>false</includePluginDependencies>
        <classpathScope>compile</classpathScope>
        <mainClass>${heron.topology}</mainClass>
        <cleanupDaemonThreads>false</cleanupDaemonThreads> 
    </configuration>
</plugin>
```

Another useful plug-in is the [Apache Maven Compiler Plugin](http://maven.apache.org/plugins/maven-compiler-plugin/), which is used to change compilation options. This changes the Java version that Maven uses for the source and target for your application.

> For this project we set the source and target Java version to **1.8**.

The compiler configuration is in the `<plugins>` section of the `pom.xml`. To use Java 8:
__Note__ : Java 1.8+ is required for current versions of heron, the example will not work with Java 1.7 or less 

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-compiler-plugin</artifactId>
    <version>3.6.2</version>
    <configuration>
        <source>1.8</source>
        <target>1.8</target>
    </configuration>
</plugin>
```

## Create the topology

A Java-based [Apache Storm](http://storm.apache.org/) topology consists of three components that you must author (or reference) as a dependency:

* **Spouts** read data from external sources and emits streams of data into the topology

* **Bolts** perform processing on streams emitted by spouts or other bolts, and emits one or more streams

* The **topology** defines how the spouts and bolts are arranged, and provides the entry point for the topology

> Wait a second: aren't we using *Heron* here and not Storm? Well, it's a bit complicated. Heron is a successor to Storm that can nonetheless run topologies written for Storm (it just runs them more efficiently).

### Define the topology

Once you've created the spouts and bolts that you need for your use case, you need to tie everything together into a processing graph known as a topology (technically speaking a *directed acyclic graph*). Topologies define how data flows between the components. The topology level is also where you can provide *parallelism hints* that Heron can use to make hardware and other choices when creating instances of the components within the cluster.

The following image is a target topology we are aiming for:

```
+----------------+          +-------------------+             +-------------+      
|                |          |                   |             |             |
| Coap listener  |          |                   |             |             |      
|     Spout      +----------> CBOR messages decoding  +------->+  Analytics  +---->(moving average etc..)
|                |          |       Bolt        |             |    Bolt     |      
|                |          |                   |             |             |
+----------------+          +-------------------+            +-------------+      
                                                    
         ("Messages from noise maker")              
                                                    
                                                    
                                                    

```

So far integration is done, decoding CBOR messages and processing in analytics bolt is yet to be done

### Configure logging

Heron uses Java logging to log information. Topologies can specify their own logging framework. In this example we are using [log4j](http://www.mojohaus.org/exec-maven-plugin/). To use log4j, these dependencies were placed in the `<dependencies>` section of `pom.xml`:

```xml
<dependency>
    <groupId>org.apache.logging.log4j</groupId>
    <artifactId>log4j-api</artifactId>
    <version>2.6.2</version>
</dependency>

<dependency>
    <groupId>org.apache.logging.log4j</groupId>
    <artifactId>log4j-core</artifactId>
    <version>2.6.2</version>
</dependency>
```    

As the topology runs, the topology displays startup information. The following text is an example of the bolt status output:

```
[2017-10-21 19:20:30 -0400] [STDOUT] stdout: Bolt processed 51900000 tuples in 612621 ms  
[2017-10-21 19:20:31 -0400] [STDOUT] stdout: �btsESC^@^@^A_A4q=evalue�@Mtz�G�^TieventTypea3gsubjectktemperature!!!  
[2017-10-21 19:20:31 -0400] [STDOUT] stdout: Bolt processed 52000000 tuples in 613914 ms  
[2017-10-21 19:20:32 -0400] [STDOUT] stdout: �btsESC^@^@^A_A4q=evalue�@Mtz�G�^TieventTypea3gsubjectktemperature!!!  
[2017-10-21 19:20:32 -0400] [STDOUT] stdout: Bolt processed 52100000 tuples in 615077 ms  
[2017-10-21 19:20:34 -0400] [STDOUT] stdout: �btsESC^@^@^A_A4q=evalue�@Mtz�G�^TieventTypea3gsubjectktemperature!!!  
[2017-10-21 19:20:34 -0400] [STDOUT] stdout: Bolt processed 52200000 tuples in 616320 ms  
[2017-10-21 19:20:35 -0400] [STDOUT] stdout: �btsESC^@^@^A_A4q=evalue�@Mtz�G�^TieventTypea3gsubjectktemperature!!!  
[2017-10-21 19:20:35 -0400] [STDOUT] stdout: Bolt processed 52300000 tuples in 617459 ms  
[2017-10-21 19:20:36 -0400] [STDOUT] stdout: �btsESC^@^@^A_A4q=evalue�@Mtz�G�^TieventTypea3gsubjectktemperature!!!  
[2017-10-21 19:20:36 -0400] [STDOUT] stdout: Bolt processed 52400000 tuples in 618698 ms  
[2017-10-21 19:20:37 -0400] [STDOUT] stdout: �btsESC^@^@^A_A4q=evalue�@Mtz�G�^TieventTypea3gsubjectktemperature!!!  
[2017-10-21 19:20:37 -0400] [STDOUT] stdout: Bolt processed 52500000 tuples in 619851 ms  
[2017-10-21 19:20:38 -0400] [STDOUT] stdout: �btsESC^@^@^A_A4q=evalue�@Mtz�G�^TieventTypea3gsubjectktemperature!!!  
[2017-10-21 19:20:38 -0400] [STDOUT] stdout: Bolt processed 52600000 tuples in 620950 ms  
[2017-10-21 19:20:39 -0400] [STDOUT] stdout: �btsESC^@^@^A_A4q=evalue�@Mtz�G�^TieventTypea3gsubjectktemperature!!!  
[2017-10-21 19:20:39 -0400] [STDOUT] stdout: Bolt processed 52700000 tuples in 622076 ms  
[2017-10-21 19:20:40 -0400] [STDOUT] stdout: �btsESC^@^@^A_A4q=evalue�@Mtz�G�^TieventTypea3gsubjectktemperature!!!  
[2017-10-21 19:20:40 -0400] [STDOUT] stdout: Bolt processed 52800000 tuples in 623205 ms  
[2017-10-21 19:20:41 -0400] [STDOUT] stdout: �btsESC^@^@^A_A4q=evalue�@Mtz�G�^TieventTypea3gsubjectktemperature!!!  
[2017-10-21 19:20:41 -0400] [STDOUT] stdout: Bolt processed 52900000 tuples in 624149 ms  
[2017-10-21 19:20:42 -0400] [STDOUT] stdout: �btsESC^@^@^A_A4q=evalue�@Mtz�G�^TieventTypea3gsubjectktemperature!!!  
[2017-10-21 19:20:42 -0400] [STDOUT] stdout: Bolt processed 53000000 tuples in 625102 ms  
[2017-10-21 19:20:44 -0400] [STDOUT] stdout: �btsESC^@^@^A_A4q=evalue�@Mtz�G�^TieventTypea3gsubjectktemperature!!!  
:

```



### Command explanation

There was a lot going on in that last command. We'll break it down a bit here:

```shell
$ mvn package # This will build the jar with its dependencies bundled

$ heron submit local \ # Submit to the local cluster
  ./target/heron-integrator-0.0.1-SNAPSHOT-jar-with-dependencies.jar \ # The path of the topology's jar file
  tutorial.HeronCoapIntegrator \ # The topology's Java class
  HeronCoapIntegrator \ # The name of the topology (must be unique in a cluster)
  --deploy-deactivated # Deploy in deactivated mode
```

### Activating the topology

At this point, the topology has been *submitted* to the Heron cluster but not yet *activated*. That essentially means that Heron has your application code and knows how to run it but hasn't actually started any processes yet. To activate---or run---the topology:

```shell
$ heron activate local HeronCoapIntegrator
```

### Deactivating the topology

you can deactivate and kill the topology:

```shell
$ heron deactivate local HeronCoapIntegrator
$ heron kill local HeronCoapIntegrator 
```

> Deactivating a topology means that the topology is still known to the cluster but not running; killing the topology deletes it from the cluster for good.
