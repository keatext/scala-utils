package com.keatext.mintosci.scalautils

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.client.RequestBuilding
import akka.http.scaladsl.marshalling.Marshaller
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.{Unmarshal, Unmarshaller}
import akka.stream.Materializer

import scala.concurrent.{ExecutionContext, Future}


trait HttpRequests {
  // extend HttpRequests and override this if you need to include extra headers with every request
  def addExtraHeaders(httpRequest: HttpRequest): HttpRequest =
    httpRequest

  // the Future fails if the request fails
  def requestAs[A](httpRequest: HttpRequest)(
    implicit unmarshaller: Unmarshaller[ResponseEntity,A],
    actorSystem: ActorSystem,
    materializer: Materializer,
    executionContext: ExecutionContext
  ): Future[A] =
    Http().singleRequest(addExtraHeaders(httpRequest)).flatMap {
      case HttpResponse(statusCode, _, responseEntity, _) if statusCode.isSuccess() =>
        Unmarshal(responseEntity).to[A]
      case HttpResponse(statusCode, _, responseEntity, _) =>
        Unmarshal(responseEntity).to[String].map(content =>
          throw new Exception(
            s"${statusCode.value} returned by ${httpRequest.method.name} ${httpRequest.getUri()} ${content}")
        )
    }

  def getAs[A](url: String)(
    implicit unmarshaller: Unmarshaller[ResponseEntity,A],
    actorSystem: ActorSystem,
    materializer: Materializer,
    executionContext: ExecutionContext
  ): Future[A] =
    requestAs[A](RequestBuilding.Get(url))

  def postAs[S, R](url: String, payload: S)(
    implicit unmarshaller: Unmarshaller[ResponseEntity, R],
    marshaller: Marshaller[S, RequestEntity],
    actorSystem: ActorSystem,
    materializer: Materializer,
    executionContext: ExecutionContext
  ): Future[R] =
    requestAs[R](RequestBuilding.Post(url, payload))

  def deleteAs[R](url: String)(
    implicit unmarshaller: Unmarshaller[ResponseEntity, R],
    actorSystem: ActorSystem,
    materializer: Materializer,
    executionContext: ExecutionContext
  ): Future[R] =
    requestAs[R](RequestBuilding.Delete(url))
}

object HttpRequests extends HttpRequests