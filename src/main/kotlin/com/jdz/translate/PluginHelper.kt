package com.jdz.translate

import com.intellij.ide.plugins.PluginManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.util.*

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

/**
 *翻译可以默认前缀
 */
fun getTranslateKeyPrefix(): String {
    return "ido_key_"
}

/**
 *通过中文查找翻译，以下特殊符号不参与匹配比较
 */
fun getSearchSpecialSymbolsList(): List<String> {
    return mutableListOf(
        "%d", "%1d", "%2d", "%3d", "%4d", "%s", "%1s", "%2s", "%3s", "%4s",
        "%1\$s", "%2\$s", "%3\$s", "%4\$s", "%1\$d", "%2\$d", "%3\$d", "%4\$d", ",", "，", ".", "。", "\\\n",
        "{params}", "{params1}", "{params2}", "{params3}", "{params4}",
        "\\t", "\\b", "\\r", "\\v", "\\f", "\\e", "\\n", "\\r\\n", "\\\"", "\\"
    )
}

/**
 *开始通过Key找翻译
 * @param keyValue 搜索的关键字
 * @param event 触发事件信息
 * @param exactSearch 是否精确查找
 */
fun findTranslateByKey(keyValue: String, event: AnActionEvent, exactSearch: Boolean): List<List<TranslateInfo>> {
    logD("开始通过key查找翻译信息")
    val editor = event.getData(PlatformDataKeys.EDITOR)!!
    val manager = FileDocumentManager.getInstance()
    var virtualFile = manager.getFile(editor.document)!!
    val backupJsonDirPath = getBackJsonFileDir(project = event.project!!, virtualFile = virtualFile)
    val backupJsonFile = File(backupJsonDirPath)
    val childFileList = backupJsonFile.listFiles()
    if (!backupJsonFile.exists() || !backupJsonFile.isDirectory || childFileList.isNullOrEmpty()) {
        logD("缓存的JSON文件不存在: backupJsonDirPath=$backupJsonDirPath")
        return mutableListOf()
    }
    var zhFile: File? = null
    var enFile: File? = null
    for (itemChildFile in childFileList) {
        if (itemChildFile.name.trim().lowercase(Locale.CHINA).endsWith("zh.json")) {
            zhFile = itemChildFile
        } else if (itemChildFile.name.trim().lowercase(Locale.CHINA).endsWith("en.json")) {
            enFile = itemChildFile
        }
        if (zhFile != null && enFile != null) break
    }
    if (zhFile == null) {
        logD("没有找到中文JSON文件")
        return mutableListOf()
    }
    val bufferedReader = BufferedReader(FileReader(zhFile))
    var zhTranslateInfo: TranslateInfo? = null
    var line: String? = null
    var lineNumber = 0
    while (true) {
        line = bufferedReader.readLine()
        if (line.isNullOrEmpty()) break
        line = line.trim()
        if (line.trim() == "{" || line.trim() == "}") {
            lineNumber++
            continue
        }
        line = line.substring(0, line.length - 1)
        val splitValue = line.split("\":\"")
        if (splitValue.size == 2) {
            var key = splitValue[0].trim()
            var value = splitValue[1].trim()
            key = key.substring(1, key.length)
            value = value.substring(0, value.length - 1)
            if (key == keyValue) {
                logD("找到的键值对信息 key=$key,value=$value")
                zhTranslateInfo = TranslateInfo(
                    key = key,
                    languageCode = "zh",
                    value = value,
                    lineNumber = lineNumber
                )
                break
            }
        }
        lineNumber++
    }
    if (zhTranslateInfo == null) {
        logE("没有找到指定key的翻译")
        return mutableListOf()
    }
    val result = mutableListOf<MutableList<TranslateInfo>>()
    result.add(mutableListOf(zhTranslateInfo))
    val notFoundDefaultValue = "没有找到翻译"
    var hasFound = false
    if (!exactSearch && enFile != null) {
        lineNumber = 0
        val enBufferReader = BufferedReader(FileReader(enFile))
        while (true) {
            line = enBufferReader.readLine()
            if (line.isNullOrEmpty()) {
                break
            }
            if (lineNumber == zhTranslateInfo.lineNumber) {
                line = line.trim().substring(0, line.length - 1)
                val splitValue = line.split("\":\"")
                if (splitValue.size == 2) {
                    var key = splitValue[0].trim()
                    var value = splitValue[1].trim()
                    key = key.substring(1, key.length)
                    value = value.substring(0, value.length - 1)
                    logD("找到的键值对信息 key=$key,value=$value")
                    result.add(
                        mutableListOf(
                            TranslateInfo(
                                key = key,
                                languageCode = "en",
                                value = value,
                                lineNumber = lineNumber
                            )
                        )
                    )
                } else {
                    result.add(
                        mutableListOf(
                            TranslateInfo(
                                key = zhTranslateInfo.key,
                                languageCode = "en", value = notFoundDefaultValue, lineNumber = lineNumber
                            )
                        )
                    )
                }
                hasFound = true
                break
            }
            lineNumber++
        }
        if (!hasFound) {
            result.add(
                mutableListOf(
                    TranslateInfo(
                        key = zhTranslateInfo.key,
                        languageCode = "en", value = notFoundDefaultValue, lineNumber = lineNumber
                    )
                )
            )
        }
    }

//        for (itemChildFile in childFileList) {
//            if (itemChildFile == zhFile || itemChildFile == enFile) continue
//            val fileName = itemChildFile.name.trim().lowercase(Locale.CHINA)
//            if (!fileName.endsWith(".json")) continue
//            val languageCode = fileName.substring(
//                fileName.lastIndexOf("_") + 1,
//                fileName.lastIndexOf(".")
//            )
//            lineNumber = 0
//            val itemBufferReader = BufferedReader(FileReader(itemChildFile))
//            hasFound = false
//            while (true) {
//                line = itemBufferReader.readLine()
//                if (line.isNullOrEmpty()) {
//                    break
//                }
//                if (lineNumber == zhTranslateInfo.lineNumber) {
//                    line = line.trim().substring(0, line.length - 1)
//                    val splitValue = line.split("\":\"")
//                    if (splitValue.size == 2) {
//                        var key = splitValue[0].trim()
//                        var value = splitValue[1].trim()
//                        key = key.substring(1, key.length)
//                        value = value.substring(0, value.length - 1)
//                        logD("找到的键值对信息 key=$key,value=$value")
//                        result.add(
//                            TranslateInfo(
//                                key = key,
//                                languageCode = languageCode,
//                                value = value,
//                                lineNumber = lineNumber
//                            )
//                        )
//                    } else {
//                        result.add(
//                            TranslateInfo(
//                                key = zhTranslateInfo.key,
//                                languageCode = languageCode, value = notFoundDefaultValue, lineNumber = lineNumber
//                            )
//                        )
//                    }
//                    hasFound = true
//                    break
//                }
//                lineNumber++
//            }
//            if (!hasFound) {
//                result.add(
//                    TranslateInfo(
//                        key = zhTranslateInfo.key,
//                        languageCode = languageCode, value = notFoundDefaultValue, lineNumber = lineNumber
//                    )
//                )
//            }
//        }
    return result
}

/**
 *通过中文查找翻译
 * @param keyValue 搜索的关键字
 * @param event 触发事件信息
 * @param exactSearch 是否精确查找
 */
fun findTranslateByZh(zhValue: String, event: AnActionEvent, exactSearch: Boolean): List<List<TranslateInfo>> {
    logD("开始通过中文查找翻译信息")
    val editor = event.getData(PlatformDataKeys.EDITOR)!!
    val manager = FileDocumentManager.getInstance()
    var virtualFile = manager.getFile(editor.document)!!
    val backupJsonDirPath = getBackJsonFileDir(project = event.project!!, virtualFile = virtualFile)
    val backupJsonFile = File(backupJsonDirPath)
    val childFileList = backupJsonFile.listFiles()
    if (!backupJsonFile.exists() || !backupJsonFile.isDirectory || childFileList.isNullOrEmpty()) {
        logD("缓存的JSON文件不存在 backupJsonDirPath=$backupJsonDirPath")
        return mutableListOf()
    }
    var zhFile: File? = null
    var enFile: File? = null
    for (itemChildFile in childFileList) {
        if (itemChildFile.name.trim().lowercase(Locale.CHINA).endsWith("zh.json")) {
            zhFile = itemChildFile
        } else if (itemChildFile.name.trim().lowercase(Locale.CHINA).endsWith("en.json")) {
            enFile = itemChildFile
        }
        if (zhFile != null && enFile != null) break
    }
    if (zhFile == null) {
        logD("没有找到中文JSON文件")
        return mutableListOf()
    }
    val bufferedReader = BufferedReader(FileReader(zhFile))
    val zhTranslateInfoList = mutableListOf<TranslateInfo>()
    var line: String? = null
    var lineNumber = 0
    var searchText = zhValue.trim()
    val specialSymbolsList = getSearchSpecialSymbolsList()
    for (specialSymbols in specialSymbolsList) {
        searchText = searchText.replace(specialSymbols, "")
    }
    while (true) {
        line = bufferedReader.readLine()
        if (line.isNullOrEmpty()) break
        line = line.trim()
        if (line.trim() == "{" || line.trim() == "}") {
            lineNumber++
            continue
        }
        line = line.substring(0, line.length - 1)
        val splitValue = line.split("\":\"")
        if (splitValue.size == 2) {
            var key = splitValue[0].trim()
            var value = splitValue[1].trim()
            key = key.substring(1, key.length)
            value = value.substring(0, value.length - 1)
            var originalValue = value
            for (specialSymbols in specialSymbolsList) {
                value = value.replace(specialSymbols, "")
            }
            if (exactSearch) {
                if (value == searchText) {
                    logD("中文----->找到的键值对信息 key=$key,value=$originalValue")
                    zhTranslateInfoList.add(
                        TranslateInfo(
                            key = key,
                            languageCode = "zh",
                            value = originalValue,
                            lineNumber = lineNumber
                        )
                    )
                    break
                }
            } else {
                if (value.contains(searchText)) {
                    logD("中文----->找到的键值对信息 key=$key,value=$originalValue")
                    zhTranslateInfoList.add(
                        TranslateInfo(
                            key = key,
                            languageCode = "zh",
                            value = originalValue,
                            lineNumber = lineNumber
                        )
                    )
                }
            }
        }
        lineNumber++
    }
    if (zhTranslateInfoList.isEmpty()) {
        logE("没有找到相似的翻译")
        return mutableListOf()
    }
    if (exactSearch) {
        return mutableListOf(zhTranslateInfoList)
    }
    zhTranslateInfoList.sortBy { it.value.length }
    val lineNumberList = mutableListOf<Int>()
    for (itemTranslateInfo in zhTranslateInfoList) {
        lineNumberList.add(itemTranslateInfo.lineNumber)
    }
    lineNumberList.sortBy { it }
    val result = mutableListOf<MutableList<TranslateInfo>>()
    result.add(zhTranslateInfoList)
    var hasFinedLineNumberIndex = 0
    val notFoundDefaultValue = "没有找到翻译"
    if (enFile != null && !exactSearch) {
        val enTranslateInfo = mutableListOf<TranslateInfo>()
        lineNumber = 0
        val enBufferReader = BufferedReader(FileReader(enFile))
        while (true) {
            if (hasFinedLineNumberIndex >= lineNumberList.size) {
                break
            }
            line = enBufferReader.readLine()
            if (line == null) {
                logD("JSON文件读取完了 hasFinedLineNumberIndex=$hasFinedLineNumberIndex,${if (hasFinedLineNumberIndex < lineNumberList.size) lineNumberList[hasFinedLineNumberIndex] else ""}")
                break
            }
            if (lineNumber == lineNumberList[hasFinedLineNumberIndex]) {
                line = line.trim().substring(0, line.length - 1)
                val splitValue = line.split("\":\"")
                if (splitValue.size == 2) {
                    var key = splitValue[0].trim()
                    var value = splitValue[1].trim()
                    key = key.substring(1, key.length)
                    value = value.substring(0, value.length - 1)
                    logD("英文----->找到的键值对信息 key=$key,value=$value")
                    enTranslateInfo.add(
                        TranslateInfo(
                            key = key,
                            languageCode = "en",
                            value = value,
                            lineNumber = lineNumber
                        )
                    )
                } else {
                    enTranslateInfo.add(
                        TranslateInfo(
                            key = zhTranslateInfoList[hasFinedLineNumberIndex].key,
                            languageCode = "en", value = notFoundDefaultValue, lineNumber = lineNumber
                        )
                    )
                }
                hasFinedLineNumberIndex++
            }
            lineNumber++
        }
        while (hasFinedLineNumberIndex < lineNumberList.size) {
            enTranslateInfo.add(
                TranslateInfo(
                    key = zhTranslateInfoList[hasFinedLineNumberIndex].key,
                    languageCode = "en",
                    value = notFoundDefaultValue,
                    lineNumber = lineNumberList[hasFinedLineNumberIndex]
                )
            )
            hasFinedLineNumberIndex++
        }
        result.add(enTranslateInfo)
    }


//        for (itemChildFile in childFileList) {
//            if (itemChildFile == zhFile || itemChildFile == enFile) continue
//            val fileName = itemChildFile.name.trim().lowercase(Locale.CHINA)
//            if (!fileName.endsWith(".json")) continue
//            val languageCode = fileName.substring(
//                fileName.lastIndexOf("_") + 1,
//                fileName.lastIndexOf(".")
//            )
//            val itemTranslateInfo = mutableListOf<TranslateInfo>()
//            lineNumber = 0
//            hasFineLineNumberIndex = 0
//            val itemBufferReader = BufferedReader(FileReader(itemChildFile))
//            while (true) {
//                if (hasFineLineNumberIndex >= lineNumberList.size) {
//                    break
//                }
//                line = itemBufferReader.readLine()
//                if (line.isNullOrEmpty()) {
//                    break
//                }
//                if (lineNumber == lineNumberList[hasFineLineNumberIndex]) {
//                    line = line.trim().substring(0, line.length - 1)
//                    val splitValue = line.split("\":\"")
//                    if (splitValue.size == 2) {
//                        var key = splitValue[0].trim()
//                        var value = splitValue[1].trim()
//                        key = key.substring(1, key.length)
//                        value = value.substring(0, value.length - 1)
//                        logD("找到的键值对信息 key=$key,value=$value")
//                        itemTranslateInfo.add(
//                            TranslateInfo(
//                                key = key,
//                                languageCode = languageCode,
//                                value = value,
//                                lineNumber = lineNumber
//                            )
//                        )
//                    } else {
//                        itemTranslateInfo.add(
//                            TranslateInfo(
//                                key = zhTranslateInfoList[hasFineLineNumberIndex].key,
//                                languageCode = languageCode, value = notFoundDefaultValue, lineNumber = lineNumber
//                            )
//                        )
//                    }
//                    hasFineLineNumberIndex++
//                }
//                lineNumber++
//            }
//            while (hasFineLineNumberIndex < lineNumberList.size) {
//                itemTranslateInfo.add(
//                    TranslateInfo(
//                        key = zhTranslateInfoList[hasFineLineNumberIndex].key,
//                        languageCode = languageCode,
//                        value = notFoundDefaultValue,
//                        lineNumber = lineNumberList[hasFineLineNumberIndex]
//                    )
//                )
//                hasFineLineNumberIndex++
//            }
//            result.add(itemTranslateInfo)
//        }
    return result
}

data class TranslateInfo(var key: String, var languageCode: String, var value: String, var lineNumber: Int)