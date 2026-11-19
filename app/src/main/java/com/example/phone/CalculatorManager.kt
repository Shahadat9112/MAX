package com.example.phone

import android.content.Context
import android.content.Intent
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Offline, memory-lightweight Calculator controller for MAX.
 *
 * Evaluates arithmetic expressions (+, -, *, /, %, parentheses) cleanly in-process
 * without launching heavy third-party apps unless explicitly asked, and also supports
 * opening the device's system Calculator app when requested.
 */
class CalculatorManager(context: Context) {
    private val appContext = context.applicationContext

    /**
     * Evaluates a clean math expression and returns a formatted numeric result.
     */
    fun evaluate(expression: String): Result<String> {
        val bnDigitsNormalized = expression
            .replace('০', '0').replace('১', '1').replace('২', '2').replace('৩', '3').replace('৪', '4')
            .replace('৫', '5').replace('৬', '6').replace('৭', '7').replace('৮', '8').replace('৯', '9')
        val normalized = bnDigitsNormalized
            .replace("×", "*")
            .replace("÷", "/")
            .replace("x", "*", ignoreCase = true)
            .replace("plus", "+", ignoreCase = true)
            .replace("minus", "-", ignoreCase = true)
            .replace("times", "*", ignoreCase = true)
            .replace("multiplied by", "*", ignoreCase = true)
            .replace("divided by", "/", ignoreCase = true)
            .replace("যোগ", "+")
            .replace("বিয়োগ", "-")
            .replace("গুণ", "*")
            .replace("ভাগ", "/")
            .replace(Regex("[^0-9.+\\-*/%()]"), "")
            .trim()

        if (normalized.isEmpty()) {
            return Result.failure(IllegalArgumentException("No valid mathematical expression found."))
        }

        return runCatching {
            val value = ExpressionParser(normalized).parse()
            if (value.isNaN() || value.isInfinite()) {
                throw ArithmeticException("Cannot divide by zero.")
            }
            val bd = BigDecimal.valueOf(value).setScale(6, RoundingMode.HALF_UP).stripTrailingZeros()
            bd.toPlainString()
        }
    }

    /**
     * Opens the device Calculator application on explicit user command.
     */
    fun openDeviceCalculator(): Boolean {
        return runCatching {
            val intent = Intent.makeMainSelectorActivity(
                Intent.ACTION_MAIN,
                Intent.CATEGORY_APP_CALCULATOR
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            appContext.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    private class ExpressionParser(private val expr: String) {
        private var pos = -1
        private var ch = 0

        fun parse(): Double {
            nextChar()
            val x = parseExpression()
            if (pos < expr.length) {
                throw IllegalArgumentException("Unexpected character: ${ch.toChar()}")
            }
            return x
        }

        private fun nextChar() {
            ch = if (++pos < expr.length) expr[pos].code else -1
        }

        private fun eat(charToEat: Int): Boolean {
            while (ch == ' '.code) nextChar()
            if (ch == charToEat) {
                nextChar()
                return true
            }
            return false
        }

        private fun parseExpression(): Double {
            var x = parseTerm()
            while (true) {
                when {
                    eat('+'.code) -> x += parseTerm()
                    eat('-'.code) -> x -= parseTerm()
                    else -> return x
                }
            }
        }

        private fun parseTerm(): Double {
            var x = parseFactor()
            while (true) {
                when {
                    eat('*'.code) -> x *= parseFactor()
                    eat('/'.code) -> x /= parseFactor()
                    eat('%'.code) -> x %= parseFactor()
                    else -> return x
                }
            }
        }

        private fun parseFactor(): Double {
            if (eat('+'.code)) return parseFactor()
            if (eat('-'.code)) return -parseFactor()

            val startPos = pos
            val x: Double
            if (eat('('.code)) {
                x = parseExpression()
                if (!eat(')'.code)) throw IllegalArgumentException("Missing closing parenthesis")
            } else if (ch in '0'.code..'9'.code || ch == '.'.code) {
                while (ch in '0'.code..'9'.code || ch == '.'.code) nextChar()
                x = expr.substring(startPos, pos).toDouble()
            } else {
                throw IllegalArgumentException("Unexpected expression token")
            }
            return x
        }
    }
}
