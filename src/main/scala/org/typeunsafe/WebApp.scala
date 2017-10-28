package org.typeunsafe

import java.util
import java.util.{ArrayList, List}

import io.vertx.core.{AbstractVerticle, Handler}
import io.vertx.core.json.{JsonArray, JsonObject}
import io.vertx.scala.core.Vertx
import io.vertx.scala.ext.web.Router
import io.vertx.scala.ext.web.RoutingContext
import io.vertx.scala.ext.web.client.WebClient
import io.vertx.scala.ext.web.handler.StaticHandler
import io.vertx.scala.ext.web.handler.sockjs.{BridgeOptions, PermittedOptions, SockJSHandler}
import io.vertx.scala.servicediscovery.types.HttpEndpoint
import io.vertx.scala.servicediscovery.{Record, ServiceDiscovery, ServiceDiscoveryOptions}
import io.vertx.servicediscovery.rest.ServiceDiscoveryRestEndpoint

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}
import scala.collection.JavaConverters._


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

    // Setings EventBus options
    val bridgeOptions = BridgeOptions().addOutboundPermitted(PermittedOptions().setAddress("raiders"))
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


    val sockJSHandler = SockJSHandler.create(vertx).bridge(bridgeOptions)
    router.route("/eventbus/*").handler(sockJSHandler)


    //router.get("/api/raiders").handler(context: Handler[RoutingContext] => {})

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


    ServiceDiscoveryRestEndpoint.create(router.asJava.asInstanceOf[io.vertx.ext.web.Router], discovery.asJava.asInstanceOf[io.vertx.servicediscovery.ServiceDiscovery])

    router.route("/*").handler(StaticHandler.create())

    println(s"🌍 Listening on $httpPort  - Enjoy 😄")
    server.requestHandler(router.accept _).listenFuture(httpPort)

    vertx.setPeriodic(updateInterval, _ => {
      discovery
        .getRecordsFuture(record => record.getMetadata.getString("kind").equals("raider"))
        .onComplete {
          case Success(results) => {
            //TODO here we can have a NPE
            vertx.eventBus().publish("raiders", new JsonArray(results.toList.asJava).encodePrettily())


          }
          case Failure(cause) => {
            //TODO Send error
          }
        }
    }) // Start timer for fetching raiders infos.
  }
}
