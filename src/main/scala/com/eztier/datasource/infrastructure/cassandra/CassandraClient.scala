package com.eztier.datasource
package infrastructure.cassandra

import cats.implicits._
import cats.Functor
import cats.effect.{Async, Concurrent, Sync}
import fs2.{Chunk, Stream}
import com.datastax.driver.core.{ResultSet, Row, Session, SimpleStatement, Statement}

import scala.concurrent.{Future}
import scala.util.{Failure, Success}
import scala.reflect.runtime.universe._
import collection.JavaConverters._

class CassandraClient[F[_] : Async : Sync: Concurrent : Functor](val session: Session)
  extends WithBlockingThreadPool
  with WithInsertStatementBuilder
  with WithCreateStatementBuilder {

  def execAsync(ss: Statement): F[ResultSet] =
    Async[F].async {
      (cb: Either[Throwable, ResultSet] => Unit) =>

        val f: Future[ResultSet] = session.executeAsync(ss)

        f.onComplete {
          case Success(s) => cb(Right(s))
          case Failure(e) => cb(Left(e))
        }
    }

    // Threaded insert.
    def insertManyAsync[A <: AnyRef](records: Chunk[A], keySpace: String = "", tableName: String = ""): F[Vector[ResultSet]] =
      Async[F].async {
        (cb: Either[Throwable, Vector[ResultSet]] => Unit) =>

          val statements = buildInsertStatements(records, keySpace, tableName)

          val l = statements.map { stmt =>
            val f: Future[ResultSet] = session.executeAsync(stmt)
            f
          }

          val f = Future.sequence(l)

          f.onComplete {
            case Success(s) => cb(Right(s))
            case Failure(e) => cb(Left(e))
          }
      }

  // Single BatchStatement.
  def batchInsertAsync[A <: AnyRef](records: Chunk[A], keySpace: String = "", tableName: String = ""): F[ResultSet] = {
    val batchStatement = buildInsertStatements(records, keySpace, tableName)
      .asBatchStatement

    execAsync(batchStatement)

  }

  def createAsync[A: TypeTag](keySpace: String)(partitionKeys: String*)(clusteringKeys: String*)(orderBy: (String, Option[Int])*): F[ResultSet] = {
    val simpleStatement = getCreateStmt(keySpace)(partitionKeys:_*)(clusteringKeys:_*)(orderBy:_*)

    execAsync(simpleStatement)
  }

  def readAsync(stmt: String): Stream[F, Row] =
    Stream.eval(execAsync(new SimpleStatement(stmt)))
      .flatMap { r =>
        val it: java.util.Iterator[Row] = r.iterator()
        Stream.fromIterator(it.asScala)
      }

}

object CassandraClient {
  def apply[F[_] : Async : Concurrent](session: Session): CassandraClient[F] = new CassandraClient[F](session)
}
