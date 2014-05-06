package usbinstall

import grizzled.slf4j.Logging
import scala.language.postfixOps
import scalafx.Includes._
import scalafx.beans.property.ObjectProperty
import scalafx.collections.ObservableBuffer
import scalafx.event.ActionEvent
import scalafx.event.subscriptions.Subscription
import scalafx.geometry.{HPos, Insets, VPos}
import scalafx.scene.Node
import scalafx.scene.control.{Button, CheckBox, ComboBox, Hyperlink, Label}
import scalafx.scene.layout.{
  AnchorPane,
  ColumnConstraints,
  GridPane,
  RowConstraints
}
import scalafxml.core.macros.sfxml
import suiryc.scala.misc.{RichOptional, Units}
import suiryc.scala.sys.CommandResult
import suiryc.scala.sys.linux.DevicePartition
import usbinstall.os.{OSInstall, OSInstallStatus, OSKind, OSSettings}
import usbinstall.settings.{InstallSettings, Settings}


@sfxml
class ChoosePartitionsController(
  private val elements: GridPane,
  private val formatAll: CheckBox,
  private val installAll: CheckBox,
  private val autoSelectPartitions: Hyperlink,
  private val partitionsPane: AnchorPane
) extends HasEventSubscriptions with Logging
{

  /* Note: subscriptions on external object need to be cancelled for
   * pane/scene to be GCed. */
  var subscriptions: List[Subscription] = Nil

  def getSubscriptions(): List[Subscription] = subscriptions

  val devicePartitions = InstallSettings.device().get.partitions.toList sortBy(_.partNumber)
  var partitions = List[DevicePartition]()
  val partitionsStringProp = ObjectProperty(ObservableBuffer[String]())
  updateAvailablePartitions()

  formatAll.onAction = { event: ActionEvent =>
    Settings.core.oses foreach { settings =>
      settings.format() = formatAll.selected.value
    }
  }

  installAll.onAction = { event: ActionEvent =>
    val status = if (installAll.indeterminate.value) OSInstallStatus.Installed
      else if (installAll.selected.value) OSInstallStatus.Install
      else OSInstallStatus.NotInstalled
    Settings.core.oses foreach { settings =>
      settings.installStatus() = status
    }
  }

  /* Note: rows 1 (labels) and 2 (checkboxes) already used */
  Settings.core.oses.foldLeft(2) { (idx, partition) =>
    elements.rowConstraints.add(new RowConstraints(minHeight = 30, prefHeight = 30, maxHeight = 30) { valignment = VPos.CENTER } delegate)
    elements.addRow(idx, osRow(partition) map { n => n:javafx.scene.Node } : _*)
    idx + 1
  }

  /* Initial partitions selection */
  selectPartitions(false)

  private def updatePartitionsPane() {
    val partitions = new GridPane {
      padding = Insets(10)
      hgap = 5
      vgap = 3

      for (alignement <- List(HPos.RIGHT, HPos.LEFT, HPos.LEFT, HPos.LEFT))
        columnConstraints.add(new ColumnConstraints() { halignment = alignement } delegate)
    }

    devicePartitions.foldLeft(0) { (idx, partition) =>
      if (partition.mounted) {
        val button = new Button {
          text = "Unmount"
          onAction = { event: ActionEvent =>
            val CommandResult(result, stdout, stderr) = partition.umount

            if (result != 0) {
              error(s"Cannot unmount partition[${partition.dev}]: $stderr")
              Stages.errorStage("Cannot unmount partition", Some(partition.dev.toString()), stderr)
            }
            updateAvailablePartitions()
            updatePartitionsPane()
          }
        }
        partitions.add(button, 0, idx)
      }

      val name = new Label {
        text = partition.dev.toString
        style = "-fx-font-weight:bold"
      }
      partitions.add(name, 1, idx)

      val size = new Label {
        text = Units.storage.toHumanReadable(partition.size())
      }
      partitions.add(size, 2, idx)

      val label = new Label {
        text = partition.label.fold(_ => "", label => if (label == "") label else s"($label)")
      }
      partitions.add(label, 3, idx)

      partitions.rowConstraints.add(new RowConstraints(minHeight = 30, prefHeight = 30, maxHeight = 40) { valignment = VPos.CENTER } delegate)

      idx + 1
    }
    partitionsPane.content = partitions
  }
  updatePartitionsPane()

  private def osRow(settings: OSSettings): List[Node] = {
    val osLabel = new Label {
      text = settings.label
      style = "-fx-font-weight:bold"
    }

    val osFormat = new CheckBox {
      selected = settings.format()
    }
    osFormat.selected.onChange { (_, _, selected) =>
      settings.format() = selected
    }
    subscriptions ::= settings.format.onChange { (_, _, newValue) =>
      osFormat.selected = newValue
    }

    val osInstall = new CheckBox {
      allowIndeterminate = true
      onAction = { event: ActionEvent =>
        settings.installStatus() = if (indeterminate.value) OSInstallStatus.Installed
          else if (selected.value) OSInstallStatus.Install
          else OSInstallStatus.NotInstalled
      }
    }

    def installStatusToUI(v: OSInstallStatus.Value) {
      v match {
        case OSInstallStatus.Installed =>
          osInstall.selected = true
          osInstall.indeterminate = true

        case OSInstallStatus.Install =>
          osInstall.selected = true
          osInstall.indeterminate = false

        case OSInstallStatus.NotInstalled =>
          osInstall.selected = false
          osInstall.indeterminate = false
      }
      osFormat.disable = (v != OSInstallStatus.Install)
    }

    subscriptions ::= settings.installStatus.onChange { (_, _, newValue) =>
      installStatusToUI(newValue)
    }
    installStatusToUI(settings.installStatus())

    val osPartition = new ComboBox[String] {
      promptText = "Partition"
      items = partitionsStringProp()
    }
    def selectPartition() {
      settings.partition() foreach { partition =>
        osPartition.selectionModel().select(partition.dev.toString())
      }
    }
    selectPartition()
    partitionsStringProp.onChange { (_, _, newValue) =>
      osPartition.items = newValue
      selectPartition()
    }
    settings.partition() foreach { partition =>
      osPartition.selectionModel().select(partition.dev.toString())
    }
    osPartition.selectionModel().selectedItem.onChange { (_, _, selected) =>
      /* Note: first change those settings, to shorten change cyclic
       * propagation when swapping partition with another OS.
       */
      partitions.find(_.dev.toString == selected) foreach { partition =>
        val current = settings.partition()
        settings.partition() = Some(partition)

        /* Swap partitions if previously selected for other OS */
        Settings.core.oses.filterNot(_ == settings).find(_.partition() == Some(partition)) foreach { os =>
          os.partition() = current
        }
      }
    }
    subscriptions ::= settings.partition.onChange { (_, _, newValue) =>
      settings.partition().fold(osPartition.selectionModel().select(-1)) { partition =>
        osPartition.selectionModel().select(partition.dev.toString)
      }
      settings.partition() = newValue
    }

    val osISO = settings.isoPattern map { regex =>
      val available = Settings.core.isos.filter { file =>
        regex.pattern.matcher(file.getName()).find()
      }

      val osISO = new ComboBox[String] {
        items = ObservableBuffer(available.map(_.getName()))
      }
      osISO.selectionModel().selectedIndex.onChange { (_, _, selected) =>
        settings.iso() = Some(available(selected.intValue()))
      }
      /* Select outside ctor to trigger settings assignation */
      osISO.selectionModel().select(0)

      osISO
    }

    osLabel :: osFormat :: osInstall :: osPartition :: Nil ++ osISO
  }

  private def availablePartitions() =
    devicePartitions filter { partition =>
      val size = partition.size()
      !partition.mounted && (size > 1 * 1024 * 1024)
    }

  private def updateAvailablePartitions() {
    partitions = availablePartitions()
    partitionsStringProp() = ObservableBuffer(partitions.map(_.dev.toString))
  }

  private def selectPartitions(redo: Boolean) {
    Settings.core.oses.foldLeft(partitions) { (devicePartitions, os) =>
      if ((redo || !os.partition().find(devicePartitions.contains(_)).isDefined) &&
        (os.installStatus() != OSInstallStatus.NotInstalled))
        os.partition() =
          if (devicePartitions.isEmpty) None
          else Some(
            devicePartitions.filter(_.size() >= os.size).headOption.getOrElse(
              /* Note: double reverse gives us the 'first' partition when more
               * than one have the same size
               */
              devicePartitions.reverse.sortBy(_.size()).reverse.head
            )
          )

      import RichOptional._
      devicePartitions.optional[DevicePartition](os.partition(), (parts, part) => parts.filterNot(_ == part))
    }
  }

  def onAutoSelectPartitions(event: ActionEvent) {
    autoSelectPartitions.parent().requestFocus()
    selectPartitions(true)
  }

}
