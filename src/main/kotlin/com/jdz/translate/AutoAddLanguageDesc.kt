package com.jdz.translate

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import org.jetbrains.plugins.notebooks.editor.getCells
import kotlin.math.min

/**
 * 自动添加翻译注释
 * 1,如果有明确指定了翻译注释语种，就自动补齐对应语种的翻译
 * 2，如果没有指定就默认添加中文语种翻译
 * 3,如果选中的翻译dart代码中的部分，就只补齐选中的翻译注释
 * 4，如果没有选中任何dart代码，就将遍历整个dart文件，补齐所有的翻译注释
 */
class AutoAddLanguageDesc : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val editor = e.getRequiredData(PlatformDataKeys.EDITOR)
        var selectedText = editor.selectionModel.selectedText
        if (selectedText.isNullOrBlank()) {
            appendAllLanguageDesc(e = e, editor = editor)
        } else {
            appendSpecialLanguageDesc(languageCode = getDefaultLanguageDesc(), selectedDartClass =)
        }
    }

    /**
     *遍历整个文件，将翻译注释补齐
     */
    private fun appendAllLanguageDesc(e: AnActionEvent, editor: Editor) {

    }

    /**
     *添加对应语种的翻译
     */
    private fun appendSpecialLanguageDesc(
        languageCode: String,
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
        var endChartIndex = -1
        val startIndex = sampleText.indexOf(selectedText)
        if (startIndex > -1) {
            val selectedTextEndIndex = startIndex + selectedText.length - 1
            var valid = false
            var findPreFixIndex = -1
            var findEndFixIndex = -1
            while (!valid) {
                for (index in selectedTextEndIndex + 1 until sampleText.length) {
                    val itemChart = sampleText[index]
                    when (itemChart) {
                        '(' -> {
                            findPreFixIndex = index
                            if (findEndFixIndex<=findPreFixIndex){
                                break
                            }
                        }

                        ')' -> {
                            findEndFixIndex = index
                            if (findEndFixIndex<=findPreFixIndex){
                                break
                            }
                            if (findPreFixIndex>-1){
                                valid=true
                            }
                        }
                        ' '->{

                        }
                        else->{
                            if (findPreFixIndex<0||findEndFixIndex<0){
                                break
                            }
                        }
                    }
                }
            }
        } else {
            logD("出现异常")
        }

    }
}