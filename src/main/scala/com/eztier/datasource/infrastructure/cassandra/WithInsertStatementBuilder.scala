package com.eztier.datasource
package infrastructure.cassandra

import com.datastax.driver.core.BatchStatement
import com.datastax.driver.core.querybuilder.{Insert, QueryBuilder}
import fs2.Chunk

import scala.collection.JavaConverters._

trait WithInsertStatementBuilder extends WithCommon {

  private def zipKV(
    in: AnyRef,
    filterFunc: java.lang.reflect.Field => Boolean = (_) => true,
    formatFunc: Any => Any = a => a
  ): (Array[String], Array[AnyRef]) =
    ((Array[String](), Array[AnyRef]()) /: in.getClass.getDeclaredFields.filter(filterFunc)) {
      (a, f) =>
        f.setAccessible(true)
        val k = a._1 :+ formatFunc(camelToUnderscores(f.getName)).asInstanceOf[String]

        val v = a._2 :+
          (formatFunc(f.get(in)) match {
            case Some(o) =>
              o match {
                case a: Map[_, _] => a.asJava
                case a: List[_] => a.asJava
                case a: Vector[_] => a.asJava
                case a: Seq[_] => a.asJava
                case _ => o
              }
            case None => null
            case a: Map[_, _] => a.asJava
            case a: List[_] => a.asJava
            case a: Vector[_] => a.asJava
            case a: Seq[_] => a.asJava
            case _ => formatFunc(f.get(in))
          }).asInstanceOf[AnyRef]

        (k, v)
    }

  def buildInsertStatements[A <: AnyRef](records: Chunk[A], keySpace: String, tableName: String): Vector[Insert] =
    records.map {
      c =>
        val (keys, values) = zipKV(c)

        QueryBuilder.insertInto(keySpace, tableName).values(keys, values)
    }.toVector

  def combineInsertAsBatch(batch: Vector[Insert]): BatchStatement =
    new BatchStatement(BatchStatement.Type.UNLOGGED)
      .addAll(batch.asJava)

  implicit class CombineAsBatchStatement(batch: Vector[Insert]) {
    def asBatchStatement: BatchStatement = new BatchStatement(BatchStatement.Type.UNLOGGED)
      .addAll(batch.asJava)
  }

}
