package com.keatext.mintosci.scalautils

import slick.driver.PostgresDriver.api._
import spray.json.{JsValue, JsonFormat}

import scala.language.implicitConversions
import scala.reflect.ClassTag


// For type safe case classes wrapping a dumb value such as a String.
// The goal is to tag different types of strings to prevent accidentally using one type of
// String when a different type of String is expected.
//
// Example usage:
//
//     case class Foo(
//       value: String
//     ) extends TypedValue[String]
//
trait TypedValue[T] {
  val value: T

  // There are two cases in which string interpolation syntax is likely to be used with TypedValues:
  //   - s"${itemId} not found"
  //   - s"/items/${itemId}/create"
  // While for the first case we prefer "ItemId(1234) not found" to "1234 not found", so we can see at a glance
  // what kind of thing was not found, the second case is more important, and we prefer "/items/1234/create", so
  // we override toString() to get that behavior.
  override def toString: String =
    value.toString

  // For those cases when we do prefer "ItemId(1234)".
  def show: String =
    s"${this.getClass.getSimpleName}(${value.toString})"
}

object TypedValue {
  // A helper to define the implicit JsonFormat for TypedValue subclasses.
  // Since the outside world doesn't know about our more precise type distinction,
  // we format them the same way as the underlying value.
  //
  // Example usage:
  //
  //     object Foo {
  //       object JsonProtocol {
  //         implicit val fooJsonFormat = mkJsonFormat[Foo,String](Foo.apply)
  //       }
  //     }
  def mkJsonFormat[A <: TypedValue[T], T](
    f: T => A
  )(
    implicit jsonFormat: JsonFormat[T]
  ): JsonFormat[A] = new JsonFormat[A] {
    override def read(json: JsValue): A =
      f(jsonFormat.read(json))

    override def write(typedValue: A): JsValue =
      jsonFormat.write(typedValue.value)
  }

  // A helper to define the implicit BaseColumnType for TypedValue subclasses.
  // Since the database doesn't know about our more precise type distinction,
  // we format them the same way as the underlying value.
  implicit def mkColumnType[A <: TypedValue[T], T](
    f: T => A
  )(implicit classTag: ClassTag[A], baseColumnType: BaseColumnType[T]): BaseColumnType[A] =
    MappedColumnType.base[A, T](_.value, f)
}
