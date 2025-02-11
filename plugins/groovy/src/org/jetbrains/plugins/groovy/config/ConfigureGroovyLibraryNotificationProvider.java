// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.config;

import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.EditorNotificationProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.annotator.GroovyFrameworkConfigNotification;

/**
 * @author Maxim.Medvedev
 */
public final class ConfigureGroovyLibraryNotificationProvider implements EditorNotificationProvider<EditorNotificationPanel> {
  private static final Key<EditorNotificationPanel> KEY = Key.create("configure.groovy.library");

  @NotNull
  @Override
  public Key<EditorNotificationPanel> getKey() {
    return KEY;
  }

  @Override
  public @NotNull ComponentProvider<EditorNotificationPanel> collectNotificationData(@NotNull Project project,
                                                                                     @NotNull VirtualFile file) {
    try {
      if (!file.getFileType().equals(GroovyFileType.GROOVY_FILE_TYPE)) return ComponentProvider.getDummy();
      // do not show the panel for Gradle build scripts
      // expecting groovy library to always be available at the gradle distribution
      if (StringUtil.endsWith(file.getName(), ".gradle")) return ComponentProvider.getDummy();
      if (CompilerManager.getInstance(project).isExcludedFromCompilation(file)) return ComponentProvider.getDummy();

      final Module module = ModuleUtilCore.findModuleForFile(file, project);
      if (module == null) return ComponentProvider.getDummy();

      if (isMavenModule(module)) return ComponentProvider.getDummy();

      for (GroovyFrameworkConfigNotification configNotification : GroovyFrameworkConfigNotification.EP_NAME.getExtensions()) {
        if (configNotification.hasFrameworkStructure(module)) {
          if (!configNotification.hasFrameworkLibrary(module)) {
            return fileEditor -> (EditorNotificationPanel)configNotification.createConfigureNotificationPanel(module, fileEditor);
          }
          return ComponentProvider.getDummy();
        }
      }
    }
    catch (ProcessCanceledException | IndexNotReadyException ignored) {

    }

    return ComponentProvider.getDummy();
  }

  private static boolean isMavenModule(@NotNull Module module) {
    for (VirtualFile root : ModuleRootManager.getInstance(module).getContentRoots()) {
      if (root.findChild("pom.xml") != null) return true;
    }

    return false;
  }

}
