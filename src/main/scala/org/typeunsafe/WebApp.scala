package org.typeunsafe
import java.util
import java.util.{ArrayList, List}

import io.vertx.core.json.{JsonArray, JsonObject}
import io.vertx.scala.core.Vertx
import io.vertx.scala.ext.web.Router
import io.vertx.scala.ext.web.client.WebClient
import io.vertx.scala.ext.web.handler.StaticHandler
import io.vertx.scala.servicediscovery.types.HttpEndpoint
import io.vertx.scala.servicediscovery.{Record, ServiceDiscovery, ServiceDiscoveryOptions}
import io.vertx.servicediscovery.rest.ServiceDiscoveryRestEndpoint

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}
import scala.collection.JavaConverters._

package object WebApp {

  val vertx: Vertx = Vertx.vertx()

  //TODO manage the on stop

  def main(args: Array[String]): Unit = {
    val server = vertx.createHttpServer()
    val router = Router.router(vertx)

    // Settings for the Redis backend
    val redisHost = sys.env.getOrElse("REDIS_HOST", "127.0.0.1")
    val redisPort = sys.env.getOrElse("REDIS_PORT", "6379").toInt
    val redisAuth = sys.env.getOrElse("REDIS_PASSWORD", null)
    val redisRecordsKey = sys.env.getOrElse("REDIS_RECORDS_KEY", "vert.x.ms")

    // Mount the service discovery backend (Redis)
    val discovery = ServiceDiscovery.create(vertx, ServiceDiscoveryOptions()
      .setBackendConfiguration(
        new JsonObject()
          .put("host", redisHost)
          .put("port", redisPort)
          .put("auth", redisAuth)
          .put("key", redisRecordsKey)
      )
    )


    // set and create a record
    val serviceName = sys.env.getOrElse("SERVICE_NAME", "monitor")
    val serviceHost = sys.env.getOrElse("SERVICE_HOST", "localhost") // domain name
    val servicePort = sys.env.getOrElse("SERVICE_PORT", "8080").toInt // set to 80 on Clever Cloud
    val serviceRoot = sys.env.getOrElse("SERVICE_ROOT", "/api")

    // create the microservice record
    val record = HttpEndpoint.createRecord(
      serviceName,
      serviceHost,
      servicePort,
      serviceRoot
    )

    //TODO: with the other projects when query on metadata if there
    record.setMetadata(new JsonObject()
        .put("kind", "monitor")
    )


    discovery.publishFuture(record).onComplete{
      case Success(result) => println(s"😃 publication OK")
      case Failure(cause) => println(s"😡 publication KO: $cause")
    }


    val httpPort = sys.env.getOrElse("PORT", "8080").toInt

    router.get("/api/raiders").handler(context => {

      discovery
        .getRecordsFuture(record => record.getMetadata.getString("kind").equals("raider"))
        .onComplete {
          case Success(results) => {
            //TODO here we can have a NPE
            context
              .response()
              .putHeader("content-type", "application/json;charset=UTF-8")
              .end(new JsonArray(results.toList.asJava).encodePrettily())

          }
          case Failure(cause) => {
            context
              .response()
              .putHeader("content-type", "application/json;charset=UTF-8")
              .end(new JsonObject().put("error", cause.getMessage).encodePrettily())
          }
        }
    })

    router.route("/*").handler(StaticHandler.create())

    println(s"🌍 Listening on $httpPort  - Enjoy 😄")
    server.requestHandler(router.accept _).listen(httpPort)
  }
}
