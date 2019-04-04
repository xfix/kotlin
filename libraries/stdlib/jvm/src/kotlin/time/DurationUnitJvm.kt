/*
 * Copyright 2010-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */


package kotlin.time

import java.util.concurrent.TimeUnit

//Actual typealias 'DurationUnit' has no corresponding expected declaration
//The following declaration is incompatible because modality is different:
//    public final expect enum class DurationUnit : Enum<DurationUnit>

//@Suppress("ACTUAL_WITHOUT_EXPECT")
//public actual typealias DurationUnit = java.util.concurrent.TimeUnit


public actual enum class DurationUnit(internal val actualUnit: TimeUnit) {
    NANOSECONDS(TimeUnit.NANOSECONDS),
    /**
     * Time unit representing one microsecond, which is 1/1000 of a millisecond.
     */
    MICROSECONDS(TimeUnit.MICROSECONDS),
    /**
     * Time unit representing one millisecond, which is 1/1000 of a second.
     */
    MILLISECONDS(TimeUnit.MILLISECONDS),
    /**
     * Time unit representing one second.
     */
    SECONDS(TimeUnit.SECONDS),
    /**
     * Time unit representing one minute.
     */
    MINUTES(TimeUnit.MINUTES),
    /**
     * Time unit representing one hour.
     */
    HOURS(TimeUnit.HOURS),
    /**
     * Time unit representing one day, which always equals 24 hours.
     */
    DAYS(TimeUnit.DAYS);
}

