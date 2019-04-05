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
import org.jetbrains.kotlin.gradle.plugin.*
import org.jetbrains.kotlin.gradle.plugin.mpp.*
import org.jetbrains.kotlin.gradle.plugin.sources.*
import org.jetbrains.kotlin.gradle.tasks.KotlinTasksProvider
import org.jetbrains.kotlin.gradle.utils.addExtendsFromRelation
import org.jetbrains.kotlin.gradle.utils.isGradleVersionAtLeast
import org.jetbrains.kotlin.gradle.utils.lowerCamelCaseName
import java.io.File
import java.util.concurrent.Callable

internal const val ALL_COMPILE_METADATA_CONFIGURATION_NAME = "allSourceSetsCompileDependenciesMetadata"
internal const val ALL_RUNTIME_METADATA_CONFIGURATION_NAME = "allSourceSetsRuntimeDependenciesMetadata"

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
    }

    override fun setupCompilationDependencyFiles(project: Project, compilation: KotlinCompilation<KotlinCommonOptions>) {
        compilation.compileDependencyFiles = project.files()
        /** See [createTransformedMetadataClasspath] and its usage. */
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

    private fun transformGranularMetadataTaskName(sourceSet: KotlinSourceSet) =
        lowerCamelCaseName("transform", sourceSet.name, "DependenciesMetadata")

    private fun createMetadataCompilationsForCommonSourceSets(
        target: KotlinMetadataTarget,
        allMetadataJar: Jar
    ) = target.project.whenEvaluated {
        // Do this after all targets are configured by the user build script

        val publishedCommonSourceSets: Set<KotlinSourceSet> = getPublishedCommonSourceSets(project)

        listOf(ALL_COMPILE_METADATA_CONFIGURATION_NAME, ALL_RUNTIME_METADATA_CONFIGURATION_NAME).forEach { configurationName ->
            project.configurations.create(configurationName).apply {
                isCanBeConsumed = false
                isCanBeResolved = true

                usesPlatformOf(target)
                attributes.attribute(Usage.USAGE_ATTRIBUTE, KotlinUsages.consumerApiUsage(target))
                attributes.attribute(KotlinSourceSetsMetadata.ATTRIBUTE, KotlinSourceSetsMetadata.ALL_SOURCE_SETS)
            }
        }

        val sourceSetsWithMetadataCompilations: Map<KotlinSourceSet, KotlinCommonCompilation> =
            publishedCommonSourceSets.associate { sourceSet ->
                sourceSet to configureMetadataCompilation(target, sourceSet, allMetadataJar)
            }

        sourceSetsWithMetadataCompilations.forEach { (sourceSet, metadataCompilation) ->
            val compileMetadataTransformationTasksForHierarchy = mutableSetOf<TransformKotlinGranularMetadata>()

            // Adjust metadata compilation to support source set hierarchies, i.e. use both the outputs of dependsOn source set compilation
            // and their dependencies metadata transformed for compilation:
            sourceSet.getSourceSetHierarchy().forEach { hierarchySourceSet ->
                if (hierarchySourceSet != sourceSet) {
                    val dependencyCompilation = sourceSetsWithMetadataCompilations.getValue(hierarchySourceSet as DefaultKotlinSourceSet)
                    metadataCompilation.compileDependencyFiles += dependencyCompilation.output.classesDirs.filter { it.exists() }
                }

                project.tasks.withType(TransformKotlinGranularMetadata::class.java)
                    .findByName(transformGranularMetadataTaskName(hierarchySourceSet))
                    ?.let(compileMetadataTransformationTasksForHierarchy::add)
            }

            addExtendsFromRelation(metadataCompilation.compileDependencyConfigurationName, ALL_COMPILE_METADATA_CONFIGURATION_NAME)

            metadataCompilation.compileDependencyFiles += createTransformedMetadataClasspath(
                project.configurations.getByName(ALL_COMPILE_METADATA_CONFIGURATION_NAME),
                project,
                compileMetadataTransformationTasksForHierarchy
            )
        }

        val generateMetadata =
            createGenerateProjectStructureMetadataTask()

        allMetadataJar.from(project.files(Callable { generateMetadata.resultXmlFile }).builtBy(generateMetadata)) { spec ->
            spec.into("META-INF").rename { MULTIPLATFORM_PROJECT_METADATA_FILE_NAME }
        }
    }

    private fun configureMetadataCompilation(
        target: KotlinMetadataTarget,
        sourceSet: KotlinSourceSet,
        allMetadataJar: Jar
    ): KotlinCommonCompilation {
        val project = target.project

        val metadataCompilation = when (sourceSet.name) {
            // Historically, we already had a 'main' compilation in metadata targets; TODO consider removing it instead
            KotlinSourceSet.COMMON_MAIN_SOURCE_SET_NAME -> target.compilations.getByName(KotlinCompilation.MAIN_COMPILATION_NAME)
            else -> target.compilations.create(lowerCamelCaseName(sourceSet.name)) { compilation ->
                compilation.addExactSourceSetsEagerly(setOf(sourceSet))
            }
        }

        allMetadataJar.from(metadataCompilation.output.allOutputs) { spec ->
            spec.into(metadataCompilation.defaultSourceSet.name)
        }

        // With the metadata target, we publish all API dependencies of all source sets together
        target.apiElementsConfiguration.extendsFrom(project.configurations.getByName(sourceSet.apiConfigurationName))

        @Suppress("UnstableApiUsage")
        project.tasks.create(
            transformGranularMetadataTaskName(sourceSet),
            TransformKotlinGranularMetadata::class.java,
            sourceSet
        )

        // Setup dependency transformation for IDE import:
        KotlinDependencyScope.values().forEach { scope ->
            val allMetadataConfigurations = mutableListOf<Configuration>().apply {
                if (scope != KotlinDependencyScope.COMPILE_ONLY_SCOPE)
                    add(project.configurations.getByName(ALL_RUNTIME_METADATA_CONFIGURATION_NAME))
                if (scope != KotlinDependencyScope.RUNTIME_ONLY_SCOPE)
                    add(project.configurations.getByName(ALL_COMPILE_METADATA_CONFIGURATION_NAME))
            }

            val granularMetadataTransformation = GranularMetadataTransformation(
                project,
                sourceSet,
                listOf(scope),
                allMetadataConfigurations
            )

            (sourceSet as DefaultKotlinSourceSet).dependencyTransformations[scope] = granularMetadataTransformation

            val sourceSetMetadataConfigurationByScope = project.sourceSetMetadataConfigurationByScope(sourceSet, scope)

            granularMetadataTransformation.applyToConfiguration(sourceSetMetadataConfigurationByScope)

            val sourceSetDependencyConfigurationByScope = project.sourceSetDependencyConfigurationByScope(sourceSet, scope)

            // All source set dependencies except for compileOnly take part and agree in versions with all other runtime dependencies:
            if (scope != KotlinDependencyScope.COMPILE_ONLY_SCOPE) {
                project.addExtendsFromRelation(
                    ALL_RUNTIME_METADATA_CONFIGURATION_NAME,
                    sourceSetDependencyConfigurationByScope.name
                )
                project.addExtendsFromRelation(
                    sourceSetMetadataConfigurationByScope.name,
                    ALL_RUNTIME_METADATA_CONFIGURATION_NAME
                )
            }

            // All source set dependencies except for runtimeOnly take part and agree in versions with all other compile dependencies:
            if (scope != KotlinDependencyScope.RUNTIME_ONLY_SCOPE) {
                project.addExtendsFromRelation(
                    ALL_COMPILE_METADATA_CONFIGURATION_NAME,
                    sourceSetDependencyConfigurationByScope.name
                )
                project.addExtendsFromRelation(
                    sourceSetMetadataConfigurationByScope.name,
                    ALL_COMPILE_METADATA_CONFIGURATION_NAME
                )
            }
        }

        return metadataCompilation
    }

    /** Ensure that the [configuration] excludes the dependencies that are classified by this [GranularMetadataTransformation] as
     * [MetadataDependencyResolution.ExcludeAsUnrequested], and uses exactly the same versions as were resolved for the requested
     * dependencies during the transformation. */
    private fun GranularMetadataTransformation.applyToConfiguration(configuration: Configuration) {
        // Run this action immediately before the configuration first takes part in dependency resolution:
        @Suppress("UnstableApiUsage")
        configuration.withDependencies {
            val (unrequested, requested) = metadataDependencyResolutions
                .partition { it is MetadataDependencyResolution.ExcludeAsUnrequested }

            unrequested.forEach { configuration.exclude(mapOf("group" to it.dependency.moduleGroup, "module" to it.dependency.moduleName)) }

            requested.forEach {
                val notation = listOf(it.dependency.moduleGroup, it.dependency.moduleName, it.dependency.moduleVersion).joinToString(":")
                configuration.resolutionStrategy.force(notation)
            }
        }
    }


    private fun createTransformedMetadataClasspath(
        fromFiles: Iterable<File>,
        project: Project,
        granularMetadataTransformationTasks: Set<TransformKotlinGranularMetadata>
    ): FileCollection {
        return project.files(Callable {
            val allTransformationResults = granularMetadataTransformationTasks
                .flatMap { it.metadataDependencyTransformationResults }
                .groupBy { it.dependency }
                .filterKeys { it.moduleArtifacts.size == 1 } // TODO do we have modules that resolve to more than one artifact? use sets?
                .mapKeys { (dependency, _) -> dependency.moduleArtifacts.single().file }

            val transformedFiles = granularMetadataTransformationTasks.flatMap { it.filesByDependency.toList() }.toMap()

            mutableSetOf<Any /* File | FileCollection */>().apply {
                fromFiles.forEach { file ->
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