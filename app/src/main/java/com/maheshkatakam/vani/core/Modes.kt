package com.maheshkatakam.vani.core

object Modes {
    const val PROFESSIONAL = "professional"
    const val EMAIL = "email"
    const val MESSAGE = "message"
    const val FORMAL = "formal"
    const val CONCISE = "concise"
    const val DIPLOMATIC = "diplomatic"
    const val CLEAN = "clean"

    val builtIns: List<Mode> = listOf(
        Mode(
            PROFESSIONAL, "Professional", "💼",
            "Rewrite it as polished, professional writing that a capable professional would send to a " +
                "colleague, manager or client: clear, confident, courteous and grammatically flawless. " +
                "Improve word choice and sentence flow, tighten rambling and put ideas in a logical order, " +
                "while keeping the user's intent, level of detail and roughly the same length. Replace slang " +
                "and overly casual phrasing with natural professional wording; it should sound like a real " +
                "person, not stiff or robotic. Do not add greetings, sign-offs or pleasantries the user didn't say.",
            builtIn = true,
        ),
        Mode(
            EMAIL, "Email", "✉️",
            "Turn it into a ready-to-send professional email body. Open with a short greeting only if the " +
                "user named or clearly implied the recipient (for example \"Hi Priya,\"). Use short paragraphs " +
                "with one idea each, and a list if the user listed several items. If it fits, end with a brief " +
                "closing line, then a sign-off with the user's name when it is known (for example \"Best regards,\" " +
                "on one line and the name on the next). Don't write a subject line unless the user dictated one; " +
                "if they did, put it first as \"Subject: …\".",
            builtIn = true,
        ),
        Mode(
            MESSAGE, "Message", "💬",
            "Write it as a concise, friendly and professional chat message (WhatsApp, Slack, Teams, SMS). " +
                "Natural and warm, short sentences, easy to read on a phone. No formal greeting or sign-off " +
                "unless the user said one, and no stiff phrases like \"I hope this message finds you well\". " +
                "Keep emojis only if the user said them.",
            builtIn = true,
        ),
        Mode(
            FORMAL, "Formal", "🎩",
            "Rewrite it in a formal, respectful register suitable for official communication, such as a " +
                "university office, HR, a government office, a landlord or a senior executive. Use complete " +
                "sentences, no contractions, and polite, precise wording.",
            builtIn = true,
        ),
        Mode(
            CONCISE, "Concise", "✂️",
            "Make it as short as possible while keeping every important point. Remove repetition, filler " +
                "and pleasantries, prefer short sentences, and use \"- \" bullet points when there are several " +
                "separate items.",
            builtIn = true,
        ),
        Mode(
            DIPLOMATIC, "Diplomatic", "🤝",
            "Rewrite it to be tactful and diplomatic: keep the message and any request or disagreement, but " +
                "phrase it kindly and constructively, assume good intent, avoid blame and sharp words, and keep " +
                "it professional and warm.",
            builtIn = true,
        ),
        Mode(
            CLEAN, "Just clean up", "✍️",
            "Don't rewrite. Keep the user's own words, phrasing and sentence structure. Only fix punctuation, " +
                "capitalization, small grammar slips and obvious speech-recognition errors, and remove filler " +
                "words, stutters and false starts.",
            builtIn = true,
        ),
    )

    fun all(custom: List<Mode>): List<Mode> = builtIns + custom

    fun find(id: String?, custom: List<Mode>): Mode =
        all(custom).firstOrNull { it.id == id } ?: builtIns.first()
}

object Languages {
    const val ENGLISH = "English"
    const val SAME = "same"

    val indic = setOf(
        "Telugu", "Hindi", "Tamil", "Kannada", "Malayalam", "Marathi", "Bengali", "Gujarati",
        "Punjabi", "Urdu", "Odia", "Assamese", "Nepali", "Sanskrit",
    )

    /** Languages offered as a target for the rewritten text. */
    val outputChoices = listOf(
        "English", "Telugu", "Hindi", "Tamil", "Kannada", "Malayalam", "Marathi", "Bengali",
        "Gujarati", "Punjabi", "Urdu", "Odia", "Spanish", "French", "German", "Portuguese",
        "Arabic", "Chinese", "Japanese", "Korean",
    )

    /** Languages the user might speak (used as hints for speech recognition). */
    val spokenChoices = listOf(
        "English", "Telugu", "Hindi", "Tamil", "Kannada", "Malayalam", "Marathi", "Bengali",
        "Gujarati", "Punjabi", "Urdu", "Odia", "Spanish", "French", "German", "Arabic",
        "Chinese", "Japanese",
    )

    val isoCodes = mapOf(
        "English" to "en", "Telugu" to "te", "Hindi" to "hi", "Tamil" to "ta", "Kannada" to "kn",
        "Malayalam" to "ml", "Marathi" to "mr", "Bengali" to "bn", "Gujarati" to "gu",
        "Punjabi" to "pa", "Urdu" to "ur", "Odia" to "or", "Spanish" to "es", "French" to "fr",
        "German" to "de", "Portuguese" to "pt", "Arabic" to "ar", "Chinese" to "zh",
        "Japanese" to "ja", "Korean" to "ko",
    )

    fun label(output: String, detected: String? = null): String = when (output) {
        SAME -> if (!detected.isNullOrBlank() && !detected.equals(ENGLISH, ignoreCase = true)) detected else "As spoken"
        else -> output
    }
}

object DefaultAppRules {
    val list: List<AppRule> = listOf(
        AppRule("com.google.android.gm", "Gmail", Modes.EMAIL),
        AppRule("com.microsoft.office.outlook", "Outlook", Modes.EMAIL),
        AppRule("com.whatsapp", "WhatsApp", Modes.MESSAGE),
        AppRule("com.whatsapp.w4b", "WhatsApp Business", Modes.MESSAGE),
        AppRule("org.telegram.messenger", "Telegram", Modes.MESSAGE),
        AppRule("com.google.android.apps.messaging", "Messages", Modes.MESSAGE),
        AppRule("com.Slack", "Slack", Modes.MESSAGE),
        AppRule("com.microsoft.teams", "Teams", Modes.MESSAGE),
        AppRule("com.instagram.android", "Instagram", Modes.MESSAGE),
        AppRule("com.linkedin.android", "LinkedIn", Modes.PROFESSIONAL),
    )
}
