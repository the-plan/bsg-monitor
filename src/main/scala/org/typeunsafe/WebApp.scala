package org.typeunsafe

import io.vertx.core.json.{JsonArray, JsonObject}
import io.vertx.scala.core.Vertx
import io.vertx.scala.ext.web.Router
import io.vertx.scala.ext.web.handler.StaticHandler
import io.vertx.scala.servicediscovery.{ServiceDiscovery, ServiceDiscoveryOptions}
import io.vertx.servicediscovery.rest.ServiceDiscoveryRestEndpoint

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}


/*
class WebApp extends AbstractVerticle () {

}
*/


package object WebApp {

  val vertx: Vertx = Vertx.vertx()


  //TODO manage the on stop

  def main(args: Array[String]): Unit = {
    val server = vertx.createHttpServer()
    val router = Router.router(vertx)

    val updateInterval = 1000L

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


    val httpPort = sys.env.getOrElse("PORT", "8080").toInt

    router.route("/api/raiders").handler(context => {
      context.response().setChunked(true)
      context.response().putHeader("Content-Type", "text/event-stream")
      context.response().putHeader("Connection", "keep-alive")
      context.response().putHeader("Cache-Control", "no-cache")
      context.response().putHeader("Access-Control-Allow-Origin", "*")

      vertx.setPeriodic(updateInterval, _ => {
        discovery
          .getRecordsFuture(record => record.getMetadata.getString("kind").equals("raider"))
          .onComplete {
            case Success(results) => {
              //TODO here we can have a NPE
              val raidersToSend = new JsonArray(results.toList.asJava)
              context.response().write("data: " + raidersToSend + "\n\n")
            }
            case Failure(cause) => {
              //TODO Send error
            }
          }
      }) // Start timer for fetching raiders infos.
    })


    ServiceDiscoveryRestEndpoint.create(router.asJava.asInstanceOf[io.vertx.ext.web.Router], discovery.asJava.asInstanceOf[io.vertx.servicediscovery.ServiceDiscovery])

    router.route("/*").handler(StaticHandler.create())

    println(s"🌍 Listening on $httpPort  - Enjoy 😄")
    server.requestHandler(router.accept _).listenFuture(httpPort)
  }
}
