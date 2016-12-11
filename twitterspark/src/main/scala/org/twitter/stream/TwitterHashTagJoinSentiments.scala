package org.twitter.stream


import org.apache.log4j.{Level, Logger}

import org.apache.spark.SparkConf
import org.apache.spark.streaming.{Seconds, StreamingContext}
import org.apache.spark.streaming.twitter.TwitterUtils


object TwitterHashTagJoinSentiments {
    System.setProperty("hadoop.home.dir", "D:/pathHad")
  def main(args: Array[String]) {
   // if (args.length < 4) {
   //   System.err.println("Usage: TwitterHashTagJoinSentiments <consumer key> <consumer secret> " +
   //     "<access token> <access token secret> [<filters>]")
   //   System.exit(1)
   // }

    // Set logging level if log4j not configured (override by adding log4j.properties to classpath)
    if (!Logger.getRootLogger.getAllAppenders.hasMoreElements) {
      Logger.getRootLogger.setLevel(Level.WARN)
    }

   // val Array(consumerKey, consumerSecret, accessToken, accessTokenSecret) = args.take(4)
    val filters = Seq("#PlanetEarth")

    // Set the system properties so that Twitter4j library used by Twitter stream
    // can use them to generate OAuth credentials
    System.setProperty("twitter4j.oauth.consumerKey", "NoGnDgnmxHRwcI8lXTm68ezUS")
    System.setProperty("twitter4j.oauth.consumerSecret", "I9sItJpz62Fd30TaHU4ntaXXwwohLM46chJqxkWLqu4JoD2IW1")
    System.setProperty("twitter4j.oauth.accessToken", "72037685-Qu0amZgBW7iyi3Y8oDz7Uq8IjGa2WEotp668l2Hjh")
    System.setProperty("twitter4j.oauth.accessTokenSecret", "jYVtYXkwi0fpySCSCH6jpQSBhVVqgV4Zh7pCxXWavZUzd")

    val sparkConf = new SparkConf().setAppName("TwitterHashTagJoinSentiments")

    // check Spark configuration for master URL, set it to local if not configured
    if (!sparkConf.contains("spark.master")) {
      sparkConf.setMaster("local[2]")
    }

    val ssc = new StreamingContext(sparkConf, Seconds(10))
    val stream = TwitterUtils.createStream(ssc, None, filters)
    
    stream.foreachRDD(rdd => {
      println("\nNew tweets %s:".format(rdd.count()))
    })
    
    val tweets = stream.map(status => status.getText)
    tweets.print()
    
   
     val sentiment = stream.map(status => SentimentAnalysisUtils.detectSentiment(status.getText))
     sentiment.print()
     
     sentiment.foreachRDD{rdd =>  
       rdd.saveAsTextFile("data/streaming/sentiment.txt")
     }
    
   // sentiment.foreach(println) 
    
    tweets.foreachRDD{rdd =>
      rdd.saveAsTextFile("data/streaming/tweets.txt")  
    }
    
    tweets.saveAsTextFiles("data/streaming/test.txt")
   
     var x =tweets.map{t => 
         (t, SentimentAnalysisUtils.detectSentiment(t).toString())
       }.reduceByKey(_ + _)
       
     x.foreachRDD{rdd => rdd.saveAsTextFile("data/streaming/testeArray.txt") }
       
    //  text.flatMap { line => 
    //    line.split(" ")
    //  }.map { word =>
    //    (word,1)
    //  }.reduceByKey(_ + _)
    //  .saveAsTextFile("food.count.txt")

    val hashTags = stream.flatMap(status => status.getText.split(" ").filter(_.startsWith("#")))

    // Read in the word-sentiment list and create a static RDD from it
    val wordSentimentFilePath = "data/streaming/AFINN-111.txt"
    val wordSentiments = ssc.sparkContext.textFile(wordSentimentFilePath).map { line =>
      val Array(word, happinessValue) = line.split("\t")
      (word, happinessValue.toInt)
    }.cache()

    // Determine the hash tags with the highest sentiment values by joining the streaming RDD
    // with the static RDD inside the transform() method and then multiplying
    // the frequency of the hash tag by its sentiment value
    val happiest60 = hashTags.map(hashTag => (hashTag.tail, 1))
      .reduceByKeyAndWindow(_ + _, Seconds(60))
      .transform{topicCount => wordSentiments.join(topicCount)}
      .map{case (topic, tuple) => (topic, tuple._1 * tuple._2)}
      .map{case (topic, happinessValue) => (happinessValue, topic)}
      .transform(_.sortByKey(false))

    val happiest10 = hashTags.map(hashTag => (hashTag.tail, 1))
      .reduceByKeyAndWindow(_ + _, Seconds(10))
      .transform{topicCount => wordSentiments.join(topicCount)}
      .map{case (topic, tuple) => (topic, tuple._1 * tuple._2)}
      .map{case (topic, happinessValue) => (happinessValue, topic)}
      .transform(_.sortByKey(false))

    // Print hash tags with the most positive sentiment values
    happiest60.foreachRDD(rdd => {
      val topList = rdd.take(10)
      println("\nHappiest topics in last 60 seconds (%s total):".format(rdd.count()))
      topList.foreach{case (happiness, tag) => println("%s (%s happiness)".format(tag, happiness))}
    })

    happiest10.foreachRDD(rdd => {
      val topList = rdd.take(10)
      println("\nHappiest topics in last 10 seconds (%s total):".format(rdd.count()))
      topList.foreach{case (happiness, tag) => println("%s (%s happiness)".format(tag, happiness))}
    })

    ssc.start()
    ssc.awaitTermination()
  }
}