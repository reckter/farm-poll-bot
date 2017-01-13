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
import org.litote.kmongo.findOneById
import org.litote.kmongo.save

/**
 * Created by Hannes on 12/01/2017.
 *
 */


val DAYS_OF_THE_WEEK = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

class SettingsBot(
        val telegram: Telegram,
        val groupCollection: GroupCollection
) {


    @OnCommand("choosePollingDay")
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


        if(data[2] == "disable") {
            group = group.copy(enableAutoPolling = false)
            telegram.answerCallback(callbackQuery, "disabled auto polling")
        } else {
            group = group.copy(startPollingOnDay = data[2].toInt(), enableAutoPolling = true)
            telegram.answerCallback(callbackQuery, "Set day to ${DAYS_OF_THE_WEEK[data[2].toInt()]}")
        }


        telegram.sendEditMessage(callbackQuery.message!!){
            var text = "choose your polling day"
            if(group.enableAutoPolling) {
                text += "\n currently on day: ${DAYS_OF_THE_WEEK[group.startPollingOnDay]}"
            }else {
                text += "\ndisabled"
            }
            text("$text\n(The day the poll will be posted for the week after)")
            buildInlineKeyboard {
                DAYS_OF_THE_WEEK.forEachIndexed { i, day ->
                    button(text = day, callBackData = "pollingDay#${callbackQuery.message!!.chat.id}#$i")
                    nextRow()
                }
                button(text = "disable automatic polling", callBackData="pollingDay#${callbackQuery.message!!.chat.id}#disable")
            }
        }


        groupCollection.save(group)

    }
}