package app.termora

import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*

/** Keeps terminal sessions independent of the split layout and their tab-strip positions. */
internal class TerminalSplitLayout(
    private val title: (TerminalTab) -> String,
    private val select: (TerminalTab) -> Unit,
    private val close: (TerminalTab) -> Unit,
    private val showMenu: (TerminalTab, JComponent) -> Unit,
) {
    private val entries = linkedMapOf<TerminalTab, Entry>()
    private var activeTab: TerminalTab? = null

    private class TabHost(var tab: TerminalTab) : JPanel(BorderLayout()) {
        override fun requestFocusInWindow() = tab.getJComponent().requestFocusInWindow()
    }

    private class Entry(val host: TabHost, val leaf: Leaf, var group: Group)

    private sealed interface Node {
        val component: JComponent
        fun leaves(): List<Leaf>
    }

    private class Leaf(val tab: TerminalTab, val header: JPanel, val label: JLabel) : Node {
        override val component = JPanel(BorderLayout()).apply {
            minimumSize = Dimension(100, 80)
            add(header, BorderLayout.NORTH)
            add(tab.getJComponent(), BorderLayout.CENTER)
        }

        override fun leaves() = listOf(this)
    }

    private class Branch(orientation: Int, first: Node, second: Node) : Node {
        override val component = object : JSplitPane(orientation) {
            private var initialLayout = true

            override fun doLayout() {
                if (initialLayout && width > 0 && height > 0) {
                    initialLayout = false
                    setDividerLocation(0.5)
                }
                super.doLayout()
            }
        }.apply {
            border = BorderFactory.createEmptyBorder()
            isContinuousLayout = true
            resizeWeight = 0.5
            minimumSize = Dimension(100, 80)
        }
        var first: Node = first
            set(value) {
                field = value
                component.leftComponent = value.component
            }
        var second: Node = second
            set(value) {
                field = value
                component.rightComponent = value.component
            }

        init {
            component.leftComponent = first.component
            component.rightComponent = second.component
        }

        override fun leaves() = first.leaves() + second.leaves()
    }

    private class Group(var root: Node?) {
        val panel = JPanel(BorderLayout())

        fun replace(old: Node, replacement: Node?) {
            if (root === old) {
                root = replacement
                panel.removeAll()
                replacement?.let { panel.add(it.component, BorderLayout.CENTER) }
            } else {
                val parent = parentOf(root, old) ?: return
                requireNotNull(replacement)
                if (parent.first === old) parent.first = replacement else parent.second = replacement
            }
            panel.revalidate()
            panel.repaint()
        }

        fun remove(leaf: Leaf) {
            val parent = parentOf(root, leaf)
            if (parent == null) {
                if (root === leaf) replace(leaf, null)
            } else {
                replace(parent, if (parent.first === leaf) parent.second else parent.first)
            }
        }

        private fun parentOf(node: Node?, target: Node): Branch? {
            if (node !is Branch) return null
            if (node.first === target || node.second === target) return node
            return parentOf(node.first, target) ?: parentOf(node.second, target)
        }
    }

    fun add(tab: TerminalTab): JComponent {
        check(tab !in entries)
        val leaf = createLeaf(tab)
        val group = Group(leaf)
        group.panel.add(leaf.component, BorderLayout.CENTER)
        val host = TabHost(tab)
        entries[tab] = Entry(host, leaf, group)
        mount(group)
        return host
    }

    fun show(tab: TerminalTab) {
        val entry = entries[tab] ?: return
        val previous = entries[activeTab]?.group
        activeTab = tab
        previous?.let { refresh(it) }
        mount(entry.group)
    }

    fun remove(tab: TerminalTab): TerminalTab? {
        val entry = entries.remove(tab) ?: return null
        entry.group.remove(entry.leaf)
        val neighbour = entry.group.root?.leaves()?.firstOrNull()?.tab
        if (activeTab === tab) activeTab = neighbour
        entry.host.removeAll()
        mount(entry.group)
        return neighbour
    }

    fun replace(old: TerminalTab, replacement: TerminalTab) {
        check(replacement !in entries)
        val entry = entries.remove(old) ?: return
        val leaf = createLeaf(replacement)
        entry.host.tab = replacement
        entries[replacement] = Entry(entry.host, leaf, entry.group)
        entry.group.replace(entry.leaf, leaf)
        if (activeTab === old) activeTab = replacement
        mount(entry.group)
    }

    fun split(target: TerminalTab, tab: TerminalTab, orientation: Int): Boolean {
        require(orientation == JSplitPane.HORIZONTAL_SPLIT || orientation == JSplitPane.VERTICAL_SPLIT)
        val anchor = entries[target] ?: return false
        val other = entries[tab] ?: return false
        if (anchor.group === other.group) return false

        val previousGroup = other.group
        previousGroup.remove(other.leaf)
        other.host.removeAll()
        mount(previousGroup)
        other.group = anchor.group
        anchor.group.replace(anchor.leaf, Branch(orientation, anchor.leaf, other.leaf))
        mount(anchor.group)
        return true
    }

    /** Restore separate tabs without stopping or recreating any session. */
    fun unsplit(tab: TerminalTab) {
        val group = entries[tab]?.group ?: return
        for (leaf in group.root?.leaves().orEmpty().filter { it.tab !== tab }) {
            group.remove(leaf)
            val separate = Group(leaf)
            separate.panel.add(leaf.component, BorderLayout.CENTER)
            entries.getValue(leaf.tab).group = separate
            mount(separate)
        }
        mount(group)
    }

    fun groupTabs(tab: TerminalTab): List<TerminalTab> =
        entries[tab]?.group?.root?.leaves()?.map { it.tab }.orEmpty()

    fun findTab(component: Component): TerminalTab? = entries.keys.firstOrNull {
        component === it.getJComponent() || SwingUtilities.isDescendingFrom(component, it.getJComponent())
    }

    fun refresh(tab: TerminalTab) {
        entries[tab]?.group?.let { refresh(it) }
    }

    private fun mount(group: Group) {
        val leaves = group.root?.leaves().orEmpty()
        if (leaves.isEmpty()) return
        val tab = leaves.firstOrNull { it.tab === activeTab }?.tab ?: leaves.first().tab
        val host = entries.getValue(tab).host
        if (group.panel.parent !== host) {
            host.add(group.panel, BorderLayout.CENTER)
            host.revalidate()
            host.repaint()
        }
        refresh(group)
    }

    private fun refresh(group: Group) {
        val leaves = group.root?.leaves().orEmpty()
        for (leaf in leaves) {
            leaf.header.isVisible = leaves.size > 1
            leaf.label.text = title(leaf.tab)
            leaf.label.icon = leaf.tab.getIcon()
            val color = if (leaf.tab === activeTab) UIManager.getColor("Component.focusColor")
            else UIManager.getColor("Separator.foreground")
            leaf.component.border = if (leaves.size > 1) BorderFactory.createLineBorder(color ?: leaf.label.foreground)
            else BorderFactory.createEmptyBorder()
        }
        group.panel.revalidate()
        group.panel.repaint()
    }

    private fun createLeaf(tab: TerminalTab): Leaf {
        val label = JLabel(title(tab), tab.getIcon(), SwingConstants.LEADING)
        val header = JPanel(BorderLayout()).apply {
            border = BorderFactory.createEmptyBorder(2, 6, 2, 2)
            add(label, BorderLayout.CENTER)
        }
        val buttons = JPanel(FlowLayout(FlowLayout.RIGHT, 0, 0))
        buttons.add(JButton(Icons.split).apply {
            isFocusable = false
            isContentAreaFilled = false
            border = BorderFactory.createEmptyBorder(3, 4, 3, 4)
            toolTipText = I18n.getString("termora.tabbed.split.actions")
            addActionListener { showMenu(tab, this) }
        })
        buttons.add(JButton(Icons.closeSmall).apply {
            isFocusable = false
            isContentAreaFilled = false
            border = BorderFactory.createEmptyBorder(3, 4, 3, 4)
            toolTipText = I18n.getString("termora.tabbed.contextmenu.close")
            addActionListener { close(tab) }
        })
        header.add(buttons, BorderLayout.EAST)
        val listener = object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                select(tab)
                if (SwingUtilities.isRightMouseButton(e)) showMenu(tab, header)
            }
        }
        header.addMouseListener(listener)
        label.addMouseListener(listener)
        return Leaf(tab, header, label)
    }
}
