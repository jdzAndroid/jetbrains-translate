package com.jdz.translate

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.roots.ProjectFileIndex

/**
 * 用途-将json文件转换成dart实体类
 * 只读取当前上下文所在目录中的JSON文件
 * 文件数据路径-当前上下文所在目录
 * 输出文件命名规范：和Json文件名称一样
 * 数据文件类型：生成的json dart类基类和具体的dart类
 * 注意事项：json文件key值完全保持一致
 */
class GenerateDartFromJson : AnAction() {

    override fun actionPerformed(event: AnActionEvent) {
        if (!hasCachedJsonFile(event)) {
            refreshCachedJsonFile(event)
        }
        var backupJsonFileDirPath = getBackJsonFileDir(event)
        val dartFileDirPath = getExportFilePath(event)
        val worldCityFilePath = getWorldCityPath(event)
        GenerateDartFromJsonHelper().generateJson(backupJsonFileDirPath = backupJsonFileDirPath, dartFileDirPath = dartFileDirPath, worldCityFilePath = worldCityFilePath)
        ProjectFileIndex.getInstance(event.project!!).getContentRootForFile(event.project!!.projectFile!!)
            ?.refresh(true, true)
    }
}