package me.reckter.user

import org.litote.kmongo.MongoId

/**
 * Created by Hannes on 12/01/2017.
 *
 */
data class User(
        @MongoId
        val id: String,
        val name: String
)