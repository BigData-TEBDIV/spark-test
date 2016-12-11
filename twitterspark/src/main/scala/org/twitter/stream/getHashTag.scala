package org.twitter.stream

import org.twitter.stream._
import org.apache.spark.SparkConf
import org.apache.spark.streaming.{ Seconds, StreamingContext }
import org.apache.spark.streaming.twitter.TwitterUtils

object getHashTag {
  System.setProperty("hadoop.home.dir", "D:/pathHad")
  def main(args: Array[String]) {
    System.setProperty("twitter4j.oauth.consumerKey", "NoGnDgnmxHRwcI8lXTm68ezUS")
    System.setProperty("twitter4j.oauth.consumerSecret", "I9sItJpz62Fd30TaHU4ntaXXwwohLM46chJqxkWLqu4JoD2IW1")
    System.setProperty("twitter4j.oauth.accessToken", "72037685-Qu0amZgBW7iyi3Y8oDz7Uq8IjGa2WEotp668l2Hjh")
    System.setProperty("twitter4j.oauth.accessTokenSecret", "jYVtYXkwi0fpySCSCH6jpQSBhVVqgV4Zh7pCxXWavZUzd")

    val sparkConf = new SparkConf().setAppName("twitter-stream-sentiment")

    // check Spark configuration for master URL, set it to local if not configured
    if (!sparkConf.contains("spark.master")) {
      sparkConf.setMaster("local[2]")
    }

    val ssc = new StreamingContext(sparkConf, Seconds(10))
    val stream = TwitterUtils.createStream(ssc, None)

    val tweets = stream.filter { t =>
      val tags = t.getText.split(" ")
        .filter(_.startsWith("#"))
        .map(_.toLowerCase)
        
      tags.contains("#bigdata") && tags.contains("#food")
      
    }
    
    val data = tweets.map { status =>
   val sentiment = SentimentAnalysisUtils.detectSentiment(status.getText)
   val tags = status.getHashtagEntities.map(_.getText.toLowerCase)
  
      

   (status.getText, sentiment.toString, tags)
}
    data.foreachRDD{ rdd => rdd.saveAsTextFile("data/streaming/tweets.txt")
      
    }
    
    
  }
}