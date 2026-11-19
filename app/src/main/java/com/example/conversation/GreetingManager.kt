package com.example.conversation

import java.util.Calendar

/**
 * Manages natural greeting phrases for MAX addressing "Sanju" (সানজু).
 */
object GreetingManager {

    private val greetings = listOf(
        "আসসালামু আলাইকুম, জি সানজু। কেমন আছেন? আজ কি কোনো কাজ আছে?",
        "ওয়ালাইকুমুসসালাম সানজু। আজ আপনাকে কীভাবে সাহায্য করতে পারি বলুন?",
        "নমস্কার সানজু। আশা করি আপনার দিনটি ভালো কাটছে। বলুন, কী করতে পারি?"
    )

    fun getInitialGreeting(): String = greetings.random()
}

/**
 * Manages daytime and nighttime farewell phrases for MAX addressing "Sanju" (সানজু).
 */
object FarewellManager {

    private val dayFarewells = listOf(
        "আল্লাহ হাফেজ সানজু। আপনার দিনটি সুন্দর কাটুক। ভালো থাকবেন।",
        "খোদা হাফেজ সানজু। আপনার কাজগুলো সফল হোক, শুভকামনা রইল।"
    )

    private val nightFarewells = listOf(
        "আল্লাহ হাফেজ সানজু। আপনার রাতটি শান্তিময় ও ভালো কাটুক। শুভ রাত্রি।",
        "শুভ রাত্রি সানজু। আরামে ঘুমান, ইনশাআল্লাহ সকালে দেখা হবে।"
    )

    private val followUps = listOf(
        "সানজু, আপনাকে কি আর কোনোভাবে সাহায্য করতে পারি?",
        "আর কি কোনো সেবা দিতে পারি সানজু?",
        "সব ঠিক আছে তো সানজু? আর কিছু লাগবে কি?"
    )

    private val idleTimeouts = listOf(
        "সানজু, মনে হচ্ছে আপনি একটু ব্যস্ত আছেন। ঠিক আছে, যখন প্রয়োজন হবে শুধু MAX বলে ডাকবেন। আল্লাহ হাফেজ।",
        "সানজু, আপনি ব্যস্ত আছেন মনে হয়। আমি স্ট্যান্ডবাইতে রইলাম, প্রয়োজন হলে ডাকবেন। আল্লাহ হাফেজ।"
    )

    fun getFollowUpPrompt(): String = followUps.random()

    fun getFarewell(): String {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return if (hour in 6..18) {
            dayFarewells.random()
        } else {
            nightFarewells.random()
        }
    }

    fun getIdleTimeoutMessage(): String = idleTimeouts.random()
}

/**
 * Central personality helper ensuring MAX sounds friendly, warm, non-robotic, and never repetitive.
 */
object PersonalityManager {
    fun formatAssistantReply(rawReply: String): String {
        return rawReply.trim()
    }
}
