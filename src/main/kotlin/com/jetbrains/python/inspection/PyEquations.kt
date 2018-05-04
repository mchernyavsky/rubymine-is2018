package com.jetbrains.python.inspection

import com.jetbrains.python.PyTokenTypes
import com.jetbrains.python.psi.*
import java.math.BigInteger

internal interface Equation {
    fun not(): Equation
    fun merge(other: Equation): Equation
    fun canSolve(): Boolean
}

private interface VariableRestriction : Equation {
    val varName: String
}

private data class EqEquation(override val varName: String, val value: Int) : VariableRestriction {

    override fun not(): Equation = NotEqEquation(varName, value)

    override fun merge(other: Equation): Equation {
        if (other is VariableRestriction && varName != other.varName) return AndEquation(this, other)
        return when (other) {
            is EqEquation -> if (value == other.value) this else FailEquation
            is NotEqEquation -> if (value != other.value) this else FailEquation
            is LowerEqualEquation -> if (value <= other.bound) this else FailEquation
            is GreaterEqualEquation -> if (value >= other.bound) this else FailEquation
            is TrulyEquation -> if (value != 0) this else FailEquation
            is FalsyEquation -> if (value == 0) this else FailEquation
            else -> mergeCommon(other)
        }
    }

    override fun canSolve(): Boolean = true
}

private data class NotEqEquation(override val varName: String, val value: Int) : VariableRestriction {

    override fun not(): Equation = EqEquation(varName, value)

    override fun merge(other: Equation): Equation {
        if (other is VariableRestriction && varName != other.varName) return AndEquation(this, other)
        return when (other) {
            is EqEquation -> if (value != other.value) other else FailEquation
            is NotEqEquation -> if (value == other.value) this else AndEquation(this, other)
            is LowerEqualEquation -> if (value <= other.bound) AndEquation(this, other) else other
            is GreaterEqualEquation -> if (value >= other.bound) AndEquation(this, other) else other
            is TrulyEquation -> this
            is FalsyEquation -> if (value == 0) FailEquation else this
            else -> mergeCommon(other)
        }
    }

    override fun canSolve(): Boolean = true
}

private data class LowerEqualEquation(override val varName: String, val bound: Int) : VariableRestriction {

    override fun not(): Equation = GreaterEqualEquation(varName, bound + 1)

    override fun merge(other: Equation): Equation {
        if (other is VariableRestriction && varName != other.varName) return AndEquation(this, other)
        return when (other) {
            is EqEquation -> if (other.value <= bound) other else FailEquation
            is NotEqEquation -> if (other.value <= bound) AndEquation(this, other) else this
            is LowerEqualEquation -> if (bound <= other.bound) this else other
            is GreaterEqualEquation -> if (bound >= other.bound) AndEquation(this, other) else FailEquation
            is TrulyEquation -> this
            is FalsyEquation -> if (bound >= 0) this else FailEquation
            else -> mergeCommon(other)
        }
    }

    override fun canSolve(): Boolean = true
}

private data class GreaterEqualEquation(override val varName: String, val bound: Int) : VariableRestriction {

    override fun not(): Equation = LowerEqualEquation(varName, bound - 1)

    override fun merge(other: Equation): Equation {
        if (other is VariableRestriction && varName != other.varName) return AndEquation(this, other)
        return when (other) {
            is EqEquation -> if (other.value >= bound) other else FailEquation
            is NotEqEquation -> if (other.value >= bound) AndEquation(this, other) else this
            is LowerEqualEquation -> if (bound <= other.bound) AndEquation(this, other) else FailEquation
            is GreaterEqualEquation -> if (bound >= other.bound) this else other
            is TrulyEquation -> this
            is FalsyEquation -> if (bound <= 0) this else FailEquation
            else -> mergeCommon(other)
        }
    }

    override fun canSolve(): Boolean = true
}

private class TrulyEquation(override val varName: String) : VariableRestriction {

    override fun not(): Equation = FalsyEquation(varName)

    override fun merge(other: Equation): Equation {
        if (other is VariableRestriction && varName != other.varName) return AndEquation(this, other)
        return when (other) {
            is EqEquation -> if (other.value != 0) other else FailEquation
            is NotEqEquation -> AndEquation(this, other)
            is LowerEqualEquation -> AndEquation(this, other)
            is GreaterEqualEquation -> AndEquation(this, other)
            is TrulyEquation -> this
            is FalsyEquation -> FailEquation
            else -> mergeCommon(other)
        }
    }

    override fun canSolve(): Boolean = true
}

private class FalsyEquation(override val varName: String) : VariableRestriction {

    override fun not(): Equation = TrulyEquation(varName)

    override fun merge(other: Equation): Equation {
        if (other is VariableRestriction && varName != other.varName) return AndEquation(this, other)
        return when (other) {
            is EqEquation -> if (other.value != 0) other else FailEquation
            is NotEqEquation -> AndEquation(this, other)
            is LowerEqualEquation -> AndEquation(this, other)
            is GreaterEqualEquation -> AndEquation(this, other)
            is TrulyEquation -> FailEquation
            is FalsyEquation -> this
            else -> mergeCommon(other)
        }
    }

    override fun canSolve(): Boolean = true
}

private data class AndEquation(val equations: Set<Equation>) : Equation {

    constructor(vararg equations: Equation): this(equations.toSet())

    override fun not(): Equation = OrEquation(equations.map { it.not() }.toSet())

    override fun merge(other: Equation): Equation = when (other) {
        is EqEquation -> other.merge(this)
        is NotEqEquation -> other.merge(this)
        is LowerEqualEquation -> other.merge(this)
        is GreaterEqualEquation -> other.merge(this)
        is TrulyEquation -> other.merge(this)
        is FalsyEquation -> other.merge(this)
        else -> mergeCommon(other)
    }

    override fun canSolve(): Boolean = equations.all { it.canSolve() }
}

private data class OrEquation(val equations: Set<Equation>) : Equation {

    constructor(vararg equations: Equation): this(equations.toSet())

    override fun not(): Equation = AndEquation(equations.map { it.not() }.toSet())

    override fun merge(other: Equation): Equation = when (other) {
        is EqEquation -> other.merge(this)
        is NotEqEquation -> other.merge(this)
        is LowerEqualEquation -> other.merge(this)
        is GreaterEqualEquation -> other.merge(this)
        is TrulyEquation -> other.merge(this)
        is FalsyEquation -> other.merge(this)
        else -> mergeCommon(other)
    }

    override fun canSolve(): Boolean = equations.any { it.canSolve() }
}

private object OkEquation : Equation {
    override fun not(): Equation = FailEquation
    override fun merge(other: Equation): Equation = other
    override fun canSolve(): Boolean = true
}

private object FailEquation : Equation {
    override fun not(): Equation = OkEquation
    override fun merge(other: Equation): Equation = FailEquation
    override fun canSolve(): Boolean = false
}

private fun Equation.mergeCommon(other: Equation): Equation = when (other) {
    is AndEquation -> AndEquation(other.equations.map(this::merge).toSet())
    is OrEquation -> OrEquation(other.equations.map(this::merge).toSet())
    is OkEquation -> this
    else -> FailEquation
}

internal val PyExpression.equation: Equation
    get() = when (this) {
        is PyBinaryExpression -> equation
        is PyPrefixExpression -> equation
        is PyReferenceExpression -> equation
        is PyParenthesizedExpression -> equation
        else -> OkEquation
    }

private val PyBinaryExpression.equation: Equation
    get() {
        val leftExpression = leftExpression
        val rightExpression = rightExpression ?: return OkEquation

        fun makeVariableRestriction(
                name: String?,
                operator: PyElementType?,
                expression: PyExpression?
        ): Equation {
            val value = expression?.bigInt?.toInt()
            if (name == null || value == null) return OkEquation
            return when (operator) {
                PyTokenTypes.LT -> LowerEqualEquation(name, value - 1)
                PyTokenTypes.GT -> GreaterEqualEquation(name, value + 1)
                PyTokenTypes.LE -> LowerEqualEquation(name, value)
                PyTokenTypes.GE -> GreaterEqualEquation(name, value)
                PyTokenTypes.EQEQ -> EqEquation(name, value)
                PyTokenTypes.NE -> NotEqEquation(name, value)
                else -> OkEquation
            }
        }

        return when {
            operator == PyTokenTypes.AND_KEYWORD -> leftExpression.equation.merge(rightExpression.equation)
            operator == PyTokenTypes.OR_KEYWORD -> OrEquation(leftExpression.equation, rightExpression.equation)
            leftExpression is PyReferenceExpression && rightExpression is PyReferenceExpression ->
                OkEquation  // TODO
            leftExpression is PyReferenceExpression ->
                makeVariableRestriction(leftExpression.name, operator, rightExpression)
            rightExpression is PyReferenceExpression ->
                makeVariableRestriction(rightExpression.name, operator?.flip, leftExpression)
            else -> if (bool == false || bigInt == BigInteger.ZERO) FailEquation else OkEquation
        }
    }

private val PyPrefixExpression.equation: Equation
    get() {
        val operand = operand?.equation ?: return OkEquation
        return when (operator) {
            PyTokenTypes.NOT_KEYWORD -> operand.not()
            else -> OkEquation
        }
    }

private val PyReferenceExpression.equation: Equation
    get() = name?.let { TrulyEquation(it) } ?: OkEquation

private val PyParenthesizedExpression.equation: Equation
    get() = containedExpression?.equation ?: OkEquation

private val PyElementType.flip: PyElementType
    get() = when (this) {
        PyTokenTypes.LT -> PyTokenTypes.GE
        PyTokenTypes.GT -> PyTokenTypes.LE
        PyTokenTypes.LE -> PyTokenTypes.GT
        PyTokenTypes.GE -> PyTokenTypes.LT
        else -> this
    }
