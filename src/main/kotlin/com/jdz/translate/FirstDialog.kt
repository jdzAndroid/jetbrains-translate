package com.jdz.translate

import com.intellij.openapi.ui.DialogWrapper
import javax.swing.*


class FirstDialog(val component:JComponent):DialogWrapper(null, true){
    override fun createCenterPanel(): JComponent {
        return component
    }
}