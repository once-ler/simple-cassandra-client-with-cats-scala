package com.eztier.datasource
package infrastructure.cassandra

import cats.effect.{Async, Concurrent, Resource, Sync}
import fs2.Chunk
import com.datastax.driver.core.{ResultSet, Session, Statement}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}
import scala.reflect.runtime.universe._

class CassandraClient[F[_] : Async : Sync: Concurrent](session: Resource[F, Session])
  extends WithBlockingThreadPool
  with WithInsertStatementBuilder
  with WithCreateStatementBuilder {

  def execAsync(ss: Statement): F[ResultSet] =
    session.use {
      s =>

        blockingThreadPool.use { ec: ExecutionContext =>
          implicit val cs = ec

          Async[F].async {
            (cb: Either[Throwable, ResultSet] => Unit) =>

              val f:Future[ResultSet] = s.executeAsync(ss)

              f.onComplete {
                case Success(s) => cb(Right(s))
                case Failure(e) => cb(Left(e))
              }
          }
        }
    }

  def insertManyAsync[A <: AnyRef](records: Chunk[A], keySpace: String = "", tableName: String = ""): F[Vector[ResultSet]] = {
    session.use { s =>

      blockingThreadPool.use { ec: ExecutionContext =>
        implicit val cs = ec

        Async[F].async {
          (cb: Either[Throwable, Vector[ResultSet]] => Unit) =>

            val statements = buildInsertStatements(records, keySpace, tableName)

            val l = statements.map { stmt =>
              val f: Future[ResultSet] = s.executeAsync(stmt)
              f
            }

            val f = Future.sequence(l)

            f.onComplete {
              case Success(s) => cb(Right(s))
              case Failure(e) => cb(Left(e))
            }
        }

      }
    }

  }

  def batchInsertAsync[A <: AnyRef](records: Chunk[A], keySpace: String = "", tableName: String = ""): F[ResultSet] =
    session.use { s =>

      blockingThreadPool.use { ec: ExecutionContext =>
        implicit val cs = ec

        Async[F].async {
          (cb: Either[Throwable, ResultSet] => Unit) =>

            val batchStatement = buildInsertStatements(records, keySpace, tableName)
              .asBatchStatement

            val f: Future[ResultSet] = s.executeAsync(batchStatement)

            f.onComplete {
              case Success(s) => cb(Right(s))
              case Failure(e) => cb(Left(e))
            }
        }
      }
    }

  def createAsync[A: TypeTag](keySpace: String, tableName: Option[String] = None)(partitionKeys: String*)(clusteringKeys: String*)(orderBy: Option[String] = None, direction: Option[Int] = None): F[ResultSet] =
    session.use { s =>

      blockingThreadPool.use { ec: ExecutionContext =>
        implicit val cs = ec

        Async[F].async {
          (cb: Either[Throwable, ResultSet] => Unit) =>

            val simpleStatement = getCreateStmt(keySpace, tableName)(partitionKeys:_*)(clusteringKeys:_*)(orderBy, direction)

            val f:Future[ResultSet] = s.executeAsync(simpleStatement)

            f.onComplete {
              case Success(s) => cb(Right(s))
              case Failure(e) => cb(Left(e))
            }
        }
      }
    }

}

object CassandraClient {
  def apply[F[_] : Async : Concurrent](session: Resource[F, Session]): CassandraClient[F] = new CassandraClient[F](session)
}
