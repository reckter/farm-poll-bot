package me.reckter.bot

import me.reckter.GroupCollection
import me.reckter.UserCollection
import me.reckter.telegram.Telegram
import me.reckter.telegram.listener.OnCallBack
import me.reckter.telegram.listener.OnCommand
import me.reckter.telegram.model.ChatStatus
import me.reckter.telegram.model.Message
import me.reckter.telegram.model.User
import me.reckter.telegram.model.update.CallbackQuery
import me.reckter.user.Group
import org.litote.kmongo.findOneById
import org.litote.kmongo.save

/**
 * Created by Hannes on 12/01/2017.
 *
 */


val DAYS_OF_THE_WEEK = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

class SettingsBot(
        val telegram: Telegram,
        val groupCollection: GroupCollection,
        val userCollection: UserCollection
) {


    @OnCommand("help", "start")
    fun help(message: Message, args: List<String>) {

        val group = groupCollection.getOrCreate(message.chat)

        val text = """
/settings - change the settings (if and when to auto post) (and subscription settings)
/createPoll - creates a poll
"""

        message.respond(text)
    }


    @OnCallBack
    fun subscribeCallback(callbackQuery: CallbackQuery) {
        if(!(callbackQuery.data?.startsWith("subscribe") ?: false)) {
            return
        }

        var user = userCollection.getOrCreate(callbackQuery.from)

        user = user.copy(notify = true)


        try {
            telegram.sendMessage {
                recipient(user.id)
                text("This is a test notification, to check if you are getting it :)\n use /settings to change your subscription")
            }
        } catch (e: Exception) {
            return telegram.answerCallback(callbackQuery, "Please start a private chat with me first!")
        }

        userCollection.save(user)

        telegram.answerCallback(callbackQuery, "Will notify you in the future!")

        if(callbackQuery.data?.contains("update") ?: false) {
            updateSettings(callbackQuery.message!!, groupCollection.getOrCreate(callbackQuery.message!!.chat), user)
        }

    }

    @OnCallBack
    fun unsubscribeCallback(callbackQuery: CallbackQuery) {

        if(!(callbackQuery.data?.startsWith("unsubscribe") ?: false)) {
            return
        }

        var user = userCollection.getOrCreate(callbackQuery.from)

        user = user.copy(notify = false)

        userCollection.save(user)

        telegram.answerCallback(callbackQuery, "Will not notify you in the future :(")

        if(callbackQuery.data?.contains("update") ?: false) {
            updateSettings(callbackQuery.message!!, groupCollection.getOrCreate(callbackQuery.message!!.chat), user)
        }

    }

    @OnCommand("choosePollingDay", "settings")
    fun setPollingDay(message: Message, args: List<String>) {

        val member = telegram.getChatMember(message.chat, message.user)
        if (message.chat !is User && member.status != ChatStatus.administrator
                && member.status != ChatStatus.creator) {
            telegram.sendMessage {
                chat(message.chat)
                text("only an admin can do that!")
            }
            return
        }

        println("displaying settings")
        val group = groupCollection.getOrCreate(message.chat)
        telegram.sendMessage {
            chat(message.chat)
            var text = "choose your polling day"
            if(group.enableAutoPolling) {
               text += "\ncurrently on day: ${DAYS_OF_THE_WEEK[group.startPollingOnDay]}"
            }else {
                text += "\ndisabled"
            }
            text("$text\n(The day the poll will be posted for the week after)")
            buildInlineKeyboard {
                if(message.chat is User) {
                    val user = userCollection.getOrCreate(message.user)
                    if (user.notify) {
                        button(text = "disable Notifications", callBackData = "unsubscribe#update")
                    } else {
                        button(text = "enable Notifications", callBackData = "subscribe#update")
                    }
                    nextRow()
                    button(text = " ", callBackData = " ")
                    nextRow()
                }
                DAYS_OF_THE_WEEK.forEachIndexed { i, day ->
                    button(text = day, callBackData = "pollingDay#${message.chat.id}#$i")
                    nextRow()
                }
                button(text = "disable automatic polling", callBackData="pollingDay#${message.chat.id}#disable")
            }
        }
    }


    fun updateSettings(message: Message, group: Group, user: me.reckter.user.User? = null) {

        telegram.sendEditMessage(message){
            var text = "choose your polling day"
            if(group.enableAutoPolling) {
                text += "\n currently on day: ${DAYS_OF_THE_WEEK[group.startPollingOnDay]}"
            }else {
                text += "\ndisabled"
            }
            text("$text\n(The day the poll will be posted for the week after)")
            buildInlineKeyboard {

                val user = user ?: userCollection.getOrCreate(message.user)

                if(message.chat is User ) {
                    if (user.notify) {
                        button(text = "disable Notifications", callBackData = "unsubscribe#update")
                    } else {
                        button(text = "enable Notifications", callBackData = "subscribe#update")
                    }
                    nextRow()
                    button(text = " ", callBackData = " ")
                    nextRow()
                }
                DAYS_OF_THE_WEEK.forEachIndexed { i, day ->
                    button(text = day, callBackData = "pollingDay#${message.chat.id}#$i")
                    nextRow()
                }
                button(text = "disable automatic polling", callBackData="pollingDay#${message.chat.id}#disable")
            }
        }

    }

    @OnCallBack
    fun pollingCallBack(callbackQuery: CallbackQuery) {
        if (!(callbackQuery.data?.startsWith("pollingDay") ?: false)) {
            return
        }
        val data = callbackQuery.data!!.split("#")

        var group = groupCollection.findOneById(data[1]) ?: return telegram.answerCallback(callbackQuery, "Did not find a group!")


        val member = telegram.getChatMember(group.id, callbackQuery.from.id)
        if (callbackQuery.message?.chat !is User && member.status != ChatStatus.administrator
                &&member.status != ChatStatus.creator) {
            telegram.answerCallback(callbackQuery, "Only an admin can change that!")
            return
        }


        println("changing settings")

        if(data[2] == "disable") {
            group = group.copy(enableAutoPolling = false)
            telegram.answerCallback(callbackQuery, "disabled auto polling")
        } else {
            group = group.copy(startPollingOnDay = data[2].toInt(), enableAutoPolling = true)
            telegram.answerCallback(callbackQuery, "Set day to ${DAYS_OF_THE_WEEK[data[2].toInt()]}")
        }



        groupCollection.save(group)

        updateSettings(callbackQuery.message!!, group)

    }
}