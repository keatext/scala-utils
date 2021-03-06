package com.keatext.mintosci.scalautils.db

import slick.driver.PostgresDriver
import slick.ast.ColumnOption.AutoInc
import slick.ast._
import slick.dbio.{DBIO, DBIOAction, Effect, NoStream}
import slick.jdbc.meta.{MColumn, MTable}

import scala.concurrent.ExecutionContext


case class ColumnSignature(nullable: Boolean, typeName: String, isAutoInc: Boolean)
case class TableSignature(columns: Map[String,ColumnSignature])
case class DBSignature(tables: Map[String,TableSignature])

sealed trait DBValidity
case class ValidDB() extends DBValidity
case class EmptyDB() extends DBValidity
case class DBRequiresMigration() extends DBValidity

object DBValidator {
  def validationQuery(
    expectedDBSignature: DBSignature
  )(
    implicit ec: ExecutionContext
  ): DBIOAction[DBValidity, NoStream, Effect.Read] = {
    val requiredTableNames = expectedDBSignature.tables.keySet
    querySignatureForDB(requiredTableNames).map { dbSignature =>
      validate(dbSignature, expectedDBSignature)
    }
  }

  private def validate(dBSignature: DBSignature, expectedDBSignature: DBSignature): DBValidity =
    if (dBSignature.tables.isEmpty)
      EmptyDB()
    else if (isCompatibleWith(dBSignature, expectedDBSignature))
      ValidDB()
    else
      DBRequiresMigration()


  private def isCompatibleWith(actual: DBSignature, expected: DBSignature): Boolean =
    expected.tables.forall {
      case (tableName, expectedTableSignature) =>
        isCompatibleWith(actual.tables.get(tableName), Some(expectedTableSignature))
    }

  private def isCompatibleWith(optionActual: Option[TableSignature], optionExpected: Option[TableSignature]): Boolean =
    (optionActual, optionExpected) match {
      case (None, None) => true
      case (Some(actual), Some(expected)) => isCompatibleWith(actual, expected)
      case _ => false
    }

  private def isCompatibleWith(actual: TableSignature, expected: TableSignature): Boolean =
    expected.columns.forall {
      case (columnName, expectedColumnSignature) =>
        val isContained = actual.columns.get(columnName).contains(expectedColumnSignature)
        if (!isContained) {
          println(s"$columnName is not matching signature of ")
          println(expectedColumnSignature)
          println("as it was ")
          println(actual.columns.get(columnName))
        }
        isContained
    }


  private def sqlType(column: FieldSymbol): String = {
    val columnBuilder = new PostgresDriver.ColumnDDLBuilder(column)

    val sb = StringBuilder.newBuilder
    columnBuilder.appendType(sb)
    sb.mkString
  }

  // If we start using fancier types like "inet" or "polygon", we'll probably have to extend this list otherwise we'll
  // have spurious DBRequiresMigration results.
  //
  // See http://www.postgresql.org/docs/9.4/static/datatype.html for a full list of all postgres types.
  private def normalizeSqlType(sqlType: String): String =
    sqlType.toUpperCase match {
      case "_INT2" => "INT2 ARRAY"
      case "_INT4" => "INT4 ARRAY"
      case "_INT8" => "INT8 ARRAY"
      case "_TEXT" => "TEXT ARRAY"
      case "BOOL" => "BOOLEAN"
      case "INT4" => "INTEGER"
      case "INT8" => "BIGINT"
      case "SERIAL" => "INTEGER"
      case "BIGSERIAL" => "BIGINT"
      case other => other
    }


  // Obtaining the signatures for the existing tables, aka MTables.

  private def signatureForColumn(mColumn: MColumn): ColumnSignature = {
    // I don't understand under which circumstance nullable and isAutoInc could be None.
    // In practice, they never are.
    val nullable = mColumn.nullable.get
    val typeName = normalizeSqlType(mColumn.typeName)
    val isAutoInc = mColumn.isAutoInc.get

    ColumnSignature(nullable, typeName, isAutoInc)
  }

  private def querySignatureForTable(
    mTable: MTable
  )(
    implicit ec: ExecutionContext
  ): DBIOAction[TableSignature, NoStream, Effect.Read] =
    for {
      columns <- mTable.getColumns
    } yield {
      val columnSignatures: Map[String,ColumnSignature] =
        columns.map { column =>
          column.name -> signatureForColumn(column)
        }.toMap

      TableSignature(columnSignatures)
    }

  private def queryRequiredTables(
    requiredTableNames: Set[String]
  )(
    implicit ec: ExecutionContext
  ): DBIOAction[Vector[MTable], NoStream, Effect.Read] =
    // postgres creates a lot of helper tables in addition to those we create ourselves, so MTable.getTables returns
    // a *lot* of junk. This simple after-the-fact filtering saves more than 6 seconds of startup time!
    for {
      tables <- MTable.getTables
    } yield tables.filter { table =>
      requiredTableNames.contains(table.name.name)
    }

  private def querySignatureForDB(
    requiredTableNames: Set[String]
  )(
    implicit ec: ExecutionContext
  ): DBIOAction[DBSignature, NoStream, Effect.Read] =
    for {
      tables <- queryRequiredTables(requiredTableNames)
      signatures <- DBIO.sequence(
        tables.map { table =>
          querySignatureForTable(table)
        }
      )
    } yield {
      val tableSignatures: Map[String,TableSignature] =
        (tables zip signatures).map { case (table, signature) =>
          table.name.name -> signature
        }.toMap

      DBSignature(tableSignatures)
    }


  // Obtaining the signatures for the tables we need, aka Tables.
  //
  // Slick does not provide an easy way to enumerate all the columns from a Table, so we have to traverse its AST nodes
  // and perform a lot of type coercions.

  // for debugging
  private def dumpAST(node: Node, indent: String = ""): Unit = {
    println(indent + node.toString + " : " + node.getClass.toString)
    node.nodeChildren.foreach {
      dumpAST(_, indent + "  ")
    }
  }

  private def signatureForFieldSymbol(column: FieldSymbol): ColumnSignature = {
    val nullable = column.tpe.isInstanceOf[OptionTypedType[_]]
    val typeName = normalizeSqlType(sqlType(column))
    val isAutoInc = column.options.contains(AutoInc)

    ColumnSignature(nullable, typeName, isAutoInc)
  }

  private def signatureForElement(node: Node): Seq[(String, ColumnSignature)] =
    node match {
      // def * : ProvenShape[(Int,Int)] = (columnX, columnY)
      case ProductNode(elements) =>
        elements.flatMap(signatureForElement)

      // def * : ProvenShape[Point] = (columnX, columnY) <> (Point.tupled, Point.unapply)
      case TypeMapping(lhs, _, _) =>
        signatureForElement(lhs)

      // def * : ProvenShape[Int] = columnX
      case Select(_, fieldSymbol: FieldSymbol) =>
        Seq(fieldSymbol.name -> signatureForFieldSymbol(fieldSymbol))

      // def * : ProvenShape[Long] = id.?
      case OptionApply(element) =>
        signatureForElement(element)

      case _ => sys.error(s"$node is not a column node")
    }

  // The node can be obtained via an expression of the form `TableQuery[Users].toNode`
  def signatureForTable(node: Node): TableSignature =
    node match {
      case TableExpansion(_, _, columns) =>
        TableSignature(signatureForElement(columns).toMap)

      case _ => sys.error(s"$node is not a table node")
    }
}
