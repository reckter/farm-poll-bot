package me.reckter

import com.mongodb.MongoClient
import com.mongodb.MongoClientURI
import com.mongodb.client.MongoDatabase
import org.litote.kmongo.KMongo

/**
 * @author Hannes Güdelhöfer
 */
class KMongoConfig {

    companion object {
        val client: MongoClient

        init {

            val url = System.getenv("MONGO_URL")
            if(url == null) {
                client = KMongo.createClient()
            } else {
                client = KMongo.createClient(MongoClientURI(System.getenv("MONGO_URL"))) //get com.mongodb.MongoClient new instance
            }
        }

        val database: MongoDatabase = client.getDatabase("farm-poll-bot") //normal java driver usage

    }
}
