package com.jdz.translate

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.roots.ProjectRootManager
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.util.*

/**
 *翻译配置文件名称
 */
const val Config_File_Name = "translate_config.json"

/**
 *获取存储备份的JSON文件路径
 */
fun getBackJsonFileDir(event: AnActionEvent): String {
    val rootFile =
        ProjectRootManager.getInstance(event.project!!).fileIndex.getContentRootForFile(event.project!!.projectFile!!)!!
    if (isMacSystem()) {
        return rootFile.path.plus(File.separatorChar).plus("build/language/backup")
    }
    val pluginId = PluginId.getId("org.jdz.translate.translate")
    val plugin = PluginManagerCore.getPlugin(pluginId)!!
    val pluginInstallPath = plugin.pluginPath.toFile().absolutePath
    return pluginInstallPath.plus(File.separatorChar).plus(rootFile.name)
}

/**
 *获取项目根目录
 */
fun getRootPath(event: AnActionEvent): String {
    return ProjectFileIndex.getInstance(event.project!!).getContentRootForFile(event.project!!.projectFile!!)!!.path
}

/**
 *获取excel转成dart文件存储路径
 */
fun getExportFilePath(event: AnActionEvent): String {
    val rootPath = getRootPath(event)
    val file = File(rootPath.plus(File.separatorChar).plus(Config_File_Name))
    var dartClassPath = Gson().fromJson<Map<String, String>>(file.readText(charset = Charsets.UTF_8),
        object : TypeToken<Map<String, String>>() {}.type)["out_put_dir"]!!.trim()
    if (dartClassPath.startsWith(File.separatorChar))dartClassPath=dartClassPath.substring(1)
    return getRootPath(event).plus(File.separatorChar).plus(dartClassPath)
}

/**
 *获取excel文件存储路径
 */
fun getExcelFilePath(event: AnActionEvent): String {
    val rootPath = getRootPath(event)
    val file = File(rootPath.plus(File.separatorChar).plus(Config_File_Name))
    var excelPath = Gson().fromJson<Map<String, String>>(file.readText(charset = Charsets.UTF_8),
        object : TypeToken<Map<String, String>>() {}.type)["excel_path"]!!.trim()
    if (excelPath.startsWith(File.separatorChar))excelPath=excelPath.substring(1)
    return getRootPath(event).plus(File.separatorChar).plus(excelPath)
}

/**
 *获取世界时钟文件存储路径
 */
fun getWorldCityPath(event: AnActionEvent): String {
    val rootPath = getRootPath(event)
    val file = File(rootPath.plus(File.separatorChar).plus(Config_File_Name))
    var excelPath = Gson().fromJson<Map<String, String>>(file.readText(charset = Charsets.UTF_8),
        object : TypeToken<Map<String, String>>() {}.type)["world_cities"]!!.trim()
    if (excelPath.startsWith(File.separatorChar))excelPath=excelPath.substring(1)
    return getRootPath(event).plus(File.separatorChar).plus(excelPath)
}

/**
 *判断当前系统是否是MAC系统
 */
fun isMacSystem(): Boolean {
    val osName = System.getProperty("os.name")
    logD("osName=$osName")
    return !osName.isNullOrEmpty() && osName.contains("Mac")
}

/**
 *翻译可以默认前缀
 */
fun getTranslateKeyPrefix(): String {
    return "idoKey"
}

/**
 *获取语言翻译生成的dart文件名称
 */
fun getDartFileName(languageCode: String): String {
    return "language_$languageCode.dart"
}

/**
 *获取语言翻译生成的json文件名称
 */
fun getJsonFileName(languageCode: String): String {
    return "language_$languageCode.json"
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
    if (!hasCachedJsonFile(event)) {
        logD("没有检测到缓存的json文件，开始刷新缓存")
        refreshCachedJsonFile(event)
    }
    val backupJsonDirPath = getBackJsonFileDir(event)
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
    var line: String?
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
    return result
}

/**
 *通过中文查找翻译
 * @param zhValue 搜索的关键字
 * @param event 触发事件信息
 * @param exactSearch 是否精确查找
 */
fun findTranslateByZh(zhValue: String, event: AnActionEvent, exactSearch: Boolean): List<List<TranslateInfo>> {
    logD("开始通过中文查找翻译信息")
    if (!hasCachedJsonFile(event)) {
        logD("没有检测到缓存的json文件，开始刷新缓存")
        refreshCachedJsonFile(event)
    }
    val backupJsonDirPath = getBackJsonFileDir(event)
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
    var line: String?
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
            val originalValue = value
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
    logD("zhTranslateInfoList=${Gson().toJson(zhTranslateInfoList)}")
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
        val enTranslateInfoMap = mutableMapOf<String, TranslateInfo>()
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
                    enTranslateInfoMap[key] = TranslateInfo(
                        key = key,
                        languageCode = "en",
                        value = value,
                        lineNumber = lineNumber
                    )
                } else {
                    enTranslateInfoMap[zhTranslateInfoList[hasFinedLineNumberIndex].key] = TranslateInfo(
                        key = zhTranslateInfoList[hasFinedLineNumberIndex].key,
                        languageCode = "en", value = notFoundDefaultValue, lineNumber = lineNumber
                    )
                }
                hasFinedLineNumberIndex++
            }
            lineNumber++
        }
        while (hasFinedLineNumberIndex < lineNumberList.size) {
            enTranslateInfoMap[zhTranslateInfoList[hasFinedLineNumberIndex].key] = TranslateInfo(
                key = zhTranslateInfoList[hasFinedLineNumberIndex].key,
                languageCode = "en",
                value = notFoundDefaultValue,
                lineNumber = lineNumberList[hasFinedLineNumberIndex]
            )
            hasFinedLineNumberIndex++
        }
        val enTranslateInfoList = mutableListOf<TranslateInfo>()
        for (itemTranslateInfo in zhTranslateInfoList) {
            enTranslateInfoList.add(enTranslateInfoMap[itemTranslateInfo.key]!!)
        }
        logD("enTranslateInfo=${Gson().toJson(enTranslateInfoList)}")
        result.add(enTranslateInfoList)
    }
    return result
}

/**
 *转换翻译注释显示文本
 * 当超过指定长度的时候，就以...结尾
 */
fun getShowCommentText(sourceText: String, maxLength: Int = 20): String {
    if (sourceText.length <= maxLength) return sourceText
    return sourceText.substring(0, maxLength).plus("...")
}

/**
 *验证JSON文件名是否合法
 */
fun validJsonFileName(fileName: String): Boolean {
    return fileName.isNotEmpty() && fileName.indexOf("_") in 1 until fileName.length - 1
}

/**
 *自动化添加翻译默认语种
 */
fun getDefaultLanguageDesc(): String {
    return "zh"
}

/**
 *检查本地是否缓存有JSON文件
 */
fun hasCachedJsonFile(event: AnActionEvent): Boolean {
    val backJsonFilePath = getBackJsonFileDir(event)
    logD("开始查找本地是否存在缓存的JSON文件 backJsonFilePath=$backJsonFilePath")
    val backupJsonFile = File(backJsonFilePath)
    return backupJsonFile.exists() && backupJsonFile.isDirectory && !backupJsonFile.listFiles().isNullOrEmpty()
}

/**
 *重新刷新缓存的JSON文件
 */
fun refreshCachedJsonFile(event: AnActionEvent) {
    logD("检测到本地不存在缓存的JSON文件，开始从Dart文件中读取缓存文件内容")
    //从dart文件中匹配key的正则表达式
    val keyPlatter = Regex("ido_key_\\d{1,10}")
    //从dart文件中匹配value的正则表达式
    val valuePlatter = Regex("(\\\".*\\\"[.|;|\\n])")
    //翻译dart文件存放目录
    val languageDartDirPath = getExportFilePath(event)
    //缓存JSON文件目录
    val cacheJsonDirPath = getBackJsonFileDir(event)
    clearDirChildFile(dirPath = cacheJsonDirPath)
    val cacheDir = File(cacheJsonDirPath)
    if (!cacheDir.exists() || !cacheDir.isDirectory) {
        cacheDir.mkdirs()
    }
    val languageDartDirFile = File(languageDartDirPath)
    if (!languageDartDirFile.exists()) {
        logD("项目指定目录不存在 languageDartDirPath=$languageDartDirPath")
        return
    }
    val chileFileList = languageDartDirFile.listFiles { file ->
        file.name.endsWith(".dart") && file.name.contains("_") && file.name.startsWith("language_")
    }
    if (chileFileList.isNullOrEmpty()) {
        logD("项目指定目录中不存在任何Dart文件")
        return
    }
    for (itemChildFile in chileFileList) {
        val local = itemChildFile.name.substring(
            itemChildFile.name.lastIndexOf("_") + 1,
            itemChildFile.name.lastIndexOf(".dart")
        ).lowercase(
            Locale.CHINA
        )
        if (local.isEmpty()) continue
        val bufferReader = BufferedReader(FileReader(itemChildFile))
        val fileContent = bufferReader.readText()
        bufferReader.close()
        if (fileContent.isNotEmpty()) {
            val keyList = keyPlatter.findAll(fileContent).toList()
            val valueList = valuePlatter.findAll(fileContent).toList()
            if (keyList.count() == valueList.count()) {
                val outFilePath = cacheJsonDirPath.plus(File.separatorChar).plus(getJsonFileName(languageCode = local))
                val outFile = File(outFilePath)
                if (outFile.exists() && outFile.isFile) {
                    outFile.delete()
                }
                outFile.createNewFile()
                val bufferWriter = BufferedWriter(FileWriter(outFile))
                bufferWriter.write("{")
                val totalCount = keyList.count()
                for (index in 0 until totalCount) {
                    val itemKey = keyList[index].value
                    var itemValue = valueList[index].value.trim()
                    if (itemValue.endsWith(".") || itemValue.endsWith(";")) {
                        itemValue = itemValue.substring(0, itemValue.length - 1)
                    }
                    bufferWriter.newLine()
                    if (index == totalCount - 1) {
                        bufferWriter.write("\"$itemKey\":$itemValue")
                    } else {
                        bufferWriter.write("\"$itemKey\":$itemValue,")
                    }
                }
                bufferWriter.newLine()
                bufferWriter.write("}")
                bufferWriter.flush()
                bufferWriter.close()
            }
        }
    }
}

fun clearDirChildFile(dirPath: String) {
    val dirFile = File(dirPath)
    if (dirFile.exists() && dirFile.isDirectory) {
        val childFileList = dirFile.listFiles()
        if (!childFileList.isNullOrEmpty()) {
            for (itemChildFile in childFileList) {
                itemChildFile.delete()
            }
        }
    }
}

data class TranslateInfo(var key: String, var languageCode: String, var value: String, var lineNumber: Int)