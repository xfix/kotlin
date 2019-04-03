/*
 * Copyright 2010-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.plugin.mpp

import org.gradle.api.Project
import org.gradle.api.artifacts.*
import org.gradle.api.file.FileCollection
import org.jetbrains.kotlin.gradle.dsl.multiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.plugin.sources.getSourceSetHierarchy
import org.jetbrains.kotlin.gradle.targets.metadata.ALL_SOURCE_SETS_API_METADATA_CONFIGURATION_NAME
import org.jetbrains.kotlin.gradle.targets.metadata.buildKotlinProjectStructureMetadata
import java.io.File
import java.util.*
import java.util.concurrent.Callable
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import javax.xml.parsers.DocumentBuilderFactory

internal sealed class MetadataDependencyResolution(
    val dependency: ResolvedDependency,
    val projectDependency: ProjectDependency?
) {
    class UseOriginalDependency(
        dependency: ResolvedDependency,
        projectDependency: ProjectDependency?
    ) : MetadataDependencyResolution(dependency, projectDependency)

    class ExcludeAsUnrequested(
        dependency: ResolvedDependency,
        projectDependency: ProjectDependency?
    ) : MetadataDependencyResolution(dependency, projectDependency)

    abstract class ChooseVisibleSourceSets(
        dependency: ResolvedDependency,
        projectDependency: ProjectDependency?,
        val allVisibleSourceSetNames: Set<String>,
        val visibleSourceSetNamesExcludingDependsOn: Set<String>,
        val visibleTransitiveDependencies: Set<ResolvedDependency>
    ) : MetadataDependencyResolution(dependency, projectDependency) {
        /** Returns the mapping of source set names to files which should be used as the [dependency] parts representing the source sets.
         * If any temporary files need to be created, their paths are built from the [baseDir].
         * If [doProcessFiles] is true, these temporary files are actually re-created, otherwise only their paths are returned, while the
         * files might be missing.
         */
        abstract fun getMetadataFiles(baseDir: File, doProcessFiles: Boolean): Map<String, FileCollection>
    }
}

open class GranularMetadataTransformation(
    val project: Project,
    val kotlinSourceSet: KotlinSourceSet
) {
    internal val lazyTransform: Unit by lazy {
        transform()
    }

    private val metadataDependencyResolutionsImpl = mutableListOf<MetadataDependencyResolution>()

    internal val metadataDependencyResolutions: Iterable<MetadataDependencyResolution> by lazy {
        lazyTransform
        metadataDependencyResolutionsImpl
    }

    private val visitedDependenciesImpl = mutableSetOf<ResolvedDependency>()

    private lateinit var allDependenciesImpl: Set<ResolvedDependency>

    private val knownProjectDependencies =
        mutableMapOf<Pair<String?, String>, ProjectDependency>()

    private fun transform() {
        // Keep parents of each dependency, too. We need a dependency's parent when it's an MPP's metadata module dependency:
        // in this case, the parent is the MPP's root module
        data class ResolvedDependencyWithParent(
            val dependency: ResolvedDependency,
            val parent: ResolvedDependency?
        )

        val directRequestedDependencies = run {
            val requestedDirectDependencies = kotlinSourceSet.getSourceSetHierarchy().flatMapTo(mutableSetOf()) {
                project.configurations.getByName(it.apiConfigurationName).allDependencies
            }

            requestedDirectDependencies.filterIsInstance<ProjectDependency>().associateTo(knownProjectDependencies) {
                (it.group to it.name) to it
            }

            requestedDirectDependencies.map { it.group to it.name }.toSet()
        }

        val resolvedDependenciesFromAllSourceSets =
            project.configurations.getByName(ALL_SOURCE_SETS_API_METADATA_CONFIGURATION_NAME)
                .resolvedConfiguration.lenientConfiguration

        allDependenciesImpl = mutableSetOf<ResolvedDependency>().apply {
            fun visit(resolvedDependency: ResolvedDependency) {
                if (add(resolvedDependency)) {
                    resolvedDependency.children.forEach { visit(it) }
                }
            }
            resolvedDependenciesFromAllSourceSets.firstLevelModuleDependencies.forEach { visit(it) }
        }

        val resolvedDependencyQueue: Queue<ResolvedDependencyWithParent> = ArrayDeque<ResolvedDependencyWithParent>().apply {
            addAll(
                resolvedDependenciesFromAllSourceSets.firstLevelModuleDependencies
                    .filter { (it.moduleGroup to it.moduleName) in directRequestedDependencies }
                    .map { ResolvedDependencyWithParent(it, null) }
            )
        }

        while (resolvedDependencyQueue.isNotEmpty()) {
            val (resolvedDependency, parent) = resolvedDependencyQueue.poll()

            val group = resolvedDependency.moduleGroup
            val name = resolvedDependency.moduleName

            var projectDependency: ProjectDependency? = null

            knownProjectDependencies[group to name]?.let {
                projectDependency = it
                val dependencyProject = it.dependencyProject
                val configuration = dependencyProject.configurations.getByName(resolvedDependency.configuration)
                configuration.allDependencies.filterIsInstance<ProjectDependency>().associateTo(knownProjectDependencies) {
                    (it.group to it.name) to it
                }
            }

            visitedDependenciesImpl.add(resolvedDependency)

            val dependencyResult = processDependency(resolvedDependency, parent, projectDependency)
            metadataDependencyResolutionsImpl.add(dependencyResult)

            val transitiveDependenciesToVisit = when (dependencyResult) {
                is MetadataDependencyResolution.ChooseVisibleSourceSets -> dependencyResult.visibleTransitiveDependencies
                is MetadataDependencyResolution.UseOriginalDependency -> resolvedDependency.children
                else -> emptySet()
            }

            resolvedDependencyQueue.addAll(
                transitiveDependenciesToVisit
                    .filter { it !in visitedDependenciesImpl }
                    .map { ResolvedDependencyWithParent(it, resolvedDependency) }
            )
        }

        allDependenciesImpl.forEach { resolvedDependency ->
            if (resolvedDependency !in visitedDependenciesImpl) {
                metadataDependencyResolutionsImpl.add(
                    MetadataDependencyResolution.ExcludeAsUnrequested(resolvedDependency, null)
                )
            }
        }
    }

    /**
     * If the [module] is an MPP metadata module, we extract [KotlinProjectStructureMetadata] and do the following:
     *
     * * determine the set *S* of source sets that should be seen in the [kotlinSourceSet] by finding which variants the [parent]
     *   dependency got resolved for the compilations where [kotlinSourceSet] participates:
     *
     * * transform the single Kotlin metadata artifact into a set of Kotlin metadata artifacts for the particular source sets in
     *   *S* and add them to [transformedOutputsImpl]
     *
     * * based on the project structure metadata, determine which of the module's dependencies are requested by the
     *   source sets in *S*, only these transitive dependencies, ignore the others;
     */
    private fun processDependency(
        module: ResolvedDependency,
        parent: ResolvedDependency?,
        projectDependency: ProjectDependency?
    ): MetadataDependencyResolution {

        // If the module is non-MPP, we need to visit all of its children, but otherwise, they are filtered below.
        val transitiveDependenciesToVisit = module.children.toMutableSet()

        val mppDependencyMetadataExtractor =
            when {
                projectDependency != null -> ProjectMppDependencyMetadataExtractor(project, module, projectDependency.dependencyProject)
                parent != null -> JarArtifactMppDependencyMetadataExtractor(project, module)
                else -> null
            }

        val projectStructureMetadata = mppDependencyMetadataExtractor?.getProjectStructureMetadata()

        if (projectStructureMetadata == null) {
            return MetadataDependencyResolution.UseOriginalDependency(module, projectDependency)
        }

        val (allVisibleSourceSets, visibleByParents) =
            SourceSetVisibilityProvider(project).getVisibleSourceSets(
                kotlinSourceSet,
                parent ?: module,
                projectStructureMetadata,
                projectDependency?.dependencyProject
            )

        // Keep only the transitive dependencies requested by the visible source sets:
        val requestedTransitiveDependencies: Set<Pair<String, String>> =
            mutableSetOf<Pair<String, String>>().apply {
                projectStructureMetadata.sourceSetModuleDependencies
                    .filterKeys { it in allVisibleSourceSets }
                    .forEach { addAll(it.value) }
            }

        transitiveDependenciesToVisit.removeIf {
            (it.moduleGroup to it.moduleName) !in requestedTransitiveDependencies
        }

        val visibleExcludingDependsOn = allVisibleSourceSets.filterTo(mutableSetOf()) { it !in visibleByParents }

        return object : MetadataDependencyResolution.ChooseVisibleSourceSets(
            module,
            projectDependency,
            allVisibleSourceSets,
            visibleExcludingDependsOn,
            transitiveDependenciesToVisit
        ) {
            override fun getMetadataFiles(baseDir: File, doProcessFiles: Boolean): Map<String, FileCollection> =
                mppDependencyMetadataExtractor.getVisibleSourceSetsMetadata(visibleExcludingDependsOn, baseDir, doProcessFiles)
        }
    }
}

// FIXME remove this workaround once the IDE import correctly filters the dependencies based on transformation results
@Suppress("UnstableApiUsage")
internal fun GranularMetadataTransformation.applyToConfiguration(
    project: Project,
    configuration: Configuration,
    excludeUnrequestedDependencies: Boolean
) {
    val outputFilesToAdd = project.files(Callable {
        metadataDependencyResolutions
            .filterIsInstance<MetadataDependencyResolution.ChooseVisibleSourceSets>()
            .map {
                it.getMetadataFiles(
                    project.buildDir.resolve("tmp/kotlinMetadata/${kotlinSourceSet.name}"),
                    doProcessFiles = true
                ).values
            }
    })

    project.dependencies.add(configuration.name, project.dependencies.create(outputFilesToAdd))

    // TODO get rid of this logic, filter the actual resolved dependencies at the consumer side?
    if (excludeUnrequestedDependencies) {
        configuration.withDependencies { _ ->
            val unrequestedDependencies = metadataDependencyResolutions
                .filterIsInstance<MetadataDependencyResolution.ExcludeAsUnrequested>()
                .map { it.dependency }

            unrequestedDependencies.forEach {
                configuration.exclude(mapOf("group" to it.moduleGroup, "module" to it.moduleName))
            }
        }
    }
}

private abstract class MppDependencyMetadataExtractor(val project: Project, val dependency: ResolvedDependency) {
    abstract fun getProjectStructureMetadata(): KotlinProjectStructureMetadata?
    abstract fun getVisibleSourceSetsMetadata(
        visibleSourceSetNames: Set<String>,
        baseDir: File,
        doProcessFiles: Boolean
    ): Map<String, FileCollection>
}

private class ProjectMppDependencyMetadataExtractor(
    project: Project,
    dependency: ResolvedDependency,
    private val dependencyProject: Project
) : MppDependencyMetadataExtractor(project, dependency) {
    override fun getProjectStructureMetadata(): KotlinProjectStructureMetadata? = buildKotlinProjectStructureMetadata(dependencyProject)

    override fun getVisibleSourceSetsMetadata(
        visibleSourceSetNames: Set<String>,
        baseDir: File,
        doProcessFiles: Boolean
    ): Map<String, FileCollection> =
        dependencyProject.multiplatformExtension.targets.getByName(KotlinMultiplatformPlugin.METADATA_TARGET_NAME).compilations
            .filter { it.defaultSourceSet.name in visibleSourceSetNames }
            .associate { it.defaultSourceSet.name to it.output.classesDirs }
}

private class JarArtifactMppDependencyMetadataExtractor(
    project: Project,
    dependency: ResolvedDependency
) : MppDependencyMetadataExtractor(project, dependency) {

    private val artifact = dependency.moduleArtifacts.singleOrNull { it.extension == "jar" }

    override fun getProjectStructureMetadata(): KotlinProjectStructureMetadata? {
        val artifactFile = artifact?.file ?: return null

        return ZipFile(artifactFile).use { zip ->
            val metadata = zip.getEntry("META-INF/$MULTIPLATFORM_PROJECT_METADATA_FILE_NAME")
                ?: return null

            val metadataXmlDocument = zip.getInputStream(metadata).use { inputStream ->
                DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(inputStream)
            }
            parseKotlinSourceSetMetadataFromXml(metadataXmlDocument)
        }
    }

    override fun getVisibleSourceSetsMetadata(
        visibleSourceSetNames: Set<String>,
        baseDir: File,
        doProcessFiles: Boolean
    ): Map<String, FileCollection> {
        artifact as ResolvedArtifact
        val artifactFile = artifact.file
        val moduleId = artifact.moduleVersion.id

        if (doProcessFiles) {
            if (baseDir.isDirectory) {
                baseDir.deleteRecursively()
            }
            baseDir.mkdirs()
        }

        return extractSourceSetMetadataFromJar(moduleId, visibleSourceSetNames, artifactFile, baseDir, doProcessFiles)
    }

    private fun extractSourceSetMetadataFromJar(
        module: ModuleVersionIdentifier,
        chooseSourceSetsByNames: Set<String>,
        artifactJar: File,
        baseDir: File,
        doProcessFiles: Boolean
    ): Map<String, FileCollection> {
        val moduleString = "${module.group}-${module.name}-${module.version}"
        val transformedModuleRoot = run { baseDir.resolve(moduleString).also { it.mkdirs() } }

        val resultFiles = mutableMapOf<String, FileCollection>()

        ZipFile(artifactJar).use { zip ->
            zip.entries().asSequence().filter { it.name.substringBefore("/") in chooseSourceSetsByNames }
                .groupBy { it.name.substringBefore("/") }
                .forEach { (sourceSetName, entries) ->
                    val extractToJarFile = transformedModuleRoot.resolve("$moduleString-$sourceSetName.jar")
                    resultFiles[sourceSetName] = project.files(extractToJarFile)

                    if (doProcessFiles) {
                        ZipOutputStream(extractToJarFile.outputStream()).use { resultZipOutput ->
                            entries.forEach forEachEntry@{ entry ->
                                if (entry.isDirectory) return@forEachEntry
                                val newEntry = ZipEntry(entry.name.substringAfter("/"))

                                zip.getInputStream(entry).use { inputStream ->
                                    resultZipOutput.putNextEntry(newEntry)
                                    resultZipOutput.write(inputStream.readBytes())
                                    resultZipOutput.closeEntry()
                                }
                            }
                        }
                    }
                }
        }

        return resultFiles
    }
}
