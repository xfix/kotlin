/*
 * Copyright 2010-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.plugin.mpp

import org.gradle.api.DefaultTask
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFiles
import org.gradle.api.tasks.TaskAction
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.utils.isParentOf
import java.io.File
import javax.inject.Inject

open class TransformKotlinGranularMetadata
@Inject constructor(
    @get:Internal
    val kotlinSourceSet: KotlinSourceSet,

    @get:OutputDirectory
    val outputsDir: File
) : DefaultTask() {

    private val transformation =
        GranularMetadataTransformation(project, kotlinSourceSet)

    @get:Internal
    internal val metadataDependencyTransformationResults: Iterable<MetadataDependencyResolution>
        get() = transformation.metadataDependencyResolutions

    @get:Internal
    internal val filesByDependency: Map<out MetadataDependencyResolution, FileCollection>
        get() = metadataDependencyTransformationResults.filterIsInstance<MetadataDependencyResolution.ChooseVisibleSourceSets>()
            .associate { it to project.files(it.getMetadataFiles(outputsDir, doProcessFiles = false)) }

    @Suppress("UNUSED")
    @get:OutputFiles
    val allOutputFiles: Iterable<FileCollection>
        get() = filesByDependency.values

    @TaskAction
    fun transformMetadata() {
        transformation.lazyTransform

        // Access all replacement files to trigger metadata extraction.
        metadataDependencyTransformationResults
            .filterIsInstance<MetadataDependencyResolution.ChooseVisibleSourceSets>()
            .forEach { result ->
                val resultFiles = result.getMetadataFiles(outputsDir, doProcessFiles = true)

                if (result.projectDependency == null) {
                    // Also assert that all files extracted from non-project dependencies are placed inside [outputsDir],
                    // as otherwise Gradle won't correctly track their up-do-date state
                    resultFiles.forEach { (_, files) ->
                        files.forEach { file ->
                            assert(outputsDir.isParentOf(file)) {
                                "The metadata replacement file $file for dependency ${result.dependency} should be placed inside $outputsDir"
                            }
                        }
                    }
                }
            }
    }
}