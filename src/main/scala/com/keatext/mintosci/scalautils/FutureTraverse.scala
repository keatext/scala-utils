package com.keatext.mintosci.scalautils

import scala.concurrent.{ExecutionContext, Future}

/**
  * Implements a way to execute Future operations sequentially and wrap them in a global Future
  * (note that Future.sequence is __not__ sequential so we need something better)
  */
object FutureTraverse {
  def traverse[A, B]
    (objects: Iterable[A])(f: A => Future[B])
    (implicit ec: ExecutionContext)
  : Future[List[B]] = {
    objects match {
      case (head :: tail) =>
        for {
          res <- f(head)
          restail <- traverse(tail)(f)
        } yield res :: restail
      case Nil => Future.successful(List())
    }
  }
}
