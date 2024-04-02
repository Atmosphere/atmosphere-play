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

import play.api.OptionalDevContext
import play.api.http.*
import play.api.inject.Injector
import play.api.mvc.*
import play.core.j.*
import play.mvc.Http
import play.mvc.Http.RequestBody

import javax.inject.{Inject, Provider}
import scala.concurrent.ExecutionContext.Implicits.global


class AtmosphereHttpRequestHandler@Inject()(  webCommands: _root_.play.core.WebCommands,
                                              optDevContext: _root_.play.api.OptionalDevContext,
                                              router: _root_.javax.inject.Provider[_root_.play.api.routing.Router],
                                              errorHandler: _root_.play.api.http.HttpErrorHandler,
                                              configuration: _root_.play.api.http.HttpConfiguration,
                                              filters: _root_.play.api.http.HttpFilters,
                                              handlerComponents: _root_.play.core.j.JavaHandlerComponents,
                                              injector: _root_.play.api.inject.Injector 
                                                                 ) extends JavaCompatibleHttpRequestHandler( webCommands,
                                                                                                             optDevContext,
                                                                                                             router,
                                                                                                             errorHandler,
                                                                                                             configuration,
                                                                                                             filters, 
                                                                                                             handlerComponents) {

  

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
    dispatch(request, controllerClass, upgradeHeader, connectionHeaders)
  }

  private def dispatch(request: RequestHeader,
                       controllerClass: Class[_ <: AtmosphereController],
                       upgradeHeader: Option[String],
                       connectionHeaders: Seq[String]): Option[Handler] = {
    if (AtmosphereCoordinator.instance.matchPath(request.path)) {
      //val controller: AtmosphereController = Option(components.instanceOf(controllerClass)).getOrElse(controllerClass.newInstance)
      val controller: AtmosphereController = Option(injector.instanceOf(controllerClass)).getOrElse(controllerClass.getDeclaredConstructor().newInstance())
      // Netty fail to decode headers separated by a ','
      val javaAction =
        if (isWsSupported(upgradeHeader, connectionHeaders))
          controller.webSocket(request.asJava)
        else
         // new JavaAction(components) {
          new JavaAction(handlerComponents) {
            val annotations = new JavaActionAnnotations(controllerClass, controllerClass.getMethod("http", classOf[Http.RequestHeader]), new ActionCompositionConfiguration())
            val parser = javaBodyParserToScala(injector.instanceOf(annotations.parser))
            def invocation(req : play.mvc.Http.Request) = controller.http(req)
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


