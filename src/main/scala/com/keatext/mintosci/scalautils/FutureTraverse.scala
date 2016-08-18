package com.keatext.mintosci.scalautils

import scala.collection.GenTraversableLike
import scala.collection.generic.CanBuildFrom
import scala.collection.mutable.Builder
import scala.concurrent._

// Future.traverse runs the futures in parallel,
// these methods run them one after the other.
object FutureTraverse {

  def fromBlocking[A](
    fct: => A
  )(implicit
    ec: ExecutionContext
  )
  : Future[A] = {
    Future{blocking{fct}}
  }

  def traverse[A, B, Repr, That](
    objects: GenTraversableLike[A,Repr]
  )(
    f: A => Future[B]
  )(
    implicit cbf: CanBuildFrom[Repr, B, That],
    ec: ExecutionContext
  )
  : Future[That] =
    objects.foldLeft[Future[Builder[B,That]]](
      Future.successful(cbf())
    ) { (futureBuilder, a) =>
      for {
        builder <- futureBuilder
        elem <- f(a)
      } yield builder += elem
    }.map { builder =>
      builder.result()
    }

  def filter[A, Repr, That](
    objects: GenTraversableLike[A,Repr]
  )(
    f: A => Future[Boolean]
  )(
    implicit cbf: CanBuildFrom[Repr, A, That],
    ec: ExecutionContext
  )
  : Future[That] =
    objects.foldLeft[Future[Builder[A,That]]](
      Future.successful(cbf())
    ) { (futureBuilder, a) =>
      for {
        builder <- futureBuilder
        keep <- f(a)
      } yield if (keep) {
        builder += a
      } else {
        builder
      }
    }.map { builder =>
      builder.result()
    }
}

