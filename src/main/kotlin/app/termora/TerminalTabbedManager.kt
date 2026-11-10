package app.termora

interface TerminalTabbedManager {
    fun addTerminalTab(tab: TerminalTab, selected: Boolean = true)
    fun addTerminalTab(index: Int, tab: TerminalTab, selected: Boolean = true)
    fun getSelectedTerminalTab(): TerminalTab?
    fun getTerminalTabs(): List<TerminalTab>
    fun setSelectedTerminalTab(tab: TerminalTab)
    fun closeTerminalTab(tab: TerminalTab, disposable: Boolean = true, reconnect: Boolean = false)
    fun refreshTerminalTabs()
    fun indexOfTerminalTab(tab: TerminalTab): Int

    fun replaceTerminalTab(tab: TerminalTab, replacement: TerminalTab) {
        val index = indexOfTerminalTab(tab)
        if (index < 0) return
        addTerminalTab(index, replacement)
        closeTerminalTab(tab, reconnect = true)
    }
}
