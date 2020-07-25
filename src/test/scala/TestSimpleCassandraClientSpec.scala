package com.eztier.datasource
package infrastructure.cassandra
package test

import java.time.{Instant, LocalDateTime, ZoneOffset}
import java.time.temporal.TemporalUnit
import java.util.{Date, UUID}

import cats.data._
import cats.implicits._
import cats.effect.{IO, Sync}
import com.datastax.driver.core.utils.UUIDs
import fs2.{Chunk, Pipe, Stream}
import com.datastax.driver.core.{ResultSet, Row, Session, SimpleStatement, Statement}
import org.specs2.mutable.Specification
import com.eztier.datasource.infrastructure.cassandra.{CassandraClient, CassandraSession}

case class CaResourceModified
(
  Environment: String,
  Store: String,
  Type: String,
  StartTime: Date,
  Id: String,
  Oid: String,
  Uid: UUID,
  Current: String
)

case class CaResourceProcessed
(
  Environment: String,
  Store: String,
  Type: String,
  Purpose: String,
  StartTime: Date,
  Id: String,
  Oid: String,
  Uid: UUID,
  Current: String
)

class TestSimpleCassandraClientSpec extends Specification {
  val ec = scala.concurrent.ExecutionContext.global
  implicit val timer = IO.timer(ec)
  implicit val cs = IO.contextShift(ec)

  val environment = "development"
  val store = "IKEA"
  val type_ = "Sales"
  val purpose = "forecast"

  "Simple Cassandra Client" should {

    "Create a table" in {

      val res = createSimpleCassandraClientResource[IO].use {
        case db =>

          val u = db.createAsync[CaResourceModified]("dwh")("Environment", "Store", "Type")("Uid", "StartTime")(("Uid" -> None), ("StartTime" -> None))

          val r = u.unsafeRunSync()

          IO.pure(r)
      }.unsafeRunSync()

      res.isInstanceOf[ResultSet]
    }

    "Create another table" in {

      val res = createSimpleCassandraClientResource[IO].use {
        case db =>

          val u = db.createAsync[CaResourceProcessed]("dwh")("Environment", "Store", "Type", "Purpose")("Uid", "StartTime")(("Uid" -> -1.some), ("StartTime" -> -1.some))

          val r = u.unsafeRunSync()

          IO.pure(r)
      }.unsafeRunSync()

      res.isInstanceOf[ResultSet]
    }

    "Insert a row" in {
      val res = createSimpleCassandraClientResource[IO].use {
        case db =>

          val a = CaResourceModified(
            Environment = environment,
            Store = store,
            Type = type_,
            StartTime = Date.from(Instant.now),
            Id = "ABC123",
            Oid = "5dd81233ae6d16d797be3915e52ac94b",
            Uid = UUIDs.timeBased(),
            Current = <root><inserted><row>
              <oid>5dd81233ae6d16d797be3915e52ac94b</oid>
              <type>Sales</type>
              <ID>ABC123</ID>
              <dateModified>1595638722248</dateModified>
              </row></inserted></root>.toString()
          )

          val u = db.batchInsertAsync(Chunk.vector(Vector(a)), "dwh", "ca_resource_modified")

          val r = u.unsafeRunSync()

          IO.pure(r)
      }.unsafeRunSync()

      res.isInstanceOf[ResultSet]
    }

    /*
      https://docs.datastax.com/en/cql-oss/3.3/cql/cql_reference/timeuuid_functions_r.html
      Conversion:
        toTimestamp(uid)
      Range:
        WHERE uid > maxTimeuuid('2013-01-01 00:05+0000') AND uid < minTimeuuid('2013-02-02 10:00+0000')
    */
    "Read a table" in {
      val res = createSimpleCassandraClientResource[IO].use {
        case db =>
          val u = db.readAsync(
            s"""select uid from dwh.ca_resource_processed
               | where environment = '${environment}'
               | and store = '${store}'
               | and type = '${type_}'
               | and purpose = '${purpose}' limit 1""".stripMargin)
            .flatMap { a =>
              val uid = a.getUUID("uid")

              val r = uid match {
                case a if a == null =>
                  val ts = LocalDateTime.now().minusDays(2).toInstant(ZoneOffset.UTC).toEpochMilli()
                  UUIDs.endOf(ts)
                case _ => uid
              }
              Stream.emit(r).covary[IO]
            }

          val r = u.compile.toList.unsafeRunSync()
          IO.pure(r)

          // IO.pure(u.compile.toList.unsafeRunSync())
      }.unsafeRunSync()

      res.isInstanceOf[List[UUID]]
    }

    "Read another table" in {
      val res = createSimpleCassandraClientResource[IO].use {
        case db =>
          val ts = LocalDateTime.now().minusDays(2).toInstant(ZoneOffset.UTC).toEpochMilli()
          val r = UUIDs.endOf(ts)

          val nextStmt = s"""select * from dwh.ca_resource_modified
                            | where environment = '${environment}'
                            | and store = '${store}'
                            | and type = '${type_}'
                            | and uid > ${r.toString}
                            | limit 10""".stripMargin

          println(nextStmt)

          val u = db.readAsync(nextStmt)
          IO.suspend(u.compile.toList)
      }.unsafeRunSync()

      res.isInstanceOf[List[Row]]
    }

  }

}
