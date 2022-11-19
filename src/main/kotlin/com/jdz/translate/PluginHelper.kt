package com.jdz.translate

import com.intellij.ide.plugins.PluginManager
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile
import java.io.File

/**
 *获取存储备份的JSON文件路径
 */
fun getBackJsonFileDir(project: Project, virtualFile: VirtualFile): String {
    val pluginId = PluginId.getId("org.jdz.translate.translate");
    val plugin = PluginManager.getPlugin(pluginId)!!
    val pluginInstallPath = plugin.path.absolutePath
    val rootFile = ProjectRootManager.getInstance(project).fileIndex.getContentRootForFile(virtualFile)!!
    return pluginInstallPath.plus(File.separatorChar).plus(rootFile.name)
}