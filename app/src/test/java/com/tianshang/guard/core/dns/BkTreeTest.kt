package com.tianshang.guard.core.dns

import com.tianshang.guard.BaseUnitTest
import org.junit.Assert
import org.junit.Before
import org.junit.Test

class BkTreeTest : BaseUnitTest() {

    private lateinit var tree: BkTree

    @Before
    fun setUp() {
        tree = BkTree(threshold = 3)
    }

    @Test
    fun `insert and search exact match`() {
        tree.insert("google.com")
        val results = tree.search("google.com")
        Assert.assertEquals(1, results.size)
        Assert.assertEquals("google.com", results[0])
    }

    @Test
    fun `search returns matches within threshold`() {
        tree.insert("google.com")
        tree.insert("gogle.com")
        val results = tree.search("google.com")
        Assert.assertTrue(results.contains("google.com"))
        Assert.assertTrue(results.contains("gogle.com"))
    }

    @Test
    fun `search returns empty list for distant query`() {
        tree.insert("google.com")
        val results = tree.search("yahoo.com")
        Assert.assertTrue(results.isEmpty())
    }

    @Test
    fun `hasNearMatch returns true for near match`() {
        tree.insert("gogle.com")
        Assert.assertTrue(tree.hasNearMatch("google.com"))
    }

    @Test
    fun `hasNearMatch returns false for distant string`() {
        tree.insert("abc")
        Assert.assertFalse(tree.hasNearMatch("xyzxyz"))
    }

    @Test
    fun `hasNearMatch returns false for empty tree`() {
        Assert.assertFalse(tree.hasNearMatch("anything"))
    }

    @Test
    fun `findClosest returns best match`() {
        tree.insert("gogle.com")
        tree.insert("gooogle.com")
        val closest = tree.findClosest("google.com")
        Assert.assertNotNull(closest)
        Assert.assertEquals("gogle.com", closest!!.first)
    }

    @Test
    fun `findClosest returns null for empty tree`() {
        val closest = tree.findClosest("anything")
        Assert.assertNull(closest)
    }

    @Test
    fun `duplicate insertion does not increase size`() {
        tree.insert("test.com")
        tree.insert("test.com")
        Assert.assertEquals(1, tree.getSize())
    }

    @Test
    fun `multiple insertions track size correctly`() {
        tree.insert("a")
        tree.insert("b")
        tree.insert("c")
        Assert.assertEquals(3, tree.getSize())
    }
}
