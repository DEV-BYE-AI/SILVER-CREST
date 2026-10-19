package com.devbyeai.macuisine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.json.JSONArray
import org.json.JSONObject

object RecipeStore {
    private var cache: List<Recipe>? = null

    fun load(ctx: Context): List<Recipe> {
        cache?.let { return it }
        val txt = ctx.assets.open("recipes.json").bufferedReader().use { it.readText() }
        val arr = JSONArray(txt)
        val list = ArrayList<Recipe>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            list.add(parse(o))
        }
        cache = list
        return list
    }

    private fun parse(o: JSONObject): Recipe {
        val ings = ArrayList<String>()
        val ia = o.optJSONArray("ingredients")
        if (ia != null) for (j in 0 until ia.length()) {
            val it = ia.getJSONObject(j)
            val t = it.optString("t", "")
            val h = it.optString("h", "")
            if (h.isNotEmpty()) ings.add("# $h") else if (t.isNotEmpty()) ings.add(t)
        }
        val ins = ArrayList<String>()
        val insa = o.optJSONArray("instructions")
        if (insa != null) for (j in 0 until insa.length()) ins.add(insa.getString(j))
        val steps = ArrayList<Step>()
        val sa = o.optJSONArray("steps")
        if (sa != null) for (j in 0 until sa.length()) {
            val s = sa.getJSONObject(j)
            steps.add(Step(s.optString("text",""), s.optInt("speed",0), s.optInt("temp",0), s.optInt("time",0), s.optBoolean("reverse",false)))
        }
        val tags = ArrayList<String>()
        val ta = o.optJSONArray("tags")
        if (ta != null) for (j in 0 until ta.length()) tags.add(ta.getString(j))
        return Recipe(
            o.optInt("id"), o.optString("name"), o.optString("level"),
            o.optInt("duration"), o.optInt("yield"), o.optString("yieldUnit"),
            tags, ings, ins, steps
        )
    }

    fun image(ctx: Context, id: Int): Bitmap? = try {
        ctx.assets.open("img/$id.jpg").use { BitmapFactory.decodeStream(it) }
    } catch (e: Exception) { null }
}
