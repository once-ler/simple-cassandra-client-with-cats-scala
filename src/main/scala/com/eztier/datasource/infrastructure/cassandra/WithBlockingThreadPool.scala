package com.eztier.datasource
package infrastructure.cassandra

import cats.effect.{Resource, Sync}
import java.util.concurrent.Executors
import scala.concurrent.{ExecutionContext, Future, Promise}
import com.datastax.driver.core.{ResultSet, ResultSetFuture}
import com.google.common.util.concurrent.{FutureCallback, Futures, ListenableFuture}

trait WithBlockingThreadPool {
  def blockingThreadPool[F[_]: Sync]: Resource[F, ExecutionContext] =
    Resource(Sync[F].delay {
      val executor = Executors.newFixedThreadPool(4)
      val ec = ExecutionContext.fromExecutor(executor)
      (ec, Sync[F].delay(executor.shutdown()))
    })

  implicit def resultSetFutureToScala(f: ResultSetFuture): Future[ResultSet] = {
    val p = Promise[ResultSet]()
    Futures.addCallback(f,
      new FutureCallback[ResultSet] {
        def onSuccess(r: ResultSet) = p success r
        def onFailure(t: Throwable) = p failure t
      })
    p.future
  }

  implicit def listenableFutureToFuture[T](f: ListenableFuture[T]): Future[T] = {
    val p = Promise[T]()
    Futures.addCallback(f, new FutureCallback[T] {
      def onFailure(error: Throwable): Unit = p.failure(error)
      def onSuccess(result: T): Unit = p.success(result)
    })
    p.future
  }
}
