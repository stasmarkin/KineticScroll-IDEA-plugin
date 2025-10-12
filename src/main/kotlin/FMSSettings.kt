package com.stasmarkin.kineticscroll

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*
import com.intellij.openapi.options.UiDslUnnamedConfigurable
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.EnumComboBoxModel
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.layout.selected
import com.intellij.ui.layout.selectedValueIs


enum class InertiaAlgorithm { EXPONENTIAL, LINEAR, }
enum class InertiaDirection { BOTH, VERTICAL, NONE }

// Mapping functions for centered scroll multiplier slider
// Slider range: 0-100, where 50 = x1.0
object ScrollMultiplierMapper {
    // Maps slider position (0-100) to scroll scale percentage (20-500)
    fun sliderToScale(sliderValue: Int): Int {
        return when {
            sliderValue < 50 -> {
                // Left half: 0->20%, 50->100%
                // Linear interpolation from x0.2 to x1.0
                20 + ((sliderValue / 50.0) * 80).toInt()
            }

            else -> {
                // Right half: 50->100%, 100->500%
                // Linear interpolation from x1.0 to x5.0
                100 + (((sliderValue - 50) / 50.0) * 400).toInt()
            }
        }
    }

    // Maps scroll scale percentage (20-500) to slider position (0-100)
    fun scaleToSlider(scaleValue: Int): Int {
        return when {
            scaleValue < 100 -> {
                // Map 20-100 to slider 0-50
                ((scaleValue - 20) / 80.0 * 50).toInt()
            }

            else -> {
                // Map 100-500 to slider 50-100
                50 + (((scaleValue - 100) / 400.0) * 50).toInt()
            }
        }
    }
}

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
    var enableTerminalHandler by property(true)
    var terminalScrollScale by property(15)
    var scrollScale by property(100) // stored as percentage (100 = 1.0x, 200 = 2.0x, 20 = 0.2x)
    var inverseScrolling by property(false)
}


class FMSConfigurable : UiDslUnnamedConfigurable.Simple() {

    override fun Panel.createContent() {
        val settings = FMSSettings.instance

        row {
            label("Refresh rate (fps):")
            slider(20, 120, 5, 20)
                .bindValue(settings::fps)
        }

        separator()

        row {
            label("Scroll speed multiplier:")
            slider(0, 100, 5, 50)
                .applyToComponent {
                    paintTicks = true
                    paintLabels = true
                    snapToTicks = false

                    // Set custom labels at specific positions (slider 0-100, center at 50 = x1)
                    val labels = java.util.Hashtable<Int, javax.swing.JLabel>()
                    labels[0] = javax.swing.JLabel("x0.2")
                    labels[25] = javax.swing.JLabel("x0.5")
                    labels[50] = javax.swing.JLabel("x1")
                    labels[75] = javax.swing.JLabel("x2")
                    labels[100] = javax.swing.JLabel("x5")
                    labelTable = labels

                    // Initialize slider from stored scale value
                    value = ScrollMultiplierMapper.scaleToSlider(settings.scrollScale)

                    // Add custom change listener for sticky behavior at x1.0 (slider position 50)
                    addChangeListener {
                        val sliderValue = this.value
                        val snapThreshold = 3 // snap within ±3 slider units of center

                        // Snap to center (x1.0)
                        if (sliderValue in (50 - snapThreshold)..(50 + snapThreshold) && sliderValue != 50) {
                            this.value = 50
                        }

                        // Update the actual scale value
                        settings.scrollScale = ScrollMultiplierMapper.sliderToScale(this.value)
                    }
                }
                .comment("Adjusts scroll speed (x0.20 to x5.00, sticky at x1.00)")
        }

        row {
            checkBox("Inverse scrolling direction")
                .bindSelected(settings::inverseScrolling)
                .comment("Reverses the scrolling direction")
        }

        separator()

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
                .comment(
                    "Higher value = more noticeable one pixel movements in the end of scroll<br>" +
                        "Lower value = more abrupt stop"
                )
        }.visibleIf(comboBox.selectedValueIs(InertiaAlgorithm.EXPONENTIAL))

        separator()

        lateinit var terminalCheckbox: JBCheckBox
        row {
            terminalCheckbox = checkBox("Enable kinetic scrolling in Terminal window")
                .bindSelected(settings::enableTerminalHandler)
                .component
        }

        row {
            label("Terminal scroll slowdown:")
            slider(1, 30, 1, 15)
                .bindValue(settings::terminalScrollScale)
                .comment("Higher value = slower scrolling")
        }.visibleIf(terminalCheckbox.selected)
    }

}
