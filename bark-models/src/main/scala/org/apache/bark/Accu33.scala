/**
  * Accuracy of source data comparing with target data
  *
  * Purpose: suppose that each row of source data could be found in target data,
  * but there exists some errors resulting the data missing, in this progress
  * we count the missing data of source data set, which is not found in the target data set
  *
  *
  *
  */

package com.ebay.bark

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import org.apache.spark.rdd.RDD.rddToPairRDDFunctions
import org.apache.spark.{SparkConf, SparkContext}

import scala.collection.immutable.HashSet
import scala.collection.mutable.{MutableList, HashSet => MutableHashSet, Map => MutableMap}

object Accu33 {

  def main(args: Array[String]) {

    val input = HdfsUtils.openFile(args(0))

    val outputPath = args(1) + System.getProperty("file.separator")

    //add files for job scheduling
    val startFile = outputPath + "_START"
    val resultFile = outputPath + "_RESULT"
    val doneFile = outputPath + "_FINISHED"
    val missingFile = outputPath + "missingRec.txt"

    val mapper = new ObjectMapper()
    mapper.registerModule(DefaultScalaModule)

    //read the config info of comparison
    val configure = mapper.readValue(input, classOf[AccuracyConf])

    val mp = configure.accuracyMapping

    //the key column info, to match different rows between source and target
    val sojKeyIndexList = MutableList[scala.Tuple2[Int, String]]()
    val beKeyIndexList = MutableList[scala.Tuple2[Int, String]]()

    //the value column info, to be compared with between the match rows
    val sojValueIndexList = MutableList[scala.Tuple2[Int, String]]()
    val beValueIndexList = MutableList[scala.Tuple2[Int, String]]()

    //get the key and value column info from config
    for (i <- mp) {
      if (i.isPK) {

        val sojkey = scala.Tuple2(i.sourceColId, i.sourceColName)
        sojKeyIndexList += sojkey

        val bekey = scala.Tuple2(i.targetColId, i.targetColName)
        beKeyIndexList += bekey

      }

      val sojValue = scala.Tuple2(i.sourceColId, i.sourceColName)
      sojValueIndexList += sojValue

      val beValue = scala.Tuple2(i.targetColId, i.sourceColName)
      beValueIndexList += beValue

    }

    def toTuple[A <: Object](as: MutableList[A]): Product = {
      val tupleClass = Class.forName("scala.Tuple" + as.size)
      tupleClass.getConstructors.apply(0).newInstance(as: _*).asInstanceOf[Product]
    }

    //--0. ready to start--

    val conf = new SparkConf().setAppName("Acc")
    val sc: SparkContext = new SparkContext(conf)
    val sqlContext = new org.apache.spark.sql.hive.HiveContext(sc)

    //add spark applicationId for debugging
    val applicationId = sc.applicationId
    HdfsUtils.writeFile(startFile, applicationId)

    //get source data
    val sojdf = sqlContext.sql("SELECT * FROM  " + configure.source + PartitionUtils.generateWhereClause(configure.srcPartitions)).rdd
//    println("sojdf:" + sojdf.count())

    //get target data
    val bedf = sqlContext.sql(PartitionUtils.generateTargetSQLClause(configure.target, configure.tgtPartitions)).rdd
//    println("bedf:" + bedf.count())

    //--1. convert data into same format (key, value)--

    //convert source data rows into (key, ("__source__", valMap)), key is the key column value tuple, valMap is the value map of row
    val sojkvs = sojdf.map { row =>
      val kk = sojKeyIndexList map { t => row.getString(t._1) }

      val kkk = toTuple(kk)

      val v = sojValueIndexList.foldLeft(Map[String, Any]()) { (c, x) => c + (x._2 -> row.get(x._1)) }

      (kkk -> v)
    }

    //convert source data rows into (key, ("__target__", valMap)), key is the key column value tuple, valMap is the value map of row
    val bekvs = bedf.map { row =>
      val kk = beKeyIndexList map { t => row.getString(t._1) }

      val kkk = toTuple(kk)

      val v = beValueIndexList.foldLeft(Map[String, Any]()) { (c, x) => c + (x._2 -> row.get(x._1)) }

      (kkk -> v)
    }

    //--2. cogroup src RDD[(k, v1)] and tgt RDD[(k, v2)] into RDD[(k, (Iterable[v1], Iterable[v2]))]
    val allkvs = sojkvs.cogroup(bekvs)

    //--3. get missed count of source data--

    //with the same key, for each source data in list, if it does not exists in the target set, one missed data found
    def seqMissed(cnt: ((Long, Long), List[String]), kv: (Product, (Iterable[Map[String, Any]], Iterable[Map[String, Any]]))) = {
      val ls = kv._2._1
      val st = kv._2._2

      if (ls.size > 2 && st.size > 4) {
        val st1 = st.foldLeft(HashSet[Map[String, Any]]())((set, mp) => set + mp)
        val ss = ls.foldLeft((0, List[String]())) { (c, mp) =>
          if (st1.contains(mp)) {
            c
          } else {
            (c._1 + 1, mp.toString :: c._2)
          }
        }
        ((cnt._1._1 + ss._1, cnt._1._2 + ls.size), ss._2 ::: cnt._2)
      } else {
        val ss = ls.foldLeft((0, List[String]())) { (c, mp) =>
          if (st.exists(mp.equals(_))) {
            c
          } else {
            (c._1 + 1, mp.toString :: c._2)
          }
        }
        ((cnt._1._1 + ss._1, cnt._1._2 + ls.size), ss._2 ::: cnt._2)
      }
    }

    //add missed count of each partition
    def combMissed(cnt1: ((Long, Long), List[String]), cnt2: ((Long, Long), List[String])) = {
      ((cnt1._1._1 + cnt2._1._1, cnt1._1._2 + cnt2._1._2), cnt1._2 ::: cnt2._2)
    }

    //count missed source data
    val missed = allkvs.aggregate(((0L, 0L), List[String]()))(seqMissed, combMissed)

    //output: need to change
    println("source count: " + missed._1._2 + " missed count : " + missed._1._1)

    HdfsUtils.writeFile(resultFile, ((1 - missed._1._1.toDouble / missed._1._2) * 100).toString())

    val sb = new StringBuilder
    missed._2.foreach { item =>
      sb.append(item)
      sb.append("\n")
    }
    HdfsUtils.writeFile(missingFile, sb.toString())

    HdfsUtils.createFile(doneFile)

    sc.stop()

  }

}
