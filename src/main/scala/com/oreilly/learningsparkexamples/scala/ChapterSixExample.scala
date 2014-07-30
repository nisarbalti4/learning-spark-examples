/**
 * Contains the Chapter 6 Example
 */
package com.oreilly.learningsparkexamples.scala

import org.apache.spark._
import org.apache.spark.SparkContext._


object AdvancedSparkProgrammingExample {
    def main(args: Array[String]) {
      val master = args(0)
      val inputFile = args(1)
      val sc = new SparkContext(master, "AdvancedSparkProgramming", System.getenv("SPARK_HOME"))
      val file = sc.textFile(inputFile)
      // Create Accumulator[Int] initialized to 0
      val errorLines = sc.accumulator(0)
      val dataLines = sc.accumulator(0)
      val validSignCount = sc.accumulator(0)
      val invalidSignCount = sc.accumulator(0)
      val unknownCountry = sc.accumulator(0)
      val resolvedCountry = sc.accumulator(0)
      val callSigns = file.flatMap(line => {
        if (line == "") {
          errorLines += 1
        } else {
          dataLines +=1
        }
        line.split(" ")
      })
      // Validate a call sign
      val callSignRegex = "\\A\\d?[a-zA-Z]{1,2}\\d{1,4}[a-zA-Z]{1,3}\\Z".r
      val validSigns = callSigns.flatMap{sign =>
        sign match {
          case callSignRegex() => {validSignCount += 1; Some(sign)}
          case _ => {invalidSignCount += 1; None}
        }
      }
      val contactCount = validSigns.map(callSign => (callSign, 1)).reduceByKey((x, y) => x + y)
      // Force evaluation so the counters are populated
      contactCount.count()
      if (invalidSignCount.value < 0.5 * validSignCount.value) {
        contactCount.saveAsTextFile("output.txt")
      } else {
        println(s"Too many errors ${invalidSignCount.value} for ${validSignCount.value}")
        exit(1)
      }
      // Lookup the countries for each call sign
      val callSignMap = scala.io.Source.fromFile("./files/callsign_tbl_sorted").getLines().filter(_ != "").map(_.split(",")).toList
      val callSignKeys = callSignMap.map(line => line(0)).toArray
      val callSignLocations = callSignMap.map(line => line(1)).toArray
      val countryContactCount = contactCount.map{case (sign, count) =>
        val pos = java.util.Arrays.binarySearch(callSignKeys.asInstanceOf[Array[AnyRef]], sign) match {
          case x if x < 0 => -x-1
          case x => x
        }
        (callSignLocations(pos),count)
      }.reduceByKey((x, y) => x + y)
      // Force evaluation so the counters are populated
      countryContactCount.saveAsTextFile("countries.txt")
    }
}