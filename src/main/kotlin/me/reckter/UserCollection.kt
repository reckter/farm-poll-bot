package me.reckter

import com.mongodb.client.MongoCollection
import me.reckter.poll.Poll
import me.reckter.user.Group
import me.reckter.user.User
import org.litote.kmongo.*

import me.reckter.telegram.model.User as TgUser

/**
 * Created by Hannes on 12/01/2017.
 *
 */
class UserCollection : MongoCollection<User> by KMongoConfig.database.getCollection<User>() {

    fun getOrCreate(tgUser: TgUser): User {
        var ret = this.findOneById(tgUser.id)
        if (ret == null) {
            ret = User(tgUser.id, "")
        }

        var name = tgUser.fistName ?: ""
        if (tgUser.lastName != null) {
            name += " " + tgUser.lastName
        }
        if (tgUser.username != null) {
            name += " (@${tgUser.username})"
        }
        ret = ret.copy(name = name)
        this.save(ret)
        return ret
    }

}