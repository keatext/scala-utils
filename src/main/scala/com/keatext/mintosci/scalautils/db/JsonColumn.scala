package com.keatext.mintosci.scalautils.db

import slick.driver.PostgresDriver.api._
import spray.json._

import scala.reflect.ClassTag


object JsonColumn {
  def readAsJson[A](jsonFormat: JsonFormat[A])(string: String): A =
    jsonFormat.read(string.parseJson)

  def writeAsJson[A](jsonFormat: JsonFormat[A])(a: A): String =
    jsonFormat.write(a).compactPrint

  def apply[A : ClassTag](jsonFormat: JsonFormat[A]): BaseColumnType[A] =
    MappedColumnType.base[A, String](writeAsJson(jsonFormat), readAsJson(jsonFormat))
}
