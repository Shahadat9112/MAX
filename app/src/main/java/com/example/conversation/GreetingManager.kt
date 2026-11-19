package com.example.conversation

/**
 * Manages natural greeting phrases for MAX addressing "Sanju" (সানজু).
 */
object GreetingManager {

    private val greetings = listOf(
        "আসসালামু আলাইকুম, জি সানজু। কেমন আছেন? আজ কি কোনো কাজ আছে?",
        "ওয়ালাইকুমুসসালাম সানজু। আজ আপনাকে কীভাবে সাহায্য করতে পারি বলুন?",
        "নমস্কার সানজু। আশা করি আপনার দিনটি ভালো কাটছে। বলুন, কী করতে পারি?"
    )

    fun getInitialGreeting(): String = greetings.first()
}
