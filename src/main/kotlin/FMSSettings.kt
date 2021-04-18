package com.stasmarkin.kineticscroll

import com.intellij.openapi.components.*
import com.intellij.openapi.options.UnnamedConfigurable
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.EnumComboBoxModel
import com.intellij.ui.JBIntSpinner
import com.intellij.ui.layout.*
import javax.swing.JComponent
import javax.swing.JSlider


enum class InertiaAlgorithm { EXPONENTIAL, LINEAR, }
enum class InertiaDirection { BOTH, VERTICAL, NONE }

@State(name = "KineticMouseScrollSettings", storages = [Storage("other.xml")])
class FMSSettings : BaseState(), PersistentStateComponent<FMSSettings> {
  companion object {
    val instance: FMSSettings get() = ServiceManager.getService(FMSSettings::class.java)
  }

  override fun getState(): FMSSettings = this
  override fun loadState(state: FMSSettings) {
    copyFrom(state)
  }

  var trailMode by enum(InertiaAlgorithm.EXPONENTIAL)
  var scrollMode by enum(InertiaDirection.BOTH)
  var delayMs by property(10)
  var activationMs by property(100)
  var decayCoeff1000 by property(500)
  var velocityWindowMs by property(140)
}


class FMSConfigurable : UnnamedConfigurable {
  private val builtPanel: JComponent
  private val inertiaDirectionCombobox = ComboBox(EnumComboBoxModel(InertiaDirection::class.java))
  private val inertiaAlgorithmCombobox = ComboBox(EnumComboBoxModel(InertiaAlgorithm::class.java))
  private val refreshDelayMsSpinner = JBIntSpinner(10, 5, 200)
  private val activationMsSpinner = JBIntSpinner(100, 0, 1000)
  private val decayCoeff1000Slider = JSlider(0, 1000, 500)
  private val velocityWindowMsSlider = JSlider(0, 300, 140)

  init {
    builtPanel = panel {
      row("Refresh delay, ms:") { refreshDelayMsSpinner() }
      row("Inertia activation, ms:") { activationMsSpinner() }

      createChildRow(noGrid = true).apply {
        label("Direction:")
        inertiaDirectionCombobox()

        label("              ")
        label("Throwing sensitivity:")
        velocityWindowMsSlider()
      }

      createChildRow(noGrid = true).apply {
        label("Slowdown algorithm:")
        inertiaAlgorithmCombobox()

        label("     ")
        label("Duration:")
        decayCoeff1000Slider()
      }
    }
  }

  override fun createComponent(): JComponent = builtPanel

  @Suppress("SimplifyBooleanWithConstants")
  override fun isModified(): Boolean = with(FMSSettings.instance) {
    false
    || scrollMode != inertiaDirectionCombobox.selectedItem
    || trailMode != inertiaAlgorithmCombobox.selectedItem
    || delayMs != refreshDelayMsSpinner.number
    || activationMs != activationMsSpinner.number
    || decayCoeff1000 != decayCoeff1000Slider.value
    || velocityWindowMs != velocityWindowMsSlider.value
  }

  override fun reset() = with(FMSSettings.instance) {
    inertiaDirectionCombobox.selectedItem = scrollMode
    inertiaAlgorithmCombobox.selectedItem = trailMode
    refreshDelayMsSpinner.number = delayMs
    activationMsSpinner.number = activationMs
    decayCoeff1000Slider.value = decayCoeff1000
    velocityWindowMsSlider.value = velocityWindowMs
  }

  override fun apply() = with(FMSSettings.instance) {
    scrollMode = inertiaDirectionCombobox.selectedItem as InertiaDirection
    trailMode = inertiaAlgorithmCombobox.selectedItem as InertiaAlgorithm
    delayMs = refreshDelayMsSpinner.number
    activationMs = activationMsSpinner.number
    decayCoeff1000 = decayCoeff1000Slider.value
    velocityWindowMs = velocityWindowMsSlider.value
  }
}
