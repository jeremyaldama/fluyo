package com.qolve.fluyo.domain.model

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Exact monetary amount stored as minor units (cents).
 *
 * Domain and presentation code must keep money in this type. Floating-point conversion is
 * deliberately named as a transport/presentation escape hatch so it cannot accidentally be
 * used for persistence or financial aggregation.
 */
@JvmInline
value class MoneyAmount private constructor(val cents: Long) : Comparable<MoneyAmount> {

    operator fun plus(other: MoneyAmount): MoneyAmount =
        ofCents(Math.addExact(cents, other.cents))

    operator fun minus(other: MoneyAmount): MoneyAmount =
        ofCents(Math.subtractExact(cents, other.cents))

    operator fun unaryMinus(): MoneyAmount = ofCents(Math.negateExact(cents))

    override fun compareTo(other: MoneyAmount): Int = cents.compareTo(other.cents)

    /** Decimal major units, with exactly two fraction digits and no precision loss. */
    fun toBigDecimal(): BigDecimal = BigDecimal.valueOf(cents, FRACTION_DIGITS)

    /** Supabase NUMERIC transport boundary only. Never aggregate the returned value. */
    fun toTransportDouble(): Double = toBigDecimal().toDouble()

    /** Ratio for progress indicators; money remains exact until the final UI scalar. */
    fun ratioOf(total: MoneyAmount): Float {
        if (total <= ZERO) return 0f
        return toBigDecimal()
            .divide(total.toBigDecimal(), RATIO_SCALE, RoundingMode.HALF_EVEN)
            .toFloat()
    }

    fun dividedBy(divisor: Long, roundingMode: RoundingMode): MoneyAmount {
        require(divisor != 0L) { "Divisor must not be zero" }
        return fromMajor(
            toBigDecimal().divide(BigDecimal.valueOf(divisor), FRACTION_DIGITS, roundingMode),
            RoundingMode.UNNECESSARY,
        )
    }

    companion object {
        const val FRACTION_DIGITS: Int = 2
        private const val RATIO_SCALE: Int = 12

        val ZERO: MoneyAmount = MoneyAmount(0L)

        fun ofCents(cents: Long): MoneyAmount = MoneyAmount(cents)

        /**
         * Converts decimal major units with an explicitly selected rounding policy.
         * Throws [ArithmeticException] if the rounded value does not fit in a [Long].
         */
        fun fromMajor(value: BigDecimal, roundingMode: RoundingMode): MoneyAmount {
            val cents = value
                .setScale(FRACTION_DIGITS, roundingMode)
                .movePointRight(FRACTION_DIGITS)
                .longValueExact()
            return ofCents(cents)
        }

        /** Converts a Supabase NUMERIC decoded as Double immediately at the transport edge. */
        fun fromTransport(value: Double, roundingMode: RoundingMode): MoneyAmount {
            require(value.isFinite()) { "Money amount must be finite" }
            return fromMajor(BigDecimal.valueOf(value), roundingMode)
        }

        /**
         * Parses user-entered major units. Both comma and dot are accepted as the decimal
         * separator. Grouping separators are intentionally rejected to avoid ambiguity.
         */
        fun parse(input: String, roundingMode: RoundingMode): MoneyAmount? {
            val normalized = input.trim().replace(',', '.')
            if (!INPUT_PATTERN.matches(normalized)) return null
            return runCatching { fromMajor(normalized.toBigDecimal(), roundingMode) }.getOrNull()
        }

        private val INPUT_PATTERN = Regex("[+-]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)")
    }
}

/** Exact checked sum; overflow fails loudly instead of corrupting a balance. */
fun Iterable<MoneyAmount>.sumMoney(): MoneyAmount =
    fold(MoneyAmount.ZERO) { total, amount -> total + amount }
