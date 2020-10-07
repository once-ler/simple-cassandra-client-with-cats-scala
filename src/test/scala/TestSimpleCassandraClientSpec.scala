package com.eztier.datasource
package infrastructure.cassandra
package test

import java.time.{Instant, LocalDateTime, ZoneOffset}
import java.util.{Date, UUID}

import cats.implicits._
import cats.effect.{IO, Sync, Async, ConcurrentEffect, ContextShift, Resource, Timer}
import com.datastax.driver.core.utils.UUIDs
import fs2.{Chunk, Pipe, Stream}
import com.datastax.driver.core.{ResultSet, Row}
import com.weather.scalacass.syntax._
import com.eztier.common.mergeSyntax._
import org.specs2.mutable.Specification
import scala.concurrent.duration._

case class CaResourceModified
(
  Environment: String,
  Store: String,
  Type: String,
  Start_Time: Date,
  Id: String,
  Oid: String,
  Uid: UUID,
  Current: String
)

case class CaResourceProcessed
(
  Environment: String = "",
  Store: String = "",
  Type: String = "",
  Purpose: String = "",
  Start_Time: Date = Date.from(Instant.now),
  Id: String = "",
  Oid: String = "",
  Uid: UUID = UUIDs.timeBased(),
  Current: String = ""
)

class CaResourcePurposeHandler[F[_]]
(
  val dataType: String,
  val purpose: String,
  val handler: CaResourceProcessed => Stream[F, CaResourceProcessed]
)

object CaResourcePurposeHandler {
  def apply[F[_]](
    dataType: String = "",
    purpose: String = "",
    handler: CaResourceProcessed => Stream[F, CaResourceProcessed] = (s: CaResourceProcessed) => Stream.emit(s).covary[F]
  ): CaResourcePurposeHandler[F] = new CaResourcePurposeHandler(dataType, purpose, handler)
}

// class CaResourceManager[F[_]: Sync] extends WithCommon {
class CaResourceManager[F[_]: Sync](db: CassandraClient[F], val keyspace: String, val environment: String, val store: String) extends WithCommon {

  private val caResourceProcessedTable = "ca_resource_processed"

  // def fetchNextTasks(db: CassandraClient[F], keyspace: String, environment: String, store: String, dataType: String, purpose: String)(func: CaResourceProcessed => CaResourceProcessed): Stream[F, ResultSet] = {
  def fetchNextTasks(dataType: String, purpose: String)(func: CaResourceProcessed => Stream[F, CaResourceProcessed]): Stream[F, ResultSet] = {
    val ts = LocalDateTime.now().minusDays(2).toInstant(ZoneOffset.UTC).toEpochMilli()
    val defaultUuid = UUIDs.endOf(ts)

    val fa: F[List[UUID]] = (db.readAsync(
      s"""select uid from $keyspace.ca_resource_processed
         | where environment = '$environment'
         | and store = '$store'
         | and type = '$dataType'
         | and purpose = '$purpose' limit 1""".stripMargin
    ).map(_.getUUID("uid")) ++ Stream.emit(defaultUuid).covary[F]).compile.toList

    val fb: F[List[CaResourceProcessed]] = Stream
      .eval(fa)
      .map(_.head)
      .flatMap { a =>
        val nextStmt =
          s"""select * from $keyspace.ca_resource_modified
             | where environment = '$environment'
             | and store = '$store'
             | and type = '$dataType'
             | and uid > ${a.toString}
             | limit 20""".stripMargin

        db.readAsync(nextStmt)
      }
      .map (_.as[CaResourceModified])
      .map { a =>
        val b = CaResourceProcessed()
        (b merge a).copy(Purpose = purpose)
      }
      .compile.toList

    Stream.eval(fb).flatMap[F, ResultSet] { l =>
      // Make sure each id is the latest.
      val m = l.groupBy(_.Id)
        .mapValues(_.sortBy(_.Uid.timestamp()).reverse.head)
        .values
        .toList
        .sortBy(_.Uid.timestamp())

      Stream.emits(m)
        .covary[F]
        .flatMap[F, CaResourceProcessed](func)
        .chunkN(10)
        .flatMap[F, ResultSet] { a =>
        Stream.eval(db.batchInsertAsync(a, keyspace, caResourceProcessedTable))
      }
    }

  }

}

object CaResourceManager {
  // def apply[F[_]: Sync]: CaResourceManager[F] = new CaResourceManager[F]()
  def apply[F[_]: Sync](db: CassandraClient[F], keyspace: String, environment: String, store: String): CaResourceManager[F] = new CaResourceManager[F](db, keyspace, environment, store)
}

object domain {
  private val keyspace = "dwh"
  private val environment = "development"
  private val store = "IKEA"

  final case class AppConfig
  (
    keyspace: String = keyspace,
    environment: String = environment,
    store: String = store
  )

  def createCaResourceManagerResource[F[_] : Async : ContextShift : ConcurrentEffect : Timer](): Resource[F, CaResourceManager[F]] =
    for {
      conf <- Resource.liftF(Sync[F].delay(AppConfig()))
      db <- createSimpleCassandraClientResource[F]
      ca = CaResourceManager[F](db, conf.keyspace, conf.environment, conf.store)
    } yield ca
}

class TestSimpleCassandraClientSpec extends Specification {
  val ec = scala.concurrent.ExecutionContext.global
  implicit val timer = IO.timer(ec)
  implicit val cs = IO.contextShift(ec)

  val keyspace = "dwh"
  val environment = "development"
  val store = "IKEA"
  val type_ = "Sales"
  val purpose = "forecast"

  private def pause[F[_]: Timer](d: FiniteDuration) = Stream.emit(1).covary[F].delayBy(d)

  private def repeat(io : IO[Unit]): IO[Nothing] =
    IO.suspend(
      io
        *> IO.delay(pause[IO](2 seconds).compile.drain.unsafeRunSync())
        *> repeat(io)
    )

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
            Start_Time = Date.from(Instant.now),
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
          val ts = LocalDateTime.now().minusDays(2).toInstant(ZoneOffset.UTC).toEpochMilli()
          val defaultUuid = UUIDs.endOf(ts)

          val u =  Stream.eval((db.readAsync(
            s"""select uid from $keyspace.ca_resource_processed
               | where environment = '$environment'
               | and store = '$store'
               | and type = '$type_'
               | and purpose = '$purpose' limit 1""".stripMargin
            ).map(_.getUUID("uid")) ++ Stream.emit(defaultUuid).covary[IO])
            .compile.toList)
            .map(_.head)

          IO.suspend(u.compile.toList)
      }.unsafeRunSync()

      res.isInstanceOf[List[AnyRef]]
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

      // Conversion to a type.
      val res2 = res.map { row =>
        row.as[CaResourceModified]
      }

      res.isInstanceOf[List[Row]]
    }

    "Read multiple tables" in {
      import domain._

      val res = createCaResourceManagerResource[IO].use { caResourceManager =>
        val u2 = caResourceManager.fetchNextTasks(type_, purpose) { a =>
          println(a)
          Stream.emit(a)
        }
        IO.suspend(u2.compile.toList)
      }.unsafeRunSync()

      res.isInstanceOf[List[ResultSet]]
/*
      val res = createSimpleCassandraClientResource[IO].use {
        case db =>

          val u2 = CaResourceManager[IO].fetchNextTasks(db, keyspace, environment, store, type_, purpose) { a =>
            println(a)
            a
          }

          IO.suspend(u2.compile.toList)
      }.unsafeRunSync()

      res.isInstanceOf[List[ResultSet]]
*/
    }

    "Test repeated reads" in {
      import domain._

      val purposeHandlers = List(
        CaResourcePurposeHandler[IO](
          dataType = type_,
          purpose = purpose,
          handler = t => Stream.emit(t)
        )
      )

      createCaResourceManagerResource[IO].use { caResourceManager =>
        val io2: Stream[IO, Stream[IO, Int]] = Stream.emits(purposeHandlers)
          .map { a =>
            println(Instant.now())
            val s: Stream[IO, Int] = caResourceManager.fetchNextTasks(a.dataType, a.purpose)(a.handler).evalMap[IO, Int](_ => IO(0))
            s.flatMap { i =>
              println(i)
              Stream.emit(i)
            }
          }
        // Concurrently run all.
        val io = io2.parJoin(4).compile.drain

        repeat(io).unsafeRunSync()

      }.unsafeRunSync()

      1 mustEqual 1
    }

  }

}
