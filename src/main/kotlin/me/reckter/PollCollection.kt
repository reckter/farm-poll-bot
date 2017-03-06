package me.reckter

import com.mongodb.client.MongoCollection
import me.reckter.poll.Poll
import me.reckter.user.Group
import org.litote.kmongo.MongoOperator
import org.litote.kmongo.MongoOperator.*
import org.litote.kmongo.getCollection
import org.litote.kmongo.json
import org.litote.kmongo.find
import java.time.LocalDate

/**
 * Created by Hannes on 12/01/2017.
 *
 */
class PollCollection: MongoCollection<Poll> by KMongoConfig.database.getCollection<Poll>() {

    fun findByGroup(group: Group) = this.find("""{"groupId":${group.id.json}}""")
    fun findByGroupAndAfter(group: Group, date: LocalDate) = this.find("""{"groupId":${group.id.json}, date: {$gt : ${date.json}}}""")
}