/*
 * Copyright 2010-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.targets.metadata

import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.attributes.Usage
import org.gradle.api.internal.component.SoftwareComponentInternal
import org.gradle.api.plugins.BasePlugin
import org.gradle.api.tasks.bundling.Jar
import org.jetbrains.kotlin.gradle.dsl.KotlinCommonOptions
import org.jetbrains.kotlin.gradle.dsl.multiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.*
import org.jetbrains.kotlin.gradle.plugin.KotlinCommonSourceSetProcessor
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSetProcessor
import org.jetbrains.kotlin.gradle.plugin.mpp.*
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinGranularMetadataTransformation
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinProjectStructureMetadata
import org.jetbrains.kotlin.gradle.plugin.sources.getSourceSetHierarchy
import org.jetbrains.kotlin.gradle.plugin.whenEvaluated
import org.jetbrains.kotlin.gradle.tasks.KotlinTasksProvider
import org.jetbrains.kotlin.gradle.utils.addExtendsFromRelation
import org.jetbrains.kotlin.gradle.utils.isGradleVersionAtLeast
import org.jetbrains.kotlin.gradle.utils.lowerCamelCaseName
import java.util.concurrent.Callable

internal const val METADATA_DEPENDENCY_ELEMENTS_CONFIGURATION_NAME = "metadataDependencyElements"

internal const val ALL_SOURCE_SETS_API_METADATA_CONFIGURATION_NAME = "allSourceSetsApiDependenciesKotlinMetadata"

class KotlinMetadataTargetConfigurator(kotlinPluginVersion: String) :
    KotlinTargetConfigurator<KotlinCommonCompilation>(
        createDefaultSourceSets = false,
        createTestCompilation = false,
        kotlinPluginVersion = kotlinPluginVersion
    ) {

    private val KotlinOnlyTarget<KotlinCommonCompilation>.apiElementsConfiguration: Configuration
        get() = project.configurations.getByName(apiElementsConfigurationName)

    override fun configureTarget(target: KotlinOnlyTarget<KotlinCommonCompilation>) {
        super.configureTarget(target)

        target as KotlinMetadataTarget

        val jar = target.project.tasks.getByName(target.artifactsTaskName) as Jar
        createMetadataCompilationsForCommonSourceSets(target, jar)

        createMetadataDependencyElementsConfiguration(target)
        createMergedSourceSetDependenciesConfiguration(target)

        target.apiElementsConfiguration.attributes.attribute(KotlinSourceSetsMetadata.ATTRIBUTE, KotlinSourceSetsMetadata.ALL_SOURCE_SETS)
    }

    private fun createMetadataDependencyElementsConfiguration(target: KotlinMetadataTarget) {
        val project = target.project

        // TODO it may be not necessary to publish this empty JAR if the IDE can import dependencies with no artifacts
        val emptyMetadataJar = project.tasks.create("emptyMetadataJar", org.gradle.jvm.tasks.Jar::class.java) { sourcesJar ->
            sourcesJar.appendix = target.name
            sourcesJar.classifier = "dependencies"
        }

        project.configurations.create(METADATA_DEPENDENCY_ELEMENTS_CONFIGURATION_NAME).also { mergedConfiguration ->
            mergedConfiguration.isCanBeConsumed = true
            mergedConfiguration.isCanBeResolved = false
            target.compilations.all { compilation ->
                project.addExtendsFromRelation(mergedConfiguration.name, compilation.apiConfigurationName)
            }
            mergedConfiguration.attributes.attribute(KotlinSourceSetsMetadata.ATTRIBUTE, KotlinSourceSetsMetadata.DEPENDENCIES_ONLY)
            mergedConfiguration.usesPlatformOf(target)
            mergedConfiguration.attributes.attribute(Usage.USAGE_ATTRIBUTE, KotlinUsages.producerApiUsage(target))

            project.artifacts.add(mergedConfiguration.name, emptyMetadataJar) {
                it.classifier = "metadata-dependencies"
            }
        }
    }

    override fun buildCompilationProcessor(compilation: KotlinCommonCompilation): KotlinSourceSetProcessor<*> {
        val tasksProvider = KotlinTasksProvider(compilation.target.targetName)
        return KotlinCommonSourceSetProcessor(compilation.target.project, compilation, tasksProvider, kotlinPluginVersion)
    }

    override fun createJarTask(target: KotlinOnlyTarget<KotlinCommonCompilation>): Jar {
        val result = target.project.tasks.create(target.artifactsTaskName, Jar::class.java)
        result.description = "Assembles a jar archive containing the metadata for all Kotlin source sets."
        result.group = BasePlugin.BUILD_GROUP

        if (isGradleVersionAtLeast(5, 2)) {
            result.archiveAppendix.convention(target.name.toLowerCase())
        } else {
            @Suppress("DEPRECATION")
            result.appendix = target.name.toLowerCase()
        }

        return result
    }

    private fun createMetadataCompilationsForCommonSourceSets(
        target: KotlinMetadataTarget,
        allMetadataJar: Jar
    ) = target.project.whenEvaluated {
        // Do this after all targets are configured by the user build script

        val publishedCommonSourceSets: Set<KotlinSourceSet> = getPublishedCommonSourceSets(project)

        val granularMetadataTransformationBySourceSet = mutableMapOf<KotlinSourceSet, KotlinGranularMetadataTransformation>()

        val sourceSetsWithMetadataCompilations =
            publishedCommonSourceSets.associate { sourceSet ->
                val metadataCompilation = when (sourceSet.name) {
                    // Historically, we already had a 'main' compilation in metadata targets; TODO consider removing it
                    KotlinSourceSet.COMMON_MAIN_SOURCE_SET_NAME -> target.compilations.getByName(KotlinCompilation.MAIN_COMPILATION_NAME)
                    else -> target.compilations.create(lowerCamelCaseName(sourceSet.name)) { compilation ->
                        compilation.addExactSourceSetsEagerly(setOf(sourceSet))
                    }
                }

                allMetadataJar.from(metadataCompilation.output.allOutputs) { spec ->
                    spec.into(metadataCompilation.defaultSourceSet.name)
                }

                val compileDependenciesConfiguration =
                    project.configurations.getByName(metadataCompilation.compileDependencyConfigurationName)

                project.configurations.getByName(metadataCompilation.compileDependencyConfigurationName).run {
                    attributes.attribute(
                        KotlinSourceSetsMetadata.ATTRIBUTE,
                        KotlinSourceSetsMetadata.DEPENDENCIES_ONLY
                    )
                }

                val granularMetadataTransformation = KotlinGranularMetadataTransformation(
                    project,
                    kotlinSourceSet = sourceSet,
                    outputsDir = project.buildDir.resolve("kotlinMetadata/${sourceSet.name}")
                ).also { granularMetadataTransformationBySourceSet[sourceSet] = it }

                val apiMetadataConfiguration = project.configurations.getByName(sourceSet.apiMetadataConfigurationName)

                listOf(compileDependenciesConfiguration, apiMetadataConfiguration).forEach {
                    it.attributes.attribute(
                        KotlinSourceSetsMetadata.ATTRIBUTE,
                        KotlinSourceSetsMetadata.DEPENDENCIES_ONLY
                    )
                    granularMetadataTransformation.applyToConfiguration(project, it)

                    sourceSet.getSourceSetHierarchy().forEach { dependsOnSourceSet ->
                        addExtendsFromRelation(it.name, dependsOnSourceSet.apiConfigurationName)
                    }
                }

                sourceSet to metadataCompilation
            }

        sourceSetsWithMetadataCompilations.forEach { (sourceSet, metadataCompilation) ->
            // TODO better place for this statement?
            target.apiElementsConfiguration.extendsFrom(project.configurations.getByName(sourceSet.apiConfigurationName))

            sourceSet.getSourceSetHierarchy().filter { it != sourceSet }.forEach { otherSourceSet ->
                val dependencyCompilation = sourceSetsWithMetadataCompilations.getValue(otherSourceSet)

                project.addExtendsFromRelation(
                    metadataCompilation.compileDependencyConfigurationName,
                    dependencyCompilation.apiConfigurationName
                )

                project.dependencies.run {
                    add(
                        metadataCompilation.compileDependencyConfigurationName,
                        create(dependencyCompilation.output.classesDirs.filter { it.exists() })
                    )

                    // TODO maybe implement a more elegant solution (this one has an ugly map of transformations)?
                    project.dependencies.add(
                        metadataCompilation.compileDependencyConfigurationName,
                        project.dependencies.create(granularMetadataTransformationBySourceSet.getValue(otherSourceSet).allOutputFiles)
                    )
                }
            }
        }

        val publishedVariantsNamesWithCompilation = getPublishedPlatformCompilations(project).mapKeys { it.key.name }

        val generateMetadata =
            createGenerateProjectStructureMetadataTask(publishedVariantsNamesWithCompilation, sourceSetsWithMetadataCompilations)

        allMetadataJar.from(project.files(Callable { generateMetadata.resultXmlFile }).builtBy(generateMetadata)) { spec ->
            spec.into("META-INF").rename { MULTIPLATFORM_PROJECT_METADATA_FILE_NAME }
        }
    }

    private fun createMergedSourceSetDependenciesConfiguration(target: KotlinMetadataTarget) {
        val project = target.project

        project.configurations.create(ALL_SOURCE_SETS_API_METADATA_CONFIGURATION_NAME).apply {
            isCanBeConsumed = false
            isCanBeResolved = true
            attributes {
                usesPlatformOf(target)
                attributes.attribute(Usage.USAGE_ATTRIBUTE, KotlinUsages.consumerApiUsage(target))
                attributes.attribute(KotlinSourceSetsMetadata.ATTRIBUTE, KotlinSourceSetsMetadata.ALL_SOURCE_SETS)
            }

            project.multiplatformExtension.sourceSets.all {
                extendsFrom(project.configurations.getByName(it.apiConfigurationName))
            }
        }
    }

    private fun getPublishedCommonSourceSets(project: Project): Set<KotlinSourceSet> {
        val compilationsBySourceSet: Map<KotlinSourceSet, Set<KotlinCompilation<*>>> =
            CompilationSourceSetUtil.compilationsBySourceSets(project)

        // For now, we will only compile metadata from source sets used by multiple targets
        val sourceSetsUsedInMultipleTargets = compilationsBySourceSet.filterValues { compilations ->
            compilations.map { it.target }.distinct().size > 1
        }

        // We don't want to publish source set metadata from source sets that don't participate in any compilation that is published,
        // such as test or benchmark sources; find all published compilations:
        val publishedCompilations = getPublishedPlatformCompilations(project).values

        return sourceSetsUsedInMultipleTargets
            .filterValues { compilations -> compilations.any { it in publishedCompilations } }
            .keys
    }

    private fun getPublishedPlatformCompilations(project: Project): Map<KotlinUsageContext, KotlinCompilation<*>> {
        val result = mutableMapOf<KotlinUsageContext, KotlinCompilation<*>>()

        project.multiplatformExtension.targets.withType(AbstractKotlinTarget::class.java).forEach { target ->
            if (target.platformType == KotlinPlatformType.common)
                return@forEach

            target.kotlinComponents
                .filterIsInstance<SoftwareComponentInternal>()
                .forEach { component ->
                    component.usages
                        .filterIsInstance<KotlinUsageContext>()
                        .forEach { usage -> result[usage] = usage.compilation }
                }
        }

        return result
    }

    private fun Project.createGenerateProjectStructureMetadataTask(
        publishedVariantsNamesWithCompilation: Map<String, KotlinCompilation<*>>,
        sourceSetsWithMetadataCompilations: Map<KotlinSourceSet, AbstractKotlinCompilation<out KotlinCommonOptions>>
    ): GenerateProjectStructureMetadata =
        tasks.create("generateSourceSetsMetainformation", GenerateProjectStructureMetadata::class.java) { task ->
            task.kotlinProjectStructureMetadata = KotlinProjectStructureMetadata(
                sourceSetNamesByVariantName = publishedVariantsNamesWithCompilation.mapValues { (_, compilation) ->
                    compilation.allKotlinSourceSets.filter { it in sourceSetsWithMetadataCompilations }.map { it.name }.toSet()
                },
                sourceSetsDependsOnRelation = sourceSetsWithMetadataCompilations.keys.associate { sourceSet ->
                    sourceSet.name to sourceSet.dependsOn.filter { it in sourceSetsWithMetadataCompilations }.map { it.name }.toSet()
                },
                sourceSetModuleDependencies = sourceSetsWithMetadataCompilations.keys.associate { sourceSet ->
                    sourceSet.name to project.configurations.getByName(sourceSet.apiConfigurationName).allDependencies.map {
                        it.group.orEmpty() to it.name
                    }.toSet()
                }
            )
        }
}