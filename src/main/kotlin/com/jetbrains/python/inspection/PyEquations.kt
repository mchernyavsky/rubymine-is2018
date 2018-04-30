package com.jetbrains.python.inspection

import com.jetbrains.python.PyTokenTypes
import com.jetbrains.python.psi.*

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
            else -> mergeCommon(other)
        }
    }

    override fun canSolve(): Boolean = true
}

private data class LowerEqualEquation(override val varName: String, val bound: Int) : VariableRestriction {

    override fun not(): Equation = GreaterEqualEquation(varName, bound - 1)

    override fun merge(other: Equation): Equation {
        if (other is VariableRestriction && varName != other.varName) return AndEquation(this, other)
        return when (other) {
            is EqEquation -> if (other.value <= bound) other else FailEquation
            is NotEqEquation -> if (other.value <= bound) AndEquation(this, other) else this
            is LowerEqualEquation -> if (bound <= other.bound) this else other
            is GreaterEqualEquation -> if (bound >= other.bound) AndEquation(this, other) else FailEquation
            else -> mergeCommon(other)
        }
    }

    override fun canSolve(): Boolean = true
}

private data class GreaterEqualEquation(override val varName: String, val bound: Int) : VariableRestriction {

    override fun not(): Equation = LowerEqualEquation(varName, bound + 1)

    override fun merge(other: Equation): Equation {
        if (other is VariableRestriction && varName != other.varName) return AndEquation(this, other)
        return when (other) {
            is EqEquation -> if (other.value >= bound) other else FailEquation
            is NotEqEquation -> if (other.value >= bound) AndEquation(this, other) else this
            is LowerEqualEquation -> if (bound <= other.bound) AndEquation(this, other) else FailEquation
            is GreaterEqualEquation -> if (bound >= other.bound) this else other
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
    is AndEquation -> other.equations.fold(this) { acc, next -> acc.merge(next) }
    is OrEquation -> OrEquation(other.equations.map(this::merge).toSet())
    is OkEquation -> this
    else -> FailEquation
}

internal val PyExpression.equation: Equation
    get() = when (this) {
        is PyBinaryExpression -> equation
        is PyPrefixExpression -> equation
        is PyParenthesizedExpression -> equation
        else -> OkEquation
    }

private val PyBinaryExpression.equation: Equation
    get() {
        if (leftExpression is PyReferenceExpression && rightExpression is PyReferenceExpression) {
            return OkEquation  // TODO
        } else if (leftExpression is PyReferenceExpression) {
            val name = leftExpression.name
            val value = rightExpression?.bigInt?.toInt()
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
        } else if (rightExpression is PyReferenceExpression) {
            val name = rightExpression?.name
            val value = leftExpression.bigInt?.toInt()
            if (name == null || value == null) return OkEquation
            return when (operator) {
                PyTokenTypes.LT -> GreaterEqualEquation(name, value + 1)
                PyTokenTypes.GT -> LowerEqualEquation(name, value - 1)
                PyTokenTypes.LE -> GreaterEqualEquation(name, value)
                PyTokenTypes.GE -> LowerEqualEquation(name, value)
                PyTokenTypes.EQEQ -> EqEquation(name, value)
                PyTokenTypes.NE -> NotEqEquation(name, value)
                else -> OkEquation
            }
        }

        val leftEquation = leftExpression.equation
        val rightEquation = rightExpression?.equation ?: return OkEquation
        return when (operator) {
            PyTokenTypes.AND_KEYWORD -> leftEquation.merge(rightEquation)
            PyTokenTypes.OR_KEYWORD -> OrEquation(leftEquation, rightEquation)
            else -> OkEquation
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

private val PyParenthesizedExpression.equation: Equation
    get() = containedExpression?.equation ?: OkEquation
