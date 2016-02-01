package com.keatext.mintosci.scalautils.db

import slick.dbio.{DBIO, Effect, NoStream, DBIOAction}
import slick.driver.PostgresDriver.api._
import slick.lifted.TableQuery

import scala.concurrent.{Future, ExecutionContext}


case class InconsistentDatabaseException() extends IllegalStateException("Database requires migration")

trait DatabaseManager {
  // A variant of "TableQuery[_]" supporting implicit converters like "tableQueryToTableQueryExtensionMethods",
  // enabling methods like "table.schema".
  type SomeTableQuery = TableQuery[_ <: Table[_]]

  // Make sure to list dependencies first.
  val tables: List[SomeTableQuery]


  // methods returning DBIO

  def createTables(implicit ec: ExecutionContext): DBIOAction[Unit, NoStream, Effect.Schema] =
    DBIO.sequence(
      tables.map(_.schema.create)
    ).map(_ => ())

  def validationQuery(implicit ec: ExecutionContext): DBIOAction[DBValidity, NoStream, Effect.Read] = {
    val expectedDBSignature = DBSignature(
      tables.map { table =>
        table.baseTableRow.tableName -> DBValidator.signatureForTable(table.toNode)
      }.toMap
    )

    DBValidator.validationQuery(expectedDBSignature)
  }

  def dropTables(implicit ec: ExecutionContext): DBIOAction[Unit, NoStream, Effect.Schema] =
    DBIO.sequence(
      tables.map(_.schema.drop)
    ).map(_ => ())


  // methods returning Future

  def createTablesIfNeeded()(
    implicit executionContext: ExecutionContext,
    db: Database
  ): Future[Unit] =
    for {
      status <- db.run(validationQuery)
      result <- status match {
        case EmptyDB() =>
          for {
            () <- db.run(createTables)
            () = println("Created database tables")
          } yield ()
        case ValidDB() =>
          println("Validated database schema")
          Future.successful(())
        case DBRequiresMigration() =>
          Future.failed(InconsistentDatabaseException())
      }
    } yield result
}
