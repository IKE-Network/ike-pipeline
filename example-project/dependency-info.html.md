---
date_published: 2026-04-05
date_modified: 2026-04-05
canonical_url: https://github.com/IKE-Network/example-project/dependency-info.html
---

# Maven Coordinates

## [Apache Maven](#apache-maven)

```
<dependency>
  <groupId>network.ike</groupId>
  <artifactId>example-project</artifactId>
  <version>55</version>
</dependency>
```

## [Apache Ivy](#apache-ivy)

```
<dependency org="network.ike" name="example-project" rev="55">
  <artifact name="example-project" type="jar" />
</dependency>
```

## [Groovy Grape](#groovy-grape)

```
@Grapes(
@Grab(group='network.ike', module='example-project', version='55')
)
```

## [Gradle/Grails](#gradle-grails)

```
implementation 'network.ike:example-project:55'
```

## [Scala SBT](#scala-sbt)

```
libraryDependencies += "network.ike" % "example-project" % "55"
```

## [Leiningen](#leiningen)

```
[network.ike/example-project "55"]
```
