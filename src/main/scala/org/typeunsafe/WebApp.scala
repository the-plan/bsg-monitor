package org.typeunsafe
import io.vertx.core.json.JsonObject
import io.vertx.scala.core.Vertx
import io.vertx.scala.ext.web.Router
import io.vertx.scala.servicediscovery.types.HttpEndpoint
import io.vertx.scala.servicediscovery.{ServiceDiscovery, ServiceDiscoveryOptions}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}

package object WebApp {

  val vertx = Vertx.vertx()

  def discovery = {
    // Settings for the Redis backend
    val redisHost = sys.env.get("REDIS_HOST").getOrElse("127.0.0.1")
    val redisPort = sys.env.get("REDIS_PORT").getOrElse("6379").toInt
    val redisAuth = sys.env.get("REDIS_PASSWORD").getOrElse(null)
    val redisRecordsKey = sys.env.get("REDIS_RECORDS_KEY").getOrElse("scala-records")

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

    // Settings for record the service
    val serviceId = sys.env.get("SERVICE_ID").getOrElse("johndoe")
    val serviceHost = sys.env.get("SERVICE_HOST").getOrElse("localhost") // domain name
    val servicePort = sys.env.get("SERVICE_PORT").getOrElse("8080").toInt // set to 80 on Clever Cloud
    val serviceRoot = sys.env.get("SERVICE_ROOT").getOrElse("/api")

    // create the microservice record
    val record = HttpEndpoint.createRecord(
      serviceId,
      serviceHost,
      servicePort,
      serviceRoot
    )

    discovery.publishFuture(record).onComplete{
      case Success(result) => println(s"😃 publication OK")
      case Failure(cause) => println(s"😡 publication KO: $cause")
    }
    // discovery.close() // or not
  }

  def main(args: Array[String]): Unit = {
    val server = vertx.createHttpServer()
    val router = Router.router(vertx)

    // use redis backend to publish service informations
    discovery

    val httpPort = sys.env.get ("PORT").getOrElse("8080").toInt

    // my services
    router.get("/api/add/:a/:b").handler(context => {
      val res: Integer = context.request.getParam("a").get.toInt + context.request.getParam("b").get.toInt
      context
        .response()
        .putHeader("content-type", "application/json;charset=UTF-8")
        .end(new JsonObject().put("result", res).encodePrettily())
    })

    router.get("/api/multiply/:a/:b").handler(context => {
      val res: Integer = context.request.getParam("a").get.toInt * context.request.getParam("b").get.toInt
      context
        .response()
        .putHeader("content-type", "application/json;charset=UTF-8")
        .end(new JsonObject().put("result", res).encodePrettily())

    })

    // home page
    router.get("/").handler(context => {
      context
        .response()
        .putHeader("content-type", "text/html;charset=UTF-8")
        .end("<h1>Hello 🌍</h1>")
    })

    println(s"🌍 Listening on $httpPort  - Enjoy 😄")
    server.requestHandler(router.accept _).listen(httpPort)
  }
}
