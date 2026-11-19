package com.example.conversation

import java.util.Calendar

/**
 * Manages daytime and nighttime farewell phrases, task completion follow-ups, and idle timeouts for MAX addressing "Sanju" (সানজু).
 */
object FarewellManager {

    private val dayFarewells = listOf(
        "আল্লাহ হাফেজ সানজু। আপনার দিনটি সুন্দর কাটুক। ভালো থাকবেন।",
        "খোদা হাফেজ সানজু। আপনার কাজগুলো সফল হোক, শুভকামনা রইল।"
    )

    private val nightFarewells = listOf(
        "আল্লাহ হাফেজ সানজু। আপনার রাতটি শান্তিময় ও ভালো কাটুক।",
        "শুভ রাত্রি সানজু। আরামে ঘুমান, ভালো থাকবেন।"
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

    fun getFollowUpPrompt(): String = followUps.first()

    fun getFarewell(): String {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return if (hour in 6..18) {
            dayFarewells.first()
        } else {
            nightFarewells.first()
        }
    }

    fun getIdleTimeoutMessage(): String = idleTimeouts.first()
}
