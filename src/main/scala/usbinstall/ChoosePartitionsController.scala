package usbinstall

import grizzled.slf4j.Logging
import scala.language.postfixOps
import scalafx.Includes._
import scalafx.collections.ObservableBuffer
import scalafx.event.subscriptions.Subscription
import scalafx.geometry.{HPos, Insets, VPos}
import scalafx.scene.Node
import scalafx.scene.control.{Button, CheckBox, ComboBox, Label}
import scalafx.scene.layout.{
  AnchorPane,
  ColumnConstraints,
  GridPane,
  RowConstraints
}
import scalafxml.core.macros.sfxml
import usbinstall.os.{OSInstall, OSInstallStatus, OSKind, OSSettings}
import usbinstall.settings.{InstallSettings, Settings}
import suiryc.scala.misc.{RichOptional, Units}
import suiryc.scala.sys.CommandResult
import suiryc.scala.sys.linux.DevicePartition
import scalafx.event.ActionEvent


/* Note: ScalaFXML macro fails when extending more than one trait? */
trait ChoosePartitionsControllerTraits extends HasEventSubscriptions with Logging

@sfxml
class ChoosePartitionsController(
  private val elements: GridPane,
  private val formatAll: CheckBox,
  private val installAll: CheckBox,
  private val partitionsPane: AnchorPane
) extends ChoosePartitionsControllerTraits
{

  /* Note: subscriptions on external object need to be cancelled for
   * pane/scene to be GCed. */
  var subscriptions: List[Subscription] = Nil

  val devicePartitions = InstallSettings.device().get.partitions.toList filter { partition =>
    val size = partition.size()
    size > 1 * 1024 * 1024
  } sortBy(_.partNumber)

  formatAll.onAction = { e: ActionEvent =>
    Settings.core.oses foreach { settings =>
      settings.format() = formatAll.selected.value
    }
  }

  installAll.onAction = { e: ActionEvent =>
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
    elements.children ++= (osRow(partition, idx) map { n => n:javafx.scene.Node })
    idx + 1
  }

  /* Initial partitions selection */
  Settings.core.oses.foldLeft(devicePartitions) { (devicePartitions, os) =>
    import RichOptional._

    if (!os.partition().isDefined &&
      (os.installStatus() != OSInstallStatus.NotInstalled) &&
      !devicePartitions.isEmpty
    )
      os.partition() = Some(
        devicePartitions.filter(_.size() >= os.size).headOption.getOrElse(
          /* Note: double reverse gives us the 'first' partition when more
           * than one have the same size
           */
          devicePartitions.reverse.sortBy(_.size()).reverse.head
        )
      )

    devicePartitions.optional[DevicePartition](os.partition(), (parts, part) => parts.filterNot(_ == part))
  }

  val partitions = new GridPane {
    padding = Insets(10)
    hgap = 5
    vgap = 3

    columnConstraints.add(new ColumnConstraints() { halignment = HPos.RIGHT } delegate)
    columnConstraints.add(new ColumnConstraints() { halignment = HPos.LEFT } delegate)
    columnConstraints.add(new ColumnConstraints() { halignment = HPos.LEFT } delegate)
  }

  devicePartitions.foldLeft(0) { (idx, partition) =>
    if (partition.mounted) {
      val button = new Button {
        text = "Unmount"
        onAction = { e: ActionEvent =>
          val CommandResult(result, stdout, stderr) = partition.umount

          if (result != 0) {
            error(s"Cannot unmount partition[${partition.dev}]: $stderr")
            Stages.errorStage("Cannot unmount partition", Some(partition.dev.toString()), stderr)
          }

          Stages.choosePartitions
        }
      }
      GridPane.setConstraints(button, 0, idx)

      partitions.children += button
    }

    val label = new Label {
      text = partition.dev.toString
      style = "-fx-font-weight:bold"
    }
    GridPane.setConstraints(label, 1, idx)
    partitions.children += label

    val size = new Label {
      text = Units.storage.toHumanReadable(partition.size())
    }
    GridPane.setConstraints(size, 2, idx)
    partitions.children += size

    partitions.rowConstraints.add(new RowConstraints(minHeight = 30, prefHeight = 30, maxHeight = 40) { valignment = VPos.CENTER } delegate)

    idx + 1
  }
  partitionsPane.content = partitions

  def getSubscriptions(): List[Subscription] = subscriptions

  def osRow(settings: OSSettings, idx: Int): List[Node] = {
    val osLabel = new Label {
      text = settings.label
      style = "-fx-font-weight:bold"
    }
    GridPane.setConstraints(osLabel, 0, idx)

    val osFormat = new CheckBox {
      selected = settings.format()
    }
    GridPane.setConstraints(osFormat, 1, idx)
    osFormat.selected.onChange { (_, _, selected) =>
      settings.format() = selected
    }
    subscriptions ::= settings.format.property.onChange { (_, _, newValue) =>
      osFormat.selected = newValue
    }

    val osInstall = new CheckBox {
      allowIndeterminate = true
      onAction = { e: ActionEvent =>
        settings.installStatus() = if (indeterminate.value) OSInstallStatus.Installed
          else if (selected.value) OSInstallStatus.Install
          else OSInstallStatus.NotInstalled
      }
    }
    GridPane.setConstraints(osInstall, 2, idx)

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

    subscriptions ::= settings.installStatus.property.onChange { (_, _, newValue) =>
      installStatusToUI(newValue)
    }
    installStatusToUI(settings.installStatus())

    val osPartition = new ComboBox[String] {
      promptText = "Partition"
      items = ObservableBuffer(devicePartitions.map(_.dev.toString))
    }
    settings.partition() foreach { partition =>
      osPartition.selectionModel().select(partition.dev.toString())
    }
    osPartition.selectionModel().selectedItem.onChange { (_, _, selected) =>
      /* Note: first change those settings, to shorten change cyclic
       * propagation when swapping partition with another OS.
       */
      devicePartitions.find(_.dev.toString == selected) foreach { partition =>
        val current = settings.partition()
        settings.partition() = Some(partition)

        /* Swap partitions if previously selected for other OS */
        Settings.core.oses.filterNot(_ == settings).find(_.partition() == Some(partition)) foreach { os =>
          os.partition() = current
        }
      }
    }
    subscriptions ::= settings.partition.property.onChange { (_, _, newValue) =>
      settings.partition().fold(osPartition.selectionModel().select(-1)) { partition =>
        osPartition.selectionModel().select(partition.dev.toString)
      }
      settings.partition() = newValue
    }
    GridPane.setConstraints(osPartition, 3, idx)

    val osISO = settings.isoPattern map { regex =>
      val available = Settings.core.isos.filter { file =>
        regex.pattern.matcher(file.getName()).find()
      }

      val osISO = new ComboBox[String] {
        items = ObservableBuffer(available.map(_.getName()))
      }
      GridPane.setConstraints(osISO, 4, idx)
      osISO.selectionModel().selectedIndex.onChange { (_, _, selected) =>
        settings.iso() = Some(available(selected.intValue()))
      }
      /* Select outside ctor to trigger settings assignation */
      osISO.selectionModel().select(0)

      osISO
    }

    osLabel :: osFormat :: osInstall :: osPartition :: Nil ++ osISO
  }

}
