package com.jdz.translate

import com.google.gson.Gson
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.ui.table.JBTable
import java.awt.ComponentOrientation
import java.awt.Dimension
import java.awt.ScrollPane
import java.awt.Toolkit
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.util.*
import javax.sound.sampled.Line
import javax.swing.*
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.DefaultTableModel
import kotlin.math.max
import kotlin.math.roundToInt


/**
 * 翻译快速搜索提示
 */
class QuickTipAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val editor = e.getRequiredData(PlatformDataKeys.EDITOR)
        if (editor == null) {
            logD("请选中需要搜索的中文")
            return
        } else {
            var selectedText = editor.selectionModel.selectedText
            if (selectedText.isNullOrBlank()) {
                logD("请选中需要搜索的中文")
                return
            }
//            println("selectionStart=${editor.selectionModel.selectionStart},selectionEnd=${editor.selectionModel.selectionEnd}")
//            WriteCommandAction.runWriteCommandAction(e!!.project) {
//                editor.document.replaceString(
//                    editor.selectionModel.selectionStart,
//                    editor.selectionModel.selectionEnd,
//                    "Success"
//                )
//            }

            selectedText = selectedText.trim()
            logD("你需要搜索翻译的关键字:$selectedText")
            if (selectedText.startsWith(getTranslateKeyPrefix())) {
                val result = findTranslateByKey(selectedText, e)
                if (result.isEmpty()) {
                    logD("没有查找到任何搜索结果")
                    showNotFound()
                } else {
                    showSearchResult(result = result)
                }
            } else {
                val result = findTranslateByZh(selectedText, e)
                if (result.isEmpty()) {
                    logD("没有查找到任何搜索结果")
                    showNotFound()
                } else {
                    showSearchResult(result = result)
                }
            }
        }
    }

    /**
     *通过图形化形式显示出查找到的翻译结果
     */
    private fun showSearchResult(result: List<List<TranslateInfo>>) {
        logD("查找到的结果:${Gson().toJson(result)}")
        val tableData = Vector<Vector<String>>()
        val tableColumnNames = Vector<String>()
        tableColumnNames.add("KEY")
        val lineCountList = mutableListOf<Int>()
        val firstTranslateInfoList = result.first()
        for (itemTranslateInfoList in result) {
            tableColumnNames.add(itemTranslateInfoList.first().languageCode.uppercase(Locale.CHINA))
        }
        lineCountList.add(1)
        tableData.add(tableColumnNames)
        val screenSize = Toolkit.getDefaultToolkit().screenSize
        val padding = 100
        val rowCount = firstTranslateInfoList.size
        val columnCount = result.size
        val lineCount = screenSize.width / 15 / 3
        for (rowIndex in 0 until rowCount) {
            val itemVector = Vector<String>()
            var maxLineCount = 0
            for (columnIndex in 0 until columnCount) {
                if (columnIndex == 0) {
                    itemVector.add(result[columnIndex][rowIndex].key)
                }
                val value = result[columnIndex][rowIndex].value
                maxLineCount = max(maxLineCount, (value.length.toFloat() / lineCount).roundToInt())
                itemVector.add(value)
            }
            tableData.add(itemVector)
            lineCountList.add(maxLineCount)
        }

        val defaultTableModel = DefaultTableModel(tableData, tableColumnNames)
        val jbTable = JBTable(defaultTableModel)
        jbTable.setShowColumns(true)
        jbTable.autoscrolls = true
        jbTable.dragEnabled = true
        jbTable.setShowGrid(true)
        jbTable.setExpandableItemsEnabled(true)
        jbTable.setEnableAntialiasing(true)
        jbTable.cellSelectionEnabled = true
        jbTable.autoCreateRowSorter = true
        jbTable.autoResizeMode = JTable.AUTO_RESIZE_OFF
        jbTable.dragEnabled = true
        jbTable.rowMargin = 1
        jbTable.autoCreateColumnsFromModel = true
        logD("lineCountList=$lineCountList")
        for (column in 0 until jbTable.columnCount) {
            jbTable.columnModel.getColumn(column)
                .setCellRenderer({ table, value, isSelected, hasFocus, row, column ->
                    val jTextArea = JTextArea(value as String)
                    jTextArea.lineWrap = true
                    jTextArea.wrapStyleWord = true
                    if (column == 0) {
                        jTextArea.preferredSize = Dimension(200, 30 * lineCountList[row])
                    } else {
                        jTextArea.preferredSize =
                            Dimension((screenSize.width - 2 * padding - 202) / 2, 30 * lineCountList[row])
                    }

                    jTextArea
                })
        }
        jbTable.columnModel.getColumn(0).maxWidth = 200
        jbTable.columnModel.getColumn(0).preferredWidth = 200
        val tcr = DefaultTableCellRenderer() //单元格渲染器
        tcr.horizontalAlignment = JLabel.CENTER //居中显示
        jbTable.setDefaultRenderer(String::class.java, tcr)
        val jFrame = JFrame()
        jFrame.layout = VerticalLayout(10, SwingConstants.CENTER)
        jFrame.title = "搜索结果"
        val scroll = ScrollPane()
        scroll.isWheelScrollingEnabled = true
        scroll.componentOrientation = ComponentOrientation.LEFT_TO_RIGHT
        scroll.setSize(screenSize.width - 2 * padding, screenSize.height - 100)
        scroll.setLocation(padding, 0)
        scroll.add(jbTable)
        jbTable.setSize(screenSize.width - 2 * padding, screenSize.height - 100)
        jbTable.setLocation(padding, 0)
        jFrame.setSize(screenSize.width, screenSize.height - 100)
        logD("height=${screenSize.height}")
        jFrame.add(scroll)
        jFrame.isVisible = true
    }

    /**
     *显示没有找到任何结果的视图
     */
    private fun showNotFound() {

    }

    /**
     *开始通过Key找翻译
     */
    private fun findTranslateByKey(keyValue: String, event: AnActionEvent): List<List<TranslateInfo>> {
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
        if (enFile != null) {
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
     */
    private fun findTranslateByZh(zhValue: String, event: AnActionEvent): List<List<TranslateInfo>> {
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
                if (value.contains(zhValue)) {
                    logD("中文----->找到的键值对信息 key=$key,value=$value")
                    zhTranslateInfoList.add(
                        TranslateInfo(
                            key = key,
                            languageCode = "zh",
                            value = value,
                            lineNumber = lineNumber
                        )
                    )
                }
            }
            lineNumber++
        }
        if (zhTranslateInfoList.isEmpty()) {
            logE("没有找到相似的翻译")
            return mutableListOf()
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
        if (enFile != null) {
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

    private data class TranslateInfo(var key: String, var languageCode: String, var value: String, var lineNumber: Int)
}