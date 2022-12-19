package com.jdz.translate

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.roots.ProjectFileIndex
import org.apache.poi.openxml4j.opc.OPCPackage
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.util.*


/**
 * Excel 转JSON
 */
class ExcelToJson : AnAction() {
    override fun actionPerformed(event: AnActionEvent) {
        val backupJsonFilePath = getBackJsonFileDir(event)
        logD("JSON文件存储目录:$backupJsonFilePath")
        val backupJsonFile = File(backupJsonFilePath)
        if (!backupJsonFile.exists() || !backupJsonFile.isDirectory) {
            backupJsonFile.mkdirs()
        } else {
            logD("开始删除JSON备份目录下面的所有JSON子文件")
            val childFileList = backupJsonFile.listFiles()
            if (!childFileList.isNullOrEmpty()) {
                for (itemChildFile in childFileList) {
                    if (itemChildFile.name.trim().lowercase(Locale.CHINA).endsWith(".json")) {
                        itemChildFile.delete()
                    }
                }
            }
        }

        val excelFilePath = getExcelFilePath(event)

        logD("需要转换成json的excel文件是 excelFilePath=$excelFilePath")
        val xssfWorkbook: XSSFWorkbook?
        val pkg = OPCPackage.open(excelFilePath)
        xssfWorkbook = XSSFWorkbook(pkg)
        val sheetCount = xssfWorkbook.numberOfSheets
        logD("excel sheet 大小等于 $sheetCount")
        if (sheetCount == 0) {
            return
        }
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
            val itemJsonFileName = getJsonFileName(sampleName.lowercase(Locale.CHINA))

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
        var paramsCount: Int
        //参数不一致的key
        val errorKey = mutableSetOf<String>()
        val regex = Regex("\\{\\S+\\}")
        val writeContentList = mutableListOf<String>()
        for (sheetIndex in 0 until sheetCount) {
            writeContentList.clear()
            val xssfSheet = xssfWorkbook.getSheetAt(sheetIndex)
            for (rowIndex in 1 until Int.MAX_VALUE) {
                paramsCount = 0
                try {
                    val row = xssfSheet.getRow(rowIndex)
                    if (row == null) {
                        if (writeContentList.size == backupJsonFileMap.size) {
                            for (contentIndex in writeContentList.indices) {
                                val itemContent = writeContentList[contentIndex]
                                val backJsonFileWriter = backupJsonFileMap[contentIndex + 1]!!
                                backJsonFileWriter.newLine()
                                if (sheetIndex==sheetCount-1){
                                    backJsonFileWriter.write(itemContent)
                                }
                                else{
                                    backJsonFileWriter.write("$itemContent,")
                                }
                            }
                        }
                        writeContentList.clear()
                        break
                    }

                    var key = row.getCell(0)?.toString()
                    if (key.isNullOrEmpty()) {
                        if (writeContentList.size == backupJsonFileMap.size) {
                            for (contentIndex in writeContentList.indices) {
                                val itemContent = writeContentList[contentIndex]
                                val backupJsonWriter = backupJsonFileMap[contentIndex + 1]!!
                                backupJsonWriter.newLine()
                                if (sheetIndex==sheetCount-1){
                                    backupJsonWriter.write(itemContent)
                                }
                                else{
                                    backupJsonWriter.write("$itemContent,")
                                }
                            }
                        }
                        writeContentList.clear()
                        break
                    }
                    key = try {
                        "${getTranslateKeyPrefix()}${key.toFloat().toInt()}"
                    } catch (e: Exception) {
                        "${getTranslateKeyPrefix()}$key"
                    }
                    if (writeContentList.isNotEmpty() && writeContentList.size == backupJsonFileMap.size) {
                        for (index in writeContentList.indices) {
                            val itemContent = writeContentList[index]
                            val backupJsonWriter = backupJsonFileMap[index + 1]!!
                            backupJsonWriter.newLine()
                            backupJsonWriter.write("$itemContent,")
                        }
                    }
                    writeContentList.clear()
                    for (columnIndex in 0 until backupJsonFileMap.size) {
                        var columnValue = row.getCell(columnIndex + 1)?.toString()
                        if (columnValue.isNullOrEmpty()) {
                            columnValue = ""
                        }
                        val commonDot = "language_comon_dot_flag"
                        val commonN = "language_comon_n_flag"
                        columnValue = columnValue
                            .replace("%d", "{params}").replace("%s", "{params}")
                            .replace("%1d", "{params1}").replace("%2d", "{params2}")
                            .replace("%3d", "{params3}").replace("%4d", "{params4}")
                            .replace("%1s", "{params1}").replace("%2s", "{params2}")
                            .replace("%3s", "{params3}").replace("%4s", "{params4}")
                            .replace("%1\$s", "{params1}").replace("%2\$s", "{params2}")
                            .replace("%3\$s", "{params3}").replace("%4\$s", "{params4}")
                            .replace("%1\$d", "{params1}").replace("%2\$d", "{params2}")
                            .replace("%3\$d", "{params3}").replace("%4\$d", "{params4}")
                            .replace("\\t", "").replace("\\b", "")
                            .replace("\\r", "").replace("\\v", "")
                            .replace("\\f", "").replace("\\e", "")
                            .replace("\\n", "").replace("\\r\\n", "")
                            .replace("\"", commonDot).replace("\\\"", commonDot)
                            .replace("\\ \n", commonN).replace("\\\n", commonN)

                            .replace("%D", "{params}").replace("%S", "{params}")
                            .replace("%1D", "{params1}").replace("%2D", "{params2}")
                            .replace("%3D", "{params3}").replace("%4D", "{params4}")
                            .replace("%1S", "{params1}").replace("%2S", "{params2}")
                            .replace("%3S", "{params3}").replace("%4S", "{params4}")
                            .replace("%1\$S", "{params1}").replace("%2\$S", "{params2}")
                            .replace("%3\$S", "{params3}").replace("%4\$S", "{params4}")
                            .replace("%1\$D", "{params1}").replace("%2\$D", "{params2}")
                            .replace("%3\$D", "{params3}").replace("%4\$D", "{params4}")
                            .replace("\\T", "").replace("\\B", "")
                            .replace("\\R", "").replace("\\V", "")
                            .replace("\\F", "").replace("\\E", "")
                            .replace("\\N", "").replace("\\R\\N", "")
                            .replace("\\ N", commonN).replace("\\\n", commonN)
                            .replace("\\", "").replace(commonDot, "\\\"")
                            .replace(commonN, "\\\n")

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
        for (backupJsonFileWriter in backupJsonFileMap.values) {
            backupJsonFileWriter.newLine()
            backupJsonFileWriter.write("}")
            backupJsonFileWriter.flush()
            backupJsonFileWriter.close()
        }
        pkg.close()
        logE("参数不一致的Key=$errorKey")
        ProjectFileIndex.getInstance(event.project!!).getContentRootForFile(event.project!!.projectFile!!)?.refresh(true, true)
    }
}