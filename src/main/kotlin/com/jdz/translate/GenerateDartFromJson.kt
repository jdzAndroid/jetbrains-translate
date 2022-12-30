package com.jdz.translate

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.roots.ProjectFileIndex
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.util.*


/**
 * 用途-将json文件转换成dart实体类
 * 只读取当前上下文所在目录中的JSON文件
 * 文件数据路径-当前上下文所在目录
 * 输出文件命名规范：和Json文件名称一样
 * 数据文件类型：生成的json dart类基类和具体的dart类
 * 注意事项：json文件key值完全保持一致
 */
class GenerateDartFromJson : AnAction() {
    private val mRegex = Regex("\\{\\S+\\}")

    //用于存储中文Dart Class 类名
    private var mDefaultDartClassName = ""

    //用于存储中文Dart实体文件名
    private var mDefaultDartFileName = ""

    override fun actionPerformed(event: AnActionEvent) {
        if (!hasCachedJsonFile(event)) {
            refreshCachedJsonFile(event)
        }
        val backupJsonFileDirPath = getBackJsonFileDir(event)
        val dartFileDirPath = getExportFilePath(event)
        logD("开始将json文件转换成dart文件 backupJsonFileDirPath=$backupJsonFileDirPath,dartFileDirPath=$dartFileDirPath")
        val dartDirFile = File(dartFileDirPath)
        if (!dartDirFile.exists()) {
            dartDirFile.mkdirs()
        }
        //存储当前目录下面的所有json文件的绝对路径
        val jsonFilePathList = mutableListOf<String>()
        logD("开始查找当前目录下面的所有JSON格式的文件")
        collectAllJsonFile(directoryPath = backupJsonFileDirPath, jsonFilePathList = jsonFilePathList)
        val jsonFileDir = File(backupJsonFileDirPath)
        if (!jsonFileDir.exists() || !jsonFileDir.isDirectory) {
            jsonFileDir.mkdirs()
        }
        val worldCityFilePath = getWorldCityPath(event)
        val worldCityFile = File(worldCityFilePath)


        if (jsonFilePathList.isEmpty() && !worldCityFile.exists()) {
            logE("$backupJsonFileDirPath 和 $worldCityFilePath 目录下面没有找人任何json文件")
            return
        }
        val worldCityJsonInfo = collectWorldCityKeyValueInfo(worldCityFilePath)
        val excelJsonInfoList = collectAllKeyValueInfo(jsonFilePathList)
        if (excelJsonInfoList.isEmpty()) {
            logD("没有查找到任何的JSON文件的健值对信息，或者JSON文件内容为空")
        }
        val excelMethodInfoList = collectMethodInfo(excelJsonInfoList)
        val worldMethodInfoList = collectMethodInfo(mutableListOf(worldCityJsonInfo))
        if (worldMethodInfoList.isNotEmpty()) {
            excelMethodInfoList.addAll(worldMethodInfoList)
            excelJsonInfoList.forEach {
                it.valueInfoMap.putAll(worldCityJsonInfo.valueInfoMap)
            }
        }
        if (excelMethodInfoList.isNotEmpty()) {
            generateBaseDartClass(dartFileDirPath, excelMethodInfoList)
            val dartClassInfoList = generateJsonDartClass(dartFileDirPath, excelJsonInfoList, excelMethodInfoList)
            generateTranslateProxyClass(dartFileDirPath, excelMethodInfoList)
            generateTranslateManagerDartClass(
                rootPath = dartFileDirPath,
                dartClassInfoList = dartClassInfoList,
                worldCityMethodInfoList = worldMethodInfoList
            )
            generateStringExtensionClass(dartClassInfoList)
            generateDefaultLanguageLocalization(dartClassInfoList)
            ProjectFileIndex.getInstance(event.project!!).getContentRootForFile(event.project!!.projectFile!!)
                ?.refresh(true, true)
        } else {
            logE("没有解析到任何可用信息")
        }
    }

    /**
     *生成字符串扩展方法，方便更直观的查看翻译
     */
    private fun generateStringExtensionClass(dartClassInfoList: List<DartClassInfo>) {
        if (dartClassInfoList.isEmpty()) return
        val dartClassFile = File(dartClassInfoList.first().filePath)
        val extensionsClassFile = File(dartClassFile.parent, mLanguageExtensionFileName)
        if (extensionsClassFile.exists() && extensionsClassFile.isFile) {
            extensionsClassFile.delete()
        }
        val bufferWriter = BufferedWriter(FileWriter(extensionsClassFile))
        bufferWriter.write("extension $mLanguageExtensionClassName on String {")
        for (index in dartClassInfoList.indices) {
            bufferWriter.newLine()
            if (index > 0) {
                bufferWriter.newLine()
            }
            val itemDartClassInfo = dartClassInfoList[index]
            bufferWriter.write("  String ${itemDartClassInfo.local}(String ${itemDartClassInfo.local}) {")
            bufferWriter.newLine()
            bufferWriter.write("    return this;")
            bufferWriter.newLine()
            bufferWriter.write("  }")
        }
        bufferWriter.newLine()
        bufferWriter.write("}")
        bufferWriter.newLine()
        bufferWriter.flush()
        bufferWriter.close()
    }

    /**
     *生成Dart Translate Manager类
     */
    private fun generateTranslateManagerDartClass(
        rootPath: String,
        dartClassInfoList: List<DartClassInfo>,
        worldCityMethodInfoList: List<MethodInfo>
    ) {
        val dartClassFilePath = if (rootPath.endsWith(File.separator)) rootPath.plus(mManagerDartFileName)
        else rootPath.plus(File.separator).plus(mManagerDartFileName)
        val managerFile = File(dartClassFilePath)
        if (managerFile.exists() && managerFile.isFile) {
            managerFile.delete()
        }
        managerFile.createNewFile()
        logD("translateManagerFilePath=$dartClassFilePath")
        val bufferedWriter = BufferedWriter(FileWriter(managerFile))
        bufferedWriter.write("import '$mBaseDartFileName';")
        bufferedWriter.newLine()
        bufferedWriter.newLine()
        bufferedWriter.write("import '$mProxyDartFileName';")
        for (itemDartClassInfo in dartClassInfoList) {
            bufferedWriter.newLine()
            bufferedWriter.write("import '${itemDartClassInfo.fileName}';")
        }
        bufferedWriter.newLine()
        bufferedWriter.newLine()
        bufferedWriter.write("class $mManagerDartClassName extends $mBaseDartClassName with $mProxyDartClassName {")
        bufferedWriter.newLine()
        bufferedWriter.write("  static const Map<String, String> emptyMap = {};")
        bufferedWriter.newLine()
        bufferedWriter.write("  static final TranslateManager _manager = TranslateManager._newInstance();")
        bufferedWriter.newLine()
        bufferedWriter.write("  Locale _currentLocal=const Locale(\"zh\",\"cn\");")
        bufferedWriter.newLine()
        bufferedWriter.write("  Locale get currentLocal=>_currentLocal;")
        bufferedWriter.newLine()
        bufferedWriter.newLine()
        bufferedWriter.write("  static TranslateProxy get translateProxy => _manager;")
        bufferedWriter.newLine()
        bufferedWriter.newLine()
        bufferedWriter.write("  TranslateManager._newInstance();")
        bufferedWriter.newLine()
        bufferedWriter.newLine()
        bufferedWriter.write("  factory TranslateManager() {")
        bufferedWriter.newLine()
        bufferedWriter.write("    return _manager;")
        bufferedWriter.newLine()
        bufferedWriter.write("  }")
        bufferedWriter.newLine()
        bufferedWriter.newLine()
        bufferedWriter.write("  void localeChanged(")
        bufferedWriter.newLine()
        bufferedWriter.write("      {required Locale locale,")
        bufferedWriter.newLine()
        bufferedWriter.write("      Map<String, String> serverValueMap = emptyMap}) {")
        bufferedWriter.newLine()
        bufferedWriter.write("    _currentLocal=locale;")
        bufferedWriter.newLine()
        bufferedWriter.write("    BaseTranslate localTranslate;")
        bufferedWriter.write("    switch (_currentLocal.languageCode.toLowerCase()) {")
        var defaultTranslate = dartClassInfoList.first()
        for (itemDartClassInfo in dartClassInfoList) {
            if (itemDartClassInfo.local == getDefaultLanguageDesc()) {
                defaultTranslate = itemDartClassInfo
                continue
            }
            bufferedWriter.newLine()
            bufferedWriter.write("      case \"${itemDartClassInfo.local}\":")
            bufferedWriter.newLine()
            bufferedWriter.write("        localTranslate = ${itemDartClassInfo.className}();")
            bufferedWriter.newLine()
            bufferedWriter.write("        break;")
        }
        bufferedWriter.newLine()
        bufferedWriter.write("      default:")
        bufferedWriter.newLine()
        bufferedWriter.write("        localTranslate = ${defaultTranslate.className}();")
        bufferedWriter.newLine()
        bufferedWriter.write("        break;")
        bufferedWriter.newLine()
        bufferedWriter.write("    }")
        bufferedWriter.newLine()
        bufferedWriter.write("    translateProxy.updateSource(")
        bufferedWriter.newLine()
        bufferedWriter.write("        localTranslate: localTranslate, serverValueMap: serverValueMap);")
        bufferedWriter.newLine()
        bufferedWriter.write("  }")
        bufferedWriter.newLine()
        if (worldCityMethodInfoList.isNotEmpty()) {
            bufferedWriter.newLine()
            bufferedWriter.write("  String getWorldValue(String worldKey) {")
            bufferedWriter.newLine()
            bufferedWriter.write("    String result = \"\";")
            bufferedWriter.newLine()
            bufferedWriter.write("    worldKey = worldKey.trim();")
            bufferedWriter.newLine()
            bufferedWriter.write("    switch (worldKey) {")
            bufferedWriter.newLine()
            for (methodInfo in worldCityMethodInfoList) {
                bufferedWriter.write("      case \"${methodInfo.key}\":")
                bufferedWriter.newLine()
                bufferedWriter.write("      case \"${methodInfo.key.replace(World_Key_Prefix, "")}\":")
                bufferedWriter.newLine()
                bufferedWriter.write("        result = ${methodInfo.key}();")
                bufferedWriter.newLine()
                bufferedWriter.write("        break;")
                bufferedWriter.newLine()
            }
            bufferedWriter.write("      default:")
            bufferedWriter.newLine()
            bufferedWriter.write("        result = \"\";")
            bufferedWriter.newLine()
            bufferedWriter.write("        break;")
            bufferedWriter.newLine()
            bufferedWriter.write("    }")
            bufferedWriter.newLine()
            bufferedWriter.write("    return result;")
            bufferedWriter.newLine()
            bufferedWriter.write("  }")
        }
        bufferedWriter.write("}")
        bufferedWriter.newLine()
        bufferedWriter.flush()
        bufferedWriter.close()
    }

    /**
     *生成获取翻译的代理Dart Class
     */
    private fun generateTranslateProxyClass(dartFileDirPath: String, methodInfoList: List<MethodInfo>) {
        val baseDartPackageImport = "import '$mBaseDartFileName';"
        val dartClassFilePath = if (dartFileDirPath.endsWith(File.separator)) dartFileDirPath.plus(mProxyDartFileName)
        else dartFileDirPath.plus(File.separator).plus(mProxyDartFileName)
        logD("dartClassFilePath=$dartClassFilePath")
        val dartClassFile = File(dartClassFilePath)
        if (dartClassFile.exists() && dartClassFile.isFile) {
            dartClassFile.delete()
        }
        dartClassFile.createNewFile()
        val bufferedWriter = BufferedWriter(FileWriter(dartClassFile))
        bufferedWriter.write(baseDartPackageImport)
        bufferedWriter.newLine()
        bufferedWriter.write("import 'language_zh.dart'")
        bufferedWriter.newLine()
        bufferedWriter.newLine()
        bufferedWriter.write("mixin $mProxyDartClassName on $mBaseDartClassName {")
        val serverValueMap = "_serverValueMap"
        val localTranslate = "_localTranslate"
        bufferedWriter.newLine()
        bufferedWriter.write("  $mBaseDartClassName $localTranslate=LanguageZhTranslate();")
        bufferedWriter.newLine()
        bufferedWriter.write("  Map<String, String> $serverValueMap={};")
        bufferedWriter.newLine()
        bufferedWriter.newLine()
        bufferedWriter.write("  void updateSource(")
        bufferedWriter.newLine()
        bufferedWriter.write("      {$mBaseDartClassName? localTranslate, Map<String, String>? serverValueMap}) {")
        bufferedWriter.newLine()
        bufferedWriter.write("    if (localTranslate != null) {")
        bufferedWriter.newLine()
        bufferedWriter.write("      $localTranslate = localTranslate;")
        bufferedWriter.newLine()
        bufferedWriter.write("    }")
        bufferedWriter.newLine()
        bufferedWriter.write("    if (serverValueMap != null) {")
        bufferedWriter.newLine()
        bufferedWriter.write("      $serverValueMap = serverValueMap;")
        bufferedWriter.newLine()
        bufferedWriter.write("    }")
        bufferedWriter.newLine()
        bufferedWriter.write("  }")

        for (itemMethodInfo in methodInfoList) {
            bufferedWriter.newLine()
            bufferedWriter.newLine()
            bufferedWriter.write("  @override")
            bufferedWriter.newLine()
            if (itemMethodInfo.argList.isEmpty()) {
                bufferedWriter.write("  String ${itemMethodInfo.methodName}() {")
                bufferedWriter.newLine()
                bufferedWriter.write("    if ($serverValueMap.containsKey(\"${itemMethodInfo.methodName}\")) {")
                bufferedWriter.newLine()
                bufferedWriter.write("      return $serverValueMap[\"${itemMethodInfo.methodName}\"]!;")
                bufferedWriter.newLine()
                bufferedWriter.write("    }")
                bufferedWriter.newLine()
                bufferedWriter.write("    return $localTranslate.${itemMethodInfo.methodName}();")
            } else {
                val argBuilder = StringBuilder()
                val replaceBuilder = StringBuilder("$serverValueMap[\"${itemMethodInfo.methodName}\"]!")
                val paramsBuilder = StringBuilder()
                for (index in itemMethodInfo.argList.indices) {
                    val itemArgName = itemMethodInfo.argList[index]
                    if (index == 0) {
                        argBuilder.append("String $itemArgName")
                        paramsBuilder.append(itemArgName)
                    } else {
                        argBuilder.append(" String $itemArgName")
                        paramsBuilder.append(" $itemArgName")
                    }
                    if (index != itemMethodInfo.argList.size - 1) {
                        argBuilder.append(",")
                        paramsBuilder.append(",")
                    }
                    replaceBuilder.append(".replaceAll(\"{$itemArgName}\", $itemArgName)")
                }
                bufferedWriter.write("  String ${itemMethodInfo.methodName}($argBuilder) {")
                bufferedWriter.newLine()
                bufferedWriter.write("    if ($serverValueMap.containsKey(\"${itemMethodInfo.methodName}\")) {")
                bufferedWriter.newLine()
                bufferedWriter.write("      return $replaceBuilder;")
                bufferedWriter.newLine()
                bufferedWriter.write("    }")
                bufferedWriter.newLine()
                bufferedWriter.write("    return $localTranslate.${itemMethodInfo.methodName}($paramsBuilder);")
            }
            bufferedWriter.newLine()
            bufferedWriter.write("  }")
        }
        bufferedWriter.newLine()
        bufferedWriter.write("}")
        bufferedWriter.newLine()
        bufferedWriter.flush()
        bufferedWriter.close()
    }

    /**
     *开始生成每个Json文件对应的Dart Class
     */
    private fun generateJsonDartClass(
        dartFileDirPath: String,
        jsonInfoList: List<JsonInfo>,
        methodInfoList: List<MethodInfo>
    ): List<DartClassInfo> {
        val classInfoList = mutableListOf<DartClassInfo>()
        for (itemJsonInfo in jsonInfoList) {
            try {
                classInfoList.add(generateJsonDartClass(dartFileDirPath, itemJsonInfo, methodInfoList))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return classInfoList
    }

    private fun generateJsonDartClass(
        dartFileDirPath: String,
        jsonInfo: JsonInfo,
        methodInfoList: List<MethodInfo>
    ): DartClassInfo {
        val jsonFile = File(jsonInfo.filePath)
        val baseDartPackageImport = "import '$mBaseDartFileName';"
        val dartClassFilePath = dartFileDirPath.plus(File.separator).plus(
            jsonFile.name.substring(0, jsonFile.name.indexOf(".")).lowercase(
                Locale.CHINA
            )
        ).plus(".dart")
        logD("dartClassFilePath=$dartClassFilePath")
        val dartClassFile = File(dartClassFilePath)
        if (dartClassFile.exists() && dartClassFile.isFile) {
            dartClassFile.delete()
        }
        dartClassFile.createNewFile()
        val languageCode = jsonFile.name.substring(jsonFile.name.lastIndexOf("_") + 1, jsonFile.name.lastIndexOf("."))
        val dartClassName = getDartClassName(dartClassFile.name)
        if (languageCode == getDefaultLanguageDesc() || (mDefaultDartFileName.isEmpty() || mDefaultDartClassName.isEmpty())) {
            mDefaultDartClassName = dartClassName
            mDefaultDartFileName = dartClassFile.name
        }
        val bufferedWriter = BufferedWriter(FileWriter(dartClassFile))
        bufferedWriter.write(baseDartPackageImport)
        bufferedWriter.newLine()
        bufferedWriter.newLine()
        bufferedWriter.write("class $dartClassName extends $mBaseDartClassName {")
        for (itemMethodInfo in methodInfoList) {
            bufferedWriter.newLine()
            bufferedWriter.write("  @override")
            bufferedWriter.newLine()
            if (itemMethodInfo.argList.isEmpty()) {
                bufferedWriter.write("  String ${itemMethodInfo.key}() {")
                bufferedWriter.newLine()
                bufferedWriter.write(
                    "    return \"${
                        jsonInfo.valueInfoMap[itemMethodInfo.key]!!.value.replace(
                            "\"",
                            "\\\""
                        ).replace("\\\\\"", "\\\"")
                    }\";"
                )
            } else {
                val argBuilder = StringBuilder()
                val returnBuilder = StringBuilder(
                    "    return \"${
                        jsonInfo.valueInfoMap[itemMethodInfo.key]!!.value.replace(
                            "\"",
                            "\\\""
                        ).replace("\\\\\"", "\\\"")
                    }\""
                )
                for (index in itemMethodInfo.argList.indices) {
                    val itemArgName = itemMethodInfo.argList[index]
                    if (index == 0) {
                        argBuilder.append("String $itemArgName")
                    } else {
                        argBuilder.append(" String $itemArgName")
                    }

                    if (index != itemMethodInfo.argList.size - 1) {
                        argBuilder.append(",")
                    }
                    returnBuilder.append(".replaceAll(\"{$itemArgName}\", $itemArgName)")
                }
                bufferedWriter.write("  String ${itemMethodInfo.key}($argBuilder) {")
                bufferedWriter.newLine()
                bufferedWriter.write("$returnBuilder;")
            }
            bufferedWriter.newLine()
            bufferedWriter.write("  }")
            bufferedWriter.newLine()
        }
        bufferedWriter.write("}")
        bufferedWriter.newLine()
        bufferedWriter.flush()
        bufferedWriter.close()
        return DartClassInfo(
            filePath = dartClassFilePath,
            local = languageCode,
            fileName = dartClassFile.name,
            className = dartClassName
        )
    }

    /**
     *获取生成的Dart类文件名
     */
    private fun getDartClassName(jsonFileName: String): String {
        var dartClassName = jsonFileName.substring(0, jsonFileName.indexOf(".")).trim()
        if (dartClassName.contains("_") || dartClassName.contains("-")) {
            val nameArray = if (dartClassName.contains("_")) dartClassName.split("_")
            else dartClassName.split("-")
            dartClassName = ""
            for (itemName in nameArray) {
                if (itemName.isEmpty()) continue
                dartClassName += itemName.substring(0, 1).uppercase(Locale.CHINA).plus(
                    itemName.substring(1).lowercase(
                        Locale.CHINA
                    )
                )
            }
            dartClassName += "Translate"
        } else {
            dartClassName =
                dartClassName.substring(0, 1).uppercase(Locale.CHINA).plus(dartClassName.substring(1)).plus("Translate")
        }
        return dartClassName
    }

    /**
     *开始生成JSON文件对应的dart文件基类
     * @param fileDirectoryPath 文件目录路径
     * @param excelMethodInfoList 方法信息
     */
    private fun generateBaseDartClass(fileDirectoryPath: String, excelMethodInfoList: List<MethodInfo>) {
        val file =
            if (fileDirectoryPath.endsWith(File.separator)) File(fileDirectoryPath.plus(mBaseDartFileName)) else File(
                fileDirectoryPath.plus(File.separator).plus(mBaseDartFileName)
            )
        if (file.exists() && file.isFile) file.delete()
        file.createNewFile()
        val bufferedWriter = BufferedWriter(FileWriter(file))
        bufferedWriter.write("abstract class $mBaseDartClassName {")
        for (itemMethodInfo in excelMethodInfoList) {
            bufferedWriter.newLine()
            if (itemMethodInfo.argList.isEmpty()) {
                bufferedWriter.write("  String ${itemMethodInfo.methodName}();")
            } else {
                val argBuilder = StringBuilder()
                for (index in itemMethodInfo.argList.indices) {
                    val itemArgName = itemMethodInfo.argList[index]
                    if (index == 0) {
                        argBuilder.append("String $itemArgName")
                    } else {
                        argBuilder.append(" String $itemArgName")
                    }

                    if (index != itemMethodInfo.argList.size - 1) {
                        argBuilder.append(",")
                    }
                }
                bufferedWriter.write("  String ${itemMethodInfo.methodName}($argBuilder);")
            }
            bufferedWriter.newLine()
        }
        bufferedWriter.write("}")
        bufferedWriter.newLine()
        bufferedWriter.flush()
        bufferedWriter.close()
    }

    /**
     *搜集方法信息
     */
    private fun collectMethodInfo(jsonInfoList: List<JsonInfo>): MutableList<MethodInfo> {
        if (jsonInfoList.isEmpty()) return mutableListOf()
        val methodInfoList = mutableListOf<MethodInfo>()
        val firstJsonInfo = jsonInfoList.first()
        for (firstValueInfo in firstJsonInfo.valueInfoMap) {
            methodInfoList.add(
                MethodInfo(
                    key = firstValueInfo.key,
                    methodName = firstValueInfo.key,
                    value = firstValueInfo.value.value,
                    argList = firstValueInfo.value.argList
                )
            )
        }
        for (index in 1 until jsonInfoList.size) {
            val itemJsonInfo = jsonInfoList[index]
            for (itemMethodInfo in methodInfoList) {
                if (itemMethodInfo.argList.size < itemJsonInfo.valueInfoMap[itemMethodInfo.key]!!.argList.size) {
                    itemMethodInfo.argList = itemJsonInfo.valueInfoMap[itemMethodInfo.key]!!.argList
                }
            }
        }
        return methodInfoList
    }

    /**
     *统计世界时钟翻译键值对信息
     */
    private fun collectWorldCityKeyValueInfo(worldCityFilePath: String): JsonInfo {
        logD("开始搜索时间时钟JSON文件的健值对信息以及参数列表")
        //存储每个json文件中的键值对
        val valueInfoMap = mutableMapOf<String, ValueInfo>()
        getWorldCityKeyValueInfoFromJson(worldCityFilePath, valueInfoMap)
        return JsonInfo(filePath = worldCityFilePath, valueInfoMap = valueInfoMap)
    }

    /**
     *统计所有JSON文件的健值对信息
     */
    private fun collectAllKeyValueInfo(jsonFilePathList: List<String>): MutableList<JsonInfo> {
        logD("开始搜索JSON文件的健值对信息以及参数列表")
        //存储每个json文件中的键值对
        val jsonValueList = mutableListOf<JsonInfo>()
        for (itemJsonFilePath in jsonFilePathList) {
            val valueInfoMap = mutableMapOf<String, ValueInfo>()
            getKeyValueInfoFromJson(itemJsonFilePath, valueInfoMap)
            if (valueInfoMap.isNotEmpty()) {
                jsonValueList.add(JsonInfo(filePath = itemJsonFilePath, valueInfoMap = valueInfoMap))
            }
        }
        return jsonValueList
    }

    /**
     *获取世界时钟json文件中的健值对信息
     */
    private fun getWorldCityKeyValueInfoFromJson(jsonFilePath: String, valueInfoMap: MutableMap<String, ValueInfo>) {
        try {
            val jsonContent = File(jsonFilePath).readText(Charsets.UTF_8)
            val jsonMap =
                Gson().fromJson<List<WorldCityBean>>(jsonContent, object : TypeToken<List<WorldCityBean>>() {}.type)
            for (itemMapEntry in jsonMap) {
                val key = World_Key_Prefix.plus(itemMapEntry.nameKey)
                val value = itemMapEntry.name
                if (key.isEmpty() || value.isNullOrEmpty()) continue
                if (!valueInfoMap.containsKey(key)) {
                    valueInfoMap[key] = ValueInfo(key = key, value = value, argList = mutableListOf())
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     *获取单个json文件中的健值对信息
     */
    private fun getKeyValueInfoFromJson(jsonFilePath: String, valueInfoMap: MutableMap<String, ValueInfo>) {
        try {
            val jsonContent = File(jsonFilePath).readText(Charsets.UTF_8)
            val jsonMap =
                Gson().fromJson<Map<String, String>>(jsonContent, object : TypeToken<Map<String, String>>() {}.type)
            for (itemMapEntry in jsonMap) {
                val key = itemMapEntry.key
                val value = itemMapEntry.value
                val mathResultList = mRegex.findAll(value.replace("{", " {").replace("}", "} "))
                if (mathResultList.count() == 0) {
                    if (!valueInfoMap.containsKey(key)) {
                        valueInfoMap[key] = ValueInfo(key = key, value = value, argList = mutableListOf())
                    }
                } else {
                    val argList = mutableListOf<String>()
                    for (itemMathResult in mathResultList) {
                        val argValue = itemMathResult.value.replace("{", "").replace("}", "")
                        if (!argList.contains(argValue)) {
                            argList.add(argValue)
                        }
                    }
                    if (!valueInfoMap.containsKey(key) || valueInfoMap[key]!!.argList.size < argList.size) {
                        valueInfoMap[key] = ValueInfo(key = key, value = value, argList = argList)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     *统计目录下面的所有json文件
     */
    private fun collectAllJsonFile(directoryPath: String, jsonFilePathList: MutableList<String>) {
        val file = File(directoryPath)
        val fileList = file.listFiles()
        if (!fileList.isNullOrEmpty()) {
            for (itemFile in fileList) {
                if (isJsonFile(itemFile)) {
                    logD("发现了一个JSON文件 path=${itemFile}")
                    jsonFilePathList.add(itemFile.absolutePath)
                }
            }
        }
    }

    /**
     *生成本地国际化默认代理类
     */
    private fun generateDefaultLanguageLocalization(dartClassInfoList: List<DartClassInfo>) {
        if (dartClassInfoList.isEmpty()) return
        val dartFile = File(dartClassInfoList.first().filePath)
        val localizationFile = File(dartFile.parent, mDefaultLocalizationFileName)
        if (localizationFile.exists() && localizationFile.isFile) {
            localizationFile.delete()
        }
        localizationFile.createNewFile()
        val bufferedWriter = BufferedWriter(FileWriter(localizationFile))
        bufferedWriter.write("import 'base_translate.dart';")
        bufferedWriter.newLine()
        bufferedWriter.write("import 'package:flutter/material.dart';")
        bufferedWriter.newLine()
        bufferedWriter.write("import 'translate_manager.dart';")
        bufferedWriter.newLine()
        bufferedWriter.newLine()
        bufferedWriter.write("class $mDefaultLocalizationClassName extends LocalizationsDelegate<$mBaseDartClassName> {")
        bufferedWriter.newLine()
        bufferedWriter.write("static LocalizationsDelegate<$mBaseDartClassName> delegate = DefaultLocalizations();")
        bufferedWriter.newLine()
        bufferedWriter.newLine()
        bufferedWriter.write("  static List<Locale> get supportedLocales {")
        bufferedWriter.newLine()
        bufferedWriter.write("    return const <Locale>[")
        for (itemClassInfo in dartClassInfoList) {
            bufferedWriter.newLine()
            bufferedWriter.write("      Locale.fromSubtags(languageCode: '${itemClassInfo.local}'),")
        }
        bufferedWriter.newLine()
        bufferedWriter.write("    ];")
        bufferedWriter.newLine()
        bufferedWriter.write("  }")
        bufferedWriter.newLine()
        bufferedWriter.newLine()
        bufferedWriter.write("  @override")
        bufferedWriter.newLine()
        bufferedWriter.write("  bool isSupported(Locale locale) => _isSupported(locale);")
        bufferedWriter.newLine()
        bufferedWriter.newLine()
        bufferedWriter.write("  @override")
        bufferedWriter.newLine()
        bufferedWriter.write("  Future<$mBaseDartClassName> load(Locale locale) {")
        bufferedWriter.newLine()
        bufferedWriter.write("    TranslateManager.localeChanged(languageCode: locale.languageCode);")
        bufferedWriter.newLine()
        bufferedWriter.write("    return Future(() => TranslateManager.translateProxy);")
        bufferedWriter.newLine()
        bufferedWriter.write("  }")
        bufferedWriter.newLine()
        bufferedWriter.newLine()
        bufferedWriter.write("  @override")
        bufferedWriter.newLine()
        bufferedWriter.write("  bool shouldReload(covariant LocalizationsDelegate<$mBaseDartClassName> old) {")
        bufferedWriter.newLine()
        bufferedWriter.write("    return false;")
        bufferedWriter.newLine()
        bufferedWriter.write("  }")
        bufferedWriter.newLine()
        bufferedWriter.newLine()
        bufferedWriter.write("  bool _isSupported(Locale locale) {")
        bufferedWriter.newLine()
        bufferedWriter.write("    for (var supportedLocale in supportedLocales) {")
        bufferedWriter.newLine()
        bufferedWriter.write("      if (supportedLocale.languageCode == locale.languageCode) {")
        bufferedWriter.newLine()
        bufferedWriter.write("       return true;")
        bufferedWriter.newLine()
        bufferedWriter.write("      }")
        bufferedWriter.newLine()
        bufferedWriter.write("    }")
        bufferedWriter.newLine()
        bufferedWriter.write("    return false;")
        bufferedWriter.newLine()
        bufferedWriter.write("  }")
        bufferedWriter.newLine()
        bufferedWriter.write("}")
        bufferedWriter.newLine()
        bufferedWriter.flush()
        bufferedWriter.close()
    }

    private fun isJsonFile(file: File): Boolean {
        return file.exists() && file.isFile && file.name.substring(file.name.lastIndexOf('.'))
            .lowercase(Locale.CHINA) == ".json" && validJsonFileName(fileName = file.name)
    }

    data class JsonInfo(val filePath: String, val valueInfoMap: MutableMap<String, ValueInfo>)

    data class ValueInfo(val key: String, val value: String, val argList: List<String>)

    data class MethodInfo(var key: String, var methodName: String, val value: String, var argList: List<String>)

    /**
     *@param filePath Dart文件所在路径
     * @param local 对应的Dart翻译在哪种语种环境下面使用
     * @param fileName 文件名
     * @param className 类名
     */
    data class DartClassInfo(val filePath: String, val local: String, val fileName: String, val className: String)

    /**
     *世界时钟信息
     */
    data class WorldCityBean(
        val id: Int,
        val name: String? = null,
        val country: String? = null,
        val abbreviation: String? = null,
        val latitude: String? = null,
        val longitude: String? = null,
        val timeZoneName: String? = null,
        val nameKey: String? = null,
        val countryKey: String? = null
    )
}