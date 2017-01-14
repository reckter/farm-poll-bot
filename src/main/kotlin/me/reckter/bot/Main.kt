package me.reckter.bot

import me.reckter.GroupCollection
import me.reckter.PollCollection
import me.reckter.UserCollection
import me.reckter.telegram.Telegram
import java.time.LocalDateTime

/**
 * Created by Hannes on 12/01/2017.
 *
 */

fun main(args: Array<String>) {

    val telegram = Telegram.Builder()
            .adminChat(System.getenv("ADMIN_CHAT"))
            .apiToken(System.getenv("API_TOKEN"))
            .build()

    val groupCollection = GroupCollection()
    val userCollection = UserCollection()
    val pollCollection = PollCollection()


    val pollBot = PollBot(telegram,
            pollCollection,
            userCollection,
            groupCollection)

    val settingsBot = SettingsBot(telegram, groupCollection, userCollection)

    telegram.addListener(pollBot)
    telegram.addListener(settingsBot)

    telegram.sendMessage {
        recipient(telegram.adminChat)
        text("loaded listeners")
    }


    while(true) {
        val now = LocalDateTime.now()
        if(now.hour == 11 && now.minute < 20) {
            println("posting today's polls!")
            pollBot.postTodaysPolls()
            Thread.sleep(20 * 60 * 60 * 1000)
        }
        Thread.sleep(10 * 60 * 1000)
    }
}