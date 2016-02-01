package com.keatext.mintosci.scalautils

import scala.concurrent.{ExecutionContext, Await, Future}
import scala.concurrent.duration.Duration
import scala.util.{Failure, Success, Try}


// a variant of Future which encourages immediate error handling instead of
// shoveling it forward to the top-level Await.result
class FutureTry[A](val future: Future[Try[A]]) {
  def map[B](f: Try[A] => Try[B])(
    implicit executionContext: ExecutionContext
  ): FutureTry[B] = new FutureTry(
    future.map(f).recover {
      // f has thrown an exception
      case err => Failure(err)
    }
  )
}

object FutureTry {
  def apply[A](future: Future[A])(
    implicit executionContext: ExecutionContext
  ): FutureTry[A] = new FutureTry(
    Future(
      Try {
        Await.result(future, Duration.Inf)
      }
    )
  )

  def successful[A](a: A)(
    implicit executionContext: ExecutionContext
  ): FutureTry[A] =
    FutureTry(Future.successful(a))

  // unlike Future.sequence, a single failure won't bring down the entire sequence
  def sequence[A](futureTries: Iterable[FutureTry[A]])(
    implicit executionContext: ExecutionContext
  ): Future[Iterable[Try[A]]] =
    Future.sequence(futureTries.map(_.future))

  // a variant of FutureTry.sequence which prints "." and "x" as each FutureTry completes
  // successfully or unsuccessfully
  def sequenceWithProgress[A](futureTries: Iterable[FutureTry[A]])(
    implicit executionContext: ExecutionContext
  ): Future[Iterable[Try[A]]] =
    for {
      r <- sequence(
        futureTries.map { futureTry =>
          futureTry.map {
            case Success(x) => print("."); Success(x)
            case Failure(x) => print("x"); Failure(x)
          }
        }
      )

      // add some spacing after the deluge of "." and "x" above
      () = println()
      () = println()
    } yield r
}
