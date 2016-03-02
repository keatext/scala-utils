package com.keatext.mintosci.scalautils

import slick.dbio.DBIOAction
import slick.driver.PostgresDriver.api._

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}


// A variant of Future for critical sections.
// Unlike Future:
// - at most one TransactionalFuture will run at a time
// - the thread isn't launched until you call run
class TransactionalFuture[A](body: => A) {
  private def privateBody: A = body

  def run(implicit ec: ExecutionContext)
  : Future[A] =
    Future {
      TransactionalFuture.mutex.synchronized {
        body
      }
    }

  def map[B](f: A => B)
  : TransactionalFuture[B] =
    new TransactionalFuture[B](f(body))

  def flatMap[B](f: A => TransactionalFuture[B])
  : TransactionalFuture[B] =
    new TransactionalFuture[B](f(body).privateBody)
}

object TransactionalFuture {
  val mutex = new Object

  def apply[A](body: => A)
  : TransactionalFuture[A] =
    new TransactionalFuture(body)

  def successful[A](a: A)
  : TransactionalFuture[A] =
    new TransactionalFuture[A](a) {
      private def privateBody: A = a

      // a is a pure value, no side effects, no need to run in the critical section.
      override def run(implicit ec: ExecutionContext)
      : Future[A] =
        Future.successful(a)

      // f is expected to be a pure function, not a computation which needs to be
      // executed in the critical section.
      override def map[B](f: A => B)
      : TransactionalFuture[B] =
        TransactionalFuture.successful(f(a))

      // f is expected to be a pure function, not a computation which needs to be
      // executed in the critical section.
      override def flatMap[B](f: A => TransactionalFuture[B])
      : TransactionalFuture[B] =
        f(a)
    }

  def sequence[A](futures: Iterable[TransactionalFuture[A]])
  : TransactionalFuture[Seq[A]] =
    futures.foldLeft(
      TransactionalFuture.successful(Seq.empty[A])
    ) { (futureSeq, futureElement) =>
      for {
        seq     <- futureSeq
        element <- futureElement
      } yield seq :+ element
    }


  // "unsafe" because the types don't guarantee correct usage.
  //
  // example of incorrect usage:
  //   {
  //     val future = Future(...)
  //     TransactionalFuture.unsafeFromFuture(future)
  //   }
  //
  // example of correct usage:
  //   TransactionalFuture.unsafeFromFuture(
  //     Future(...)
  //   )
  //
  // The former usage is incorrect because the Future starts executing
  // as soon as it is defined, outside of the critical section.
  def unsafeFromFuture[A](body: => Future[A])
  : TransactionalFuture[A] =
    new TransactionalFuture(
      Await.result(body, Duration.Inf)
    )

  // a variant of db.run which runs in a critical section
  def dbRun[R,E <: Effect](action: DBIOAction[R,NoStream,E])(
    implicit db: Database
  )
  : TransactionalFuture[R] =
    unsafeFromFuture(db.run(action))
}
