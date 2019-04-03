/*
 * Copyright 2010-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.targets.metadata

import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.attributes.Usage
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.component.SoftwareComponentInternal
import org.gradle.api.plugins.BasePlugin
import org.gradle.api.tasks.bundling.Jar
import org.jetbrains.kotlin.gradle.dsl.KotlinCommonOptions
import org.jetbrains.kotlin.gradle.dsl.multiplatformExtension
import org.jetbrains.kotlin.gradle.dsl.multiplatformExtensionOrNull
import org.jetbrains.kotlin.gradle.plugin.*
import org.jetbrains.kotlin.gradle.plugin.mpp.*
import org.jetbrains.kotlin.gradle.plugin.sources.DefaultKotlinSourceSet
import org.jetbrains.kotlin.gradle.plugin.sources.getSourceSetHierarchy
import org.jetbrains.kotlin.gradle.tasks.KotlinTasksProvider
import org.jetbrains.kotlin.gradle.utils.addExtendsFromRelation
import org.jetbrains.kotlin.gradle.utils.isGradleVersionAtLeast
import org.jetbrains.kotlin.gradle.utils.lowerCamelCaseName
import java.util.concurrent.Callable

internal const val METADATA_DEPENDENCY_ELEMENTS_CONFIGURATION_NAME = "metadataDependencyElements"

internal const val ALL_SOURCE_SETS_API_METADATA_CONFIGURATION_NAME = "allSourceSetsApiMetadata"
internal const val ALL_SOURCE_SETS_IMPLEMENTATION_METADATA_CONFIGURATION_NAME = "allSourceSetsImplementationMetadata"
internal const val ALL_SOURCE_SETS_COMPILE_ONLY_METADATA_CONFIGURATION_NAME = "allSourceSetsCompileOnlyMetadata"
internal const val ALL_SOURCE_SETS_RUNTIME_ONLY_METADATA_CONFIGURATION_NAME = "allSourceSetsRuntimeOnlyMetadata"

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

    override fun setupCompilationDependencyFiles(project: Project, compilation: KotlinCompilation<KotlinCommonOptions>) {
        compilation.compileDependencyFiles = project.files()
        /** See [createTransformedMetadataClasspath] and its usage. */
    }

    private fun createMetadataDependencyElementsConfiguration(target: KotlinMetadataTarget) {
        val project = target.project

        // TODO it may be not necessary to publish this empty JAR if the IDE can import dependencies with no artifacts
        @Suppress("DEPRECATION") // the new API was only introduced in Gradle 5.1
        val emptyMetadataJar = project.tasks.create("emptyMetadataJar", org.gradle.jvm.tasks.Jar::class.java) { emptyMetadata ->
            emptyMetadata.appendix = target.name
            emptyMetadata.classifier = "dependencies"
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

        val granularMetadataTransformationTaskBySourceSet = mutableMapOf<KotlinSourceSet, TransformKotlinGranularMetadata>()

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

                target.apiElementsConfiguration.extendsFrom(project.configurations.getByName(sourceSet.apiConfigurationName))

                @Suppress("UnstableApiUsage")
                granularMetadataTransformationTaskBySourceSet[sourceSet] = project.tasks.create(
                    "transform${sourceSet.name.capitalize()}DependenciesMetadata",
                    TransformKotlinGranularMetadata::class.java,
                    sourceSet,
                    buildDir.resolve("kotlinSourceSetMetadata/${sourceSet.name}")
                )

                val granularMetadataTransformation = GranularMetadataTransformation(
                    project,
                    kotlinSourceSet = sourceSet
                ).also { (sourceSet as DefaultKotlinSourceSet).apiMetadataDependencyTransformation = it }

                val apiMetadataConfiguration = project.configurations.getByName(sourceSet.apiMetadataConfigurationName)

//                apiMetadataConfiguration.attributes.attribute(
//                    KotlinSourceSetsMetadata.ATTRIBUTE,
//                    KotlinSourceSetsMetadata.DEPENDENCIES_ONLY
//                )

                // FIXME remove this workaround once the IDE import correctly filters the dependencies based on transformation results
//                granularMetadataTransformation.applyToConfiguration(
//                    project,
//                    apiMetadataConfiguration,
//                    excludeUnrequestedDependencies = true
//                )

                apiMetadataConfiguration.let {
                    sourceSet.getSourceSetHierarchy().forEach { dependsOnSourceSet ->
                        addExtendsFromRelation(it.name, dependsOnSourceSet.apiConfigurationName)
                    }
                }

                addExtendsFromRelation(
                    metadataCompilation.compileDependencyConfigurationName,
                    ALL_SOURCE_SETS_API_METADATA_CONFIGURATION_NAME
                )

                sourceSet to metadataCompilation
            }

        sourceSetsWithMetadataCompilations.forEach { (sourceSet, metadataCompilation) ->
            val metadataTransformationTasks = mutableSetOf<TransformKotlinGranularMetadata>()

            sourceSet.getSourceSetHierarchy().forEach { hierarchySourceSet ->
                if (hierarchySourceSet != sourceSet) {
                    val dependencyCompilation = sourceSetsWithMetadataCompilations.getValue(hierarchySourceSet)

                    project.dependencies.run {
                        add(
                            metadataCompilation.compileDependencyConfigurationName,
                            create(dependencyCompilation.output.classesDirs.filter { it.exists() })
                        )
                    }
                }

                val transformMetadataTask = granularMetadataTransformationTaskBySourceSet.getValue(hierarchySourceSet)
                metadataTransformationTasks.add(transformMetadataTask)
            }

            metadataCompilation.compileDependencyFiles += createTransformedMetadataClasspath(
                project.configurations.getByName(ALL_SOURCE_SETS_API_METADATA_CONFIGURATION_NAME),
                project,
                metadataTransformationTasks
            )
        }

        val generateMetadata =
            createGenerateProjectStructureMetadataTask()

        allMetadataJar.from(project.files(Callable { generateMetadata.resultXmlFile }).builtBy(generateMetadata)) { spec ->
            spec.into("META-INF").rename { MULTIPLATFORM_PROJECT_METADATA_FILE_NAME }
        }
    }

    private fun createTransformedMetadataClasspath(
        fromConfiguration: Configuration,
        project: Project,
        granularMetadataTransformationTasks: Set<TransformKotlinGranularMetadata>
    ): FileCollection {
        return project.files(Callable {
            val allTransformationResults = granularMetadataTransformationTasks
                .flatMap { it.metadataDependencyTransformationResults }
                .groupBy { it.dependency }
                // TODO do we have modules that resolve to more than one artifact? use file sets?
                .filterKeys { it.moduleArtifacts.size == 1 }
                .mapKeys { (dependency, _) -> dependency.moduleArtifacts.single().file }

            val transformedFiles = granularMetadataTransformationTasks.flatMap { it.filesByDependency.toList() }.toMap()

            mutableSetOf<Any/* File | FileCollection */>().apply {
                fromConfiguration.forEach { file ->
                    val resolutions = allTransformationResults[file]
                    if (resolutions == null) {
                        add(file)
                    } else {
                        val chooseVisibleSourceSets =
                            resolutions.filterIsInstance<MetadataDependencyResolution.ChooseVisibleSourceSets>()

                        if (chooseVisibleSourceSets.isNotEmpty()) {
                            add(chooseVisibleSourceSets.map { transformedFiles.getValue(it) })
                        } else if (resolutions.any { it is MetadataDependencyResolution.UseOriginalDependency }) {
                            add(file)
                        }
                    }
                }
            }
        }).builtBy(granularMetadataTransformationTasks)
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

    private fun Project.createGenerateProjectStructureMetadataTask(): GenerateProjectStructureMetadata =
        tasks.create("generateProjectStructureMetadata", GenerateProjectStructureMetadata::class.java) { task ->
            task.kotlinProjectStructureMetadata = checkNotNull(buildKotlinProjectStructureMetadata(project))
        }
}

internal fun buildKotlinProjectStructureMetadata(project: Project): KotlinProjectStructureMetadata? {
    val sourceSetsWithMetadataCompilations =
        project.multiplatformExtensionOrNull?.targets?.getByName(KotlinMultiplatformPlugin.METADATA_TARGET_NAME)?.compilations?.associate {
            it.defaultSourceSet to it
        } ?: return null

    val publishedVariantsNamesWithCompilation = getPublishedPlatformCompilations(project).mapKeys { it.key.name }

    return KotlinProjectStructureMetadata(
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

internal fun getPublishedPlatformCompilations(project: Project): Map<KotlinUsageContext, KotlinCompilation<*>> {
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