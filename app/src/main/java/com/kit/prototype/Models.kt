package com.kit.prototype

data class KitCard(
    val id: String,
    var name: String,
    var personImage: String? = null,
    var cardImage: String? = null,
    var cadenceDays: Int = 30,
    var lastKitActionAt: Long? = null,
    val actions: MutableList<ContactAction> = mutableListOf(),
    val items: MutableList<CardItem> = mutableListOf()
)

data class KitDeck(
    val id: String,
    var title: String,
    var description: String = "",
    var imageUri: String? = null,
    var cadenceDays: Int = 30,
    val cardIds: MutableList<String> = mutableListOf()
)

data class ContactAction(
    val id: String,
    var type: String,
    var label: String,
    var destination: String
)

data class CardItem(
    val id: String,
    var type: String,
    var text: String
)

enum class Appearance { SYSTEM, LIGHT, DARK }
