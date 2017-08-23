package me.reckter.bot

import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.runBlocking
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
import me.reckter.telegram.model.ChatStatus
import me.reckter.telegram.model.GroupChat
import me.reckter.telegram.model.InlineQuery
import me.reckter.telegram.model.Message
import me.reckter.telegram.model.update.CallbackQuery
import me.reckter.telegram.requests.ChatAction
import me.reckter.telegram.requests.InlineKeyboardButton
import me.reckter.telegram.requests.InlineKeyboardMarkup
import me.reckter.telegram.requests.ParseMode
import me.reckter.telegram.requests.inlineMode.*
import me.reckter.user.Group
import me.reckter.user.User
import org.litote.kmongo.findOneById
import org.litote.kmongo.save
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import java.time.DayOfWeek
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjuster
import kotlin.system.measureNanoTime


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
            if (query.query.startsWith("event")) {
                val (_, pollid) = query.query.split("#")

                val poll = pollCollection.findOneById(pollid)
                if (poll == null) {
                    InlineQueryAnswer(query.id, results = mutableListOf())
                } else {

                    InlineQueryAnswer(id = query.id,
                            results =
                            poll.options.map { option ->
                                InlineQueryResultArticle().apply {
                                    id = option
                                    title = "$option - ${poll.votes.count { it.option == option }}"
                                    inputMessageContent = InputTextMessageContent().apply {
                                        text = "poll loading..."
                                    }
                                    replyMarkup = InlineKeyboardMarkup().apply {
                                        this.inlineKeyboard = mutableListOf(
                                                mutableListOf(InlineKeyboardButton().apply {
                                                    this.text = "loading.."
                                                    this.callbackData = "loading"
                                                })
                                        )
                                    }
                                }
                            }.toMutableList(),
                            cacheTime = 0,
                            isPersonal = true
                    )
                }
            } else getInlineQueryPoll(query)
        }.onResult { result ->

            if (result.query.startsWith("event")) {
                val (_, pollId) = result.query.split("#")
                val oldPol = pollCollection.findOneById(pollId)
                if (oldPol == null) { //well no group so *shrug*
                    throw IllegalStateException("could not find an old Pol, but this should always be possible!")
                } else {
                    val poll = Poll(
                            groupId = oldPol.groupId,
                            options = listOf("yes", "maybe", "no"),
                            posts = mutableListOf(Post(result.inlineMessageId!!)),
                            question = "Farming on ${result.id}",
                            disableEventAndNotification = true,
                            radioMode = true,
                            previousPollId = pollId
                    )
                    pollCollection.save(poll)
                    updatePoll(poll)
                }
            } else {

                val poll = pollCollection.findOneById(result.query) ?: return@onResult

                poll.posts.add(Post(result.inlineMessageId!!))
                pollCollection.save(poll)

                println("sharing poll..")
                updatePoll(poll)
            }
        }
    }

    fun getInlineQueryPoll(query: InlineQuery): InlineQueryAnswer {

        val poll = pollCollection.findOneById(query.query)

        return if (poll == null) {
            InlineQueryAnswer(query.id, results = mutableListOf())
        } else {

            InlineQueryAnswer(query.id, results =
            mutableListOf(
                    InlineQueryResultArticle().apply {
                        id = poll.id.take(10)
                        title = poll.question
                        inputMessageContent = InputTextMessageContent().apply {
                            text = "poll loading..."
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
    }

    fun Poll.toText(): String {

        var description = "${this.question}\n\n"

        description += this.options.map { option ->
            var ret = "*$option*"
            val count = this.votes.count { it.option == option }
            if (count > 0) ret += " - $count"
            "$ret\n${this.votes
                    .filter { it.option == option }
                    .map {
                        userCollection.findOneById(it.tgUser)?.name ?: "<no user>"
                    }.joinToString("\n") {
                        it
                                .replace("(", "")
                                .replace(")", "")
                                .replace("[", "")
                                .replace("]", "")
                                .replace("*", "")
                                .replace("-", "")
                                .replace("<", "")
                                .replace(">", "")
                                .replace("[^\\x20-\\x7e]", "")
                    }
            }"
        }.joinToString("\n\n")
        return description
    }

    fun updatePoll(poll: Poll) = async(CommonPool) {
        fun updater(chatId: String? = null): me.reckter.telegram.UpdateMessageBuilder.() -> Unit = {

            text(poll.toText())
//            parseMode(ParseMode.MARKDOWN) TODO fix weird parsing issue in TG


            buildInlineKeyboard {

                button(text = "share", switchInlineQuery = poll.id)

                if (!poll.disableEventAndNotification) {

                    button(text = "create Event", switchInlineQuery = "event#${poll.id}")

                    nextRow()
                    button(text = "notify me in the future", callBackData = "subscribe")
                }

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

        println("updating poll with ${poll.posts.size} posts.")
        poll.posts.forEach { (id, chat) ->

            if (chat != null) {
                telegram.sendEditMessage(chat, id.toInt(), updater(chat))
            } else {
                telegram.sendEditMessage(id, updater())
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

        if (poll.radioMode) {
            poll.votes.removeAll { it.tgUser == callbackQuery.from.id }
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
            text("When do you want to farm?\n\n(setting up one moment)")
        }
        poll.posts.add(Post(message.id.toString(), message.chat.id))

        println("creating poll")

        val notifies = group.member.map {
            userCollection.findOneById(it)
        }.filterNotNull()
                .filter(User::notify)
                .map {
                    try {
                        telegram.getChatMember(it.id, group.id)
                    } catch (e: Exception) {
                        null
                    }
                }.filterNotNull().filter {
            it.status != ChatStatus.left && it.status != ChatStatus.kicked
        }

        println("notifying ${notifies.size} people")

        notifies.forEach {
            telegram.sendMessage {
                recipient(it.user.id)
                text("hey a new Poll just has been started in a group!\nIf you want to disable this notification change it in /settings")
            }
        }

        pollCollection.save(poll)
        updatePoll(poll)
    }

    @OnCommand("updateAllPost")
    fun updateAll(message: Message, args: List<String>) {
        if(message.user.id != telegram.adminChat) {
            message.respond("You do not have persmission for that")
            return
        }
        val polls = pollCollection.find()

        val text = "updating ${polls.count()} polls"
        println(text)
        message.respond(text)
        telegram.sendChatAction(message.chat.id, ChatAction.typing)
        polls.forEach {
            try {
                runBlocking {
                    updatePoll(it).await()
                }
            } catch (e: Exception) {
                telegram.sendExceptionErrorMessage(e, "Error updating Poll ${it.id}")
            }
        }
        message.respond("done")
    }

    @OnCommand("latest")
    fun latest(message: Message, args: List<String>) {

        val adjuster = TemporalAdjusters.previous(DayOfWeek.MONDAY)
        val date = LocalDate.now().with(adjuster).with(adjuster)

        val polls = when (message.chat) {
            is GroupChat -> {
                val group = groupCollection.getOrCreate(message.chat)
                pollCollection.findByGroupAndAfter(group, date)
            }
            is me.reckter.telegram.model.User -> {
                val groups = groupCollection.findByUser(message.user.id)
                groups
                        .filter {
                            val member = telegram.getChatMember(it.id, message.user.id)
                            member != null && member.status != ChatStatus.kicked && member.status != ChatStatus.left
                        }
                        .flatMap {
                            pollCollection.findByGroupAndAfter(it, date)
                        }
            }
            else -> {
                message.respond("whoops that should not be possible! Please only use this command in a chat!")
                return
            }
        }



        val toPost = polls.filter { poll ->
            polls.none { it.previousPollId == poll.id }
        }

        if(toPost.isEmpty()) {
            message.respond("No current Polls (you cann access) found!")
        }

        toPost.forEach { poll ->
            val newMessage = telegram.sendMessage {
                chat(message.chat)
                text(poll.toText())
            }
            poll.posts.add(Post(newMessage.id.toString(), newMessage.chat.id))
            pollCollection.save(poll)
            updatePoll(poll)
        }

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