package com.jdz.translate

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.fileEditor.FileDocumentManager
import org.apache.groovy.internal.util.UnicodeConst
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

        println("需要转换成jso的excel文件是 excelFilePath=$excelFilePath")
        var xssfWorkbook: XSSFWorkbook? = null
        xssfWorkbook = XSSFWorkbook(excelFilePath)
        val sheetCount = xssfWorkbook.numberOfSheets
        println("excel sheet 大小等于 $sheetCount")
        if (sheetCount == 0) {
            return
        }
        //每一列的文件
        val fileMap = mutableMapOf<Int, BufferedWriter>()
        val sampleSheet = xssfWorkbook.getSheetAt(0)
        val sampleRow = sampleSheet.getRow(0)
        for (sampleIndex in 1 until 100) {
            val sampleName = sampleRow.getCell(sampleIndex)?.toString()
            if (sampleName.isNullOrEmpty()) {
                println("error name $sampleIndex")
                break
            }
            val jsonFile = File(virtualFile.parent.path, "veryfit_${sampleName.lowercase(Locale.CHINA)}.json")
            if (jsonFile.exists() && jsonFile.isFile) {
                jsonFile.delete()
            }
            jsonFile.createNewFile()
            val bufferedWriter = BufferedWriter(FileWriter(jsonFile))
            bufferedWriter.write("{")
            fileMap[sampleIndex] = bufferedWriter
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
                                val bufferedWriter = fileMap[contentIndex + 1]!!
                                bufferedWriter.newLine()
                                bufferedWriter.write("${writeContentList[contentIndex]}")
                            }
                        }
                        writeContentList.clear()
                        break
                    }

                    var key = row?.getCell(0)?.toString()
                    if (key.isNullOrEmpty()) {
                        println("error key ${row.getCell(0)}")
                        if (writeContentList.size == fileMap.size) {
                            for (contentIndex in writeContentList.indices) {
                                val bufferedWriter = fileMap[contentIndex + 1]!!
                                bufferedWriter.newLine()
                                bufferedWriter.write("${writeContentList[contentIndex]}")
                            }
                        }
                        writeContentList.clear()
                        break
                    }
                    try {
                        key = "ido_key_${key.toFloat().toInt()}"
                    } catch (e: Exception) {
                        key = "ido_key_$key"
                    }
                    if (writeContentList.isNotEmpty() && writeContentList.size == fileMap.size) {
                        for (index in writeContentList.indices) {
                            val bufferedWriter = fileMap[index + 1]!!
                            bufferedWriter.newLine()
                            bufferedWriter.write("${writeContentList[index]},")
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
                            .replace("\"", "\\\"")
                            .replace("\\","\\\\")
                            .replace("\n", "")
                            .replace("\\\\\\\\","\\\\")
                            .replace("\\\\\"","\\\"")
                            .replace(" "," ")
                            .replace("\\r\\n","")
                        val mathResultList = regex.findAll(columnValue.replace("{", " {").replace("}", "} "))
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
        println("参数不一致的Key=$errorKey")
    }
}