package com.stasmarkin.kineticscroll

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*
import com.intellij.openapi.options.UiDslUnnamedConfigurable
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.EnumComboBoxModel
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.layout.*


enum class InertiaAlgorithm { EXPONENTIAL, LINEAR, }
enum class InertiaDirection { BOTH, VERTICAL, NONE }

@Service(Service.Level.APP)
@State(name = "KineticMouseScrollSettings", storages = [Storage("other.xml")])
class FMSSettings : BaseState(), PersistentStateComponent<FMSSettings> {
  companion object {
    val instance: FMSSettings get() = ApplicationManager.getApplication().getService(FMSSettings::class.java)
  }

  override fun getState(): FMSSettings = this

  override fun loadState(state: FMSSettings) {
    copyFrom(state)
  }


  var trailMode by enum(InertiaAlgorithm.EXPONENTIAL)
  var scrollMode by enum(InertiaDirection.BOTH)
  var fps by property(60)
  var activationMs by property(100)
  var decayCoeff1000 by property(500)
  var throwingSensivity by property(140)
  var subpixelTrail by property(35)
}


class FMSConfigurable : UiDslUnnamedConfigurable.Simple() {

  override fun Panel.createContent() {
    val settings = FMSSettings.instance

    row {
      label("Refresh rate (fps):")
      slider(20, 120, 5, 20)
        .bindValue(settings::fps)
    }

    row {
      label("Min drugging time before throw activation, ms:")
      spinner(0..1000, 100)
        .bindIntValue(settings::activationMs)
    }

    row {
      label("Throwing direction:")
      comboBox(EnumComboBoxModel(InertiaDirection::class.java))
        .bindItem({ settings.scrollMode }, { settings.scrollMode = it ?: InertiaDirection.BOTH })
        .component

      label("              ")
      label("Throwing sensitivity:")
      slider(0, 300, 25, 75)
        .bindValue(settings::throwingSensivity)
    }

    lateinit var comboBox: ComboBox<InertiaAlgorithm>
    row {
      label("Slowdown algorithm:")
      comboBox = comboBox(EnumComboBoxModel(InertiaAlgorithm::class.java))
        .bindItem({ settings.trailMode }, { settings.trailMode = it ?: InertiaAlgorithm.EXPONENTIAL })
        .component

      label("     ")
      label("Decay speed:")
      slider(0, 1000, 50, 200)
        .bindValue(settings::decayCoeff1000)
    }

    row {
      label("Subpixel trail: ")
      slider(0, 100, 5, 20)
        .bindValue(settings::subpixelTrail)
    }.visibleIf(comboBox.selectedValueIs(InertiaAlgorithm.EXPONENTIAL))
  }

}
