/*
 * Copyright 2018 Async-IO.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.atmosphere.play

import javax.inject.Inject
import play.api.http._
import play.api.inject.Injector
import play.api.mvc.{BodyParser, Handler, RequestHeader}
import play.api.routing.Router
import play.core.j._
import play.mvc.Http.RequestBody

import scala.concurrent.ExecutionContext.Implicits.global



class AtmosphereHttpRequestHandler @Inject()(components: JavaHandlerComponents,
                                             router: Router,
                                             errorHandler: HttpErrorHandler,
                                             configuration: HttpConfiguration,
                                             filters: HttpFilters,
                                             injector: Injector)

  extends JavaCompatibleHttpRequestHandler(router, errorHandler, configuration, filters, components) {

  override def routeRequest(request: RequestHeader) = {
    dispatch(request) match {
      case Some(handler) =>
        Option(handler)
      case None =>
        super.routeRequest(request)
    }
  }

  private def isWsSupported(upgradeHeader: Option[String], connectionHeaders: Seq[String]): Boolean =
    upgradeHeader.map("websocket".equalsIgnoreCase)
      .getOrElse(connectionHeaders.contains("upgrade".equalsIgnoreCase _))

  def dispatch(request: RequestHeader): Option[Handler] =
    dispatch(request, classOf[AtmosphereController])

  def dispatch(request: RequestHeader, controllerClass: Class[_ <: AtmosphereController]): Option[Handler] = {
    val upgradeHeader = request.headers.get("Upgrade")
    val connectionHeaders = Option(request.headers.get("Connection")).map(_.toSeq).getOrElse(Seq.empty)
    dispatch(request.path, controllerClass, upgradeHeader, connectionHeaders)
  }

  private def dispatch(requestPath: String,
                       controllerClass: Class[_ <: AtmosphereController],
                       upgradeHeader: Option[String],
                       connectionHeaders: Seq[String]): Option[Handler] = {
    if (AtmosphereCoordinator.instance.matchPath(requestPath)) {
      val controller: AtmosphereController = Option(injector.instanceOf(controllerClass)).getOrElse(controllerClass.newInstance)
      // Netty fail to decode headers separated by a ','
      val javaAction =
        if (isWsSupported(upgradeHeader, connectionHeaders))
          controller.webSocket
        else
          new JavaAction(components) {
            val annotations = new JavaActionAnnotations(controllerClass, controllerClass.getMethod("http"), new ActionCompositionConfiguration())
            val parser = javaBodyParserToScala(injector.instanceOf(annotations.parser))
            def invocation = controller.http
          }

      Some(javaAction)
    }
    else {
      None
    }
  }

  def javaBodyParserToScala(parser: play.mvc.BodyParser[_]): BodyParser[RequestBody] = BodyParser { request =>
    val accumulator = parser.apply(new play.core.j.RequestHeaderImpl(request)).asScala()
    accumulator.map { javaEither =>
      if (javaEither.left.isPresent) {
        Left(javaEither.left.get().asScala())
      } else {
        Right(new RequestBody(javaEither.right.get()))
      }
    }
  }

}