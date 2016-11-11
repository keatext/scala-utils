package com.keatext.mintosci.scalautils

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.client.RequestBuilding
import akka.http.scaladsl.marshalling.Marshaller
import akka.http.scaladsl.model.StatusCodes.ClientError
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.unmarshalling.{Unmarshal, Unmarshaller}
import akka.stream.Materializer

import scala.concurrent.{ExecutionContext, Future}


trait HttpRequests {
  // extend HttpRequests and override this if you need to include extra headers with every request
  def addExtraHeaders(httpRequest: HttpRequest)
  : Future[HttpRequest] =
    Future.successful(httpRequest)

  // the Future fails if the request fails
  def requestAs[A](httpRequest: HttpRequest)(
    implicit unmarshaller: Unmarshaller[ResponseEntity,A],
    actorSystem: ActorSystem,
    materializer: Materializer,
    executionContext: ExecutionContext
  ): Future[A] =
    addExtraHeaders(httpRequest)
      .flatMap { extendedRequest =>
        Http().singleRequest(extendedRequest)
      }
      .flatMap {
        case HttpResponse(statusCode, _, responseEntity, _) if statusCode.isSuccess() =>
          Unmarshal(responseEntity.withoutSizeLimit()).to[A]
        case HttpResponse(ClientError(429), headers, _, _) =>
          headers.collectFirst {
            case HttpHeader("retry-after", seconds) => seconds.toInt
          } match {
            case Some(secondsToWait) =>
              for {
                () <- Future {
                  Thread.sleep(1000 * secondsToWait)
                }

                // try again
                r <- requestAs[A](httpRequest)
              } yield r
            case None => throw new Exception("429 status with no Retry-After header")
          }
        case HttpResponse(statusCode, _, responseEntity, _) =>
          Unmarshal(responseEntity.withoutSizeLimit()).to[String].map(content =>
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

  def postAs[R](url: String)(
    implicit unmarshaller: Unmarshaller[ResponseEntity, R],
    actorSystem: ActorSystem,
    materializer: Materializer,
    executionContext: ExecutionContext
  ): Future[R] =
    requestAs[R](RequestBuilding.Post(url))

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
