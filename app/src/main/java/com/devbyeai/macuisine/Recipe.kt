package com.devbyeai.macuisine

data class Step(
    val text: String,
    val speed: Int,
    val temp: Int,
    val time: Int,
    val reverse: Boolean
)

data class Recipe(
    val id: Int,
    val name: String,
    val level: String,
    val duration: Int,
    val yield_: Int,
    val yieldUnit: String,
    val tags: List<String>,
    val ingredients: List<String>,
    val instructions: List<String>,
    val steps: List<Step>
)
