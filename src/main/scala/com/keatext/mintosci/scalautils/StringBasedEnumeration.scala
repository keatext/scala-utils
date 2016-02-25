package com.keatext.mintosci.scalautils

import spray.json._


trait StringBasedEnumeration extends Enumeration {
  def valueOf(name: String): Option[Value] = values.find(_.toString == name)

  implicit val jsonFormat = new RootJsonFormat[Value] {
    def write(value: Value) = JsString(value.toString)

    def read(value: JsValue): Value = value match {
      case JsString(s) => valueOf(s) match {
        case Some(x) => x
        case None => deserializationError(s"Expected one of ${values}, but got ${s}")
      }
      case x => deserializationError(s"Expected JsString, but got ${x}")
    }
  }
}
