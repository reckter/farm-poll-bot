package me.reckter

import com.mongodb.client.MongoCollection
import me.reckter.user.Group
import org.litote.kmongo.findOneById
import org.litote.kmongo.getCollection
import org.litote.kmongo.json
import org.litote.kmongo.save
import org.litote.kmongo.find

/**
 * Created by Hannes on 12/01/2017.
 *
 */
class GroupCollection: MongoCollection<Group> by KMongoConfig.database.getCollection<Group>() {


    fun getOrCreate(tgGroup: me.reckter.telegram.model.Chat): Group {
        var ret = this.findOneById(tgGroup.id)
        if (ret == null) {
            ret = Group(tgGroup.id, 4)
            this.save(ret)
        }
        return ret
    }

    fun findByPollingDay(day: Int) = this.find("""{"startPollingOnDay": ${day.json}, "enableAutoPolling": true}""")
}