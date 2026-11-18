package com.example.avatar

import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.Color
import com.example.R

enum class HiyoriExpression(
    val title: String,
    val description: String,
    val emoji: String,
    val auraColor: Color,
    @DrawableRes val baseDrawableRes: Int,
    val emotionNote: String
) {
    NEUTRAL(
        title = "Neutral",
        description = "Calm, slight soft smile, attentive gaze",
        emoji = "✨",
        auraColor = Color(0xFF00D2C4),
        baseDrawableRes = R.drawable.hiyori_char_neutral,
        emotionNote = "Attentive & ready"
    ),
    HAPPY(
        title = "Happy",
        description = "Joyful smile, eyes crinkled in happiness, rosy cheeks",
        emoji = "🌸",
        auraColor = Color(0xFF10B981),
        baseDrawableRes = R.drawable.hiyori_char_happy,
        emotionNote = "Delighted to assist!"
    ),
    SHY(
        title = "Shy",
        description = "Blushing heavily, glancing to the side with modest smile",
        emoji = "😳",
        auraColor = Color(0xFFA78BFA),
        baseDrawableRes = R.drawable.hiyori_char_shy,
        emotionNote = "Flustered & bashful"
    ),
    ANGRY(
        title = "Angry",
        description = "Furrowed brows, puffed cheeks, stern pout",
        emoji = "💢",
        auraColor = Color(0xFFEF4444),
        baseDrawableRes = R.drawable.hiyori_char_angry,
        emotionNote = "Hmph! Please be careful!"
    ),
    SAD(
        title = "Sad",
        description = "Droopy brows, watery eyes, slightly downcast",
        emoji = "💧",
        auraColor = Color(0xFF60A5FA),
        baseDrawableRes = R.drawable.hiyori_char_sad,
        emotionNote = "I'm so sorry..."
    ),
    EXCITED(
        title = "Excited",
        description = "Sparkling eyes, enthusiastic wide grin, energetic pose",
        emoji = "⚡",
        auraColor = Color(0xFFFBBF24),
        baseDrawableRes = R.drawable.hiyori_char_excited,
        emotionNote = "Let's do this!"
    ),
    SURPRISED(
        title = "Surprised",
        description = "Wide eyes, open mouth, eyebrows raised high",
        emoji = "❗",
        auraColor = Color(0xFF38BDF8),
        baseDrawableRes = R.drawable.hiyori_char_surprised,
        emotionNote = "Whoa! Really?!"
    ),
    SMUG(
        title = "Smug",
        description = "Confident half-closed eyes, playful smirk, clever look",
        emoji = "😏",
        auraColor = Color(0xFF8B5CF6),
        baseDrawableRes = R.drawable.hiyori_char_smug,
        emotionNote = "Easy work! Hehe."
    ),
    LOVE(
        title = "Love",
        description = "Heart-shaped loving eyes, warm rosy cheeks, sweet gentle gaze",
        emoji = "💖",
        auraColor = Color(0xFFEC4899),
        baseDrawableRes = R.drawable.hiyori_char_love,
        emotionNote = "You're my favorite master!"
    ),
    SLEEPY(
        title = "Sleepy",
        description = "Half-closed droopy eyes, relaxed gentle tilt, yawning",
        emoji = "💤",
        auraColor = Color(0xFF94A3B8),
        baseDrawableRes = R.drawable.hiyori_char_sleepy,
        emotionNote = "Yaaawn... still guarding your device."
    ),
    THINKING(
        title = "Thinking",
        description = "Deep in thought, analyzing & planning next step",
        emoji = "💭",
        auraColor = Color(0xFF38BDF8),
        baseDrawableRes = R.drawable.hiyori_char_neutral,
        emotionNote = "Reasoning & planning..."
    );

    companion object {
        fun fromString(name: String?): HiyoriExpression {
            if (name.isNullOrBlank()) return NEUTRAL
            return entries.find { it.name.equals(name, ignoreCase = true) } ?: NEUTRAL
        }

        fun detectFromText(text: String): HiyoriExpression {
            val lower = text.lowercase()
            return when {
                lower.contains("[emotion: love]") ||
                    lower.contains("love") ||
                    lower.contains("heart") ||
                    lower.contains("adore") ||
                    lower.contains("miss you") ||
                    lower.contains("favorite") ||
                    lower.contains("sweetheart") ||
                    lower.contains("thank you so much") ||
                    lower.contains("thanks a lot") ||
                    lower.contains("❤️") || lower.contains("💖") || lower.contains("💕") || lower.contains("😍") || lower.contains("🥰") -> LOVE

                lower.contains("[emotion: shy]") ||
                    lower.contains("blush") ||
                    lower.contains("embarrass") ||
                    lower.contains("cute") ||
                    lower.contains("pretty") ||
                    lower.contains("beautiful") ||
                    lower.contains("kawaii") ||
                    lower.contains("adorable") ||
                    lower.contains("shy") ||
                    lower.contains("hehe") ||
                    lower.contains("😳") || lower.contains("👉👈") -> SHY

                lower.contains("[emotion: excited]") ||
                    lower.contains("awesome") ||
                    lower.contains("yay") ||
                    lower.contains("amazing") ||
                    lower.contains("let's go") ||
                    lower.contains("incredible") ||
                    lower.contains("fantastic") ||
                    lower.contains("excited") ||
                    lower.contains("hype") ||
                    lower.contains("!!") ||
                    lower.contains("⚡") || lower.contains("🔥") || lower.contains("🎉") -> EXCITED

                lower.contains("[emotion: smug]") ||
                    lower.contains("obviously") ||
                    lower.contains("flawless") ||
                    lower.contains("done!") ||
                    lower.contains("easy") ||
                    lower.contains("genius") ||
                    lower.contains("clever") ||
                    lower.contains("nailed it") ||
                    lower.contains("😏") || lower.contains("😎") -> SMUG

                lower.contains("[emotion: angry]") ||
                    lower.contains("error") ||
                    lower.contains("fail") ||
                    lower.contains("danger") ||
                    lower.contains("angry") ||
                    lower.contains("mad") ||
                    lower.contains("hate") ||
                    lower.contains("annoying") ||
                    lower.contains("broken") ||
                    lower.contains("ugh") ||
                    lower.contains("💢") || lower.contains("😡") || lower.contains("😠") -> ANGRY

                lower.contains("[emotion: sad]") ||
                    lower.contains("sorry") ||
                    lower.contains("unfortunate") ||
                    lower.contains("sad") ||
                    lower.contains("cry") ||
                    lower.contains("lonely") ||
                    lower.contains("upset") ||
                    lower.contains("bad day") ||
                    lower.contains("hurt") ||
                    lower.contains("😢") || lower.contains("😭") || lower.contains("💧") -> SAD

                lower.contains("[emotion: sleepy]") ||
                    lower.contains("tired") ||
                    lower.contains("goodnight") ||
                    lower.contains("sleep") ||
                    lower.contains("yawn") ||
                    lower.contains("exhausted") ||
                    lower.contains("oyasumi") ||
                    lower.contains("💤") || lower.contains("😴") -> SLEEPY

                lower.contains("[emotion: surprised]") ||
                    lower.contains("whoa") ||
                    lower.contains("unexpected") ||
                    lower.contains("no way") ||
                    lower.contains("omg") ||
                    lower.contains("shocked") ||
                    lower.contains("surprised") ||
                    lower.contains("what?!") ||
                    lower.contains("really?!") ||
                    lower.contains("😮") || lower.contains("😲") || lower.contains("❗") -> SURPRISED

                lower.contains("[emotion: happy]") ||
                    lower.contains("haha") ||
                    lower.contains("lol") ||
                    lower.contains("glad") ||
                    lower.contains("great") ||
                    lower.contains("happy") ||
                    lower.contains("hello") ||
                    lower.contains("hey") ||
                    lower.contains("good morning") ||
                    lower.contains("wonderful") ||
                    lower.contains("😊") || lower.contains("😄") || lower.contains("🌸") -> HAPPY

                else -> NEUTRAL
            }
        }
    }
}
