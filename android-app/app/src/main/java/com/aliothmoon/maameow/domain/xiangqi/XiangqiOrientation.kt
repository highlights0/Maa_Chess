package com.aliothmoon.maameow.domain.xiangqi

/** Try the last visually confirmed orientation before paying for another model run. */
internal fun <T> selectXiangqiOrientation(
    preferred: Boolean,
    infer: (Boolean) -> T,
    valid: (T) -> Boolean,
    uncertainCount: (T) -> Int,
): T {
    val first = infer(preferred)
    if (valid(first)) return first
    val second = infer(!preferred)
    return if (valid(second) || uncertainCount(second) < uncertainCount(first)) second else first
}
