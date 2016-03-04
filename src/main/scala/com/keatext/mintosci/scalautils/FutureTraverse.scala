package com.keatext.mintosci.scalautils

import scala.collection.GenTraversableLike
import scala.collection.generic.CanBuildFrom
import scala.collection.immutable.{Queue, Stack}
import scala.collection.mutable.Builder
import scala.concurrent.{ExecutionContext, Future}

/**
  * Implements a way to execute Future operations sequentially and wrap them in a global Future
  * (note that Future.sequence is __not__ sequential so we need something better)
  */
object FutureTraverse {
  def traverse[A, B, Repr, That](
    objects: GenTraversableLike[A,Repr]
  )(
    f: A => Future[B]
  )(
    implicit cbf: CanBuildFrom[Repr, B, That],
    ec: ExecutionContext
  )
  : Future[That] = {
    objects.foldLeft[Future[Queue[B]]](
      Future.successful(Queue())
    ) { (futureElems, a) =>
      for {
        elems <- futureElems
        elem <- f(a)
      } yield elems :+ elem
    }.map { queue =>
      val builder: Builder[B,That] = cbf()
      builder ++= queue
      builder.result()
    }
  }
}

