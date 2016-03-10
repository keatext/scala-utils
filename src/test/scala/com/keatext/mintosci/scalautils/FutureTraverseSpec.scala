package com.keatext.mintosci.scalautils

import org.scalatest.concurrent.PatienceConfiguration.Timeout
import org.scalatest.concurrent.ScalaFutures
import org.scalatest._

import scala.collection.mutable.Queue
import scala.concurrent.{Await, ExecutionContext, Future, ExecutionContextExecutor}
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.language.postfixOps


trait FutureTraverseSpec extends Suite {
}


class FrontendApiSpecs extends FlatSpec with Matchers with ScalaFutures {
  it should "traverse a List into a List, preserving the order" in {
    val future = FutureTraverse.traverse(List(1,2,3)) { x =>
      Future(x * x)
    }

    whenReady(future) {
      case (r : List[Int]) => assert(r == List(1,4,9))
      case _ => assert(false)
    }
  }

  it should "traverse a Map into a Map" in {
    val future = FutureTraverse.traverse(
      Map(
        1 -> "hello",
        2 -> "world",
        3 -> "!"
      )
    ) {
      case (k:Int,v:String) => Future((k,s"${v}!"))
    }

    whenReady(future) {
      case (r : Map[Int,String]) => assert(r == Map(
        1 -> "hello!",
        2 -> "world!",
        3 -> "!!"
      ))
      case _ => assert(false)
    }
  }

  it should "traverse a Set into a Set, collapsing elements" in {
    val future = FutureTraverse.traverse(Set(1,2,3)) { x => Future(x % 2) }

    whenReady(future) {
      case (r : Set[Int]) => assert(r == Set(0,1))
      case _ => assert(false)
    }
  }

  it should "traverse the futures sequentially" in {
    val queue = Queue[Int]()
    val future = FutureTraverse.traverse(List(3,1,2)) { x =>
      Future {
        Thread.sleep(x * 100)
        queue += x
        x
      }
    }

    whenReady(future, Timeout(1 second)) {
      case (r : List[Int]) =>
        assert(r == List(3,1,2))
        assert(queue == Queue(3,1,2))
      case _ => assert(false)
    }
  }


  it should "keep the odd elements" in {
    val future = FutureTraverse.filter(Set(1,2,3)) { x =>
      Future(x % 2 == 1)
    }

    whenReady(future) {
      case (r : Set[Int]) => assert(r == Set(1,3))
      case _ => assert(false)
    }
  }

  it should "keep the odd elements, preserving the order" in {
    val future = FutureTraverse.filter(List(1,2,3)) { x =>
      Future(x % 2 == 1)
    }

    whenReady(future) {
      case (r : List[Int]) => assert(r == List(1,3))
      case _ => assert(false)
    }
  }

  it should "filter the futures sequentially" in {
    val queue = Queue[Int]()
    val future = FutureTraverse.filter(List(3,1,2)) { x =>
      Future {
        Thread.sleep(x * 100)
        queue += x
        x % 2 == 1
      }
    }

    whenReady(future, Timeout(1 second)) {
      case (r : List[Int]) =>
        assert(r == List(3,1))
        assert(queue == Queue(3,1,2))
      case _ => assert(false)
    }
  }
}
