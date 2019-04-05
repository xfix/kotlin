/*
 * Copyright 2010-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin

import com.intellij.lang.ASTNode
import com.intellij.lang.Language
import com.intellij.lang.PsiBuilderFactory
import com.intellij.openapi.project.Project
import com.intellij.psi.tree.ICompositeElementType
import com.intellij.psi.tree.IErrorCounterReparseableElementType
import org.jetbrains.kotlin.KtNodeTypes.BLOCK_CODE_FRAGMENT
import org.jetbrains.kotlin.KtNodeTypes.FUNCTION_LITERAL
import org.jetbrains.kotlin.KtNodeTypes.SCRIPT
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.lexer.KotlinLexer
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.parsing.KotlinParser
import org.jetbrains.kotlin.psi.KtBlockExpression

class BlockExpressionElementType : IErrorCounterReparseableElementType("BLOCK", KotlinLanguage.INSTANCE), ICompositeElementType {

    override fun createCompositeNode() = KtBlockExpression(null)

    override fun createNode(text: CharSequence?) = KtBlockExpression(text)

    override fun isParsable(parent: ASTNode?, buffer: CharSequence, fileLanguage: Language, project: Project) =
        fileLanguage == KotlinLanguage.INSTANCE &&
                BlockExpressionElementType.isAllowedParentNode(parent) &&
                BlockExpressionElementType.isReparseableBlock(buffer) &&
                super.isParsable(buffer, fileLanguage, project)

    override fun getErrorsCount(seq: CharSequence, fileLanguage: Language, project: Project) =
        ElementTypeUtils.getKotlinBlockImbalanceCount(seq)

    override fun parseContents(chameleon: ASTNode): ASTNode {
        val project = chameleon.psi.project
        val builder = PsiBuilderFactory.getInstance().createBuilder(
            project, chameleon, null, KotlinLanguage.INSTANCE, chameleon.chars
        )

        return KotlinParser.parseBlockExpression(builder).firstChildNode
    }

    companion object {

        private fun isAllowedParentNode(node: ASTNode?) =
            node != null &&
                    SCRIPT != node.elementType &&
                    FUNCTION_LITERAL != node.elementType &&
                    BLOCK_CODE_FRAGMENT != node.elementType

        fun isReparseableBlock(blockText: CharSequence): Boolean {
            val lexer = KotlinLexer()
            lexer.start(blockText)

            if (lexer.tokenType != KtTokens.LBRACE) return false

            lexer.advance()

            var identifierOnBack = false

            while (lexer.tokenType != null && lexer.tokenType != KtTokens.EOF) {
                if (lexer.tokenType == KtTokens.LBRACE) return true
                if (lexer.tokenType == KtTokens.RBRACE) return true

                //Captures a.b...
                if (identifierOnBack && lexer.tokenType == KtTokens.DOT) return true
                //Captures a(b,c,d)...
                if (identifierOnBack && lexer.tokenType == KtTokens.LPAR) return true

                //Captures lambda
                if (lexer.tokenType == KtTokens.ARROW) return false
                if (lexer.tokenType == KtTokens.COMMA) return false

                if (lexer.tokenType != KtTokens.WHITE_SPACE)
                    identifierOnBack = lexer.tokenType == KtTokens.IDENTIFIER

                lexer.advance()
            }
            return false
        }
    }
}