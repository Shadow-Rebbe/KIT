package com.kit.prototype

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class KitStore(private val context: Context) {
    private val prefs = context.getSharedPreferences("kit_alpha", Context.MODE_PRIVATE)
    val decks = mutableListOf<KitDeck>()
    val cards = mutableListOf<KitCard>()
    var appearance: Appearance = Appearance.SYSTEM

    init { load() }

    private fun seed() {
        cards.clear(); decks.clear()
        fun card(name: String, prompt: String, actions: List<ContactAction> = emptyList()): KitCard {
            return KitCard(
                id = UUID.randomUUID().toString(), name = name, cardImage = "seed",
                actions = actions.toMutableList(),
                items = mutableListOf(CardItem(UUID.randomUUID().toString(), "Prompt", prompt))
            ).also(cards::add)
        }
        val a = card("Maya Levin", "Ask how the move is going.", listOf(ContactAction("a1","WhatsApp","WhatsApp","https://wa.me/15555550101"), ContactAction("a2","Email","Email","mailto:maya@example.com")))
        val b = card("Jonah Stein", "Tell him about the cybernetics piece.", listOf(ContactAction("b1","Phone","Call","tel:+15555550102")))
        val c = card("Rachel Cohen", "Ask what she thought of the book.", listOf(ContactAction("c1","Email","Email","mailto:rachel@example.com")))
        val d = card("Aaron Feld", "Send the article you saved.")
        val e = card("Leah Rosen", "Ask about the new project.")
        val long = listOf("David K.","Nina S.","Alex M.","Talia R.","Sam P.","Ari B.","Miriam G.","Noah L.").mapIndexed { i, n -> card(n, "Something worth picking up from last time #${i+1}.") }
        decks += KitDeck(UUID.randomUUID().toString(), "Inner Circle", "People I want in the regular orbit.", cadenceDays = 7, cardIds = mutableListOf(a.id,b.id))
        decks += KitDeck(UUID.randomUUID().toString(), "Books & Ideas", "People I trade ideas, books, and essays with.", cadenceDays = 30, cardIds = mutableListOf(c.id,d.id,e.id))
        decks += KitDeck(UUID.randomUUID().toString(), "Long Arc", "People I don't want time to quietly erase.", cadenceDays = 90, cardIds = long.map { it.id }.toMutableList())
        save()
    }

    fun save() {
        val root = JSONObject()
        root.put("appearance", appearance.name)
        root.put("decks", JSONArray().apply { decks.forEach { d -> put(JSONObject().apply {
            put("id",d.id); put("title",d.title); put("description",d.description); put("imageUri",d.imageUri); put("cadenceDays",d.cadenceDays)
            put("cardIds",JSONArray(d.cardIds))
        }) } })
        root.put("cards", JSONArray().apply { cards.forEach { c -> put(JSONObject().apply {
            put("id",c.id); put("name",c.name); put("personImage",c.personImage); put("cardImage",c.cardImage); put("cadenceDays",c.cadenceDays); put("lastKitActionAt",c.lastKitActionAt)
            put("actions",JSONArray().apply { c.actions.forEach { a -> put(JSONObject().apply { put("id",a.id);put("type",a.type);put("label",a.label);put("destination",a.destination) }) } })
            put("items",JSONArray().apply { c.items.forEach { i -> put(JSONObject().apply { put("id",i.id);put("type",i.type);put("text",i.text) }) } })
        }) } })
        prefs.edit().putString("state", root.toString()).apply()
    }

    private fun load() {
        val raw = prefs.getString("state", null) ?: return seed()
        try {
            val root = JSONObject(raw)
            appearance = runCatching { Appearance.valueOf(root.optString("appearance","SYSTEM")) }.getOrDefault(Appearance.SYSTEM)
            cards.clear(); decks.clear()
            val ca = root.optJSONArray("cards") ?: JSONArray()
            for (i in 0 until ca.length()) {
                val o = ca.getJSONObject(i)
                val c = KitCard(o.getString("id"), o.getString("name"), o.optNullable("personImage"), o.optNullable("cardImage"), o.optInt("cadenceDays",30), if(o.isNull("lastKitActionAt")) null else o.optLong("lastKitActionAt"))
                val aa=o.optJSONArray("actions")?:JSONArray(); for(j in 0 until aa.length()){val a=aa.getJSONObject(j);c.actions+=ContactAction(a.getString("id"),a.optString("type"),a.optString("label"),a.optString("destination"))}
                val ia=o.optJSONArray("items")?:JSONArray(); for(j in 0 until ia.length()){val it=ia.getJSONObject(j);c.items+=CardItem(it.getString("id"),it.optString("type"),it.optString("text"))}
                cards += c
            }
            val da=root.optJSONArray("decks")?:JSONArray(); for(i in 0 until da.length()){
                val o=da.getJSONObject(i); val ids=mutableListOf<String>(); val arr=o.optJSONArray("cardIds")?:JSONArray(); for(j in 0 until arr.length()) ids+=arr.getString(j)
                decks+=KitDeck(o.getString("id"),o.optString("title"),o.optString("description"),o.optNullable("imageUri"),o.optInt("cadenceDays",30),ids)
            }
        } catch (_: Throwable) { seed() }
    }

    fun reset() { prefs.edit().clear().apply(); seed() }
    fun newDeck(): KitDeck = KitDeck(UUID.randomUUID().toString(), "New deck").also { decks += it; save() }
    fun newCard(name: String = "New person"): KitCard = KitCard(UUID.randomUUID().toString(), name).also { cards += it; save() }
    fun card(id:String)=cards.firstOrNull{it.id==id}
    fun deck(id:String)=decks.firstOrNull{it.id==id}

    private fun JSONObject.optNullable(key:String):String? = if(isNull(key)) null else optString(key).takeIf{it.isNotBlank() && it!="null"}
}
