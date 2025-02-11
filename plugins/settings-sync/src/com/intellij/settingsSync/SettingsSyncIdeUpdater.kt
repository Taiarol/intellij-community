package com.intellij.settingsSync

import com.intellij.application.options.editor.EditorOptionsPanel
import com.intellij.codeInsight.hints.ParameterHintsPassFactory
import com.intellij.configurationStore.ComponentStoreImpl
import com.intellij.configurationStore.StateStorageManagerImpl
import com.intellij.configurationStore.XmlElementStorage
import com.intellij.configurationStore.schemeManager.SchemeManagerFactoryBase
import com.intellij.configurationStore.schemeManager.SchemeManagerImpl
import com.intellij.ide.projectView.ProjectView
import com.intellij.ide.ui.LafManager
import com.intellij.ide.ui.UISettings.Companion.instance
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.openapi.application.Application
import com.intellij.openapi.application.PathManager.OPTIONS_DIRECTORY
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.components.StateStorage
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.options.SchemeManagerFactory
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.IconLoader
import com.intellij.ui.JBColor
import com.intellij.util.SmartList
import com.intellij.util.io.write
import com.intellij.util.ui.StartupUiUtil
import java.nio.file.Path

internal class SettingsSyncIdeUpdater(application: Application,
                                      private val componentStore: ComponentStoreImpl,
                                      private val rootConfig: Path) : SettingsLoggedListener {

  companion object {
    val LOG = logger<SettingsSyncIdeUpdater>()
  }

  init {
    application.messageBus.connect().subscribe(SETTINGS_LOGGED_TOPIC, this)
  }

  override fun settingsLogged(event: SettingsLoggedEvent) {
    if (!event.hasRemote) {
      return
    }

    val snapshot = event.snapshot
    // todo race between this code and SettingsSyncStreamProvider.write which can write other user settings at the same time

    // todo update only that has really changed
    for (fileState in snapshot.fileStates) {
      rootConfig.resolve(fileState.file).write(fileState.content, 0, fileState.size)
    }

    invokeAndWaitIfNeeded { reloadComponents(snapshot.fileStates.map { it.file.removePrefix("$OPTIONS_DIRECTORY/") }) }
  }

  private fun reloadComponents(changedFileSpecs: List<String>) {
    val schemeManagerFactory = SchemeManagerFactory.getInstance() as SchemeManagerFactoryBase

    val (changed, deleted) = (componentStore.storageManager as StateStorageManagerImpl).getCachedFileStorages(changedFileSpecs, emptyList(), null)

    val schemeManagersToReload = SmartList<SchemeManagerImpl<*, *>>()
    schemeManagerFactory.process {
      schemeManagersToReload.add(it)
    }

    val changedComponentNames = LinkedHashSet<String>()
    updateStateStorage(changedComponentNames, changed, false)

    for (schemeManager in schemeManagersToReload) {
      schemeManager.reload()
    }

    val notReloadableComponents = componentStore.getNotReloadableComponents(changedComponentNames)
    componentStore.reinitComponents(changedComponentNames, changed.toSet(), notReloadableComponents)

    updateUI()
  }

  private fun updateStateStorage(changedComponentNames: MutableSet<String>, stateStorages: Collection<StateStorage>, deleted: Boolean) {
    for (stateStorage in stateStorages) {
      try {
        (stateStorage as XmlElementStorage).updatedFromStreamProvider(changedComponentNames, deleted)
      }
      catch (e: Throwable) {
        LOG.error(e)
      }
    }
  }

  // todo copypasted from the CloudConfigManager
  private fun updateUI() {
    // TODO: separate and move this code to specific managers
    val lafManager = LafManager.getInstance()
    val lookAndFeel = lafManager.currentLookAndFeel
    if (lookAndFeel != null) {
      lafManager.setCurrentLookAndFeel(lookAndFeel, true)
    }
    val darcula = StartupUiUtil.isUnderDarcula()
    JBColor.setDark(darcula)
    IconLoader.setUseDarkIcons(darcula)
    ActionToolbarImpl.updateAllToolbarsImmediately()
    lafManager.updateUI()
    instance.fireUISettingsChanged()
    ParameterHintsPassFactory.forceHintsUpdateOnNextPass()
    EditorOptionsPanel.reinitAllEditors()
    EditorOptionsPanel.restartDaemons()
    for (project in ProjectManager.getInstance().openProjects) {
      ProjectView.getInstance(project).refresh()
    }
  }


}