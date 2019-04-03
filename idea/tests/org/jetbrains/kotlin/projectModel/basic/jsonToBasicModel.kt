/*
 * Copyright 2010-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.projectModel.basic

import org.jetbrains.kotlin.js.parser.sourcemaps.JsonNull
import org.jetbrains.kotlin.js.parser.sourcemaps.parseJson

fun parseInJsonFormat(text: String) {
    val jsonTree = parseJson(text)

}

interface JsonVisitor {
    fun visitNull(node: JsonNull)
    fun visitBoolean()
}