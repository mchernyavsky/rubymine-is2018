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
            val condition = pyIfPart.condition
            condition?.bool?.let {
                registerProblem(condition, "The condition is always $it")
            }
        }

        private val PyExpression.bool: Boolean?
            get() = when (this) {
                is PyBoolLiteralExpression -> value
                is PyBinaryExpression -> bool
                else -> null
            }

        private val PyExpression.bigInt: BigInteger?
            get() = when (this) {
                is PyNumericLiteralExpression -> this.bigIntegerValue
                else -> null
            }

        private val PyBinaryExpression.bool: Boolean?
            get() {
                val leftOperand = leftExpression?.bigInt ?: return null
                val rightOperand = rightExpression?.bigInt ?: return null
                return when (operator) {
                    PyTokenTypes.LT -> leftOperand < rightOperand
                    PyTokenTypes.GT -> leftOperand > rightOperand
                    PyTokenTypes.LE -> leftOperand <= rightOperand
                    PyTokenTypes.GE -> leftOperand >= rightOperand
                    PyTokenTypes.EQEQ -> leftOperand == rightOperand
                    PyTokenTypes.NE -> leftOperand != rightOperand
                    else -> null
                }
            }
    }
}
