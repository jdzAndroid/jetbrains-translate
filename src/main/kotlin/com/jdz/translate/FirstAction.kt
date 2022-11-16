package com.jdz.translate

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.ui.layout.migLayout.patched.MigLayout
import org.jetbrains.annotations.NotNull
import java.awt.*
import javax.swing.*

class FirstAction : AnAction() {
    override fun actionPerformed(@NotNull e: AnActionEvent) {
        val screenSize = Toolkit.getDefaultToolkit().screenSize
        var jFrame = JFrame("Search Translate")
        jFrame.background = Color.BLACK
        jFrame.isAlwaysOnTop = true
        jFrame.layout = FlowLayout()
        jFrame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
        val jFrameWidth = screenSize.width / 4
        val jFrameHeight = screenSize.height / 4
        jFrame.setSize(jFrameWidth,jFrameHeight)
        val jPanelInput=JPanel()
//        jPanelInput.layout=MigLayout()
        jPanelInput.setSize(jFrameWidth-40,30)
        val jLabel = JLabel("关键字")
        jPanelInput.add(jLabel)
        val jTextField = JTextField(10)
        jTextField.isEditable = true
        jPanelInput.add(jTextField)
        jPanelInput.setLocation(20,10)
        jFrame.add(jPanelInput)
        val jPanelButton = JPanel()
//        jPanelButton.layout=MigLayout()
        val startButton = JButton("开始")
        jPanelButton.add(startButton)
        startButton.addActionListener {
            startButton.text = jTextField.text
        }

        val cancelButton = JButton("取消")
        cancelButton.addActionListener {
            jFrame.isVisible = false
        }
        jPanelButton.add(cancelButton)
        jPanelButton.setSize(jFrameWidth-40,30)
        jPanelButton.setLocation(20,80)
        jFrame.add(jPanelButton)
        jFrame.isVisible = true
    }
}