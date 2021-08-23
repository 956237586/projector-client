/*
 * MIT License
 *
 * Copyright (c) 2019-2021 JetBrains s.r.o.
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
package org.jetbrains.projector.client.web.window

import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.dom.addClass
import org.jetbrains.projector.client.common.DrawEvent
import org.jetbrains.projector.client.common.SingleRenderingSurfaceProcessor
import org.jetbrains.projector.client.common.canvas.DomCanvas
import org.jetbrains.projector.client.common.canvas.buffering.DoubleBufferedRenderingSurface
import org.jetbrains.projector.client.common.canvas.buffering.UnbufferedRenderingSurface
import org.jetbrains.projector.client.common.misc.ParamsProvider
import org.jetbrains.projector.client.web.misc.toDisplayType
import org.jetbrains.projector.client.web.misc.toJsCursorType
import org.jetbrains.projector.client.web.state.ClientAction
import org.jetbrains.projector.client.web.state.ClientStateMachine
import org.jetbrains.projector.client.web.state.LafListener
import org.jetbrains.projector.client.web.state.ProjectorUI
import org.jetbrains.projector.common.protocol.data.CommonRectangle
import org.jetbrains.projector.common.protocol.data.CursorType
import org.jetbrains.projector.common.protocol.toClient.ServerCaretInfoChangedEvent
import org.jetbrains.projector.common.protocol.toClient.WindowData
import org.jetbrains.projector.common.protocol.toClient.WindowType
import org.jetbrains.projector.common.protocol.toServer.ClientWindowCloseEvent
import org.jetbrains.projector.common.protocol.toServer.ClientWindowMoveEvent
import org.jetbrains.projector.common.protocol.toServer.ClientWindowResizeEvent
import org.jetbrains.projector.common.protocol.toServer.ResizeDirection
import org.w3c.dom.CanvasRenderingContext2D
import org.w3c.dom.HTMLCanvasElement
import kotlin.math.roundToInt

interface Positionable {

  val bounds: CommonRectangle
  val zIndex: Int
}

interface Parent : Positionable {

  val children: Collection<Child>

}

interface Child : Positionable {

  val parent: Parent

  fun onParentZIndexUpdated()

}

class Window(windowData: WindowData, private val stateMachine: ClientStateMachine) : LafListener, Parent {

  val id = windowData.id

  val pendingDrawEvents = ArrayDeque<DrawEvent>()
  val newDrawEvents = ArrayDeque<DrawEvent>()

  var title: String? = null
    set(value) {
      field = value
      header?.title = value
    }

  var modal: Boolean = windowData.modal

  var isShowing: Boolean = false
    set(value) {
      header?.visible = value
      border.visible = value

      if (field == value) {
        return
      }
      field = value
      canvas.style.display = value.toDisplayType()
    }

  //public only for speculative typing.
  val canvas = createCanvas()


  val editorCanvas = (document.createElement("canvas") as HTMLCanvasElement).apply {

    style.display = "block"
    //style.display = "none"
    style.position = "fixed"
    id = "EDITOR"

    //(getContext("2d") as CanvasRenderingContext2D).apply {
    //  scale(0.002, 0.002)
    //  this.translate(400.0, 600.0)
    //}

    //document.body!!.appendChild(this)
  }


  public val renderingSurface = createRenderingSurface(canvas)

  private var header: WindowHeader? = null
  private var headerVerticalPosition: Double = 0.0
  private var headerHeight: Double = 0.0
  private val border = WindowBorder(windowData.resizable, windowData.windowType != WindowType.HEAVYWEIGHT_WRAPPER)

  private val commandProcessor = SingleRenderingSurfaceProcessor(renderingSurface)

  private val _children = mutableListOf<Child>()

  override val children: Collection<Child>
    get() = _children

  override var bounds: CommonRectangle = CommonRectangle(0.0, 0.0, 0.0, 0.0)
    set(value) {
      if (field == value) {
        return
      }
      field = value
      applyBounds()
    }

  override var zIndex: Int = 0
    set(value) {
      if (field != value) {
        field = value
        canvas.style.zIndex = "$zIndex"
        header?.zIndex = zIndex
        border.zIndex = zIndex - 1
        _children.forEach { it.onParentZIndexUpdated() }
      }
    }

  var cursorType: CursorType = CursorType.DEFAULT
    set(value) {
      if (field != value) {
        field = value
        canvas.style.cursor = value.toJsCursorType()
      }
    }

  val editorCanvasChild: Child = object : Child {

    init {
      _children.add(this)
    }

    override val parent: Parent
      get() = this@Window

    override fun onParentZIndexUpdated() {
      zIndex = parent.zIndex + 1
    }

    override val bounds: CommonRectangle
      get() = TODO("Not yet implemented")

    override var zIndex: Int = parent.zIndex + 1
      set(value) {
        field = value
        editorCanvas.style.zIndex = "$value"
      }

  }

  init {
    applyBounds()

    if (windowData.windowType == WindowType.IDEA_WINDOW || windowData.windowType == WindowType.POPUP) {
      canvas.style.border = "none"
    }
    else if (windowData.windowType == WindowType.WINDOW) {
      if (windowData.undecorated) {
        canvas.style.border = ProjectorUI.borderStyle
      }
      else {
        // If the window has a header on the host, its sizes are included in the window bounds.
        // The client header is drawn above the window, outside its bounds. At the same time,
        // the coordinates of the contents of the window come taking into account the size
        // of the header. As a result, on client an empty space is obtained between header
        // and the contents of the window. To get rid of this, we transfer the height of the system
        // window header and if it > 0, we draw the heading not over the window but inside
        // the window's bounds, filling in the empty space.

        header = WindowHeader(windowData.title)
        header!!.undecorated = windowData.undecorated
        header!!.onMove = ::onMove
        header!!.onClose = ::onClose

        headerVerticalPosition = when (windowData.headerHeight) {
          0, null -> ProjectorUI.headerHeight
          else -> 0.0
        }

        headerHeight = when (windowData.headerHeight) {
          0, null -> ProjectorUI.headerHeight
          else -> windowData.headerHeight!!.toDouble()
        }

        canvas.style.borderBottom = ProjectorUI.borderStyle
        canvas.style.borderLeft = ProjectorUI.borderStyle
        canvas.style.borderRight = ProjectorUI.borderStyle
        canvas.style.borderRadius = "0 0 ${ProjectorUI.borderRadius}px ${ProjectorUI.borderRadius}px"
      }
    }

    if (windowData.resizable) {
      border.onResize = ::onResize
    }
  }

  override fun lookAndFeelChanged() {
    if (header != null) {
      canvas.style.borderBottom = ProjectorUI.borderStyle
      canvas.style.borderLeft = ProjectorUI.borderStyle
      canvas.style.borderRight = ProjectorUI.borderStyle
      canvas.style.borderRadius = "0 0 ${ProjectorUI.borderRadius}px ${ProjectorUI.borderRadius}px"
    }
    else if (canvas.style.border != "none") {
      canvas.style.border = ProjectorUI.borderStyle
    }

    header?.lookAndFeelChanged()
    border.lookAndFeelChanged()
  }

  fun contains(x: Int, y: Int): Boolean {
    return border.bounds.contains(x, y)
  }

  fun onMouseDown(x: Int, y: Int): DragEventsInterceptor? {
    return border.onMouseDown(x, y) ?: header?.onMouseDown(x, y)
  }

  fun onMouseClick(x: Int, y: Int): DragEventsInterceptor? {
    return border.onMouseClick(x, y) ?: header?.onMouseClick(x, y)
  }

  private fun onResize(deltaX: Int, deltaY: Int, direction: ResizeDirection) {
    stateMachine.fire(ClientAction.AddEvent(ClientWindowResizeEvent(id, deltaX, deltaY, direction)))
  }

  private fun onMove(deltaX: Int, deltaY: Int) {
    stateMachine.fire(ClientAction.AddEvent(ClientWindowMoveEvent(id, deltaX, deltaY)))
  }

  private fun onClose() {
    stateMachine.fire(ClientAction.AddEvent(ClientWindowCloseEvent(id)))
  }

  private fun createCanvas() = (document.createElement("canvas") as HTMLCanvasElement).apply {
    style.position = "fixed"
    style.display = isShowing.toDisplayType()

    addClass("window")  // to easily locate windows in integration tests

    document.body!!.appendChild(this)
  }

  init {
    renderingSurface.editorCanvas = DomCanvas(editorCanvas)
  }

  fun Number.toPxStr(): String = "${this}px"

  var lastCarets: ServerCaretInfoChangedEvent.CaretInfoChange.Carets? = null

  fun applyCaretInfo(caretInfo: ServerCaretInfoChangedEvent.CaretInfoChange) {
    if (caretInfo !is ServerCaretInfoChangedEvent.CaretInfoChange.Carets) return

    val ctx = editorCanvas.getContext("2d") as CanvasRenderingContext2D

    ctx.apply {
      restore()
      save()

      ParamsProvider.SCALING_RATIO.let { scale(it, it) }

      if (caretInfo.editorMetrics != lastCarets?.editorMetrics) {
        console.log("ClearRect")
        //ctx.clearRect(0.0, 0.0, editorCanvas.width.toDouble(), editorCanvas.height.toDouble())
      }

      val editorMetrics = caretInfo.editorMetrics
      beginPath()
      rect(
        x = editorMetrics.x,
        y = editorMetrics.y,
        w = editorMetrics.width,
        h = editorMetrics.height
      )
      clip()
    }

    //editorCanvas.style.let { speculativeStyle ->
    //  speculativeStyle.left = caretInfo.editorMetrics.x.toPxStr()
    //  speculativeStyle.top = caretInfo.editorMetrics.y.toPxStr()
    //  speculativeStyle.width = caretInfo.editorMetrics.width.toPxStr()
    //  speculativeStyle.height = caretInfo.editorMetrics.height.toPxStr()
    //  speculativeStyle.zIndex = "11"
    //}

    lastCarets = caretInfo
  }

  private fun ensureSpeculativeCanvasSize(canvas: HTMLCanvasElement, targetCanvas: HTMLCanvasElement) {

    console.log("main: $canvas; target: $targetCanvas")

    canvas.style.let { canvasStyle ->
      targetCanvas.style.let { speculativeStyle ->
        speculativeStyle.left = canvasStyle.left
        speculativeStyle.top = canvasStyle.top
        speculativeStyle.width = canvasStyle.width
        speculativeStyle.height = canvasStyle.height
        speculativeStyle.zIndex = canvasStyle.zIndex.let {
          val z = it.toIntOrNull() ?: return@let it
          (z + 1).toString()
        }
      }
    }

    if (targetCanvas.width != canvas.width || targetCanvas.height != canvas.height) {
      targetCanvas.width = canvas.width
      targetCanvas.height = canvas.height
    }
  }

  fun applyBounds() {
    val userScalingRatio = ParamsProvider.USER_SCALING_RATIO
    val scalingRatio = ParamsProvider.SCALING_RATIO

    val clientBounds = CommonRectangle(
      bounds.x * userScalingRatio,
      bounds.y * userScalingRatio,
      bounds.width * userScalingRatio,
      bounds.height * userScalingRatio
    )

    if (header != null) {
      header!!.bounds = CommonRectangle(
        clientBounds.x,
        (bounds.y - headerVerticalPosition) * userScalingRatio,
        clientBounds.width,
        headerHeight * userScalingRatio
      )
    }

    border.bounds = CommonRectangle(
      clientBounds.x,
      (bounds.y - headerVerticalPosition) * userScalingRatio,
      clientBounds.width,
      clientBounds.height + headerVerticalPosition * userScalingRatio
    ).createExtended(ProjectorUI.borderThickness * userScalingRatio)

    canvas.style.apply {
      left = "${clientBounds.x}px"
      top = "${clientBounds.y}px"
      width = "${clientBounds.width}px"
      height = "${clientBounds.height}px"
    }

    renderingSurface.scalingRatio = scalingRatio
    renderingSurface.setBounds(
      width = (bounds.width * scalingRatio).roundToInt(),
      height = (bounds.height * scalingRatio).roundToInt()
    )

    ensureSpeculativeCanvasSize(canvas, editorCanvas)
  }

  fun dispose() {

    canvas.remove()
    border.dispose()
    header?.dispose()
    editorCanvas.remove()

    //window.setTimeout({
    //                    canvas.remove()
    //                    border.dispose()
    //                    header?.dispose()
    //                    editorCanvas.remove()
    //                  }, 5000)
  }

  fun drawPendingEvents() {
    if (pendingDrawEvents.isNotEmpty()) {
      commandProcessor.processPending(pendingDrawEvents)
      renderingSurface.flush()
    }
    header?.draw()  // todo: do we need to draw it every time?
  }

  fun drawNewEvents() {
    val firstUnsuccessful = commandProcessor.processNew(newDrawEvents)
    renderingSurface.flush()
    header?.draw()  // todo: do we need to draw it every time?

    if (pendingDrawEvents.isNotEmpty()) {
      pendingDrawEvents.addAll(newDrawEvents)
    }
    else if (firstUnsuccessful != null) {
      if (pendingDrawEvents.isNotEmpty()) {
        console.error("Non empty pendingDrawEvents are handled by another branch, aren't they? This branch works only for empty.")
      }
      pendingDrawEvents.addAll(newDrawEvents.subList(firstUnsuccessful, newDrawEvents.size))
    }
    newDrawEvents.clear()
  }

  companion object {
    fun createRenderingSurface(canvas: HTMLCanvasElement) = when (ParamsProvider.DOUBLE_BUFFERING) {
      true -> DoubleBufferedRenderingSurface(DomCanvas(canvas))
      false -> UnbufferedRenderingSurface(DomCanvas(canvas))
    }
  }
}
