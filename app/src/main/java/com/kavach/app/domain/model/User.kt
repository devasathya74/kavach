package com.kavach.app.domain.model

/**
 * Domain model — User / Jawan profile.
 */
data class User(
    val id       : Int,
    val pno      : String,   // Police/Personnel Number
    val name     : String,
    val rank     : String,
    val unit     : String,
    val deviceId : String,
    val isActive : Boolean
)
