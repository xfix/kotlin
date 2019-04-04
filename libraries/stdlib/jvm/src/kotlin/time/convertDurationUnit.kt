/*
 * Copyright 2010-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */
@file:kotlin.jvm.JvmMultifileClass()
@file:kotlin.jvm.JvmName("DurationUnitKt")

package kotlin.time

public actual fun convertDurationUnit(value: Double, sourceUnit: DurationUnit, targetUnit: DurationUnit): Double {
    val sourceInTargets = targetUnit.actualUnit.convert(1, sourceUnit.actualUnit)
    if (sourceInTargets > 0)
        return value * sourceInTargets

    val otherInThis = sourceUnit.actualUnit.convert(1, targetUnit.actualUnit)
    return value / otherInThis
}