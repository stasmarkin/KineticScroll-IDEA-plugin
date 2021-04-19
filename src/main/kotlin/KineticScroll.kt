package com.stasmarkin.kineticscroll

import com.intellij.ide.AppLifecycleListener
import com.intellij.ide.IdeEventQueue
import com.intellij.ide.plugins.DynamicPluginListener
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.MouseShortcut
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.impl.EditorComponentImpl
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.ComponentUtil
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.Alarm
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.update.Activatable
import com.intellij.util.ui.update.UiNotifyConnector
import java.awt.AWTEvent
import java.awt.Cursor
import java.awt.Point
import java.awt.event.InputEvent
import java.awt.event.MouseEvent
import java.awt.event.WindowEvent
import java.awt.event.WindowFocusListener
import javax.swing.JComponent
import javax.swing.JScrollPane
import kotlin.math.max


private fun isToggleMouseButton(event: AWTEvent): Boolean {
  if (event !is MouseEvent) return false
  val shortcuts =
    KeymapManager.getInstance().activeKeymap.getShortcuts("KineticScroll.Toggle").filterIsInstance<MouseShortcut>()
  return shortcuts.contains(MouseShortcut(event.button, event.modifiersEx, 1))
}

class KineticScrollStarter : AppLifecycleListener, DynamicPluginListener {
  companion object {
    private const val ourPluginId = "com.stasmarkin.kineticscroll"
  }

  private var disposable: Disposable? = null

  private fun startListen() {
    if (disposable != null) return
    disposable = Disposer.newDisposable()
    IdeEventQueue.getInstance().addDispatcher(KineticScrollEventListener(), disposable)
  }

  private fun stopListen() {
    disposable?.let { Disposer.dispose(it) }
    disposable = null
  }

  override fun appStarting(project: Project?) = startListen()
  override fun appClosing() = stopListen()
  override fun pluginLoaded(pluginDescriptor: IdeaPluginDescriptor) {
    if (pluginDescriptor.pluginId.idString == ourPluginId) startListen()
  }
  override fun beforePluginUnload(pluginDescriptor: IdeaPluginDescriptor, isUpdate: Boolean) {
    if (pluginDescriptor.pluginId.idString == ourPluginId) stopListen()
  }
}

class KineticScrollEventListener : IdeEventQueue.EventDispatcher {
  private var handler: Handler? = null

  override fun dispatch(event: AWTEvent): Boolean {
    if (event !is InputEvent || event.isConsumed) {
      return false
    }

    if (event !is MouseEvent) {
      disposeHandler() //whatever happened, let's stop scrolling
      return false
    }

    if (isToggleMouseButton(event) && event.id == MouseEvent.MOUSE_PRESSED) {
      val component = UIUtil.getDeepestComponentAt(event.component, event.x, event.y) as? JComponent
      val editor = findEditor(component)
      val scrollPane = findScrollPane(component)
      disposeHandler()

      if (handler == null && editor == null && scrollPane == null) return false

      return when {
        editor != null -> {
          installHandler(EditorHandler(editor, event))
          true
        }
        scrollPane != null -> {
          installHandler(ScrollPaneHandler(scrollPane, event))
          true
        }
        else -> false
      }
    }

    if (event.id == MouseEvent.MOUSE_RELEASED) {
      handler?.let { handler ->
        val result = handler.mouseReleased(event)
        if (!result) disposeHandler()
        return result
      }
    }

    if (event.id == MouseEvent.MOUSE_PRESSED) {
      return disposeHandler()
    }

    if ((event.id == MouseEvent.MOUSE_MOVED || event.id == MouseEvent.MOUSE_DRAGGED)) {
      handler?.let { handler ->
        handler.mouseMoved(event)
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

  private fun installHandler(newHandler: Handler) {
    Disposer.register(newHandler, UiNotifyConnector.Once(newHandler.component, object : Activatable {
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

  private inner class EditorHandler constructor(val editor: EditorEx, startEvent: MouseEvent) :
    Handler(editor.component, startEvent) {

    override fun scrollComponent(deltaX: Int, deltaY: Int) {
      editor.scrollingModel.disableAnimation()
      editor.scrollingModel.scroll(
        editor.scrollingModel.horizontalScrollOffset + deltaX,
        editor.scrollingModel.verticalScrollOffset + deltaY
      )
      editor.scrollingModel.enableAnimation()
    }

    override fun setCursor(cursor: Cursor?) {
      editor.setCustomCursor(this, cursor)
    }
  }

  private inner class ScrollPaneHandler constructor(val scrollPane: JScrollPane, startEvent: MouseEvent) :
    Handler(scrollPane, startEvent) {

    override fun scrollComponent(deltaX: Int, deltaY: Int) {
      val hBar = scrollPane.horizontalScrollBar
      val vBar = scrollPane.verticalScrollBar

      if (hBar != null && hBar.isVisible) hBar.value = hBar.value + deltaX
      if (vBar != null && vBar.isVisible) vBar.value = vBar.value + deltaY
    }

    @Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
    override fun setCursor(cursor: Cursor?) {
      scrollPane.cursor = cursor
    }
  }

  private abstract inner class Handler constructor(val component: JComponent, startEvent: MouseEvent) : Disposable {

    private val settings = FMSSettings.instance
    private val delayMs = settings.delayMs
    private val decayCoeff1000 = settings.decayCoeff1000
    private val velocityWindowMs = settings.velocityWindowMs
    private val scrollMode = settings.scrollMode
    private val trailMode = settings.trailMode
    private val scrollSinceTs: Long = System.currentTimeMillis()
    private val trailSince: Long = scrollSinceTs + settings.activationMs


    private val alarm = Alarm()

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

    fun start(): Handler {
      setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR))
      scheduleScrollEvent()
      return this
    }

    override fun dispose() {
      isDisposed = true
      setCursor(null)
      Disposer.dispose(alarm)
    }

    fun mouseReleased(event: MouseEvent): Boolean {
      setCursor(null)
      val now = System.currentTimeMillis()
      if (now < trailSince) return false

      mouseMoved(event)
      lastScrollTs = now
      trail = when (trailMode) {
        InertiaAlgorithm.EXPONENTIAL -> ExponentialSlowdownTrail(velocityX, velocityY, now, decayCoeff1000)
        InertiaAlgorithm.LINEAR -> LinearSlowdownTrail(velocityX, velocityY, now, decayCoeff1000)
      }.withScrollMode(scrollMode)

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

      val windowStart = max(scrollSinceTs, now - velocityWindowMs)
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
      alarm.addRequest(this@Handler::doScroll, delayMs)
    }

    protected abstract fun scrollComponent(deltaX: Int, deltaY: Int)
    protected abstract fun setCursor(cursor: Cursor?)
  }

}
