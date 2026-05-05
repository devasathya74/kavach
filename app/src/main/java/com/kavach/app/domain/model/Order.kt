package com.kavach.app.domain.model

/**
 * Domain model — Standing Order issued by CO.
 */
data class Order(
    val id           : String,
    val title        : String,
    val contentText  : String?,
    val imageUrl     : String?,
    val issuedBy     : String,
    val createdAt    : Long,
    val isMandatory  : Boolean,
    val isAcknowledged: Boolean
)
