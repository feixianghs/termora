package app.termora

import java.awt.BorderLayout
import java.awt.Component
import java.awt.Container
import javax.swing.*
import kotlin.test.*

class TerminalSplitLayoutTest {
    private class Tab(private val title: String) : PropertyTerminalTab() {
        val input = JTextField(title)
        var focusRequests = 0
        val panel = object : JPanel(BorderLayout()) {
            override fun requestFocusInWindow(): Boolean {
                focusRequests++
                return true
            }
        }.apply { add(input) }
        var disposed = false
        override fun getTitle() = title
        override fun getIcon(): Icon = ImageIcon()
        override fun getJComponent() = panel
        override fun dispose() { disposed = true }
    }

    private fun layout() = TerminalSplitLayout({ it.getTitle() }, {}, {}, { _, _ -> })

    @Test
    fun `nested splits keep all terminals mounted when switching tabs`() = onEdt {
        val layout = layout()
        val a = Tab("A")
        val b = Tab("B")
        val c = Tab("C")
        val hosts = listOf(a, b, c).associateWith { layout.add(it) }
        assertTrue(layout.split(a, b, JSplitPane.HORIZONTAL_SPLIT))
        assertTrue(layout.split(b, c, JSplitPane.VERTICAL_SPLIT))

        for (tab in listOf(a, b, c)) {
            layout.show(tab)
            val host = hosts.getValue(tab)
            for (other in listOf(a, b, c)) assertTrue(SwingUtilities.isDescendingFrom(other.panel, host))
            assertEquals(listOf(JSplitPane.HORIZONTAL_SPLIT, JSplitPane.VERTICAL_SPLIT), splits(host).map { it.orientation })
            assertSame(tab, layout.findTab(tab.input))
        }
    }

    @Test
    fun `closing a pane promotes its sibling without losing other splits`() = onEdt {
        val layout = layout()
        val a = Tab("A")
        val b = Tab("B")
        val c = Tab("C")
        val host = layout.add(a)
        layout.add(b)
        layout.add(c)
        layout.split(a, b, JSplitPane.HORIZONTAL_SPLIT)
        layout.split(b, c, JSplitPane.VERTICAL_SPLIT)
        layout.show(b)

        layout.remove(b)
        layout.show(a)
        assertEquals(listOf(a, c), layout.groupTabs(a))
        assertTrue(SwingUtilities.isDescendingFrom(c.panel, host))
        assertFalse(SwingUtilities.isDescendingFrom(b.panel, host))
        assertEquals(1, splits(host).size)

        layout.remove(a)
        layout.show(c)
        assertEquals(listOf(c), layout.groupTabs(c))
        assertNull(layout.remove(c))
        assertTrue(layout.groupTabs(c).isEmpty())
        assertFalse(a.disposed || b.disposed || c.disposed)
    }

    @Test
    fun `restoring tabs preserves the existing sessions`() = onEdt {
        val layout = layout()
        val tabs = listOf(Tab("A"), Tab("B"), Tab("C"))
        val hosts = tabs.associateWith { layout.add(it) }
        layout.split(tabs[0], tabs[1], JSplitPane.HORIZONTAL_SPLIT)
        layout.split(tabs[1], tabs[2], JSplitPane.VERTICAL_SPLIT)
        layout.show(tabs[2])
        layout.unsplit(tabs[1])

        for (tab in tabs) {
            layout.show(tab)
            val host = hosts.getValue(tab)
            assertEquals(listOf(tab), layout.groupTabs(tab))
            assertTrue(SwingUtilities.isDescendingFrom(tab.panel, host))
            assertTrue(splits(host).isEmpty())
            assertFalse(tab.disposed)
        }
    }

    @Test
    fun `reconnect replaces only the affected pane and retains divider position`() = onEdt {
        val layout = layout()
        val a = Tab("A")
        val b = Tab("B")
        val replacement = Tab("Reconnected")
        val host = layout.add(a)
        layout.add(b)
        layout.split(a, b, JSplitPane.HORIZONTAL_SPLIT)
        layout.show(a)
        host.setSize(1000, 600)
        layoutTree(host)
        val divider = splits(host).single()
        divider.dividerLocation = 320
        layout.replace(b, replacement)
        layout.show(replacement)

        assertEquals(listOf(a, replacement), layout.groupTabs(a))
        assertSame(divider, SwingUtilities.getAncestorOfClass(JSplitPane::class.java, replacement.panel))
        assertEquals(320, divider.dividerLocation)
        assertSame(replacement, layout.findTab(replacement.input))
        assertNull(layout.findTab(b.input))
    }

    @Test
    fun `moving an existing pane between groups preserves both groups`() = onEdt {
        val layout = layout()
        val a = Tab("A")
        val b = Tab("B")
        val c = Tab("C")
        val d = Tab("D")
        val hosts = listOf(a, b, c, d).associateWith { layout.add(it) }
        layout.split(a, b, JSplitPane.HORIZONTAL_SPLIT)
        layout.split(c, d, JSplitPane.VERTICAL_SPLIT)
        layout.show(b)
        layout.split(c, b, JSplitPane.HORIZONTAL_SPLIT)

        assertEquals(listOf(a), layout.groupTabs(a))
        assertEquals(listOf(c, b, d), layout.groupTabs(c))
        layout.show(a)
        assertTrue(SwingUtilities.isDescendingFrom(a.panel, hosts.getValue(a)))
        layout.show(d)
        for (tab in listOf(b, c, d)) assertTrue(SwingUtilities.isDescendingFrom(tab.panel, hosts.getValue(d)))
    }

    @Test
    fun `tab container forwards focus after reconnect`() = onEdt {
        val layout = layout()
        val original = Tab("Original")
        val replacement = Tab("Replacement")
        val host = layout.add(original)
        assertTrue(host.requestFocusInWindow())
        assertEquals(1, original.focusRequests)
        layout.replace(original, replacement)
        assertTrue(host.requestFocusInWindow())
        assertEquals(1, original.focusRequests)
        assertEquals(1, replacement.focusRequests)
    }

    @Test
    fun `invalid and repeated splits leave the layout intact`() = onEdt {
        val layout = layout()
        val a = Tab("A")
        val b = Tab("B")
        layout.add(a)
        layout.add(b)
        assertFalse(layout.split(a, a, JSplitPane.HORIZONTAL_SPLIT))
        assertFalse(layout.split(a, Tab("Missing"), JSplitPane.HORIZONTAL_SPLIT))
        layout.split(a, b, JSplitPane.HORIZONTAL_SPLIT)
        assertFalse(layout.split(b, a, JSplitPane.VERTICAL_SPLIT))
        assertEquals(listOf(a, b), layout.groupTabs(a))
    }

    private fun onEdt(block: () -> Unit) = SwingUtilities.invokeAndWait(block)

    private fun splits(component: Component): List<JSplitPane> =
        (if (component is JSplitPane) listOf(component) else emptyList()) +
                (component as? Container)?.components.orEmpty().flatMap { splits(it) }

    private fun layoutTree(container: Container) {
        container.doLayout()
        container.components.filterIsInstance<Container>().forEach { layoutTree(it) }
    }
}
