package com.jdz.translate

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.fileEditor.FileDocumentManager
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

    //Dart类基类名称
    private val mBaseDartClassName = "BaseTranslate"

    //Dart类基类文件名称
    private val mBaseDartFileName = "base_translate.dart"

    //获取翻译的代理Dart Class 类名
    private val mProxyDartClassName = "TranslateProxy"

    //获取翻译的代理Dart Class文件名
    private val mProxyDartFileName = "translate_proxy.dart"

    //获取翻译的管理Dart Class 类名
    private val mManagerDartClassName = "TranslateManager"

    //获取翻译的管理Dart Class文件名
    private val mManagerDartFileName = "translate_manager.dart"

    //用于存储中文Dart Class 类名
    private var mDefaultDartClassName = ""

    //用于存储中文Dart实体文件名
    private var mDefaultDartFileName = ""

    //默认翻译
    private val mDefaultLanguageCode = "zh"

    override fun actionPerformed(e: AnActionEvent) {
        val editor = e.getData(PlatformDataKeys.EDITOR)!!
        val manager = FileDocumentManager.getInstance()
        var virtualFile = manager.getFile(editor.document)!!
        val currentDirectoryPath = virtualFile.parent.path
        println("开始将json文件转换成dart文件 currentDirectoryPath=$currentDirectoryPath")
        //存储当前目录下面的所有json文件的绝对路径
        val jsonFilePathList = mutableListOf<String>()
        println("开始查找当前目录下面的所有JSON格式的文件")
        collectAllJsonFile(directoryPath = currentDirectoryPath, jsonFilePathList = jsonFilePathList)
        if (jsonFilePathList.isEmpty()) {
            println("在该目录下面没有找到任何JSON文件")
            return
        }
        val jsonInfoList = collectAllKeyValueInfo(jsonFilePathList)
        if (jsonInfoList.isEmpty()) {
            println("没有查找到任何的JSON文件的健值对信息，或者JSON文件内容为空")
            return
        }
        val methodInfoList = collectMethodInfo(jsonInfoList.first().valueInfoMap)
        generateBaseDartClass(currentDirectoryPath, methodInfoList)
        val dartClassInfoList=generateJsonDartClass(jsonInfoList)
        generateTranslateProxyClass(currentDirectoryPath, methodInfoList)
        generateTranslateManagerDartClass(rootPath = currentDirectoryPath, dartClassInfoList = dartClassInfoList)
    }

    /**
     *生成Dart Translate Manager类
     */
    private fun generateTranslateManagerDartClass(rootPath: String,dartClassInfoList:List<DartClassInfo>) {
        var dartClassFilePath = if (rootPath.endsWith(File.separator)) rootPath.plus(mManagerDartFileName)
        else rootPath.plus(File.separator).plus(mManagerDartFileName)
        val managerFile = File(dartClassFilePath)
        if (managerFile.exists() && managerFile.isFile) {
            managerFile.delete()
        }
        managerFile.createNewFile()
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
        bufferedWriter.write("class $mManagerDartClassName {")
        bufferedWriter.newLine()
        bufferedWriter.write("  static const Map<String, String> emptyMap = {};")
        bufferedWriter.newLine()
        bufferedWriter.newLine()
        bufferedWriter.write("  static $mProxyDartClassName translateProxy = $mProxyDartClassName(")
        bufferedWriter.newLine()
        bufferedWriter.write("      localTranslate: $mDefaultDartClassName(), serverValueMap: emptyMap);")
        bufferedWriter.newLine()
        bufferedWriter.newLine()
        bufferedWriter.write("  static void localeChanged({required String languageCode,")
        bufferedWriter.newLine()
        bufferedWriter.write("      Map<String, String> serverValueMap = emptyMap}) {")
        bufferedWriter.newLine()
        bufferedWriter.write("      $mBaseDartClassName localTranslate;")
        bufferedWriter.newLine()
        bufferedWriter.write("    switch (languageCode.toLowerCase()) {")
        var defaultTranslate=dartClassInfoList.first()
        for (itemDartClassInfo in dartClassInfoList){
            if (itemDartClassInfo.local==mDefaultLanguageCode){
                defaultTranslate=itemDartClassInfo
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
        bufferedWriter.write("}")
        bufferedWriter.newLine()
        bufferedWriter.flush()
        bufferedWriter.close()
    }

    /**
     *生成获取翻译的代理Dart Class
     */
    private fun generateTranslateProxyClass(rootPath: String, methodInfoList: List<MethodInfo>) {
        val baseDartPackageImport = "import '$mBaseDartFileName';"
        var dartClassFilePath = if (rootPath.endsWith(File.separator)) rootPath.plus(mProxyDartFileName)
        else rootPath.plus(File.separator).plus(mProxyDartFileName)
        println("dartClassFilePath=$dartClassFilePath")
        val dartClassFile = File(dartClassFilePath)
        if (dartClassFile.exists() && dartClassFile.isFile) {
            dartClassFile.delete()
        }
        dartClassFile.createNewFile()
        val bufferedWriter = BufferedWriter(FileWriter(dartClassFile))
        bufferedWriter.write(baseDartPackageImport)
        bufferedWriter.newLine()
        bufferedWriter.newLine()
        bufferedWriter.write("class $mProxyDartClassName extends $mBaseDartClassName {")
        val serverValueMap = "_serverValueMap"
        val localTranslate = "_localTranslate"
        bufferedWriter.newLine()
        bufferedWriter.write("  late $mBaseDartClassName $localTranslate;")
        bufferedWriter.newLine()
        bufferedWriter.write("  late Map<String, String> $serverValueMap;")
        bufferedWriter.newLine()
        bufferedWriter.newLine()
        bufferedWriter.write("  $mProxyDartClassName(")
        bufferedWriter.newLine()
        bufferedWriter.write("      {required $mBaseDartClassName localTranslate,")
        bufferedWriter.newLine()
        bufferedWriter.write("      required Map<String, String> serverValueMap}) {")
        bufferedWriter.newLine()
        bufferedWriter.write("    $localTranslate = localTranslate;")
        bufferedWriter.newLine()
        bufferedWriter.write("    $serverValueMap = serverValueMap;")
        bufferedWriter.newLine()
        bufferedWriter.write("  }")
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
                        paramsBuilder.append("$itemArgName")
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
    private fun generateJsonDartClass(jsonInfoList: List<JsonInfo>):List<DartClassInfo> {
        val classInfoList = mutableListOf<DartClassInfo>()
        for (itemJsonInfo in jsonInfoList) {
            try {
                val fileName = itemJsonInfo.filePath.substring(
                    itemJsonInfo.filePath.lastIndexOf(File.separator) + 1,
                    itemJsonInfo.filePath.lastIndexOf(".")
                )
                val local = if (fileName.contains("-")) fileName.substring(fileName.lastIndexOf("-") + 1)
                    .lowercase(Locale.CHINA)
                else fileName.substring(fileName.lastIndexOf("-") + 1).lowercase(Locale.CHINA)
                if (local.isNullOrEmpty()) continue
                classInfoList.add(generateJsonDartClass(itemJsonInfo))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return classInfoList
    }

    private fun generateJsonDartClass(jsonInfo: JsonInfo):DartClassInfo {
        val jsonFile = File(jsonInfo.filePath)
        val pFilePath = jsonFile.parentFile.absolutePath
        val baseDartPackageImport = "import '$mBaseDartFileName';"
        var dartClassFilePath = pFilePath.plus(File.separator).plus(
            jsonFile.name.substring(0, jsonFile.name.indexOf(".")).lowercase(
                Locale.CHINA
            )
        ).plus(".dart")
        println("dartClassFilePath=$dartClassFilePath")
        val dartClassFile = File(dartClassFilePath)
        if (dartClassFile.exists() && dartClassFile.isFile) {
            dartClassFile.delete()
        }
        dartClassFile.createNewFile()
        val languageCode=jsonFile.name.substring(jsonFile.name.lastIndexOf("_")+1,jsonFile.name.lastIndexOf("."))
        val dartClassName = getDartClassName(dartClassFile.name)
        if (languageCode==mDefaultLanguageCode || (mDefaultDartFileName.isEmpty() || mDefaultDartClassName.isEmpty())) {
            mDefaultDartClassName = dartClassName
            mDefaultDartFileName = dartClassFile.name
        }
        val bufferedWriter = BufferedWriter(FileWriter(dartClassFile))
        bufferedWriter.write(baseDartPackageImport)
        bufferedWriter.newLine()
        bufferedWriter.newLine()
        bufferedWriter.write("class $dartClassName extends $mBaseDartClassName {")
        val methodInfoList = jsonInfo.valueInfoMap.values.toList()
        for (itemMethodInfo in methodInfoList) {
            bufferedWriter.newLine()
            bufferedWriter.write("  @override")
            bufferedWriter.newLine()
            if (itemMethodInfo.argList.isEmpty()) {
                bufferedWriter.write("  String ${itemMethodInfo.key}() {")
                bufferedWriter.newLine()
                bufferedWriter.write("    return \"\"\"${itemMethodInfo.value}\"\"\";")
            } else {
                val argBuilder = StringBuilder()
                val returnBuilder = StringBuilder("    return \"\"\"${itemMethodInfo.value}\"\"\"")
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
        return DartClassInfo(filePath = dartClassFilePath, local = languageCode, fileName = dartClassFile.name, className = dartClassName)
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
                dartClassName += itemName.substring(0, 1).uppercase(java.util.Locale.CHINA).plus(
                    itemName.substring(1).lowercase(
                        java.util.Locale.CHINA
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
     * @param methodInfoList 方法信息
     */
    private fun generateBaseDartClass(fileDirectoryPath: String, methodInfoList: List<MethodInfo>) {
        val file =
            if (fileDirectoryPath.endsWith(File.separator)) File(fileDirectoryPath.plus(mBaseDartFileName)) else File(
                fileDirectoryPath.plus(File.separator).plus(mBaseDartFileName)
            )
        if (file.exists() && file.isFile) file.delete()
        file.createNewFile()
        val bufferedWriter = BufferedWriter(FileWriter(file))
        bufferedWriter.write("abstract class $mBaseDartClassName {")
        for (itemMethodInfo in methodInfoList) {
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
    private fun collectMethodInfo(valueMap: Map<String, ValueInfo>): List<MethodInfo> {
        val methodInfoList = mutableListOf<MethodInfo>()
        for (keyValueInfo in valueMap.values) {
            methodInfoList.add(
                MethodInfo(
                    key = keyValueInfo.key,
                    methodName = keyValueInfo.key,
                    value = keyValueInfo.value,
                    argList = keyValueInfo.argList
                )
            )
        }
        return methodInfoList
    }

    /**
     *统计所有JSON文件的健值对信息
     */
    private fun collectAllKeyValueInfo(jsonFilePathList: List<String>): List<JsonInfo> {
        println("开始搜索JSON文件的健值对信息以及参数列表")
        //存储每个json文件中的键值对
        val jsonValueList = mutableListOf<JsonInfo>()
        for (itemJsonFilePath in jsonFilePathList) {
            val valueInfoMap = getKeyValueInfoFromJson(itemJsonFilePath)
            if (valueInfoMap.isNotEmpty()) {
                jsonValueList.add(JsonInfo(filePath = itemJsonFilePath, valueInfoMap = valueInfoMap))
            }
        }
        return jsonValueList
    }

    /**
     *获取单个json文件中的健值对信息
     */
    private fun getKeyValueInfoFromJson(jsonFilePath: String): MutableMap<String, ValueInfo> {
        val result = mutableMapOf<String, ValueInfo>()
        try {
            val jsonContent = File(jsonFilePath).readText(Charsets.UTF_8)
            val jsonMap =
                Gson().fromJson<Map<String, String>>(jsonContent, object : TypeToken<Map<String, String>>() {}.type)
            for (itemMapEntry in jsonMap) {
                val key = itemMapEntry.key
                var value = itemMapEntry.value
                val mathResultList = mRegex.findAll(value.replace("{", " {").replace("}", "} "))
                if (mathResultList.count() == 0) {
                    result[key] = ValueInfo(key = key, value = value, argList = mutableListOf())
                } else {
                    val argList = mutableListOf<String>()
                    for (itemMathResult in mathResultList) {
                        argList.add(itemMathResult.value.replace("{", "").replace("}", ""))
                    }
                    result[key] = ValueInfo(key = key, value = value.replace("\\\\","\\"), argList = argList)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return result
    }

    /**
     *统计目录下面的所有json文件
     */
    private fun collectAllJsonFile(directoryPath: String, jsonFilePathList: MutableList<String>) {
        val file = File(directoryPath)
        val fileList = file.listFiles()
        if (!fileList.isNullOrEmpty()) {
            for (itemFile in fileList) {
                if (itemFile.isDirectory) {
//                    collectAllJsonFile(itemFile.absolutePath, jsonFilePathList)
                } else {
                    if (isJsonFile(itemFile)) {
                        println("发现了一个JSON文件 path=${itemFile}")
                        jsonFilePathList.add(itemFile.absolutePath)
                    }
                }
            }
        }
    }

    private fun isJsonFile(file: File): Boolean {
        return file.exists() && file.isFile && file.name.substring(file.name.lastIndexOf('.'))
            .lowercase(Locale.CHINA) == ".json"
    }

    data class JsonInfo(val filePath: String, val valueInfoMap: MutableMap<String, ValueInfo>)

    data class ValueInfo(val key: String, val value: String, val argList: List<String>)

    data class MethodInfo(val key: String, val methodName: String, val value: String, val argList: List<String>)

    /**
     *@param filePath Dart文件所在路径
     * @param local 对应的Dart翻译在哪种语种环境下面使用
     * @param filName 文件名
     * @param className 类名
     */
    data class DartClassInfo(val filePath: String, val local: String,val fileName:String,val className:String)
}