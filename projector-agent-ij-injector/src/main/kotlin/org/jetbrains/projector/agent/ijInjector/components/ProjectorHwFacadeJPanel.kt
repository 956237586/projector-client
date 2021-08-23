/*
 * Copyright (c) 2019-2021, JetBrains s.r.o. and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation. JetBrains designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact JetBrains, Na Hrebenech II 1718/10, Prague, 14000, Czech Republic
 * if you need additional information or have any questions.
 */
package org.jetbrains.projector.agent.ijInjector.components

import com.intellij.ui.jcef.HwFacadeHelper
import com.intellij.ui.jcef.HwFacadeJPanel
import java.awt.Graphics
import java.lang.reflect.InvocationTargetException
import javax.swing.JComponent
import javax.swing.JPanel

@Suppress("UnstableApiUsage")
public open class ProjectorHwFacadeJPanel : HwFacadeJPanel() {

  private val myHwFacadeHelper = kotlin.run {

    val prjClassLoaderClass =
      ClassLoader.getSystemClassLoader().loadClass("org.jetbrains.projector.util.loading.ProjectorClassLoader")

    val prjLoader = prjClassLoaderClass.getDeclaredMethod("getInstance").invoke(null) as ClassLoader

    val helperClass = prjLoader.loadClass("org.jetbrains.projector.agent.ijInjector.components.ProjectorHwFacadeHelper")
    val helperInstance = helperClass.getDeclaredConstructor(JComponent::class.java).newInstance(this)
    helperInstance as HwFacadeHelper
  }

  override fun addNotify() {
    //println("ProjectorHwFacadeJPanel: addNotify")
    super.addNotify()
    myHwFacadeHelper.addNotify()
  }

  override fun removeNotify() {
    //println("ProjectorHwFacadeJPanel: removeNotify")
    super.removeNotify()
    myHwFacadeHelper.removeNotify()
  }

  @Suppress("DEPRECATION")
  override fun show() {
    //println("ProjectorHwFacadeJPanel: show")
    super.show()
    myHwFacadeHelper.show()
  }

  @Suppress("DEPRECATION")
  override fun hide() {
    //println("ProjectorHwFacadeJPanel: hide")
    super.hide()
    myHwFacadeHelper.hide()
  }

  override fun paint(g: Graphics?) {
    //println("ProjectorHwFacadeJPanel: paint")
    //val t = this@JPanel

    super.paint(g)

    //val method = JComponent::class.java.getDeclaredMethod("paint", Graphics::class.java)
    myHwFacadeHelper.paint(g) { gg ->
      run {
        super.paint(gg)
        println("qaqa")
      }
    }
  }

  //public override fun paintChildren(g: Graphics?) {
  //  //println("ProjectorHwFacadeJPanel: paintChildren")
  //  super.paintChildren(g)
  //}

}
