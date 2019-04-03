/*
 * Copyright 2010-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.projectModel.basic

import com.intellij.util.text.nullize
import org.jetbrains.kotlin.projectModel.ProjectStructureResolveModel
import org.jetbrains.kotlin.projectModel.ResolveModule
import org.jetbrains.kotlin.projectModel.ResolveModuleBuilder
import org.jetbrains.kotlin.resolve.DefaultBuiltInPlatforms
import org.jetbrains.kotlin.resolve.TargetPlatform
import java.io.File
import java.io.InputStreamReader
import java.io.Reader

abstract class ProjectStructureParser<M : ResolveModule, ProjectBuilder : ProjectStructureResolveModel<M>, ModuleBuilder : ResolveModuleBuilder<M>>(
    private val projectRoot: File
) {
    private val builderByName: MutableMap<String, ModuleBuilder> = hashMapOf()

    abstract fun createModuleBuilder(): ModuleBuilder

    abstract fun createProjectBuilder(): ProjectBuilder

    fun parse(text: String) {
        val reader = InputStreamReader(text.byteInputStream())
        generateSequence {
            reader.parseNextDeclaration()
        }

        val projectBuilder = createProjectBuilder()
    }

    private fun Reader.parseNextDeclaration() {
        val firstWord = nextWord() ?: return
        if (firstWord == DEFINE_MODULE_KEYWORD) parseModuleDefinition() else parseDependenciesDefinition()
    }

    private fun Reader.parseModuleDefinition() {
        val name = nextWord()!!

        // skip until attributes list begins
        consumeUntilFirst { it == '{' }

        // read whole attributes list
        val attributesString = consumeUntilFirst { it == '}' }.first

        val attributesMap = attributesString.split(";").map { it.splitIntoExactlyTwoParts(DEPENDENCIES_ARROW) }.toMap()

        require(builderByName[name] == null) { "Redefinition of module $name" }
        val builder = createModuleBuilder().also { it.name = name }

        initializeModuleByAttributes(builder, attributesMap)
    }

    private fun initializeModuleByAttributes(builder: ModuleBuilder, attributes: Map<String, String>) {
        val platformAttribute = attributes["platform"]
        requireNotNull(platformAttribute) { "Missing required attribute 'platform' for module ${builder.name}" }
        builder.platform = parsePlatform(platformAttribute)

        val root = attributes["root"]
        requireNotNull(root) { "Missing required attribute 'root' for module ${builder.name}" }
        builder.root = File(projectRoot, root)

        doAdditionalInitialization(builder, attributes)
    }

    protected open fun doAdditionalInitialization(builder: ModuleBuilder, attributes: Map<String, String>) {}

    private fun Reader.parseDependenciesDefinition() {
        fun getDeclaredBuilder(name: String): ModuleBuilder = builderByName[name].also {
            requireNotNull(it) { "Module $name wasn't declared. All modules should be declared explicitly" }
        }!!

        val moduleName = nextWord() ?: return
        val builder = getDeclaredBuilder(moduleName)

        val arrow = nextWord()
        require(arrow == DEPENDENCIES_ARROW) {
            "Malformed declaration: '$moduleName $arrow ...' \n" + HELP_TEXT
        }

        val dependencies = consumeUntilFirst { it == '\n' }.first.trim().split(",").map {
            getDeclaredBuilder(it)
        }

        builder.dependencies.addAll(dependencies)
    }

    private fun parsePlatform(platformString: String): TargetPlatform {
        val platformsByPlatformName = DefaultBuiltInPlatforms.allSimplePlatforms
            .map { it.single().platformName to it.single() }
            .toMap()

        val platforms = parseRepeatableAttribute(platformString).map {
            platformsByPlatformName[it] ?: error(
                "Unknown platform $it. Available platforms: ${platformsByPlatformName.keys.joinToString()}"
            )
        }.toSet()

        return TargetPlatform(platforms)
    }

    protected fun parseRepeatableAttribute(value: String): List<String> {
        require(value.startsWith("[") && value.endsWith("]")) {
            "Value of repeatable attribute should be declared in square brackets: [foo, bar, baz]"
        }
        return value.removePrefix("[").removeSuffix("]").split(",")
    }


    companion object {
        val DEFINE_MODULE_KEYWORD = "MODULE"
        val DEPENDENCIES_ARROW = "->"
        val MODULE_ATTRIBUTES_OPENING_SEPARATOR = "{"
        val MODULE_ATTRIBUTES_CLOSING_SEPARATOR = "}"

        val HELP_TEXT = "Possible declarations:\n" +
                "- Module declaration: 'MODULE myModuleName { ...attributes... }\n" +
                "- Module dependencies: myModuleName -> otherModule1, otherModule2, ..." +
                "Note that each module should be explicitly declared before referring to it in dependencies"
    }
}

fun Reader.consumeUntilFirst(shouldStop: (Char) -> Boolean): Pair<String, Char?> {
    var char = nextChar()
    return buildString {
        while (char != null && !shouldStop(char!!)) {
            append(char!!)
            char = nextChar()
        }
    } to char
}

private fun Reader.nextChar(): Char? =
    read().takeUnless { it == -1 }?.toChar()
private fun Reader.nextWord(): String? =
    consumeUntilFirst { it.isWhitespace() || it == '\n' }.first.nullize()

private fun String.splitIntoExactlyTwoParts(separator: String): Pair<String, String> {
    val result = split(separator)
    require(result.size == 2) { "$this can not be split into exactly two parts with separator $separator" }
    return result[0] to result[1]
}