package app.termora


import app.termora.account.AccountManager
import app.termora.actions.*
import app.termora.database.DatabaseChangedExtension
import app.termora.database.DatabaseManager
import app.termora.findeverywhere.BasicFilterFindEverywhereProvider
import app.termora.findeverywhere.FindEverywhereProvider
import app.termora.findeverywhere.FindEverywhereProviderExtension
import app.termora.findeverywhere.FindEverywhereResult
import app.termora.plugin.ExtensionManager
import app.termora.plugin.internal.extension.DynamicExtensionHandler
import app.termora.terminal.DataKey
import com.formdev.flatlaf.FlatLaf
import com.formdev.flatlaf.extras.components.FlatPopupMenu
import com.formdev.flatlaf.extras.components.FlatTabbedPane
import org.apache.commons.lang3.StringUtils
import java.awt.*
import java.awt.event.ActionEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.beans.PropertyChangeListener
import javax.swing.*
import javax.swing.JTabbedPane.SCROLL_TAB_LAYOUT
import kotlin.math.min

class TerminalTabbed(
    private val windowScope: WindowScope,
    private val tabbedPane: FlatTabbedPane,
    private val layout: TermoraLayout,
) : JPanel(BorderLayout()), Disposable, TerminalTabbedManager, DataProvider {
    private val tabs = mutableListOf<TerminalTab>()
    private val actionManager = ActionManager.getInstance()
    private val dataProviderSupport = DataProviderSupport()
    private val appearance get() = DatabaseManager.getInstance().appearance
    private val titleProperty = randomUUID()
    private var selectionUpdateDepth = 0
    private var focusedTab: TerminalTab? = null
    private val splitLayout = TerminalSplitLayout(
        ::getTabTitle,
        ::setSelectedTerminalTab,
        { closeTerminalTab(it) },
        { tab, invoker -> showContextMenu(tabs.indexOf(tab), invoker, 0, invoker.height) },
    )
    private val iconListener = PropertyChangeListener { e ->
        val source = e.source
        if (e.propertyName == "icon" && source is TerminalTab) {
            val index = tabs.indexOf(source)
            if (index >= 0) {
                tabbedPane.setIconAt(index, source.getIcon())
                splitLayout.refresh(source)
            }
        }
    }


    init {
        initView()
        initEvents()
    }

    private fun initView() {
        tabbedPane.tabLayoutPolicy = SCROLL_TAB_LAYOUT
        tabbedPane.isTabsClosable = true
        tabbedPane.tabType = FlatTabbedPane.TabType.card

        add(tabbedPane, BorderLayout.CENTER)

        windowScope.getOrCreate(TerminalTabbedManager::class) { this }

        dataProviderSupport.addData(DataProviders.TerminalTabbed, this)
        dataProviderSupport.addData(DataProviders.TerminalTabbedManager, this)
    }


    private fun initEvents() {

        // 关闭 tab
        tabbedPane.setTabCloseCallback { _, i -> removeTabAt(i, true) }

        // 选中变动
        tabbedPane.addChangeListener { syncSelection() }

        val focusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager()
        val focusListener = PropertyChangeListener { event ->
            if (selectionUpdateDepth == 0) {
                val component = event.newValue as? Component
                val tab = component?.let { splitLayout.findTab(it) }
                if (tab != null && tab !== getSelectedTerminalTab()) setSelectedTerminalTab(tab)
            }
        }
        focusManager.addPropertyChangeListener("permanentFocusOwner", focusListener)
        Disposer.register(this, object : Disposable {
            override fun dispose() {
                focusManager.removePropertyChangeListener("permanentFocusOwner", focusListener)
            }
        })


        // 右键菜单
        tabbedPane.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (!SwingUtilities.isRightMouseButton(e)) {
                    return
                }

                val index = tabbedPane.indexAtLocation(e.x, e.y)
                if (index < 0) return

                showContextMenu(index, e.component, e.x, e.y)
            }
        })

        // 注册全局搜索
        DynamicExtensionHandler.getInstance()
            .register(FindEverywhereProviderExtension::class.java, object : FindEverywhereProviderExtension {
                val provider = BasicFilterFindEverywhereProvider(object : FindEverywhereProvider {
                    override fun find(pattern: String, scope: Scope): List<FindEverywhereResult> {
                        if (scope != windowScope) return emptyList()
                        val results = mutableListOf<FindEverywhereResult>()
                        for (i in 0 until tabbedPane.tabCount) {
                            val c = tabs[i].getJComponent()
                            if (c.getClientProperty(FindEverywhereProvider.SKIP_FIND_EVERYWHERE) != null) {
                                continue
                            }
                            results.add(
                                SwitchFindEverywhereResult(
                                    tabbedPane.getTitleAt(i),
                                    tabbedPane.getIconAt(i),
                                    tabbedPane.getComponentAt(i)
                                )
                            )
                        }
                        return results
                    }

                    override fun group(): String {
                        return I18n.getString("termora.find-everywhere.groups.opened-hosts")
                    }

                    override fun order(): Int {
                        return Integer.MIN_VALUE + 1
                    }
                })

                override fun getFindEverywhereProvider(): FindEverywhereProvider {
                    return provider
                }
            }).let { Disposer.register(this, it) }

    }

    private fun removeTabAt(index: Int, disposable: Boolean = true, reconnect: Boolean = false) {
        if (index in tabs.indices && tabbedPane.isTabClosable(index)) {
            val tab = tabs[index]

            // 询问是否可以关闭
            if (disposable) {
                // 如果是重连接，那么直接关闭不进行任何形式的询问
                if (reconnect.not()) {
                    // 如果开启了关闭确认，那么直接询问用户
                    if (appearance.confirmTabClose) {
                        if (OptionPane.showConfirmDialog(
                                windowScope.window,
                                I18n.getString("termora.tabbed.tab.close-prompt"),
                                messageType = JOptionPane.QUESTION_MESSAGE,
                                optionType = JOptionPane.OK_CANCEL_OPTION
                            ) != JOptionPane.OK_OPTION
                        ) {
                            return
                        }
                    } else if (!tab.willBeClose()) { // 如果没有开启则询问用户
                        return
                    }
                }
            }

            // 通知即将关闭
            if (disposable) {
                try {
                    tab.beforeClose()
                } catch (_: Exception) {
                    return
                }
            }

            tab.removePropertyChangeListener(iconListener)
            val selected = getSelectedTerminalTab()
            selectionUpdateDepth++
            try {
                val neighbour = splitLayout.remove(tab)
                tabs.removeAt(index)
                tabbedPane.removeTabAt(index)
                val next = if (selected === tab) neighbour else selected
                if (next != null && next in tabs) tabbedPane.selectedIndex = tabs.indexOf(next)
            } finally {
                selectionUpdateDepth--
            }
            syncSelection()

            if (disposable) {
                Disposer.dispose(tab)
            }
        }
    }

    private fun showContextMenu(tabIndex: Int, invoker: Component, x: Int, y: Int) {
        if (tabIndex !in tabs.indices) return
        val tab = tabs[tabIndex]
        val c = tab.getJComponent()
        val extensions = ExtensionManager.getInstance().getExtensions(TerminalTabbedContextMenuExtension::class.java)
        val menuItems = mutableListOf<JMenuItem>()
        for (extension in extensions) {
            try {
                menuItems.add(extension.createJMenuItem(windowScope, tab))
            } catch (_: UnsupportedOperationException) {
                continue
            }
        }

        val popupMenu = FlatPopupMenu()

        // 修改名称
        val rename = popupMenu.add(I18n.getString("termora.tabbed.contextmenu.rename"))
        rename.addActionListener {
            if (tabIndex > 0) {
                val text = OptionPane.showInputDialog(
                    SwingUtilities.getWindowAncestor(this),
                    title = rename.text,
                    value = tabbedPane.getTitleAt(tabIndex)
                )
                if (!text.isNullOrBlank()) {
                    tabbedPane.setTitleAt(tabIndex, text)
                    c.putClientProperty(titleProperty, text)
                    splitLayout.refresh(tab)
                }
            }

        }

        // 克隆
        val clone = popupMenu.add(I18n.getString("termora.copy"))
        clone.addActionListener { evt ->
            if (tab is HostTerminalTab) {
                actionManager
                    .getAction(OpenHostAction.OPEN_HOST)
                    .actionPerformed(OpenHostActionEvent(this, tab.host, evt, tabIndex + 1))
            }
        }

        if (canSplitTerminalTab(tab)) {
            popupMenu.add(createSplitMenu(tab))
        }

        // 编辑
        val edit = popupMenu.add(I18n.getString("termora.keymgr.edit"))
        edit.addActionListener(object : AnAction() {
            private val hostManager get() = HostManager.getInstance()
            private val accountManager get() = AccountManager.getInstance()

            override fun actionPerformed(evt: AnActionEvent) {
                if (tab is HostTerminalTab) {
                    val host = hostManager.getHost(tab.host.id) ?: return
                    val dialog = NewHostDialogV2(
                        evt.window, host,
                        accountManager.getOwners().first { it.id == host.ownerId },
                    )
                    dialog.setLocationRelativeTo(evt.window)
                    dialog.isVisible = true

                    // Sync 模式，触发 reload
                    hostManager.addHost(dialog.host ?: return, DatabaseChangedExtension.Source.Sync)
                }
            }
        })

        // 在新窗口中打开
        val openInNewWindow = popupMenu.add(I18n.getString("termora.tabbed.contextmenu.open-in-new-window"))
        openInNewWindow.addActionListener(object : AnAction() {
            override fun actionPerformed(evt: AnActionEvent) {
                val owner = evt.getData(DataProviders.TermoraFrame) ?: return
                if (tabIndex > 0) {
                    val title = tabbedPane.getTitleAt(tabIndex)
                    removeTabAt(tabIndex, false)
                    val dialog = TerminalTabDialog(
                        owner = owner,
                        terminalTab = tab,
                        size = Dimension(min(size.width, 1280), min(size.height, 800))
                    )
                    dialog.title = title
                    Disposer.register(dialog, tab)
                    Disposer.register(this@TerminalTabbed, dialog)
                    dialog.isVisible = true
                }
            }
        })

        if (menuItems.isNotEmpty()) {
            popupMenu.addSeparator()
            for (item in menuItems) {
                popupMenu.add(item)
            }
        }

        popupMenu.addSeparator()

        // 关闭
        val close = popupMenu.add(I18n.getString("termora.tabbed.contextmenu.close"))
        close.addActionListener { tabbedPane.tabCloseCallback?.accept(tabbedPane, tabIndex) }

        // 关闭其他标签页
        popupMenu.add(I18n.getString("termora.tabbed.contextmenu.close-other-tabs")).addActionListener {
            for (i in tabbedPane.tabCount - 1 downTo tabIndex + 1) {
                tabbedPane.tabCloseCallback?.accept(tabbedPane, i)
            }
            for (i in 1 until tabIndex) {
                tabbedPane.tabCloseCallback?.accept(tabbedPane, tabIndex - i)
            }
        }

        // 关闭所有标签页
        popupMenu.add(I18n.getString("termora.tabbed.contextmenu.close-all-tabs")).addActionListener {
            for (i in 0 until tabbedPane.tabCount) {
                tabbedPane.tabCloseCallback?.accept(tabbedPane, tabbedPane.tabCount - 1)
            }
        }


        close.isEnabled = tab.canClose()
        rename.isEnabled = close.isEnabled
        clone.isEnabled = close.isEnabled
        edit.isEnabled = tab is HostTerminalTab && tab.host.id != "local" && tab.host.isTemporary.not()
        openInNewWindow.isEnabled = close.isEnabled

        // 如果不允许克隆
        if (clone.isEnabled && !tab.canClone()) {
            clone.isEnabled = false
        }

        if (close.isEnabled) {
            popupMenu.addSeparator()
            val reconnect = popupMenu.add(I18n.getString("termora.tabbed.contextmenu.reconnect"))
            reconnect.addActionListener { tabs[tabIndex].reconnect() }
            reconnect.isEnabled = tabs[tabIndex].canReconnect()
        }

        popupMenu.show(invoker, x, y)
    }

    private fun getTabTitle(tab: TerminalTab): String =
        (tab.getJComponent().getClientProperty(titleProperty) ?: tab.getTitle()).toString()

    private fun syncSelection() {
        if (selectionUpdateDepth > 0) return
        val tab = getSelectedTerminalTab()
        if (focusedTab !== tab) {
            focusedTab?.onLostFocus()
            focusedTab = tab
            tab?.onGrabFocus()
        }
        if (tab == null) return
        val focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner
        val focusTarget = if (focusOwner != null && splitLayout.findTab(focusOwner) === tab) focusOwner
        else tab.getJComponent()
        splitLayout.show(tab)
        SwingUtilities.invokeLater {
            if (getSelectedTerminalTab() === tab && focusTarget.isShowing) focusTarget.requestFocusInWindow()
        }
    }

    private fun canSplitTerminalTab(tab: TerminalTab) =
        tab.canClose() && tab.getData(DataProviders.TerminalPanel) != null

    private fun createSplitMenu(tab: TerminalTab): JMenu {
        val menu = JMenu(I18n.getString("termora.tabbed.split")).apply { icon = Icons.split }
        for ((orientation, direction) in listOf(JSplitPane.HORIZONTAL_SPLIT to "right", JSplitPane.VERTICAL_SPLIT to "down")) {
            menu.add(JMenuItem(I18n.getString("termora.tabbed.split.$direction")).apply {
                isEnabled = tab is HostTerminalTab && tab.canClone()
                addActionListener { event ->
                    if (tab is HostTerminalTab && tab in tabs) {
                        val before = tabs.toSet()
                        actionManager.getAction(OpenHostAction.OPEN_HOST).actionPerformed(
                            OpenHostActionEvent(this@TerminalTabbed, tab.host, event, tabs.indexOf(tab) + 1)
                        )
                        val created = tabs.firstOrNull { it !in before }
                        if (created != null) splitTerminalTab(tab, created, orientation)
                    }
                }
            })
            val existing = JMenu(I18n.getString("termora.tabbed.split.$direction-existing"))
            val group = splitLayout.groupTabs(tab)
            for (other in tabs.filter { it !in group && canSplitTerminalTab(it) }) {
                existing.add(JMenuItem("${tabs.indexOf(other) + 1}. ${getTabTitle(other)}", other.getIcon()).apply {
                    addActionListener { splitTerminalTab(tab, other, orientation) }
                })
            }
            existing.isEnabled = existing.itemCount > 0
            menu.add(existing)
        }
        menu.addSeparator()
        menu.add(JMenuItem(I18n.getString("termora.tabbed.split.restore")).apply {
            isEnabled = splitLayout.groupTabs(tab).size > 1
            addActionListener { unsplitTerminalTab(tab) }
        })
        return menu
    }

    fun splitTerminalTab(target: TerminalTab, tab: TerminalTab, orientation: Int): Boolean {
        if (!canSplitTerminalTab(target) || !canSplitTerminalTab(tab)) return false
        selectionUpdateDepth++
        val changed: Boolean
        try {
            changed = splitLayout.split(target, tab, orientation)
            if (changed) tabbedPane.selectedIndex = tabs.indexOf(tab)
        } finally {
            selectionUpdateDepth--
        }
        syncSelection()
        return changed
    }

    fun unsplitTerminalTab(tab: TerminalTab) {
        selectionUpdateDepth++
        try {
            splitLayout.unsplit(tab)
        } finally {
            selectionUpdateDepth--
        }
        setSelectedTerminalTab(tab)
    }

    private fun addTab(index: Int, tab: TerminalTab, selected: Boolean) {
        val c = tab.getJComponent()
        val title = getTabTitle(tab)
        selectionUpdateDepth++
        try {
            c.putClientProperty(titleProperty, title)
            tab.addPropertyChangeListener(iconListener)
            tabs.add(index, tab)
            tabbedPane.insertTab(title, tab.getIcon(), splitLayout.add(tab), StringUtils.EMPTY, index)
            if (selected) tabbedPane.selectedIndex = index
            tabbedPane.setTabClosable(index, tab.canClose())
            Disposer.register(this, tab)
        } finally {
            selectionUpdateDepth--
        }
        syncSelection()
    }

    override fun refreshTerminalTabs() {
        for (i in 0 until tabbedPane.tabCount) {
            tabbedPane.setTabClosable(i, tabs[i].canClose())
        }
    }

    override fun indexOfTerminalTab(tab: TerminalTab): Int {
        return tabs.indexOf(tab)
    }

    private inner class SwitchFindEverywhereResult(
        private val title: String,
        private val icon: Icon?,
        private val c: Component
    ) : FindEverywhereResult {

        override fun actionPerformed(e: ActionEvent) {
            tabbedPane.selectedComponent = c
        }

        override fun getIcon(isSelected: Boolean): Icon {
            if (isSelected) {
                if (!FlatLaf.isLafDark()) {
                    if (icon is DynamicIcon) {
                        return icon.dark
                    }
                }
            }
            return icon ?: super.getIcon(isSelected)
        }

        override fun toString(): String {
            return title
        }
    }

    override fun paint(g: Graphics) {
        super.paint(g)
    }

    private val border = BorderFactory.createMatteBorder(1, 0, 0, 0, DynamicColor.BorderColor)
    private val banner = BannerPanel(fontSize = 13).apply {
        foreground = UIManager.getColor("textInactiveText")
    }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)

        if (layout == TermoraLayout.Fence) {
            if (g is Graphics2D) {
                if (tabbedPane.tabCount < 1) {
                    border.paintBorder(this, g, 0, tabbedPane.tabHeight, width, tabbedPane.tabHeight)
                    banner.setBounds(0, 0, width, height)
                    g.save()
                    g.translate(0, 180)
                    banner.paintComponent(g)
                    g.restore()
                }
            }
        }
    }


    override fun dispose() {
    }

    override fun addTerminalTab(tab: TerminalTab, selected: Boolean) {
        addTab(tabs.size, tab, selected)
    }

    override fun addTerminalTab(index: Int, tab: TerminalTab, selected: Boolean) {
        addTab(index, tab, selected)
    }

    override fun getSelectedTerminalTab(): TerminalTab? {
        return tabs.getOrNull(tabbedPane.selectedIndex)
    }

    override fun getTerminalTabs(): List<TerminalTab> {
        return tabs
    }

    override fun setSelectedTerminalTab(tab: TerminalTab) {
        val index = tabs.indexOf(tab)
        if (index < 0) return
        selectionUpdateDepth++
        try {
            tabbedPane.selectedIndex = index
        } finally {
            selectionUpdateDepth--
        }
        syncSelection()
    }

    override fun replaceTerminalTab(tab: TerminalTab, replacement: TerminalTab) {
        val index = tabs.indexOf(tab)
        if (index < 0) return
        tab.beforeClose()
        selectionUpdateDepth++
        try {
            tab.removePropertyChangeListener(iconListener)
            replacement.addPropertyChangeListener(iconListener)
            replacement.getJComponent().putClientProperty(titleProperty, getTabTitle(tab))
            splitLayout.replace(tab, replacement)
            tabs[index] = replacement
            tabbedPane.setIconAt(index, replacement.getIcon())
            tabbedPane.setTabClosable(index, replacement.canClose())
            Disposer.register(this, replacement)
        } finally {
            selectionUpdateDepth--
        }
        syncSelection()
        Disposer.dispose(tab)
    }

    override fun closeTerminalTab(tab: TerminalTab, disposable: Boolean, reconnect: Boolean) {
        for (i in 0 until tabs.size) {
            if (tabs[i] == tab) {
                removeTabAt(i, disposable, reconnect)
                break
            }
        }
    }

    override fun <T : Any> getData(dataKey: DataKey<T>): T? {
        if (dataKey == DataProviders.TerminalTab) {
            dataProviderSupport.removeData(dataKey)
            if (tabbedPane.selectedIndex >= 0 && tabs.size > tabbedPane.selectedIndex) {
                dataProviderSupport.addData(dataKey, tabs[tabbedPane.selectedIndex])
            }
        }
        return dataProviderSupport.getData(dataKey)
    }


}
