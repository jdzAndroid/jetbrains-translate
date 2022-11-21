package com.jdz.translate

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange

/**
 * 搜索并替换
 * 1，通过key搜索，如果搜索到了指定key就直接替换成代码，如果字符串需要动态传值，暂不处理
 * 2，通过中文搜索，如果找到了完全匹配的中文（不包含特殊符号），就直接替换成代码
 */
class SearchAndReplace : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val editor = e.getRequiredData(PlatformDataKeys.EDITOR)
        if (editor == null) {
            logD("请选中需要搜索的中文")
            return
        } else {
            var selectedText = editor.selectionModel.selectedText
            if (selectedText.isNullOrBlank()) {
                replaceAllValueInOneFile(editor = editor, event = e)
            } else {
                replaceSingleValueInOneFile(selectedText = selectedText, editor = editor, event = e)
            }
        }
    }

    /**
     *单个文件全局替换
     */
    private fun replaceAllValueInOneFile(editor: Editor, event: AnActionEvent) {
        logD("单个文件全值匹配替换")
    }

    /**
     *单个文件单个值替换
     */
    private fun replaceSingleValueInOneFile(selectedText: String, editor: Editor, event: AnActionEvent) {
        logD("开始执行单个文件单个值匹配替换")
        var searchText = selectedText.trim()
        if (searchText.startsWith("\"")) {
            searchText = searchText.substring(1, searchText.length)
        }
        if (searchText.endsWith("\"")) {
            searchText = searchText.substring(0, searchText.length - 1)
        }
        searchText = searchText.trim()
        logD("你需要搜索翻译的关键字:$searchText")
        val result = findTranslateByZh(zhValue = searchText, event = event, exactSearch = true)
        if (result.isEmpty()) {
            logD("没有查找到任何搜索结果")
            showNotFound(selectedText)
        } else {
            val translateInfo = result.first().first()
            var selectionStart = editor.selectionModel.selectionStart
            var selectionEnd = editor.selectionModel.selectionEnd

            if (!searchText.startsWith("\"") || !searchText.startsWith("'")) {
                var start = selectionStart
                while (start > -1) {
                    val value = editor.document.getText(TextRange(start, start + 1))
                    if (value == "\"" || value == "'") {
                        selectionStart = start
                        break
                    }
                    start--
                }
            }

            if (!searchText.endsWith("\"") || !searchText.endsWith("'")) {
                var end = selectionEnd
                val maxLength = editor.document.textLength
                while (end < maxLength) {
                    val value = editor.document.getText(TextRange(end, end + 1))
                    if (value == "\"" || value == "'") {
                        selectionEnd = end + 1
                        break
                    }
                    end++
                }
            }

            logD("查找到了指定的翻译信息 translateInfo=$translateInfo")
            WriteCommandAction.runWriteCommandAction(event.project!!) {
                editor.document.replaceString(
                    selectionStart,
                    selectionEnd,
                    "TranslateManager.translateProxy.${translateInfo.key}()/*$selectedText*/"
                )
            }
        }
    }

    /**
     *没有找到指定的翻译
     */
    private fun showNotFound(searchText: String) {

    }
}