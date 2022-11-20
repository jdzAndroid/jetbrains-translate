package com.jdz.translate

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.fileEditor.FileDocumentManager
import org.apache.poi.xssf.usermodel.XSSFSheet
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.util.*


/**
 * Excel 转JSON
 */
class ExcelToJson : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val editor = e.getData(PlatformDataKeys.EDITOR)!!
        val manager = FileDocumentManager.getInstance()
        var virtualFile = manager.getFile(editor.document)!!

        val backupJsonFilePath = getBackJsonFileDir(project = e.project!!, virtualFile = virtualFile)
        logD("JSON文件存储目录:$backupJsonFilePath")
        val backupJsonFile = File(backupJsonFilePath)
        if (!backupJsonFile.exists() || !backupJsonFile.isDirectory) {
            backupJsonFile.mkdirs()
        }
        else{
            logD("开始删除JSON备份目录下面的所有JSON子文件")
            val childFileList=backupJsonFile.listFiles()
            if (!childFileList.isNullOrEmpty()){
                for (itemChildFile in childFileList) {
                    if (itemChildFile.name.trim().lowercase(Locale.CHINA).endsWith(".json")){
                        itemChildFile.delete()
                    }
                }
            }
        }

        var excelFilePath = ""
        val childFile = virtualFile.parent.children
        for (itemChildFile in childFile) {
            if (itemChildFile.name.lowercase(Locale.CHINA)
                    .endsWith(".xls") || itemChildFile.name.lowercase(Locale.CHINA).endsWith(".xlsx")
            ) {
                excelFilePath = itemChildFile.path
                break
            }
        }

        logD("需要转换成json的excel文件是 excelFilePath=$excelFilePath")
        var xssfWorkbook: XSSFWorkbook? = null
        xssfWorkbook = XSSFWorkbook(excelFilePath)
        val sheetCount = xssfWorkbook.numberOfSheets
        logD("excel sheet 大小等于 $sheetCount")
        if (sheetCount == 0) {
            return
        }
        //每一列的文件
        val fileMap = mutableMapOf<Int, BufferedWriter>()
        //备份的json文件，为后面的翻译搜索以及快捷提示做准备
        val backupJsonFileMap = mutableMapOf<Int, BufferedWriter>()

        val sampleSheet = xssfWorkbook.getSheetAt(0)
        val sampleRow = sampleSheet.getRow(0)
        for (sampleIndex in 1 until 100) {
            val sampleName = sampleRow.getCell(sampleIndex)?.toString()
            if (sampleName.isNullOrEmpty()) {
                logD("error name sampleIndex=$sampleIndex")
                break
            }
            val itemJsonFileName = "veryfit_${sampleName.lowercase(Locale.CHINA)}.json"
            val jsonFile = File(virtualFile.parent.path, itemJsonFileName)
            if (jsonFile.exists() && jsonFile.isFile) {
                jsonFile.delete()
            }
            jsonFile.createNewFile()
            val bufferedWriter = BufferedWriter(FileWriter(jsonFile))
            bufferedWriter.write("{")
            fileMap[sampleIndex] = bufferedWriter

            val backupJsonFile = File(backupJsonFile, itemJsonFileName)
            if (backupJsonFile.exists() && backupJsonFile.isFile) {
                backupJsonFile.delete()
            }
            backupJsonFile.createNewFile()
            val backupJsonWriter = BufferedWriter(FileWriter(backupJsonFile))
            backupJsonWriter.write("{")
            backupJsonFileMap[sampleIndex] = backupJsonWriter
        }

        //参数个数
        var paramsCount = 0
        //参数不一致的key
        var errorKey = mutableSetOf<String>()
        val regex = Regex("\\{\\S+\\}")
        var writeContentList = mutableListOf<String>()
        for (sheetIndex in 0 until sheetCount) {
            writeContentList.clear()
            val xssfSheet: XSSFSheet = xssfWorkbook.getSheetAt(sheetIndex)
            for (rowIndex in 1 until Int.MAX_VALUE) {
                paramsCount = 0
                try {
                    val row = xssfSheet.getRow(rowIndex)
                    if (row == null) {
                        if (writeContentList.size == fileMap.size) {
                            for (contentIndex in writeContentList.indices) {
                                val itemContent = writeContentList[contentIndex]
                                val bufferedWriter = fileMap[contentIndex + 1]!!
                                bufferedWriter.newLine()
                                bufferedWriter.write("$itemContent")

                                val backJsonFileWriter = backupJsonFileMap[contentIndex + 1]!!
                                backJsonFileWriter.newLine()
                                backJsonFileWriter.write("$itemContent")
                            }
                        }
                        writeContentList.clear()
                        break
                    }

                    var key = row?.getCell(0)?.toString()
                    if (key.isNullOrEmpty()) {
                        logE("error key $key")
                        if (writeContentList.size == fileMap.size) {
                            for (contentIndex in writeContentList.indices) {
                                val itemContent = writeContentList[contentIndex]
                                val bufferedWriter = fileMap[contentIndex + 1]!!
                                bufferedWriter.newLine()
                                bufferedWriter.write("$itemContent")

                                val backupJsonWriter = backupJsonFileMap[contentIndex + 1]!!
                                backupJsonWriter.newLine()
                                backupJsonWriter.write("$itemContent")
                            }
                        }
                        writeContentList.clear()
                        break
                    }
                    try {
                        key = "${getTranslateKeyPrefix()}${key.toFloat().toInt()}"
                    } catch (e: Exception) {
                        key = "${getTranslateKeyPrefix()}$key"
                    }
                    if (writeContentList.isNotEmpty() && writeContentList.size == fileMap.size) {
                        for (index in writeContentList.indices) {
                            val itemContent = writeContentList[index]
                            val bufferedWriter = fileMap[index + 1]!!
                            bufferedWriter.newLine()
                            bufferedWriter.write("$itemContent,")

                            val backupJsonWriter = backupJsonFileMap[index + 1]!!
                            backupJsonWriter.newLine()
                            backupJsonWriter.write("$itemContent,")
                        }
                    }
                    writeContentList.clear()
                    for (columnIndex in 0 until fileMap.size) {
                        var columnValue = row.getCell(columnIndex + 1)?.toString()
                        if (columnValue.isNullOrEmpty()) {
                            columnValue = ""
                        }
                        columnValue = columnValue.replace("%s", "{params}")
                            .replace("%d", "{params}").replace("%1d", "{params1}")
                            .replace("%2d", "{params2}").replace("%3d", "{params3}")
                            .replace("%4d", "{params4}").replace("%1s", "{params1}")
                            .replace("%2s", "{params2}").replace("%3s", "{params3}")
                            .replace("%4s", "{params4}").replace("%1\$s", "{params1}")
                            .replace("%2\$s", "{params2}").replace("%3\$s", "{params3}")
                            .replace("%4\$s", "{params4}").replace("%1\$d", "{params1}")
                            .replace("%2\$d", "{params2}").replace("%3\$d", "{params3}")
                            .replace("%4\$d", "{params4}")
                            .replace("\\t", "")
                            .replace("\\b", "")
                            .replace("\\r", "")
                            .replace("\\v", "")
                            .replace("\\f", "")
                            .replace("\\e", "")
                            .replace("\n", "")
                            .replace("\\r\\n", "")
                            .replace("\\\"", "\"")
                            .replace("\"", "\\\"")
                        val lineList = columnValue.lines()
                        if (lineList.isNotEmpty()) {
                            columnValue = ""
                            for (itemLine in lineList) {
                                columnValue = columnValue.plus(itemLine)
                            }
                        } else {
                            columnValue = ""
                        }
                        val mathResultList = regex.findAll(columnValue!!.replace("{", " {").replace("}", "} "))
                        if (columnIndex == 0) {
                            paramsCount = mathResultList.count()
                        } else {
                            if (paramsCount != mathResultList.count()) {
                                errorKey.add(key!!)
                            }
                        }
                        writeContentList.add("\"$key\":\"$columnValue\"")
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        for (bufferedWriter in fileMap.values) {
            bufferedWriter.newLine()
            bufferedWriter.write("}")
            bufferedWriter.flush()
            bufferedWriter.close()
        }

        for (backupJsonFileWriter in backupJsonFileMap.values) {
            backupJsonFileWriter.newLine()
            backupJsonFileWriter.write("}")
            backupJsonFileWriter.flush()
            backupJsonFileWriter.close()
        }
        logE("参数不一致的Key=$errorKey")
    }
}