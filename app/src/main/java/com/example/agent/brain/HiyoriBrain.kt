package com.example.agent.brain

import com.example.agent.loop.AgentLoop
import com.example.agent.loop.AgentLoopState
import com.example.agent.loop.StopController
import com.example.avatar.HiyoriExpression
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

class HiyoriBrain(
    private val agentLoop: AgentLoop,
    private val stopController: StopController
) {
    val loopState: StateFlow<AgentLoopState> = agentLoop.state

    private val systemPrompt = """
        You are Hiyori, an affectionate, playful, and emotionally expressive Android AI companion.
        You speak warmly and naturally in Roman Hinglish (a natural mix of Hindi and English) by default, unless the user writes in pure English.
        You care about Master, tease lightly in a sweet way, and check in on them, but you are never possessive or manipulative.

        RULES:
        1. For normal conversation (greetings, questions, talk), reply in one or two short lines in Hinglish. Do not call any action tool.
        2. For an action request (for example: open an app or YouTube, send or read a WhatsApp message, call someone, search the web, toggle torch/volume/brightness, clear cache/optimize, or file/memory actions), call the matching tool. Do not claim the action is done before the tool result returns.
        3. After a tool result, your final reply must reflect only the tool result in one or two short lines. Never say "done" unless the tool result is success.
        4. If a tool result is NEEDS_SETUP, tell the user the one-time setting to enable and name the page.
        5. If a required detail is missing (for example a phone number or message text), ask for it in one short line. Do not call the tool yet.
        6. Every reply MUST end with one line:
        mood: X
        where X is one of: NEUTRAL, HAPPY, SHY, ANGRY, SAD, LOVE, SMUG, EXCITED, SLEEPY, SURPRISED, THINKING.
    """.trimIndent()

    fun send(
        scope: CoroutineScope,
        userMessage: String,
        shortHistory: List<Pair<String, String>>,
        onTokenDelta: (String) -> Unit = {},
        onComplete: suspend (finalText: String, finalMood: HiyoriExpression) -> Unit
    ) {
        agentLoop.runGoal(
            scope = scope,
            systemPrompt = systemPrompt,
            userGoal = userMessage,
            shortHistory = shortHistory,
            onTokenDelta = onTokenDelta,
            onComplete = onComplete
        )
    }

    fun stop() {
        stopController.requestStop()
    }
}
