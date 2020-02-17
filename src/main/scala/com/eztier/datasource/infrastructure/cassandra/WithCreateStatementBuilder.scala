package com.eztier.datasource
package infrastructure.cassandra

import java.util.Date

import com.datastax.driver.core.SimpleStatement

import scala.Option
import scala.reflect.runtime.universe._

trait WithCreateStatementBuilder {

  private def classAccessors[T: TypeTag]: List[MethodSymbol] = typeOf[T].members.collect {
    case m: MethodSymbol if m.isCaseAccessor => m
  }.toList

  private def tryMatchType(o: Type) = {
    o match {
      case a if a =:= typeOf[String] => "text"
      case a if a =:= typeOf[Date] => "timestamp"
      case a if a =:= typeOf[java.math.BigDecimal] => "decimal"
      case a if a =:= typeOf[Float] => "float"
      case a if a =:= typeOf[Double] => "double"
      case a if a =:= typeOf[Long] => "bigint"
      case a if a =:= typeOf[Int] => "int"
      case a if a =:= typeOf[Short] => "smallint"
      case a if a =:= typeOf[Byte] => "tinyint"
      case a if a =:= typeOf[Boolean] => "boolean"
      case a if (a =:= typeOf[Seq[String]] || a =:= typeOf[List[String]] || a =:= typeOf[Vector[String]]) => "list<text>"
      case a if a =:= typeOf[Seq[Date]] => "list<timestamp>"
      case a if a =:= typeOf[Seq[BigDecimal]] => "list<decimal>"
      case a if a =:= typeOf[Seq[Double]] => "list<double>"
      case a if a =:= typeOf[Seq[Float]] => "list<float>"
      case a if a =:= typeOf[Seq[Long]] => "list<bigint>"
      case a if a =:= typeOf[Seq[Int]] => "list<int>"
      case a if a =:= typeOf[Seq[Short]] => "list<smallint>"
      case a if a =:= typeOf[Seq[Byte]] => "list<tinyint>"
      case a if a =:= typeOf[Seq[Boolean]] => "list<boolean>"
      case a if a =:= typeOf[Map[String, String]] => "map<text,text>"
      case _ => ""
    }
  }

  private def toCaType[T](implicit typeTag: TypeTag[T]) = {
    val members = classAccessors[T]

    val m = (Map[String, String]() /: members) {
      (a, f) =>
        val o: Type = f.info.resultType
        val n = f.name.toString
        val v = o.typeArgs

        val s: String =
          if (o <:< typeOf[Option[Any]])
            tryMatchType(v(0))
          else
            tryMatchType(o)

        a + (n -> s)
    }

    m
  }

  private def getCreateStmtString[T](keySpace: String, tableName: Option[String] = None)(implicit typeTag: TypeTag[T]): Seq[String] = {
    val o = typeTag.tpe.resultType

    val t = tableName match {
      case Some(a) => a
      case _ => o.typeSymbol.name.toString
    }

    val m = toCaType[T]

    val f = (Array[String]() /: m) {
      (a, n) =>
        a :+ (n._1 + " " + n._2)
    }

    Seq(s"create table if not exists ${keySpace}.${t} (${f.mkString(",")});")
  }

  def getCreateStmt[T: TypeTag](keySpace: String, tableName: Option[String] = None)(partitionKeys: String*)(clusteringKeys: String*)(orderBy: Option[String] = None, direction: Option[Int] = None): SimpleStatement = {

    val t: Seq[String] = getCreateStmtString[T](keySpace, tableName)

    // val o = typeTag.tpe.resultType
    // val n = o.typeSymbol.name.toString

    val members = classAccessors[T]
    val f = members.map(_.name.toString)

    val pk = partitionKeys.collect {
      case a if f.find(_ == a) != None => a
    }.mkString(",")

    val ck = clusteringKeys.collect {
      case a if f.find(_ == a) != None => a
    }.mkString(",")

    val sb = orderBy match {
      case Some(a) if a.length > 0 && f.find(_ == a) != None =>
        val sort = direction match {
          case Some(b) => if (b > 0) "asc" else "desc"
          case None => "asc"
        }

        s" with clustering order by (${a} ${sort})"
      case None => ""
    }

    val t0 = t(0)
    val trim = t0.substring(0, t0.length - 2)

    new SimpleStatement(
      trim +
        s", primary key ((${pk})" +
        (if (ck.length > 0) s", ${ck}))" else "))") +
        sb + ";"
    )

  }

}
