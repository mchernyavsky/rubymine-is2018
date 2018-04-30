package com.jetbrains.python.inspection

import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import com.jetbrains.python.PyTokenTypes
import com.jetbrains.python.inspections.PyInspection
import com.jetbrains.python.inspections.PyInspectionVisitor
import com.jetbrains.python.psi.*
import java.math.BigInteger


class PyConstantExpression : PyInspection() {

    override fun buildVisitor(
            holder: ProblemsHolder,
            isOnTheFly: Boolean,
            session: LocalInspectionToolSession
    ): PsiElementVisitor = Visitor(holder, session)

    class Visitor(holder: ProblemsHolder?, session: LocalInspectionToolSession) : PyInspectionVisitor(holder, session) {

        override fun visitPyIfStatement(node: PyIfStatement) {
            super.visitPyIfStatement(node)
            processIfPart(node.ifPart)
            for (part in node.elifParts) {
                processIfPart(part)
            }
        }

        private fun processIfPart(pyIfPart: PyIfPart) {
            val condition = pyIfPart.condition ?: return
            condition.bool?.let {
                registerProblem(condition, "The condition is always $it")
                return
            }
            if (!condition.equation.canSolve()) {
                registerProblem(condition, "The condition is always false")
            }
        }
    }
}

internal val PyExpression.bool: Boolean?
    get() = when (this) {
        is PyBoolLiteralExpression -> value
        is PyBinaryExpression -> bool
        is PyPrefixExpression -> bool
        is PyParenthesizedExpression -> containedExpression?.bool
        else -> null
    }

internal val PyExpression.bigInt: BigInteger?
    get() = when (this) {
        is PyNumericLiteralExpression -> this.bigIntegerValue
        is PyBinaryExpression -> bigInt
        is PyParenthesizedExpression -> containedExpression?.bigInt
        else -> null
    }

private val PyBinaryExpression.bool: Boolean?
    get() {
        val leftBigInt = leftExpression?.bigInt
        val rightBigInt = rightExpression?.bigInt
        if (leftBigInt != null && rightBigInt != null) {
            return when (operator) {
                PyTokenTypes.LT -> leftBigInt < rightBigInt
                PyTokenTypes.GT -> leftBigInt > rightBigInt
                PyTokenTypes.LE -> leftBigInt <= rightBigInt
                PyTokenTypes.GE -> leftBigInt >= rightBigInt
                PyTokenTypes.EQEQ -> leftBigInt == rightBigInt
                PyTokenTypes.NE -> leftBigInt != rightBigInt
                else -> null
            }
        }

        val leftBool = leftExpression?.bool
        val rightBool = rightExpression?.bool
        if (leftBool != null && rightBool != null) {
            return when (operator) {
                PyTokenTypes.AND_KEYWORD -> leftBool && rightBool
                PyTokenTypes.OR_KEYWORD -> leftBool || rightBool
                PyTokenTypes.EQEQ -> leftBool == rightBool
                PyTokenTypes.NE -> leftBool != rightBool
                else -> null
            }
        }

        return null
    }

private val PyBinaryExpression.bigInt: BigInteger?
    get() {
        val leftBigInt = leftExpression?.bigInt ?: return null
        val rightBigInt = rightExpression?.bigInt ?: return null
        return when (operator) {
            PyTokenTypes.PLUS -> leftBigInt + rightBigInt
            PyTokenTypes.MINUS -> leftBigInt - rightBigInt
            PyTokenTypes.MULT -> leftBigInt * rightBigInt
            PyTokenTypes.EXP -> leftBigInt.pow(rightBigInt.toInt())  // overflow
            PyTokenTypes.DIV -> if (rightBigInt != BigInteger.ZERO) (leftBigInt / rightBigInt) else null
            PyTokenTypes.PERC -> if (rightBigInt != BigInteger.ZERO) (leftBigInt % rightBigInt) else null
            else -> null
        }
    }

private val PyPrefixExpression.bool: Boolean?
    get() {
        val operand = operand?.bool ?: return null
        return when (operator) {
            PyTokenTypes.NOT_KEYWORD -> !operand
            else -> null
        }
    }
