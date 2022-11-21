package com.jdz.translate

import com.google.gson.Gson
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.ui.table.JBTable
import java.awt.ComponentOrientation
import java.awt.Dimension
import java.awt.ScrollPane
import java.awt.Toolkit
import java.util.*
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

            selectedText = selectedText.trim()
            logD("你需要搜索翻译的关键字:$selectedText")
            if (selectedText.startsWith(getTranslateKeyPrefix())) {
                val result = findTranslateByKey(keyValue = selectedText, event = e!!, exactSearch = false)
                if (result.isEmpty()) {
                    logD("没有查找到任何搜索结果")
                    showNotFound()
                } else {
                    showSearchResult(result = result)
                }
            } else {
                val result = findTranslateByZh(zhValue = selectedText, event = e!!, exactSearch = false)
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
}