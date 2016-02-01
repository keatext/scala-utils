package com.keatext.mintosci.scalautils.db

import slick.ast.TypedCollectionTypeConstructor
import slick.dbio.Effect.{Read, Write}
import slick.dbio.{DBIOAction, NoStream}
import slick.driver.PostgresDriver
import slick.lifted.{Shape, FlatShapeLevel, Query}

import scala.collection.mutable
import scala.concurrent.ExecutionContext
import scala.language.implicitConversions
import scala.reflect._


// Slick queries always produce a sequence of results. Once the entire query is
// constructed, the first result can be extracted using query.result.headOption,
// but in my opinion this is too late: it is when we write query.take(1) that
// we impose a shape to the result.

// A value of type Query[E,U,Option] isn't such a good idea, because its flatMap
// could return more than one result but the Option Builder will throw an exception
// when the second value is added. So better use this wrapper instead.
//
// We represent a value of type Query[E,U,Option] using a value of type Query[E,U,Seq]
// (in case we need to flatMap into a Seq) and an implicit conversion to Query[E,U,Seq].
class QueryOption[+E,U](val query: Query[E,U,Seq]) {
  // 0 to 1 values are converted into 0 to n values. Result can be any number of values.
  def flatMap[F,T](f: (E) => Query[F,T,Seq]): Query[F,T,Seq] =
    query.flatMap(f)

  // 0 to 1 values are converted into 0 to 1 values. Result must still be 0 to 1 values.
  def flatMap[F,T](f: (E) => QueryOption[F,T]): QueryOption[F,T] = {
    val g = f.andThen(_.query)
    val mappedQuery = query.flatMap(g)
    new QueryOption(mappedQuery)
  }

  // 0 to 1 values are converted into 0 to 1 values. Result must still be 0 to 1 values.
  def map[F,G,T](f: E => F)(
    implicit shape: Shape[_ <: FlatShapeLevel,F,T,G]
  ): QueryOption[G,T] =
    new QueryOption(query.map(f))


  // Slick updates and deletions return the number of modified or deleted rows. On a query
  // which should only have 0 or 1 result, the intent is clearly to modify exactly 1 row,
  // but we often don't bother checking the resulting count, which could lead to silently
  // ignored bugs. This wrapper makes it really easy to add this check: simply call update1()
  // and delete1() instead of update() and delete().

  def update1(newValue: U)(
    implicit toUpdateActionExtensionMethods: Query[E,U,Seq] => PostgresDriver.UpdateActionExtensionMethods[U],
    executionContext: ExecutionContext
  ): DBIOAction[Unit,NoStream,Write] =
    query
    .update(newValue)
    .map { nbModifiedRows => assert(nbModifiedRows == 1) }

  def delete1()(
    implicit toDeleteActionExtensionMethods: Query[E,U,Seq] => PostgresDriver.DeleteActionExtensionMethods,
    executionContext: ExecutionContext
  ): DBIOAction[Unit,NoStream,Write] =
    query
      .delete
      .map { nbDeletedRows => assert(nbDeletedRows == 1) }


  // help type inference figure out how to call common Query methods

  def result(
    implicit toStreamingQueryActionExtensionMethods: Query[E,U,Option] => PostgresDriver.StreamingQueryActionExtensionMethods[Option[U],U],
    executionContext: ExecutionContext
  ): PostgresDriver.StreamingDriverAction[Option[U],U,Read] = {
    QueryOption.toRealQueryOption(this).result
  }
}

object QueryOption {
  // make sure the input query really does return at most one result,
  // or the output query will throw an exception when executed.
  def fromQueryWithAtMostOneResult[E,U](query: Query[E,U,Seq]): QueryOption[E,U] =
    new QueryOption(query)

  // Required for converting to a Query[_,_,Option].
  def option: TypedCollectionTypeConstructor[Option] =
    new TypedCollectionTypeConstructor[Option](classTag[Option[_]]) {
      override def createBuilder[E](implicit classTag: ClassTag[E]): mutable.Builder[E, Option[E]] =
        new mutable.Builder[E, Option[E]] {
          var result: Option[E] = None

          def +=(newElem: E): this.type = {
            result match {
              case None => result = Some(newElem)
              case Some(previousElem) => throw new IllegalStateException(
                s"trying to fit two (or more) elements in an Option: first ${previousElem}, then ${newElem}}"
              )
            }

            this
          }

          def clear(): Unit = {
            result = None
          }
        }

      override def isSequential: Boolean = false
      override def isUnique: Boolean = false
    }


  implicit def toQueryOptionOps[E,U](query: Query[E,U,Seq]): QueryOptionOps[E,U] =
    QueryOptionOps[E,U](query)

  implicit def toRealQueryOption[E,U](queryOption: QueryOption[E,U]): Query[E,U,Option] =
    queryOption.query.take(1).to(option)
}


case class QueryOptionOps[E,U](query: Query[E,U,Seq]) {
  // a variant of take(1) which returns a QueryOption instead of a Query[E,U,Seq].
  // the take(1) is delayed until toRealQueryOption, because calling take(1).update1(...) causes a runtime exception.
  def take1: QueryOption[E,U] =
    new QueryOption(query)
}
