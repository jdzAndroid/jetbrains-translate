package com.jdz.translate

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import kotlin.math.min

/**
 * 自动添加翻译注释
 * 1,如果有明确指定了翻译注释语种，就自动补齐对应语种的翻译
 * 2，如果没有指定就默认添加中文语种翻译
 * 3,如果选中的翻译dart代码中的部分，就只补齐选中的翻译注释
 * 4，如果没有选中任何dart代码，就将遍历整个dart文件，补齐所有的翻译注释
 */
class AutoAddLanguageDesc : AnAction() {

    override fun actionPerformed(event: AnActionEvent) {
        if (!hasCachedJsonFile(event)) {
            refreshCachedJsonFile(event)
        }
        showLanguageDescDialog(event)
    }

    /**
     *显示选择弹框填充指定语言的翻译
     */
    private fun showLanguageDescDialog(e: AnActionEvent) {
        val editor = e.getRequiredData(PlatformDataKeys.EDITOR)
        val selectedText = editor.selectionModel.selectedText
        if (selectedText.isNullOrBlank()) {
            appendAllLanguageDesc(e = e, editor = editor)
        } else {
            appendSpecialLanguageDesc(
                selectedText = selectedText,
                event = e,
                editor = editor
            )
        }

    }

    /**
     *遍历整个文件，将翻译注释补齐
     */
    private fun appendAllLanguageDesc(e: AnActionEvent, editor: Editor) {
        //全局匹配翻译
        val platter = Regex(".*.\\(\\)")
        val allContent = editor.document.text
        if (allContent.isEmpty()) {
            logD("文档内容为空")
            return
        }
        val mathResultList = platter.findAll(allContent).toList()
        if (mathResultList.isEmpty()) {
            return
        }
        val size = mathResultList.size
        for (index in size - 1 downTo 0) {
            val itemMathResult = mathResultList[index]
            var key=itemMathResult.value.trim()
            key = key.substring(key.lastIndexOf("." )+ 1, key.length - 2)
            val findResult = findTranslateByKey(keyValue = key, event = e, exactSearch = true)
            logD("languageDesc key=$key")
            if (findResult.isEmpty()) {
                logD("没有找到对应的翻译或者对应的翻译不存在 key=$key")
                return
            }
            WriteCommandAction.runWriteCommandAction(e.project!!) {
                editor.document.insertString(
                    itemMathResult.range.last + 1,
                    ".${getDefaultLanguageDesc()}(\"${getShowCommentText(sourceText = findResult.first().first().value)}\")"
                )
            }
        }
    }

    /**
     *添加对应语种的翻译
     */
    private fun appendSpecialLanguageDesc(
        selectedText: String,
        event: AnActionEvent,
        editor: Editor
    ) {
        val selectionModel = editor.selectionModel
        val sampleText = editor.document.getText(
            TextRange(
                selectionModel.selectionStart,
                min(selectionModel.selectionEnd + 10, editor.document.textLength - 1)
            )
        )
        //校验选中文本后面是否是()并且紧跟后面的不是.,排除空格
        val startIndex = sampleText.indexOf(selectedText)
        if (startIndex > -1) {
            val selectedTextEndIndex = startIndex + selectedText.length - 1
            var valid = false
            var findPreFixIndex = -1
            var findEndFixIndex = -1
            while (!valid) {
                for (index in selectedTextEndIndex + 1 until sampleText.length) {
                    when (sampleText[index]) {
                        '(' -> {
                            findPreFixIndex = index
                            if (findEndFixIndex > -1 && findEndFixIndex <= findPreFixIndex) {
                                break
                            }
                        }

                        ')' -> {
                            findEndFixIndex = index
                            if (findEndFixIndex <= findPreFixIndex) {
                                break
                            }
                            if (findPreFixIndex > -1) {
                                valid = true
                                break
                            }
                        }

                        ' ' -> {

                        }

                        else -> {
                            if (findPreFixIndex < 0 || findEndFixIndex < 0) {
                                break
                            }
                        }
                    }
                }
            }
            if (!valid) {
                logD("选中的文本不合法")
                return
            }
            val findResult = findTranslateByKey(keyValue = selectedText, event = event, exactSearch = true)
            if (findResult.isEmpty()) {
                logD("没有找到对应的翻译或者对应的翻译不存在 selectedText=$selectedText")
                return
            }
            WriteCommandAction.runWriteCommandAction(event.project!!) {
                editor.document.insertString(
                    selectionModel.selectionEnd+findEndFixIndex-selectedText.length+1,
                    ".${getDefaultLanguageDesc()}(\"${getShowCommentText(sourceText = findResult.first().first().value)}\")"
                )
            }
        } else {
            logD("出现异常")
        }

    }
}