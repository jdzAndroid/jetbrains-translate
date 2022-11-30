package com.jdz.translate

import com.google.gson.Gson
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.ui.table.JBTable
import java.awt.ComponentOrientation
import java.awt.Font
import java.awt.ScrollPane
import java.awt.Toolkit
import java.awt.font.FontRenderContext
import java.awt.geom.AffineTransform
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
    override fun actionPerformed(event: AnActionEvent) {
        val editor = event.getRequiredData(PlatformDataKeys.EDITOR)
        var selectedText = editor.selectionModel.selectedText
        if (selectedText.isNullOrBlank()) {
            logD("请选中需要搜索的中文")
            return
        }

        selectedText = selectedText.trim()
        logD("你需要搜索翻译的关键字:$selectedText")
        if (selectedText.startsWith(getTranslateKeyPrefix())) {
            if (!hasCachedJsonFile(event)) {
                refreshCachedJsonFile(event)
            }
            val result = findTranslateByKey(keyValue = selectedText, event = event, exactSearch = false)
            if (result.isEmpty()) {
                logD("没有查找到任何搜索结果")
                showNotFound(editor.selectionModel.selectedText!!)
            } else {
                showSearchResult(result = result)
            }
        } else {
            val result = findTranslateByZh(zhValue = selectedText, event = event, exactSearch = false)
            if (result.isEmpty()) {
                logD("没有查找到任何搜索结果")
                showNotFound(editor.selectionModel.selectedText!!)
            } else {
                if (!hasCachedJsonFile(event)) {
                    refreshCachedJsonFile(event)
                }
                showSearchResult(result = result)
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

        val defaultTableModel = DefaultTableModel(tableData, tableColumnNames)
        val jbTable = JBTable(defaultTableModel)
        val screenSize = Toolkit.getDefaultToolkit().screenSize
        val padding = 100
        val rowCount = firstTranslateInfoList.size
        val columnCount = result.size
        val tableWidth = screenSize.width - 2 * padding
        val tableHeight = screenSize.height
        val keyCellWidth = 200
        val normalColumnWidth = (tableWidth - keyCellWidth) / 2
        //一行做多可以显示多少个字符-以中文为主
        val chartCountInOneLine = measureChartSizeInOneLine(font = jbTable.font, width = normalColumnWidth) * 2 / 5
        //单行文字高度
        val lineHeight = 30
        for (rowIndex in 0 until rowCount) {
            val itemVector = Vector<String>()
            var maxLineCount = 1
            for (columnIndex in 0 until columnCount) {
                if (columnIndex == 0) {
                    itemVector.add(result[columnIndex][rowIndex].key)
                }
                val value = result[columnIndex][rowIndex].value
                maxLineCount = max(maxLineCount, (value.length.toFloat() / chartCountInOneLine).roundToInt())
                itemVector.add(value)
            }
            tableData.add(itemVector)
            lineCountList.add(maxLineCount)
        }
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
        for (column in 0 until jbTable.columnCount) {
            jbTable.columnModel.getColumn(column)
                .setCellRenderer({ table, value, isSelected, hasFocus, row, column ->
                    val jTextArea = JTextArea(value as String)
                    jTextArea.lineWrap = true
                    jTextArea.wrapStyleWord = true
                    jTextArea
                })
        }
        jbTable.columnModel.getColumn(0).preferredWidth = keyCellWidth
        jbTable.columnModel.getColumn(1).preferredWidth = normalColumnWidth
        jbTable.columnModel.getColumn(2).preferredWidth = normalColumnWidth
        val tcr = DefaultTableCellRenderer() //单元格渲染器
        tcr.horizontalAlignment = JLabel.CENTER //居中显示
        jbTable.setDefaultRenderer(String::class.java, tcr)
        for (rowIndex in 0 until rowCount) {
            jbTable.setRowHeight(rowIndex, lineHeight * lineCountList[rowIndex])
        }
        val jFrame = JFrame()
        jFrame.layout = VerticalLayout(10, SwingConstants.CENTER)
        jFrame.title = "搜索结果"
        val scroll = ScrollPane()
        scroll.isWheelScrollingEnabled = true
        scroll.componentOrientation = ComponentOrientation.LEFT_TO_RIGHT
        scroll.setSize(tableWidth, tableHeight)
        scroll.setLocation(padding, 0)
        scroll.add(jbTable)
        jbTable.setSize(tableWidth, tableHeight)
        jbTable.setLocation(padding, 0)
        jFrame.setSize(screenSize.width, screenSize.height)
        jFrame.add(scroll)
        jFrame.isVisible = true
    }

    /**
     *显示没有找到任何结果的视图
     */
    private fun showNotFound(searchText: String) {
        val jFrame = JFrame()
        val labelValue = "没有找到对应的翻译:$searchText"
        val jLabel = JLabel(labelValue, SwingConstants.CENTER)
        val labelFont = Font(null, Font.ITALIC, 16)
        jLabel.font = labelFont
        val ar = AffineTransform()
        val frc = FontRenderContext(ar, true, true)
        val labelBounds = labelFont.getStringBounds(labelValue, frc)
        val fontWidth = labelBounds.width.roundToInt() + 5
        val fontHeight = labelBounds.height.roundToInt() + 5
        val frameWidth = (fontWidth * 1.5f).roundToInt()
        val frameHeight = (fontHeight * 5f).roundToInt()
        jFrame.setSize(frameWidth, frameHeight)
        jLabel.setSize(frameWidth, fontHeight)
        logD("frameWidth=$frameWidth,frameHeight=$frameHeight,fontHeight=$fontHeight")
        jFrame.layout = VerticalLayout(10, SwingConstants.CENTER)
        val jFrame2 = JPanel()
        jFrame2.setSize(frameWidth, 12)
        jFrame.add(jFrame2)
        jFrame.add(jLabel)
        val jButton = JButton("我知道了")
        jFrame.add(jButton)
        jFrame.title = "提示"
        jFrame.isVisible = true
        val screenSize = Toolkit.getDefaultToolkit().screenSize
        jFrame.setLocation((screenSize.width - frameWidth) / 2, (screenSize.height - frameHeight) / 2)
        jButton.addActionListener {
            jFrame.isVisible = false
        }
    }

    /**
     *测量指定宽度最多能够显示多少个中文
     */
    private fun measureChartSizeInOneLine(font: Font, width: Int): Int {
        var testValue = "能够"
        val ar = AffineTransform()
        val fr = FontRenderContext(ar, true, true)
        while (true) {
            val bounds = font.getStringBounds(testValue, fr)
            if (bounds.width >= width) {
                return testValue.length
            }
            testValue += testValue
        }
        return testValue.length
    }
}