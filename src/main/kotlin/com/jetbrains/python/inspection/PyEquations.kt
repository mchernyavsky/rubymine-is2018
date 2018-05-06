package com.jetbrains.python.inspection

import com.jetbrains.python.PyTokenTypes
import com.jetbrains.python.psi.*

internal interface Equation {
    fun not(): Equation
    fun join(other: Equation): Equation
    fun meet(other: Equation): Equation
    fun solve(): Boolean? = null
}

private interface VariableRestriction : Equation {
    val varName: String
}

private data class EqEquation(override val varName: String, val value: Int) : VariableRestriction {

    override fun not(): Equation = NotEqEquation(varName, value)

    override fun join(other: Equation): Equation {
        if (other is VariableRestriction && varName != other.varName) return OrEquation(this, other)
        return when (other) {
            is EqEquation,
            is LowerEqualEquation,
            is GreaterEqualEquation,
            is TrulyEquation,
            is FalsyEquation -> OrEquation(this, other)
            is NotEqEquation -> if (value != other.value) OrEquation(this, other) else ConstTrueEquation
            else -> joinCommon(other)
        }
    }

    override fun meet(other: Equation): Equation {
        if (other is VariableRestriction && varName != other.varName) return AndEquation(this, other)
        return when (other) {
            is EqEquation -> if (value == other.value) this else ConstFalseEquation
            is NotEqEquation -> if (value != other.value) this else ConstFalseEquation
            is LowerEqualEquation -> if (value <= other.bound) this else ConstFalseEquation
            is GreaterEqualEquation -> if (value >= other.bound) this else ConstFalseEquation
            is TrulyEquation -> if (value != 0) other else ConstFalseEquation
            is FalsyEquation -> if (value == 0) other else ConstFalseEquation
            else -> meetCommon(other)
        }
    }
}

private data class NotEqEquation(override val varName: String, val value: Int) : VariableRestriction {

    override fun not(): Equation = EqEquation(varName, value)

    override fun join(other: Equation): Equation {
        if (other is VariableRestriction && varName != other.varName) return OrEquation(this, other)
        return when (other) {
            is NotEqEquation,
            is LowerEqualEquation,
            is GreaterEqualEquation,
            is TrulyEquation,
            is FalsyEquation -> OrEquation(this, other)
            is EqEquation -> if (value != other.value) OrEquation(this, other) else ConstTrueEquation
            else -> joinCommon(other)
        }
    }

    override fun meet(other: Equation): Equation {
        if (other is VariableRestriction && varName != other.varName) return AndEquation(this, other)
        return when (other) {
            is NotEqEquation,
            is LowerEqualEquation,
            is TrulyEquation,
            is GreaterEqualEquation -> AndEquation(this, other)
            is FalsyEquation -> if (value != 0) AndEquation(this, other) else ConstFalseEquation
            is EqEquation -> if (value != other.value) other else ConstFalseEquation
            else -> meetCommon(other)
        }
    }
}

private data class LowerEqualEquation(override val varName: String, val bound: Int) : VariableRestriction {

    override fun not(): Equation = GreaterEqualEquation(varName, bound + 1)

    override fun join(other: Equation): Equation {
        if (other is VariableRestriction && varName != other.varName) return OrEquation(this, other)
        return when (other) {
            is EqEquation,
            is NotEqEquation,
            is LowerEqualEquation,
            is TrulyEquation,
            is FalsyEquation -> OrEquation(this, other)
            is GreaterEqualEquation -> if (other.bound - bound > 1) OrEquation(this, other) else ConstTrueEquation
            else -> joinCommon(other)
        }
    }

    override fun meet(other: Equation): Equation {
        if (other is VariableRestriction && varName != other.varName) return AndEquation(this, other)
        return when (other) {
            is NotEqEquation,
            is LowerEqualEquation,
            is TrulyEquation -> AndEquation(this, other)
            is EqEquation -> if (other.value <= bound) other else ConstFalseEquation
            is GreaterEqualEquation -> if (bound >= other.bound) AndEquation(this, other) else ConstFalseEquation
            is FalsyEquation -> if (bound >= 0) this else ConstFalseEquation
            else -> meetCommon(other)
        }
    }
}

private data class GreaterEqualEquation(override val varName: String, val bound: Int) : VariableRestriction {

    override fun not(): Equation = LowerEqualEquation(varName, bound - 1)

    override fun join(other: Equation): Equation {
        if (other is VariableRestriction && varName != other.varName) return OrEquation(this, other)
        return when (other) {
            is EqEquation,
            is NotEqEquation,
            is GreaterEqualEquation,
            is TrulyEquation,
            is FalsyEquation -> OrEquation(this, other)
            is LowerEqualEquation -> if (bound - other.bound > 1) OrEquation(this, other) else ConstTrueEquation
            else -> joinCommon(other)
        }
    }

    override fun meet(other: Equation): Equation {
        if (other is VariableRestriction && varName != other.varName) return AndEquation(this, other)
        return when (other) {
            is NotEqEquation,
            is GreaterEqualEquation,
            is TrulyEquation -> AndEquation(this, other)
            is EqEquation -> if (other.value >= bound) other else ConstFalseEquation
            is LowerEqualEquation -> if (bound <= other.bound) AndEquation(this, other) else ConstFalseEquation
            is FalsyEquation -> if (bound <= 0) this else ConstFalseEquation
            else -> meetCommon(other)
        }
    }
}

private class TrulyEquation(override val varName: String) : VariableRestriction {

    override fun not(): Equation = FalsyEquation(varName)

    override fun join(other: Equation): Equation {
        if (other is VariableRestriction && varName != other.varName) return OrEquation(this, other)
        return when (other) {
            is EqEquation,
            is NotEqEquation,
            is LowerEqualEquation,
            is GreaterEqualEquation,
            is TrulyEquation -> OrEquation(this, other)
            is FalsyEquation -> ConstTrueEquation
            else -> joinCommon(other)
        }
    }

    override fun meet(other: Equation): Equation {
        if (other is VariableRestriction && varName != other.varName) return AndEquation(this, other)
        return when (other) {
            is EqEquation -> if (other.value != 0) other else ConstFalseEquation
            is NotEqEquation -> AndEquation(this, other)
            is LowerEqualEquation -> if (other.bound >= 0) other else ConstFalseEquation
            is GreaterEqualEquation -> if (other.bound <= 0) other else ConstFalseEquation
            is TrulyEquation -> this
            is FalsyEquation -> ConstFalseEquation
            else -> meetCommon(other)
        }
    }
}

private class FalsyEquation(override val varName: String) : VariableRestriction {

    override fun not(): Equation = TrulyEquation(varName)

    override fun join(other: Equation): Equation {
        if (other is VariableRestriction && varName != other.varName) return OrEquation(this, other)
        return when (other) {
            is EqEquation,
            is NotEqEquation,
            is LowerEqualEquation,
            is GreaterEqualEquation,
            is FalsyEquation -> OrEquation(this, other)
            is TrulyEquation -> ConstTrueEquation
            else -> joinCommon(other)
        }
    }

    override fun meet(other: Equation): Equation {
        if (other is VariableRestriction && varName != other.varName) return AndEquation(this, other)
        return when (other) {
            is EqEquation -> if (other.value == 0) other else ConstFalseEquation
            is NotEqEquation -> if (other.value != 0) this else ConstFalseEquation
            is LowerEqualEquation -> if (other.bound >= 0) this else ConstFalseEquation
            is GreaterEqualEquation -> if (other.bound <= 0) this else ConstFalseEquation
            is TrulyEquation -> ConstFalseEquation
            is FalsyEquation -> AndEquation(this, other)
            else -> meetCommon(other)
        }
    }
}

private data class AndEquation(val first: Equation, val second: Equation) : Equation {

    override fun not(): Equation = first.not().join(second.not())

    override fun join(other: Equation): Equation = when (other) {
        is EqEquation -> other.join(this)
        is NotEqEquation -> other.join(this)
        is LowerEqualEquation -> other.join(this)
        is GreaterEqualEquation -> other.join(this)
        is TrulyEquation -> other.join(this)
        is FalsyEquation -> other.join(this)
        else -> joinCommon(other)
    }

    override fun meet(other: Equation): Equation = when (other) {
        is EqEquation -> other.meet(this)
        is NotEqEquation -> other.meet(this)
        is LowerEqualEquation -> other.meet(this)
        is GreaterEqualEquation -> other.meet(this)
        is TrulyEquation -> other.meet(this)
        is FalsyEquation -> other.meet(this)
        else -> meetCommon(other)
    }

    override fun solve(): Boolean? {
        val firstResult = first.solve()
        val secondResult = second.solve()
        return when {
            firstResult == false || secondResult == false -> false
            firstResult == null || secondResult == null -> null
            else -> true
        }
    }
}

private data class OrEquation(val first: Equation, val second: Equation) : Equation {

    override fun not(): Equation = first.not().meet(second.not())

    override fun join(other: Equation): Equation = when (other) {
        is EqEquation -> other.join(this)
        is NotEqEquation -> other.join(this)
        is LowerEqualEquation -> other.join(this)
        is GreaterEqualEquation -> other.join(this)
        is TrulyEquation -> other.join(this)
        is FalsyEquation -> other.join(this)
        else -> joinCommon(other)
    }

    override fun meet(other: Equation): Equation = when (other) {
        is EqEquation -> other.meet(this)
        is NotEqEquation -> other.meet(this)
        is LowerEqualEquation -> other.meet(this)
        is GreaterEqualEquation -> other.meet(this)
        is TrulyEquation -> other.meet(this)
        is FalsyEquation -> other.meet(this)
        else -> meetCommon(other)
    }

    override fun solve(): Boolean? {
        val firstResult = first.solve()
        val secondResult = second.solve()
        return when {
            firstResult == true || secondResult == true -> true
            firstResult == null || secondResult == null -> null
            else -> false
        }
    }
}

private object ConstTrueEquation : Equation {
    override fun not(): Equation = ConstFalseEquation
    override fun join(other: Equation): Equation = ConstTrueEquation
    override fun meet(other: Equation): Equation = other
    override fun solve(): Boolean = true
}

private object ConstFalseEquation : Equation {
    override fun not(): Equation = ConstTrueEquation
    override fun join(other: Equation): Equation = other
    override fun meet(other: Equation): Equation = ConstTrueEquation
    override fun solve(): Boolean = false
}

private object UnknownEquation : Equation {
    override fun not(): Equation = UnknownEquation
    override fun join(other: Equation): Equation = if (other === ConstFalseEquation) ConstFalseEquation else this
    override fun meet(other: Equation): Equation = if (other === ConstTrueEquation) ConstTrueEquation else this
    override fun solve(): Boolean? = null
}

private fun Equation.joinCommon(other: Equation): Equation = when (other) {
    is AndEquation -> AndEquation(join(other.first), join(other.second))
    is OrEquation -> OrEquation(join(other.first), join(other.second))
    is ConstTrueEquation,
    is UnknownEquation,
    is ConstFalseEquation -> other.join(this)
    else -> this
}

private fun Equation.meetCommon(other: Equation): Equation = when (other) {
    is AndEquation -> AndEquation(meet(other.first), meet(other.second))
    is OrEquation -> OrEquation(meet(other.first), meet(other.second))
    is ConstTrueEquation,
    is UnknownEquation,
    is ConstFalseEquation -> other.meet(this)
    else -> this
}

internal val PyExpression.equation: Equation
    get() = when (this) {
        is PyBinaryExpression -> equation
        is PyPrefixExpression -> equation
        is PyReferenceExpression -> equation
        is PyParenthesizedExpression -> equation
        else -> UnknownEquation
    }

private val PyBinaryExpression.equation: Equation
    get() {
        val leftExpression = leftExpression
        val rightExpression = rightExpression ?: return UnknownEquation

        fun makeVariableRestriction(
                name: String?,
                operator: PyElementType?,
                expression: PyExpression?
        ): Equation {
            val value = expression?.bigInt?.toInt()
            if (name == null || value == null) return UnknownEquation
            return when (operator) {
                PyTokenTypes.LT -> LowerEqualEquation(name, value - 1)
                PyTokenTypes.GT -> GreaterEqualEquation(name, value + 1)
                PyTokenTypes.LE -> LowerEqualEquation(name, value)
                PyTokenTypes.GE -> GreaterEqualEquation(name, value)
                PyTokenTypes.EQEQ -> EqEquation(name, value)
                PyTokenTypes.NE -> NotEqEquation(name, value)
                else -> UnknownEquation
            }
        }

        return when {
            operator == PyTokenTypes.AND_KEYWORD -> leftExpression.equation.meet(rightExpression.equation)
            operator == PyTokenTypes.OR_KEYWORD -> leftExpression.equation.join(rightExpression.equation)
            leftExpression is PyReferenceExpression && rightExpression is PyReferenceExpression ->
                UnknownEquation  // TODO
            leftExpression is PyReferenceExpression ->
                makeVariableRestriction(leftExpression.name, operator, rightExpression)
            rightExpression is PyReferenceExpression ->
                makeVariableRestriction(rightExpression.name, operator?.flip, leftExpression)
            else -> UnknownEquation
        }
    }

private val PyPrefixExpression.equation: Equation
    get() {
        val operand = operand?.equation ?: return UnknownEquation
        return when (operator) {
            PyTokenTypes.NOT_KEYWORD -> operand.not()
            else -> UnknownEquation
        }
    }

private val PyReferenceExpression.equation: Equation
    get() = name?.let { TrulyEquation(it) } ?: UnknownEquation

private val PyParenthesizedExpression.equation: Equation
    get() = containedExpression?.equation ?: UnknownEquation

private val PyElementType.flip: PyElementType
    get() = when (this) {
        PyTokenTypes.LT -> PyTokenTypes.GE
        PyTokenTypes.GT -> PyTokenTypes.LE
        PyTokenTypes.LE -> PyTokenTypes.GT
        PyTokenTypes.GE -> PyTokenTypes.LT
        else -> this
    }
