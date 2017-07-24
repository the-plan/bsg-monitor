name := "bsg-monitor"

version := "1.0"

scalaVersion := "2.12.2"

libraryDependencies += "io.vertx" %% "vertx-web-scala" % "3.4.2"
libraryDependencies += "io.vertx" %% "vertx-web-client-scala" % "3.4.2"
libraryDependencies += "io.vertx" %% "vertx-service-discovery-scala" % "3.4.2"
libraryDependencies += "io.vertx" %% "vertx-service-discovery-backend-redis-scala" % "3.4.2"

packageArchetype.java_application

// https://www.clever-cloud.com/doc/scala/scala/
// there is some changes