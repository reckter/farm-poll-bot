package me.reckter.poll

import me.reckter.user.Group
import me.reckter.user.User
import org.litote.kmongo.MongoId
import java.util.*

/**
 * Created by Hannes on 12/01/2017.
 *
 */
data class Poll(
        val groupId: String,
        val options: List<String>,
        val question: String = "When do you want to farm?",
        val disableEventAndNotification: Boolean = false,
        val radioMode: Boolean = false,
        val votes: MutableList<Vote> = mutableListOf(),
        var posts: MutableList<Post> = mutableListOf(),
        @MongoId val id: String = UUID.randomUUID().toString()
        )

data class Post(
        val id: String,
        val chat: String? = null
)

data class Vote(
        val tgUser: String,
        val option: String
)
