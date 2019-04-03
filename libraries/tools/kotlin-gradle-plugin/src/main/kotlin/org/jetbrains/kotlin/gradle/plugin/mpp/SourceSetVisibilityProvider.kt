/*
 * Copyright 2010-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.plugin.mpp

import org.gradle.api.Project
import org.gradle.api.artifacts.ResolvedDependency
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.targets.metadata.getPublishedPlatformCompilations

internal data class DependencySourceSetVisibilityResult(
    val sourceSetsVisibleByThisSourceSet: Set<String>,
    val sourceSetsVisibleThroughDependsOn: Set<String>
)

internal class SourceSetVisibilityProvider(
    private val project: Project
) {
    fun getVisibleSourceSets(
        visibleFrom: KotlinSourceSet,
        mppDependency: ResolvedDependency,
        dependencyProjectStructureMetadata: KotlinProjectStructureMetadata,
        otherProject: Project?
    ): DependencySourceSetVisibilityResult {
        val visibleByThisSourceSet = getVisibleSourceSetsImpl(visibleFrom, mppDependency, dependencyProjectStructureMetadata, otherProject)

        val visibleByParents = visibleFrom.dependsOn
            .flatMapTo(mutableSetOf()) { getVisibleSourceSetsImpl(it, mppDependency, dependencyProjectStructureMetadata, otherProject) }

        return DependencySourceSetVisibilityResult(visibleByThisSourceSet, visibleByParents)
    }

    @Suppress("UnstableApiUsage")
    private fun getVisibleSourceSetsImpl(
        visibleFrom: KotlinSourceSet,
        mppDependency: ResolvedDependency,
        dependencyProjectMetadata: KotlinProjectStructureMetadata,
        otherProject: Project?
    ): Set<String> {
        val compilations = CompilationSourceSetUtil.compilationsBySourceSets(project).getValue(visibleFrom)

        var visiblePlatformVariantNames: Set<String> = compilations
            .filter { it.target.platformType != KotlinPlatformType.common }
            .mapNotNullTo(mutableSetOf()) {
                project.configurations.getByName(it.compileDependencyConfigurationName)
                    // Resolve the configuration but don't trigger artifacts download, only download component metainformation:
                    .incoming.resolutionResult.allComponents
                    .find { it.moduleVersion?.group == mppDependency.moduleGroup && it.moduleVersion?.name == mppDependency.moduleName }
                    ?.variant?.displayName
            }

        if (otherProject != null) {
            val publishedVariants = getPublishedPlatformCompilations(otherProject).keys
            visiblePlatformVariantNames = visiblePlatformVariantNames
                .map { configurationName ->
                    publishedVariants.first { it.dependencyConfigurationName == configurationName }.name
                }.toSet()
        }

        return dependencyProjectMetadata.sourceSetNamesByVariantName
            .filterKeys { it in visiblePlatformVariantNames }
            .values.let { if (it.isEmpty()) emptySet() else it.reduce { acc, item -> acc intersect item } }
    }
}