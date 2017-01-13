package me.reckter.user

import org.litote.kmongo.MongoId

/**
 * Created by Hannes on 12/01/2017.
 *
 */
data class Group(
        @MongoId
        val id: String,
        val startPollingOnDay: Int, // 0 = Monday ... 7 = Sunday
        val enableAutoPolling: Boolean = false, // 0 = Monday ... 7 = Sunday
        val member: MutableList<String> = mutableListOf()
)