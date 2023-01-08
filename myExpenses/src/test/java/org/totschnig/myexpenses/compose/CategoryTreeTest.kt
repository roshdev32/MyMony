package org.totschnig.myexpenses.compose

import com.google.common.truth.Truth
import org.junit.Test
import org.totschnig.myexpenses.viewmodel.data.Category

class CategoryTreeTest {

    @Test
    fun shouldKeepDeeplyNestedMatch() {
        val category = Category(
            label = "ROOT", children = listOf(
                Category(
                    label = "1", isMatching = false, children = listOf(
                        Category(
                            label = "1.1", isMatching = false, children = listOf(
                                Category(label = "1.1.2", isMatching = true)
                            )
                        )
                    )
                )
            )
        )
        Truth.assertThat(category.pruneNonMatching()).isEqualTo(category)
    }

    @Test
    fun shouldFlattenDepthFirst() {
        val category = Category(
            label = "ROOT", children = listOf(
                Category(
                    label = "1", children = listOf(
                        Category(
                            label = "1.1", children = listOf(
                                Category(label = "1.1.1")
                            )
                        )
                    )
                ),
                Category(
                    label = "2", children = listOf(
                        Category(
                            label = "2.1", children = listOf(
                                Category(label = "2.1.1")
                            )
                        )
                    )
                )
            )
        )
        Truth.assertThat(category.flatten().map { it.label }).containsExactly(
            "ROOT", "1", "1.1", "1.1.1", "2", "2.1", "2.1.1"
        )
    }

    @Test
    fun shouldRemoveNonMatchingChildren() {
        val category = Category(
            label = "ROOT", children = listOf(
                Category(
                    id= 1, label = "1", isMatching = false, children = listOf(
                        Category(
                            id = 2, label = "1.1", isMatching = false, children = listOf(
                                Category(id =3 ,label = "1.1.2", isMatching = false)
                            )
                        )
                    )
                )
            )
        )
        Truth.assertThat(category.pruneNonMatching()!!.children).isEmpty()
    }
}