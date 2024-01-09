package top.myrest.myflow.ai

import top.myrest.myflow.db.MyDb
import top.myrest.myflow.enumeration.PluginState
import top.myrest.myflow.plugin.PluginCycleListener
import top.myrest.myflow.util.javaClassName

class AssistantCycleListener : PluginCycleListener {

    override fun afterLoaded(path: String) {
        MyDb.repos[ChatHistoryRepo.javaClassName] = ChatHistoryRepo
    }

    override fun beforeAppExit() {
    }

    override fun beforeLoad(path: String): PluginState {
        return PluginState.RUNNING
    }

    override fun onPluginDisable() {
    }

    override fun onPluginRun() {
    }

    override fun onPluginStop() {
    }

    override fun onPluginUninstall(shouldDeleteSavedData: Boolean) {
    }
}