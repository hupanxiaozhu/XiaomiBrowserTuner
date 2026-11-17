package com.hupan.hookbrowser.update

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [compareVersions] 的回归测试。
 *
 * 这里钉的是「版本号比较」这条容易写错又没人注意的边界：字典序比较会把
 * "1.10.0" 判成小于 "1.9.0"，直接导致「有新版却提示已是最新」。
 */
class VersionCompareTest {

    @Test
    fun sameVersionIsEqual() {
        assertEquals(0, compareVersions("2.1.0", "2.1.0"))
    }

    @Test
    fun doubleDigitMinorBeatsSingleDigit() {
        assertEquals(1, compareVersions("1.10.0", "1.9.0"))
        assertEquals(-1, compareVersions("1.9.0", "1.10.0"))
    }

    @Test
    fun patchLevelMatters() {
        assertEquals(1, compareVersions("2.1.1", "2.1.0"))
        assertEquals(-1, compareVersions("2.0.9", "2.1.0"))
    }

    @Test
    fun shorterVersionPadsWithZero() {
        assertEquals(0, compareVersions("2.1", "2.1.0"))
        assertEquals(1, compareVersions("2.1.1", "2.1"))
    }

    @Test
    fun nonNumericSegmentsTreatedAsZero() {
        assertEquals(0, compareVersions("abc", "0"))
        assertEquals(1, compareVersions("1.2.x", "1.1.9"))
    }

    @Test
    fun largeVersionNumbers() {
        assertEquals(1, compareVersions("20.32.1011110", "20.27.1010901"))
    }
}
