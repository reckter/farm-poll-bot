package me.reckter.bot

import me.reckter.GroupCollection
import me.reckter.PollCollection
import me.reckter.UserCollection
import me.reckter.poll.Poll
import me.reckter.poll.Post
import me.reckter.poll.Vote
import me.reckter.telegram.Telegram
import me.reckter.telegram.UpdateMessageBuilder
import me.reckter.telegram.listener.OnCallBack
import me.reckter.telegram.listener.OnCommand
import me.reckter.telegram.model.Message
import me.reckter.telegram.model.update.CallbackQuery
import me.reckter.telegram.requests.InlineKeyboardButton
import me.reckter.telegram.requests.InlineKeyboardMarkup
import me.reckter.telegram.requests.ParseMode
import me.reckter.telegram.requests.inlineMode.*
import me.reckter.user.Group
import org.litote.kmongo.findOneById
import org.litote.kmongo.save
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import java.time.DayOfWeek
import java.time.format.DateTimeFormatter


/**
 * Created by Hannes on 12/01/2017.
 *
 */
class PollBot(
        val telegram: Telegram,
        val pollCollection: PollCollection,
        val userCollection: UserCollection,
        val groupCollection: GroupCollection
) {

    init {
        telegram.inlineQueryHandler { query ->
            val poll = pollCollection.findOneById(query.query)

            if (poll == null) {
                InlineQueryAnswer(query.id, results = mutableListOf())
            } else {

                InlineQueryAnswer(query.id, results =
                mutableListOf(
                        InlineQueryResultArticle().apply {
                            id = poll.id.take(10)
                            title = "Farm poll"
                            inputMessageContent = InputTextMessageContent().apply {
                                text = "poll loading.."
                            }
                            replyMarkup = InlineKeyboardMarkup().apply {
                                this.inlineKeyboard = mutableListOf(
                                        mutableListOf(InlineKeyboardButton().apply {
                                            this.text = "loading..."
                                            this.callbackData = "loading"
                                        })
                                )
                            }
                        }
                ), cacheTime = 0, isPersonal = true)
            }
        }.onResult { result ->

            val poll = pollCollection.findOneById(result.query) ?: return@onResult

            poll.posts.add(Post(result.inlineMessageId!!))
            pollCollection.save(poll)

            updatePoll(poll)
        }
    }

    fun updatePoll(poll: Poll) {
        val updater: me.reckter.telegram.UpdateMessageBuilder.() -> Unit = {

            var description = "When do you want to farm?\n\n"

            description += poll.options.map { option ->

                var ret = "*$option*"
                val count = poll.votes.count { it.option == option }
                if (count > 0) ret += " - $count"
                "$ret\n${poll.votes.filter { it.option == option }.joinToString("\n") { userCollection.findOneById(it.tgUser)?.name ?: "<no user>" }}"
            }.joinToString("\n\n")

            text(description)
            parseMode(ParseMode.MARKDOWN)

            buildInlineKeyboard {

                button(text = "share", switchInlineQuery = poll.id)
                nextRow()

                poll.options.forEach { option ->

                    var text = option
                    val count = poll.votes.count { it.option == option }
                    if (count > 0) text += " - $count"
                    button(text = text, callBackData = "vote#${poll.id}#${option.replace("#", "-*-p-*-")}")
                    nextRow()
                }
            }
        }

        poll.posts.forEach { post ->

            if (post.chat != null) {
                telegram.sendEditMessage(post.chat, post.id.toInt(), updater)
            } else {
                telegram.sendEditMessage(post.id, updater)
            }
        }
    }


    @OnCallBack
    fun voteCallBack(callbackQuery: CallbackQuery) {
        if (!(callbackQuery.data?.startsWith("vote") ?: false)) {
            return
        }

        val data = callbackQuery.data!!.split("#")

        val poll = pollCollection.findOneById(data[1])
                ?: return telegram.answerCallback(callbackQuery, "Not a valid option!")

        val option = poll.options.find { it == data[2] }
                ?: return telegram.answerCallback(callbackQuery, "Not a valid option!")

        val user = userCollection.getOrCreate(callbackQuery.from)

        val group = groupCollection.findOneById(poll.groupId) ?: return telegram.answerCallback(callbackQuery, "Not a valid Poll, contact the admin please!")

        if (group.member.none {
            it == user.id
        }) {
            group.member.add(user.id)
            groupCollection.save(group)
        }

        if (poll.votes.any { it.tgUser == user.id && it.option == option }) {
            poll.votes.removeAll { it.tgUser == user.id && it.option == option }
        } else {
            poll.votes.add(Vote(
                    user.id,
                    option
            ))
        }

        pollCollection.save(poll)
        updatePoll(poll)
        telegram.answerCallback(callbackQuery, "voted!")
    }

    fun createPoll(group: Group) {

        val format = DateTimeFormatter.ofPattern("EEEE dd.MM")

        val now = LocalDate.now()
        val monday = now.with(TemporalAdjusters.next(DayOfWeek.MONDAY))

        val options = DAYS_OF_THE_WEEK.mapIndexed { i, day ->
            val date = monday.plusDays(i.toLong())

            date.format(format)
        }
        createPoll(group, options)
    }

    fun createPoll(group: Group, options: List<String>) {
        val poll = Poll(group.id, options)
        val message = telegram.sendMessage {
            recipient(group.id)
            text("creating poll...")
        }
        poll.posts.add(Post(message.id.toString(), message.chat.id))

        pollCollection.save(poll)
        updatePoll(poll)
    }


    @OnCommand("createPoll")
    fun createPoll(message: Message, args: List<String>) {

        val group = groupCollection.getOrCreate(message.chat)

        val now = LocalDate.now()
        val monday = now.with(TemporalAdjusters.next(DayOfWeek.MONDAY))

        val format = DateTimeFormatter.ofPattern("EEEE dd.MM")
        val options = DAYS_OF_THE_WEEK.mapIndexed { i, day ->
            val date = monday.plusDays(i.toLong())

            date.format(format)
        }

        createPoll(group, options)
    }


    fun postTodaysPolls() {
        val now = LocalDate.now()

        val day = now.dayOfWeek.value - 1

        groupCollection.findByPollingDay(day).forEach { group ->
            try {
                createPoll(group)
            } catch(e: Exception) {
                telegram.sendExceptionErrorMessage(e, "got an exception while posting for group: ${group.id}; skipping")
            }
        }

    }

}