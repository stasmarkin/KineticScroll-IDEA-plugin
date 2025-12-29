package com.stasmarkin.kineticscroll

import com.intellij.ide.IdeEventQueue
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.MouseShortcut
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.impl.EditorComponentImpl
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.util.Disposer
import com.intellij.terminal.JBTerminalWidget
import com.intellij.ui.ComponentUtil
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.Alarm
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.update.Activatable
import com.intellij.util.ui.update.UiNotifyConnector
import java.awt.*
import java.awt.event.*
import javax.swing.JComponent
import javax.swing.JScrollPane
import kotlin.math.max

private fun isToggleMouseButton(event: AWTEvent): Boolean {
  if (event !is MouseEvent) return false
  val shortcuts =
    KeymapManager.getInstance().activeKeymap.getShortcuts("KineticScroll.Toggle").filterIsInstance<MouseShortcut>()
  return shortcuts.contains(MouseShortcut(event.button, event.modifiersEx, 1))
}

class KineticScrollEventListener : IdeEventQueue.EventDispatcher, AWTEventListener, Disposable {
  private var handler: Handler? = null
  private var isAWTListenerRegistered = false

  init {
    registerAWTEventListener()
  }

  private fun registerAWTEventListener() {
    if (!isAWTListenerRegistered) {
      Toolkit.getDefaultToolkit().addAWTEventListener(
        this,
        AWTEvent.MOUSE_EVENT_MASK or AWTEvent.MOUSE_MOTION_EVENT_MASK
      )
      isAWTListenerRegistered = true
    }
  }

  private fun unregisterAWTEventListener() {
    if (isAWTListenerRegistered) {
      Toolkit.getDefaultToolkit().removeAWTEventListener(this)
      isAWTListenerRegistered = false
    }
  }

  override fun dispose() {
    disposeHandler()
    unregisterAWTEventListener()
  }

  // AWTEventListener - handles events at system level before component listeners
  override fun eventDispatched(event: AWTEvent) {
    if (event !is MouseEvent) return

    val settings = FMSSettings.instance
    if (!settings.enableTerminalHandler) return

    val component = UIUtil.getDeepestComponentAt(event.component, event.x, event.y) as? JComponent
    val terminal = findTerminal(component)

    // Handle terminal events exclusively through AWTEventListener
    if (terminal != null || handler is TerminalHandler) {
      when {
        // Middle button pressed - start kinetic scroll
        isToggleMouseButton(event) && event.id == MouseEvent.MOUSE_PRESSED && terminal != null -> {
          handleMouseEvent(event)
          event.consume()
        }
        // Any mouse event when we have an active terminal handler - consume it
        handler is TerminalHandler -> {
          when (event.id) {
            MouseEvent.MOUSE_PRESSED,
            MouseEvent.MOUSE_RELEASED,
            MouseEvent.MOUSE_DRAGGED,
            MouseEvent.MOUSE_MOVED -> {
              handleMouseEvent(event)
              event.consume()
            }
          }
        }
      }
    }
  }

  // IdeEventQueue.EventDispatcher - fallback for non-terminal components
  override fun dispatch(e: AWTEvent): Boolean {
    if (e !is InputEvent || e.isConsumed) {
      return false
    }

    if (e !is MouseEvent) {
      disposeHandler() //whatever happened, let's stop scrolling
      return false
    }

    // Skip terminal handling here since it's handled by AWTEventListener
    val component = UIUtil.getDeepestComponentAt(e.component, e.x, e.y) as? JComponent
    val terminal = findTerminal(component)
    if (terminal != null) {
      // Let AWTEventListener handle terminals
      return false
    }

    return handleMouseEvent(e)
  }

  private fun handleMouseEvent(e: MouseEvent): Boolean {
    if (isToggleMouseButton(e) && e.id == MouseEvent.MOUSE_PRESSED) {
      val component = UIUtil.getDeepestComponentAt(e.component, e.x, e.y) as? JComponent
      val editor = findEditor(component)
      val terminal = findTerminal(component)
      val scrollPane = findScrollPane(component)
      disposeHandler()

      if (handler == null && editor == null && terminal == null && scrollPane == null) return false

      return when {
        editor != null -> {
          installHandler(EditorHandler(editor, e))
          true
        }

        terminal != null -> {
          installHandler(TerminalHandler(terminal, e))
          true
        }

        scrollPane != null -> {
          installHandler(ScrollPaneHandler(scrollPane, e))
          true
        }

        else -> false
      }
    }

    if (e.id == MouseEvent.MOUSE_RELEASED) {
      handler?.let { handler ->
        val result = handler.mouseReleased(e)
        if (!result) disposeHandler()
        return result
      }
    }

    if (e.id == MouseEvent.MOUSE_PRESSED) {
      return disposeHandler()
    }

    if ((e.id == MouseEvent.MOUSE_MOVED || e.id == MouseEvent.MOUSE_DRAGGED)) {
      handler?.let { handler ->
        handler.mouseMoved(e)
        return true
      }
    }

    return false
  }

  private fun findEditor(component: JComponent?): EditorEx? {
    val editorComponent = UIUtil.getParentOfType(EditorComponentImpl::class.java, component) ?: return null
    return editorComponent.editor
  }

  private fun findScrollPane(component: JComponent?): JScrollPane? {
    return UIUtil.getParentOfType(JScrollPane::class.java, component)
  }

  private fun findTerminal(component: JComponent?): JBTerminalWidget? {
    if (!FMSSettings.instance.enableTerminalHandler) return null

    return UIUtil.getParentOfType(JBTerminalWidget::class.java, component)
  }

  private fun installHandler(newHandler: Handler) {
    Disposer.register(newHandler, UiNotifyConnector.Once.installOn(newHandler.component, object : Activatable {
      override fun showNotify() = Unit
      override fun hideNotify() {
        if (handler == newHandler) {
          disposeHandler()
        }
      }
    }))

    val window = ComponentUtil.getWindow(newHandler.component)
    if (window != null) {
      val listener = object : WindowFocusListener {
        override fun windowGainedFocus(e: WindowEvent?) = Unit
        override fun windowLostFocus(e: WindowEvent?) {
          if (handler == newHandler) {
            disposeHandler()
          }
        }
      }
      window.addWindowFocusListener(listener)
      Disposer.register(newHandler) { window.removeWindowFocusListener(listener) }
    }

    handler = newHandler
    newHandler.start()
  }

  private fun disposeHandler(): Boolean {
    val handler = handler ?: return false
    val requireDispose = !handler.isDisposed
    if (requireDispose) Disposer.dispose(handler)
    this.handler = null
    return requireDispose
  }

  private inner class EditorHandler(val editor: EditorEx, startEvent: MouseEvent) :
    Handler(editor.component, startEvent) {

    override fun scrollComponent(deltaX: Int, deltaY: Int) {
      val scaledDeltaX = applyScrollScale(deltaX, isVertical = false)
      val scaledDeltaY = applyScrollScale(deltaY, isVertical = true)

      editor.scrollingModel.disableAnimation()
      editor.scrollingModel.scroll(
        editor.scrollingModel.horizontalScrollOffset + scaledDeltaX,
        editor.scrollingModel.verticalScrollOffset + scaledDeltaY
      )
      editor.scrollingModel.enableAnimation()
    }

    override fun setCursor(cursor: Cursor?) {
      editor.setCustomCursor(this, cursor)
    }
  }

  private inner class ScrollPaneHandler(val scrollPane: JScrollPane, startEvent: MouseEvent) :
    Handler(scrollPane, startEvent) {

    override fun scrollComponent(deltaX: Int, deltaY: Int) {
      val scaledDeltaX = applyScrollScale(deltaX, isVertical = false)
      val scaledDeltaY = applyScrollScale(deltaY, isVertical = true)

      val hBar = scrollPane.horizontalScrollBar
      val vBar = scrollPane.verticalScrollBar

      if (hBar != null && hBar.isVisible) hBar.value = hBar.value + scaledDeltaX
      if (vBar != null && vBar.isVisible) vBar.value = vBar.value + scaledDeltaY
    }

    @Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
    override fun setCursor(cursor: Cursor?) {
      scrollPane.cursor = cursor
    }
  }

  private inner class TerminalHandler(val terminal: JBTerminalWidget, startEvent: MouseEvent) :
    Handler(terminal.component, startEvent) {

    private var scrollBar: javax.swing.JScrollBar? = null

    init {
      // Try multiple approaches to find the scrollbar
      // Approach 1: Look for JScrollPane parent
      val scrollPane = UIUtil.getParentOfType(JScrollPane::class.java, terminal.component)
      scrollBar = scrollPane?.verticalScrollBar

      // Approach 2: Search in terminal's component hierarchy
      if (scrollBar == null) {
        scrollBar = findScrollBarInComponent(terminal.component)
      }

      // Approach 3: Try to find scrollbar via reflection
      if (scrollBar == null) {
        try {
          val method = terminal.javaClass.getMethod("getScrollBar")
          scrollBar = method.invoke(terminal) as? javax.swing.JScrollBar
        } catch (_: Exception) {
        }
      }
    }

    private fun findScrollBarInComponent(component: Component): javax.swing.JScrollBar? {
      if (component is javax.swing.JScrollBar && component.orientation == javax.swing.JScrollBar.VERTICAL) {
        return component
      }
      if (component is Container) {
        for (child in component.components) {
          val found = findScrollBarInComponent(child)
          if (found != null) return found
        }
      }
      return null
    }

    override fun scrollComponent(deltaX: Int, deltaY: Int) {
      // Terminal scrolling is typically vertical only
      if (deltaY == 0) return

      val vBar = scrollBar
      if (vBar != null) {
        // Apply terminal-specific slowdown, then apply general scroll scale
        val terminalScaledDelta = (deltaY / FMSSettings.instance.terminalScrollScale.toDouble()).toInt()
        val finalDelta = applyScrollScale(terminalScaledDelta, isVertical = true)
        vBar.value = (vBar.value + finalDelta).coerceIn(vBar.minimum, vBar.maximum)
      }
    }

    override fun setCursor(cursor: Cursor?) {
      terminal.component.cursor = cursor
    }
  }

  private abstract inner class Handler(val component: JComponent, startEvent: MouseEvent) : Disposable {

    private val settings = FMSSettings.instance
    private val scrollSinceTs: Long = System.currentTimeMillis()
    private val trailSince: Long = scrollSinceTs + settings.activationMs


    private val alarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, this)

    private var lastScrollTs: Long = scrollSinceTs
    private var lastMoveTs: Long = scrollSinceTs
    private var lastPoint: Point = RelativePoint(startEvent).getPoint(component)
    private var deltaX: Double = 0.0
    private var deltaY: Double = 0.0
    private var shiftX: Double = 0.0
    private var shiftY: Double = 0.0
    private var velocityX: Double = 0.0
    private var velocityY: Double = 0.0

    private var trail: TrailMovement? = null

    var isDisposed = false
      private set

    protected fun applyScrollScale(delta: Int, isVertical: Boolean): Int {
      val scale = settings.scrollScale / 100.0
      val inverse = if (isVertical && settings.inverseVertical || !isVertical && settings.inverseHorizontal) -1 else 1
      return (delta * scale * inverse).toInt()
    }

    fun start(): Handler {
      setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR))
      scheduleScrollEvent()
      return this
    }

    override fun dispose() {
      isDisposed = true
      setCursor(null)
    }

    fun mouseReleased(event: MouseEvent): Boolean {
      setCursor(null)
      val now = System.currentTimeMillis()
      if (now < trailSince) return false

      mouseMoved(event)
      lastScrollTs = now
      trail = when (settings.trailMode) {
        InertiaAlgorithm.EXPONENTIAL -> ExponentialSlowdownTrail(
          velocityX,
          velocityY,
          now,
          settings.decayCoeff1000,
          settings.subpixelTrail
        )

        InertiaAlgorithm.LINEAR -> LinearSlowdownTrail(velocityX, velocityY, now, settings.decayCoeff1000)
      }.withScrollMode(settings.scrollMode)

      return true
    }

    fun mouseMoved(event: MouseEvent) {
      if (isDisposed || trail != null) return
      val now = System.currentTimeMillis()
      if (now <= lastMoveTs) return

      val currentPoint = RelativePoint(event).getPoint(component)
      val moveX = lastPoint.x - currentPoint.x
      val moveY = lastPoint.y - currentPoint.y

      updateVelocity(moveX, moveY, lastMoveTs, now)

      deltaX += moveX
      deltaY += moveY
      lastMoveTs = now
      lastPoint = currentPoint
    }

    fun updateVelocity(moveX: Int, moveY: Int, lastMoveTs: Long, now: Long) {
      if (now <= lastMoveTs) return
      val deltaTs = now - lastMoveTs

      val windowStart = max(scrollSinceTs, now - settings.throwingSensivity)
      if (windowStart >= lastMoveTs) {
        velocityX = 1.0 * moveX / deltaTs
        velocityY = 1.0 * moveY / deltaTs
        return
      }

      val allWindow = now - windowStart
      val windowLeft = lastMoveTs - windowStart

      //don't even ask. I can draw this to explain that formula, but I can't explain that with text
      val lastMoveWeight = 1.0 * windowLeft * windowLeft / allWindow / allWindow
      val currentMoveWeight = 1.0 - lastMoveWeight

      velocityX = lastMoveWeight * velocityX + currentMoveWeight * moveX / deltaTs
      velocityY = lastMoveWeight * velocityY + currentMoveWeight * moveY / deltaTs
    }

    private fun doScroll() {
      if (isDisposed) return

      val from = lastScrollTs
      val to = System.currentTimeMillis()
      lastScrollTs = to

      val trail = this.trail
      if (trail != null) {
        if (trail.finished(from)) {
          Disposer.dispose(this)
          return
        }
        deltaX = trail.deltaX(from, to)
        deltaY = trail.deltaY(from, to)
      }

      val moveX = (deltaX + shiftX).toInt()
      val moveY = (deltaY + shiftY).toInt()
      scrollComponent(moveX, moveY)
      shiftX = deltaX + shiftX - moveX
      shiftY = deltaY + shiftY - moveY
      deltaX = 0.0
      deltaY = 0.0

      scheduleScrollEvent()
    }

    private fun scheduleScrollEvent() {
      alarm.addRequest(this@Handler::doScroll, 1000 / settings.fps)
    }

    protected abstract fun scrollComponent(deltaX: Int, deltaY: Int)
    protected abstract fun setCursor(cursor: Cursor?)
  }

}
