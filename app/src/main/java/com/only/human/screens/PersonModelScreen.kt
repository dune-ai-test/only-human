package com.only.human.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.only.human.world.Cast
import com.only.human.world.Engine
import com.only.human.world.World

/**
 * Pick the model one person writes through. Everything of theirs — posts,
 * comments, replies, DMs, stories — switches to it the moment it's chosen.
 */
@Composable
fun PersonModelScreen(personaId: String, onBack: () -> Unit) {
    val persona = Cast.byId(personaId)
    val endpoint = Engine.config.endpoint
    val apiKey = Engine.config.apiKey
    var models by remember(endpoint) { mutableStateOf(Engine.cachedModels(endpoint)) }

    ModelPickerScreen(
        endpoint = endpoint,
        apiKey = apiKey,
        models = models,
        selected = World.personaModel(personaId) ?: "",
        onFetched = { models = it },
        onPick = { picked ->
            World.setPersonaModel(personaId, picked)
            onBack()
        },
        onBack = onBack,
        title = "AI model",
        note = "Everything ${persona.name} writes — posts, comments, replies, DMs, " +
            "stories — goes through this model. Engine default is ${Engine.config.model}.",
        defaultTitle = "Engine default",
    )
}
