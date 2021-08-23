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
import java.awt.Component
import java.awt.Graphics
import java.awt.image.VolatileImage
import javax.swing.JPanel
import javax.swing.SwingUtilities

public class HwJPanel(private val facadeHelper: HwFacadeHelper) : JPanel() {

  private val myBackBufferField by lazy {
    facadeHelper::class.java.getDeclaredField("myBackBuffer").apply {
      isAccessible = true
    }
  }

  private val myBackBuffer: VolatileImage?
    get() = myBackBufferField.get(facadeHelper) as VolatileImage?

  override fun paintComponent(g: Graphics) {
    super.paintComponent(g)
    val buffer = myBackBuffer
    if (buffer != null) {
      //g.drawImage(buffer, 0, 0, null);
    }
  }

}
