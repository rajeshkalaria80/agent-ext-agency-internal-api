package com.evernym.extension.internal_api

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.server.{Directive0, Route}
import akka.stream.Materializer
import com.evernym.agent.api._
import akka.http.scaladsl.server.Directives._

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global


trait CorsSupport {

  def configProvider: ConfigProvider

  //this directive adds access control headers to normal responses
  private def addAccessControlHeaders(): Directive0 = {
    respondWithHeaders(
      //TODO: Insecure way of handling CORS, Consider securing it before moving to production
      `Access-Control-Allow-Origin`.*,
      `Access-Control-Allow-Credentials`(true),
      `Access-Control-Allow-Headers`("Origin", "Authorization", "Accept", "Content-Type")
    )
  }

  //this handles preFlight OPTIONS requests.
  private def preFlightRequestHandler: Route = options {
    complete(HttpResponse(StatusCodes.OK).
      withHeaders(`Access-Control-Allow-Methods`(OPTIONS, HEAD, POST, PUT, GET, DELETE)))
  }

  def corsHandler(r: Route): Route = addAccessControlHeaders() {
    preFlightRequestHandler ~ r
  }
}

class InternalAPITransportPlatform(commonParam: CommonParam) extends TransportPlatform with CorsSupport {

  override def configProvider: ConfigProvider = commonParam.configProvider
  implicit def system: ActorSystem = commonParam.actorSystem
  implicit def materializer: Materializer = commonParam.materializer

  override def start(inputParam: Option[Any]=None): Unit = {
    lazy val route: Route = logRequestResult("extension-agency-internal-api-transport-platform") {
      pathPrefix("agent") {
        pathPrefix("extension") {
          path("msg") {
            post {
              complete("successful-internal-api")
            }
          }
        }
      }
    }
    Http().bindAndHandle(corsHandler(route), "0.0.0.0", 7000)
  }

  override def stop(): Unit = {
    println("internal-api extension transport stopped")
  }

}

class AgencyInternalAPITransportPlatformExtension extends Extension {

  override val name: String = "extension-agency-internal-api-transport-platform"
  override val category: String = "transport-platform"

  var transportPlatform: TransportPlatform = _

  override def handleMsg: PartialFunction[Any, Future[Any]] = {
    case x =>
      Future.failed(throw new RuntimeException("this extension doesn't support any message"))
  }

  override def getSupportedMsgTypes: Set[MsgType] = Set.empty

  override def start(inputParam: Option[Any] = None): Unit = {
    val extParam = inputParam.asInstanceOf[Option[CommonParam]].
      getOrElse(throw new RuntimeException("unexpected input parameter"))
    transportPlatform = new InternalAPITransportPlatform(extParam)
    transportPlatform.start()
  }

  override def stop(): Unit = {
    transportPlatform.stop()
  }
}

