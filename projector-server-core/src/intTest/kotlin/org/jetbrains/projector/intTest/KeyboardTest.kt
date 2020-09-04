/*
 * MIT License
 *
 * Copyright (c) 2019-2020 JetBrains s.r.o.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.jetbrains.projector.intTest

import com.codeborne.selenide.Condition.appear
import com.codeborne.selenide.Selenide.element
import com.codeborne.selenide.Selenide.open
import io.ktor.server.engine.ApplicationEngine
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import org.jetbrains.projector.common.protocol.data.CommonRectangle
import org.jetbrains.projector.common.protocol.toClient.ServerWindowSetChangedEvent
import org.jetbrains.projector.common.protocol.toClient.WindowData
import org.jetbrains.projector.common.protocol.toClient.WindowType
import org.jetbrains.projector.common.protocol.toServer.ClientKeyEvent
import org.jetbrains.projector.common.protocol.toServer.ClientKeyPressEvent
import org.jetbrains.projector.intTest.ConnectionUtil.clientUrl
import org.jetbrains.projector.intTest.ConnectionUtil.startServerAndDoHandshake
import org.jetbrains.projector.server.core.convert.toAwt.toAwtKeyEvent
import org.openqa.selenium.Keys
import java.awt.event.KeyEvent
import javax.swing.JLabel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class KeyboardTest {

  private companion object {

    private fun withReadableException(events: List<KeyEvent?>, checks: (events: List<KeyEvent?>) -> Unit) {
      try {
        checks(events)
      }
      catch (e: AssertionError) {
        throw AssertionError("exception when checking the following events (see cause): $events", e)
      }
    }

    private fun checkEvent(actual: KeyEvent?, id: Int, keyCode: Int, keyChar: Char, keyLocation: Int, modifiersEx: Int) {
      assertNotNull(actual)
      assertEquals(id, actual.id)
      assertEquals(keyCode, actual.keyCode)
      // keyText is generated from keyCode so no need to compare it
      assertEquals(keyChar, actual.keyChar, "expected int: ${keyChar.toInt()} but was int: ${actual.keyChar.toInt()}")
      assertEquals(keyLocation, actual.keyLocation)
      if (modifiersEx >= 0) {
        assertEquals(modifiersEx, actual.modifiersEx)
      }
    }

    private fun createServerAndReceiveKeyEvents(keyEvents: Channel<List<KeyEvent?>>): ApplicationEngine {
      return startServerAndDoHandshake { (sender, receiver) ->
        val window = WindowData(
          id = 1,
          isShowing = true,
          zOrder = 0,
          bounds = CommonRectangle(10.0, 10.0, 100.0, 100.0),
          resizable = true,
          modal = false,
          undecorated = false,
          windowType = WindowType.IDEA_WINDOW
        )

        sender(listOf(ServerWindowSetChangedEvent(listOf(window))))

        while (true) {
          val list = mutableListOf<KeyEvent?>()

          val events = receiver()
          events.forEach {
            when (it) {
              is ClientKeyPressEvent -> list.add(it.toAwtKeyEvent(0, JLabel()) { println(it()) })
              is ClientKeyEvent -> list.add(it.toAwtKeyEvent(0, JLabel()) { println(it()) })
              else -> Unit
            }
          }

          if (list.isNotEmpty()) {
            keyEvents.send(list)
          }
        }
      }
    }
  }

  @Test
  fun testSimpleSymbol() {
    val keyEvents = Channel<List<KeyEvent?>>()

    val server = createServerAndReceiveKeyEvents(keyEvents)
    server.start()

    open(clientUrl)
    element(".window").should(appear)

    element("body").sendKeys("h")  // test letter "h"

    val events = runBlocking { keyEvents.receive() }

    // expected (tested "h" press in a headful app):
    // java.awt.event.KeyEvent[KEY_PRESSED,keyCode=72,keyText=H,keyChar='h',keyLocation=KEY_LOCATION_STANDARD,rawCode=0,primaryLevelUnicode=0,scancode=0,extendedKeyCode=0x0] on frame0 0
    // java.awt.event.KeyEvent[KEY_TYPED,keyCode=0,keyText=Unknown keyCode: 0x0,keyChar='h',keyLocation=KEY_LOCATION_UNKNOWN,rawCode=0,primaryLevelUnicode=0,scancode=0,extendedKeyCode=0x0] on frame0 0
    // java.awt.event.KeyEvent[KEY_RELEASED,keyCode=72,keyText=H,keyChar='h',keyLocation=KEY_LOCATION_STANDARD,rawCode=0,primaryLevelUnicode=0,scancode=0,extendedKeyCode=0x0] on frame0 0

    withReadableException(events) {
      assertEquals(3, events.size)
      checkEvent(it[0], KeyEvent.KEY_PRESSED, 72, 'h', KeyEvent.KEY_LOCATION_STANDARD, 0)
      checkEvent(it[1], KeyEvent.KEY_TYPED, 0, 'h', KeyEvent.KEY_LOCATION_UNKNOWN, 0)
      checkEvent(it[2], KeyEvent.KEY_RELEASED, 72, 'h', KeyEvent.KEY_LOCATION_STANDARD, 0)
    }

    server.stop(500, 1000)
  }

  @Test
  fun testShiftedSimpleSymbol() {
    val keyEvents = Channel<List<KeyEvent?>>()

    val server = createServerAndReceiveKeyEvents(keyEvents)
    server.start()

    open(clientUrl)
    element(".window").should(appear)

    element("body").sendKeys("H")  // test letter "H"

    val events = runBlocking { keyEvents.receive() }

    // expected (tested "H" press in a headful app):
    // java.awt.event.KeyEvent[KEY_PRESSED,keyCode=16,keyText=Shift,keyChar=Undefined keyChar,modifiers=Shift,extModifiers=Shift,keyLocation=KEY_LOCATION_LEFT,rawCode=0,primaryLevelUnicode=0,scancode=0,extendedKeyCode=0x0] on frame0 64
    // java.awt.event.KeyEvent[KEY_PRESSED,keyCode=72,keyText=H,keyChar='H',modifiers=Shift,extModifiers=Shift,keyLocation=KEY_LOCATION_STANDARD,rawCode=0,primaryLevelUnicode=0,scancode=0,extendedKeyCode=0x0] on frame0 64
    // java.awt.event.KeyEvent[KEY_TYPED,keyCode=0,keyText=Unknown keyCode: 0x0,keyChar='H',modifiers=Shift,extModifiers=Shift,keyLocation=KEY_LOCATION_UNKNOWN,rawCode=0,primaryLevelUnicode=0,scancode=0,extendedKeyCode=0x0] on frame0 64
    // java.awt.event.KeyEvent[KEY_RELEASED,keyCode=72,keyText=H,keyChar='H',modifiers=Shift,extModifiers=Shift,keyLocation=KEY_LOCATION_STANDARD,rawCode=0,primaryLevelUnicode=0,scancode=0,extendedKeyCode=0x0] on frame0 64
    // java.awt.event.KeyEvent[KEY_RELEASED,keyCode=16,keyText=Shift,keyChar=Undefined keyChar,keyLocation=KEY_LOCATION_LEFT,rawCode=0,primaryLevelUnicode=0,scancode=0,extendedKeyCode=0x0] on frame0 0

    withReadableException(events) {
      assertEquals(5, events.size)
      checkEvent(it[0], KeyEvent.KEY_PRESSED, 16, KeyEvent.CHAR_UNDEFINED, KeyEvent.KEY_LOCATION_LEFT,
                 -64)  // todo: the modifier is wrong in WebDriver so skip the check for now by making it negative
      checkEvent(it[1], KeyEvent.KEY_PRESSED, 72, 'H', KeyEvent.KEY_LOCATION_STANDARD, 64)
      checkEvent(it[2], KeyEvent.KEY_TYPED, 0, 'H', KeyEvent.KEY_LOCATION_UNKNOWN, 64)
      checkEvent(it[3], KeyEvent.KEY_RELEASED, 72, 'H', KeyEvent.KEY_LOCATION_STANDARD, 64)
      checkEvent(it[4], KeyEvent.KEY_RELEASED, 16, KeyEvent.CHAR_UNDEFINED, KeyEvent.KEY_LOCATION_LEFT, 0)
    }

    server.stop(500, 1000)
  }

  @Test
  fun testTab() {
    val keyEvents = Channel<List<KeyEvent?>>()

    val server = createServerAndReceiveKeyEvents(keyEvents)
    server.start()

    open(clientUrl)
    element(".window").should(appear)

    element("body").sendKeys(Keys.TAB)  // test TAB

    val events = runBlocking { keyEvents.receive() }

    // expected (tested TAB press in a headful app):
    // java.awt.event.KeyEvent[KEY_PRESSED,keyCode=9,keyText=Tab,keyChar=Tab,keyLocation=KEY_LOCATION_STANDARD,rawCode=0,primaryLevelUnicode=0,scancode=0,extendedKeyCode=0x0] on frame0 0
    // java.awt.event.KeyEvent[KEY_TYPED,keyCode=0,keyText=Unknown keyCode: 0x0,keyChar=Tab,keyLocation=KEY_LOCATION_UNKNOWN,rawCode=0,primaryLevelUnicode=0,scancode=0,extendedKeyCode=0x0] on frame0 0
    // java.awt.event.KeyEvent[KEY_RELEASED,keyCode=9,keyText=Tab,keyChar=Tab,keyLocation=KEY_LOCATION_STANDARD,rawCode=0,primaryLevelUnicode=0,scancode=0,extendedKeyCode=0x0] on frame0 0

    withReadableException(events) {
      assertEquals(3, events.size)
      checkEvent(it[0], KeyEvent.KEY_PRESSED, 9, '\t', KeyEvent.KEY_LOCATION_STANDARD, 0)
      checkEvent(it[1], KeyEvent.KEY_TYPED, 0, '\t', KeyEvent.KEY_LOCATION_UNKNOWN, 0)
      checkEvent(it[2], KeyEvent.KEY_RELEASED, 9, '\t', KeyEvent.KEY_LOCATION_STANDARD, 0)
    }

    server.stop(500, 1000)
  }

  @Test
  fun testEnter() {
    val keyEvents = Channel<List<KeyEvent?>>()

    val server = createServerAndReceiveKeyEvents(keyEvents)
    server.start()

    open(clientUrl)
    element(".window").should(appear)

    element("body").sendKeys(Keys.ENTER)  // test ENTER

    val events = runBlocking { keyEvents.receive() }

    // expected (tested Enter press in a headful app):
    // java.awt.event.KeyEvent[KEY_PRESSED,keyCode=10,keyText=Enter,keyChar=Enter,keyLocation=KEY_LOCATION_STANDARD,rawCode=0,primaryLevelUnicode=0,scancode=0,extendedKeyCode=0x0] on frame0 0
    // java.awt.event.KeyEvent[KEY_TYPED,keyCode=0,keyText=Unknown keyCode: 0x0,keyChar=Enter,keyLocation=KEY_LOCATION_UNKNOWN,rawCode=0,primaryLevelUnicode=0,scancode=0,extendedKeyCode=0x0] on frame0 0
    // java.awt.event.KeyEvent[KEY_RELEASED,keyCode=10,keyText=Enter,keyChar=Enter,keyLocation=KEY_LOCATION_STANDARD,rawCode=0,primaryLevelUnicode=0,scancode=0,extendedKeyCode=0x0] on frame0 0

    withReadableException(events) {
      assertEquals(3, events.size)
      checkEvent(it[0], KeyEvent.KEY_PRESSED, 10, '\n', KeyEvent.KEY_LOCATION_STANDARD, 0)
      checkEvent(it[1], KeyEvent.KEY_TYPED, 0, '\n', KeyEvent.KEY_LOCATION_UNKNOWN, 0)
      checkEvent(it[2], KeyEvent.KEY_RELEASED, 10, '\n', KeyEvent.KEY_LOCATION_STANDARD, 0)
    }

    server.stop(500, 1000)
  }

  @Test
  fun testBackspace() {
    val keyEvents = Channel<List<KeyEvent?>>()

    val server = createServerAndReceiveKeyEvents(keyEvents)
    server.start()

    open(clientUrl)
    element(".window").should(appear)

    element("body").sendKeys(Keys.BACK_SPACE)  // test BACKSPACE

    val events = runBlocking { keyEvents.receive() }

    // expected (tested Backspace press in a headful app):
    // java.awt.event.KeyEvent[KEY_PRESSED,keyCode=8,keyText=Backspace,keyChar=Backspace,keyLocation=KEY_LOCATION_STANDARD,rawCode=0,primaryLevelUnicode=0,scancode=0,extendedKeyCode=0x0] on frame0 0
    // java.awt.event.KeyEvent[KEY_TYPED,keyCode=0,keyText=Unknown keyCode: 0x0,keyChar=Backspace,keyLocation=KEY_LOCATION_UNKNOWN,rawCode=0,primaryLevelUnicode=0,scancode=0,extendedKeyCode=0x0] on frame0 0
    // java.awt.event.KeyEvent[KEY_RELEASED,keyCode=8,keyText=Backspace,keyChar=Backspace,keyLocation=KEY_LOCATION_STANDARD,rawCode=0,primaryLevelUnicode=0,scancode=0,extendedKeyCode=0x0] on frame0 0

    withReadableException(events) {
      assertEquals(3, events.size)
      checkEvent(it[0], KeyEvent.KEY_PRESSED, 8, '\b', KeyEvent.KEY_LOCATION_STANDARD, 0)
      checkEvent(it[1], KeyEvent.KEY_TYPED, 0, '\b', KeyEvent.KEY_LOCATION_UNKNOWN, 0)
      checkEvent(it[2], KeyEvent.KEY_RELEASED, 8, '\b', KeyEvent.KEY_LOCATION_STANDARD, 0)
    }

    server.stop(500, 1000)
  }

  @Test
  fun testSpace() {
    val keyEvents = Channel<List<KeyEvent?>>()

    val server = createServerAndReceiveKeyEvents(keyEvents)
    server.start()

    open(clientUrl)
    element(".window").should(appear)

    element("body").sendKeys(Keys.SPACE)  // test SPACE

    val events = runBlocking { keyEvents.receive() }

    // expected (tested Space press in a headful app):
    // java.awt.event.KeyEvent[KEY_PRESSED,keyCode=32,keyText=Space,keyChar=' ',keyLocation=KEY_LOCATION_STANDARD,rawCode=0,primaryLevelUnicode=0,scancode=0,extendedKeyCode=0x0] on frame0 0
    // java.awt.event.KeyEvent[KEY_TYPED,keyCode=0,keyText=Unknown keyCode: 0x0,keyChar=' ',keyLocation=KEY_LOCATION_UNKNOWN,rawCode=0,primaryLevelUnicode=0,scancode=0,extendedKeyCode=0x0] on frame0 0
    // java.awt.event.KeyEvent[KEY_RELEASED,keyCode=32,keyText=Space,keyChar=' ',keyLocation=KEY_LOCATION_STANDARD,rawCode=0,primaryLevelUnicode=0,scancode=0,extendedKeyCode=0x0] on frame0 0

    withReadableException(events) {
      assertEquals(3, events.size)
      checkEvent(it[0], KeyEvent.KEY_PRESSED, 32, ' ', KeyEvent.KEY_LOCATION_STANDARD, 0)
      checkEvent(it[1], KeyEvent.KEY_TYPED, 0, ' ', KeyEvent.KEY_LOCATION_UNKNOWN, 0)
      checkEvent(it[2], KeyEvent.KEY_RELEASED, 32, ' ', KeyEvent.KEY_LOCATION_STANDARD, 0)
    }

    server.stop(500, 1000)
  }

  @Test
  fun testCtrlLetter() {
    val keyEvents = Channel<List<KeyEvent?>>()

    val server = createServerAndReceiveKeyEvents(keyEvents)
    server.start()

    open(clientUrl)
    element(".window").should(appear)

    element("body").sendKeys(Keys.chord(Keys.CONTROL, "z"))  // test Ctrl+Z

    val events = runBlocking { keyEvents.receive() }

    // expected (tested Ctrl+Z press in a headful app):
    // java.awt.event.KeyEvent[KEY_PRESSED,keyCode=17,keyText=Ctrl,keyChar=Undefined keyChar,modifiers=Ctrl,extModifiers=Ctrl,keyLocation=KEY_LOCATION_LEFT,rawCode=0,primaryLevelUnicode=0,scancode=0,extendedKeyCode=0x0] on frame0 128
    //java.awt.event.KeyEvent[KEY_PRESSED,keyCode=90,keyText=Z,keyChar='',modifiers=Ctrl,extModifiers=Ctrl,keyLocation=KEY_LOCATION_STANDARD,rawCode=0,primaryLevelUnicode=0,scancode=0,extendedKeyCode=0x0] on frame0 128
    //java.awt.event.KeyEvent[KEY_TYPED,keyCode=0,keyText=Unknown keyCode: 0x0,keyChar='',modifiers=Ctrl,extModifiers=Ctrl,keyLocation=KEY_LOCATION_UNKNOWN,rawCode=0,primaryLevelUnicode=0,scancode=0,extendedKeyCode=0x0] on frame0 128
    //java.awt.event.KeyEvent[KEY_RELEASED,keyCode=90,keyText=Z,keyChar='',modifiers=Ctrl,extModifiers=Ctrl,keyLocation=KEY_LOCATION_STANDARD,rawCode=0,primaryLevelUnicode=0,scancode=0,extendedKeyCode=0x0] on frame0 128
    //java.awt.event.KeyEvent[KEY_RELEASED,keyCode=17,keyText=Ctrl,keyChar=Undefined keyChar,keyLocation=KEY_LOCATION_LEFT,rawCode=0,primaryLevelUnicode=0,scancode=0,extendedKeyCode=0x0] on frame0 0

    withReadableException(events) {
      assertEquals(5, events.size)
      checkEvent(it[0], KeyEvent.KEY_PRESSED, 17, KeyEvent.CHAR_UNDEFINED, KeyEvent.KEY_LOCATION_LEFT, 128)
      checkEvent(it[1], KeyEvent.KEY_PRESSED, 90, '', KeyEvent.KEY_LOCATION_STANDARD, 128)
      checkEvent(it[2], KeyEvent.KEY_TYPED, 0, '', KeyEvent.KEY_LOCATION_UNKNOWN, 128)
      checkEvent(it[3], KeyEvent.KEY_RELEASED, 90, '', KeyEvent.KEY_LOCATION_STANDARD, 128)
      checkEvent(it[4], KeyEvent.KEY_RELEASED, 17, KeyEvent.CHAR_UNDEFINED, KeyEvent.KEY_LOCATION_LEFT, 0)
    }

    server.stop(500, 1000)
  }
}