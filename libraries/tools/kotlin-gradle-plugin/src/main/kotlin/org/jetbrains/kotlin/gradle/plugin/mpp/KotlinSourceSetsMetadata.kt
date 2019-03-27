/*
 * Copyright 2010-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.plugin.mpp

import org.gradle.api.attributes.*

object KotlinSourceSetsMetadata {
    val ATTRIBUTE = Attribute.of("org.jetbrains.kotlin.sourceSetMetadata", String::class.java)

    const val DEPENDENCIES_ONLY = "dependenciesOnly"
    const val ALL_SOURCE_SETS = "allSourceSets"

    fun setupAttributesMatchingStrategy(attributesSchema: AttributesSchema) = with(attributesSchema) {
        attribute(ATTRIBUTE) {
            it.compatibilityRules.add(KotlinSourceSetCompatibilityRule::class.java)
            it.disambiguationRules.add(KotlinSourceSetDisambiguationRule::class.java)
        }
    }

    class KotlinSourceSetDisambiguationRule : AttributeDisambiguationRule<String> {
        override fun execute(t: MultipleCandidatesDetails<String>) = with(t) {
            when {
                consumerValue in candidateValues -> closestMatch(consumerValue)
                candidateValues.contains(DEPENDENCIES_ONLY) -> closestMatch(
                    DEPENDENCIES_ONLY
                )
            }
        }
    }

    class KotlinSourceSetCompatibilityRule : AttributeCompatibilityRule<String> {
        override fun execute(t: CompatibilityCheckDetails<String>) = with(t) {
            when {
                producerValue == DEPENDENCIES_ONLY -> compatible()
            }
        }
    }
}