package usbinstall

import grizzled.slf4j.Logging
import java.net.URL
import java.util.ResourceBundle
import javafx.beans.property.SimpleObjectProperty
import javafx.event.ActionEvent
import javafx.fxml.{FXML, Initializable}
import javafx.geometry.{HPos, Insets, VPos}
import javafx.scene.Node
import javafx.scene.control.{Button, CheckBox, ComboBox, Hyperlink, Label}
import javafx.scene.layout.{
  AnchorPane,
  ColumnConstraints,
  GridPane,
  RowConstraints
}
import suiryc.scala.javafx.beans.property.RichReadOnlyProperty._
import suiryc.scala.javafx.event.Subscription
import suiryc.scala.javafx.event.EventHandler._
import suiryc.scala.misc.{RichOptional, Units}
import suiryc.scala.sys.CommandResult
import suiryc.scala.sys.linux.DevicePartition
import usbinstall.os.{OSInstall, OSInstallStatus, OSKind, OSSettings}
import usbinstall.settings.{InstallSettings, Settings}


class ChoosePartitionsController
  extends Initializable
  with HasEventSubscriptions
  with Logging
{

  @FXML
  protected var elements: GridPane = _

  @FXML
  protected var formatAll: CheckBox = _

  @FXML
  protected var installAll: CheckBox = _

  @FXML
  protected var autoSelectPartitions: Hyperlink = _

  @FXML
  protected var partitionsPane: AnchorPane = _

  /* Note: subscriptions on external object need to be cancelled for
   * pane/scene to be GCed. */

  protected val device = InstallSettings.device.get.get
  protected val devicePartitions = device.partitions.toList sortBy(_.partNumber)
  protected var partitions = List[DevicePartition]()
  protected val partitionsStringProp = new SimpleObjectProperty(List[String]())
  updateAvailablePartitions()

  override def initialize(fxmlFileLocation: URL, resources: ResourceBundle) {
    formatAll.setOnAction { event: ActionEvent =>
      Settings.core.oses foreach { settings =>
        settings.format() = formatAll.isSelected
      }
    }

    installAll.setOnAction { event: ActionEvent =>
      val status = if (installAll.isIndeterminate) OSInstallStatus.Installed
        else if (installAll.isSelected) OSInstallStatus.Install
        else OSInstallStatus.NotInstalled
      Settings.core.oses foreach { settings =>
        settings.installStatus() = status
      }
    }

    /* Initial partitions selection */
    selectPartitions(false)

    /* Note: rows 1 (labels) and 2 (checkboxes) already used */
    Settings.core.oses.foldLeft(2) { (idx, partition) =>
      elements.getRowConstraints().add(new RowConstraints(30) { setValignment(VPos.CENTER) })
      elements.addRow(idx, osRow(partition) : _*)
      idx + 1
    }

    updatePartitionsPane()
  }

  private def updatePartitionsPane() {
    val partitions = new GridPane
    partitions.setPadding(new Insets(10))
    partitions.setHgap(5)
    partitions.setVgap(3)
    for (alignement <- List(HPos.RIGHT, HPos.LEFT, HPos.LEFT, HPos.LEFT))
      partitions.getColumnConstraints.add(new ColumnConstraints { setHalignment(alignement) })

    devicePartitions.foldLeft(0) { (idx, partition) =>
      if (partition.mounted) {
        val button = new Button("Unmount")
        button.setOnAction { event: ActionEvent =>
          val CommandResult(result, stdout, stderr) = partition.umount

          if (result != 0) {
            error(s"Cannot unmount partition[${partition.dev}]: $stderr")
            Stages.errorStage("Cannot unmount partition", Some(partition.dev.toString()), stderr)
          }
          updateAvailablePartitions()
          updatePartitionsPane()
        }
        partitions.add(button, 0, idx)
      }

      val name = new Label(partition.dev.toString)
      name.setStyle("-fx-font-weight:bold")
      partitions.add(name, 1, idx)

      val size = new Label(Units.storage.toHumanReadable(partition.size()))
      partitions.add(size, 2, idx)

      val label = new Label(partition.label.fold(_ => "", label => if (label == "") label else s"($label)"))
      partitions.add(label, 3, idx)

      partitions.getRowConstraints.add(new RowConstraints(30, 30, 40) { setValignment(VPos.CENTER) })

      idx + 1
    }
    partitionsPane.getChildren.setAll(partitions)
  }

  private def osRow(settings: OSSettings): List[Node] = {
    val osLabel = new Label(settings.label)
    osLabel.setStyle("-fx-font-weight:bold")

    val osFormat = new CheckBox
    osFormat.setSelected(settings.format())
    osFormat.selectedProperty.listen { selected =>
      settings.format() = selected
    }
    subscriptions ::= settings.format.listen { newValue =>
      osFormat.setSelected(newValue)
    }

    val osInstall = new CheckBox
    osInstall.setAllowIndeterminate(true)
    osInstall.setOnAction { event: ActionEvent =>
      settings.installStatus() = if (osInstall.isIndeterminate) OSInstallStatus.Installed
        else if (osInstall.isSelected) OSInstallStatus.Install
        else OSInstallStatus.NotInstalled
    }

    def installStatusToUI(v: OSInstallStatus.Value) {
      v match {
        case OSInstallStatus.Installed =>
          osInstall.setSelected(true)
          osInstall.setIndeterminate(true)

        case OSInstallStatus.Install =>
          osInstall.setSelected(true)
          osInstall.setIndeterminate(false)

        case OSInstallStatus.NotInstalled =>
          osInstall.setSelected(false)
          osInstall.setIndeterminate(false)
      }
      osFormat.setDisable(v != OSInstallStatus.Install)
    }

    subscriptions ::= settings.installStatus.listen { newValue =>
      installStatusToUI(newValue)
    }
    installStatusToUI(settings.installStatus())

    val osPartition = new ComboBox[String]
    osPartition.setPromptText("Partition")
    osPartition.getItems().setAll(partitionsStringProp.get:_*)
    def selectPartition() {
      settings.partition.get foreach { partition =>
        osPartition.getSelectionModel().select(partition.dev.toString())
      }
    }
    selectPartition()
    partitionsStringProp.listen { newValue =>
      osPartition.getItems().setAll(newValue:_*)
      selectPartition()
    }
    settings.partition.get foreach { partition =>
      osPartition.getSelectionModel().select(partition.dev.toString())
    }
    osPartition.getSelectionModel().selectedItemProperty.listen { selected =>
      /* Note: first change those settings, to shorten change cyclic
       * propagation when swapping partition with another OS.
       */
      partitions.find(_.dev.toString == selected) foreach { partition =>
        val current = settings.partition.get
        settings.partition.set(Some(partition))

        /* Swap partitions if previously selected for other OS */
        Settings.core.oses.filterNot(_ == settings).find(_.partition.get == Some(partition)) foreach { os =>
          os.partition.set(current)
        }
      }
    }
    subscriptions ::= settings.partition.listen { newValue =>
      settings.partition.get.fold(osPartition.getSelectionModel().select(-1)) { partition =>
        osPartition.getSelectionModel().select(partition.dev.toString)
      }
      settings.partition.set(newValue)
    }

    val osISO = settings.isoPattern map { regex =>
      val available = Settings.core.isos.filter { path =>
        regex.pattern.matcher(path.getFileName.toString).find()
      }

      val osISO = new ComboBox[String]
      osISO.getItems().setAll(available.map(_.getFileName.toString):_*)
      osISO.getSelectionModel().selectedIndexProperty.listen { selected =>
        settings.iso.set(Some(available(selected.intValue())))
      }
      /* Select outside ctor to trigger settings assignation */
      osISO.getSelectionModel().select(0)

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
    partitionsStringProp.set(partitions.map(_.dev.toString))
  }

  private def selectPartitions(redo: Boolean) {
    Settings.core.oses.foldLeft(partitions) { (devicePartitions, os) =>
      /* First reset settings for other devices */
      if (os.partition.get.exists(_.device != device))
        os.partition.set(None)

      /* What we want is to the select the best fitting partition when either:
       *   - we are redoing the selection
       *   - the saved partition is not available
       *   - there is no saved partition
       * The best fitting partition is selected amongst the remaining available
       * ones, unless the OS is not to be installed, in which case it is set to
       * None.
       *
       * In particular, the saved partition of a 'not to install' OS is still
       * reserved for this OS until we redo the selection or an OS configured
       * earlier in the list needs to select a partition and finds this one as
       * fitting.
       */
      if (redo || !os.partition.get.find(devicePartitions.contains(_)).isDefined)
        os.partition.set {
          if (devicePartitions.isEmpty || (os.installStatus() == OSInstallStatus.NotInstalled))
            None
          else Some(
            devicePartitions.filter(_.size() >= os.size).headOption.getOrElse(
              /* Note: double reverse gives us the 'first' partition when more
               * than one have the same size
               */
              devicePartitions.reverse.sortBy(_.size()).reverse.head
            )
          )
        }

      import RichOptional._
      devicePartitions.optional[DevicePartition](os.partition.get, (parts, part) => parts.filterNot(_ == part))
    }
  }

  def onAutoSelectPartitions(event: ActionEvent) {
    autoSelectPartitions.getParent().requestFocus()
    selectPartitions(true)
  }

}
