package usbinstall.controllers

import com.typesafe.scalalogging.StrictLogging
import java.net.URL
import java.util.ResourceBundle
import javafx.beans.property.SimpleObjectProperty
import javafx.event.ActionEvent
import javafx.fxml.{FXML, FXMLLoader, Initializable}
import javafx.geometry.{HPos, Insets, VPos}
import javafx.scene.{Node, Parent}
import javafx.scene.control._
import javafx.scene.layout.{AnchorPane, ColumnConstraints, GridPane, RowConstraints}
import javafx.scene.paint.Color
import javafx.stage.Popup
import suiryc.scala.javafx.beans.value.RichObservableValue
import suiryc.scala.javafx.beans.value.RichObservableValue._
import suiryc.scala.javafx.concurrent.JFXSystem
import suiryc.scala.javafx.scene.control.Dialogs
import suiryc.scala.misc.{RichOptional, Units}
import suiryc.scala.sys.CommandResult
import suiryc.scala.sys.linux.DevicePartition
import suiryc.scala.unused
import usbinstall.{HasEventSubscriptions, StepPane, USBInstall, UseStepPane}
import usbinstall.os._
import usbinstall.settings.InstallSettings


class ChoosePartitionsController
  extends Initializable
  with UseStepPane
  with HasEventSubscriptions
  with StrictLogging
{

  @FXML
  protected var elements: GridPane = _

  @FXML
  protected var selectAll: CheckBox = _

  @FXML
  protected var installAll: CheckBox = _

  @FXML
  protected var setupAll: CheckBox = _

  @FXML
  protected var bootloaderAll: CheckBox = _

  @FXML
  protected var autoSelectPartitions: Hyperlink = _

  @FXML
  protected var partitionsPane: AnchorPane = _

  protected val installPopup = new Popup()

  protected val infoPopup = new Popup()

  protected var stepPane: StepPane = _

  // Note: subscriptions on external object need to be cancelled for
  // pane/scene to be GCed.

  private var osLabels = Map[OSSettings, Label]()

  private val profile = InstallSettings.profile.get.get
  private val device = InstallSettings.device.get.get
  private val devicePartitions = device.partitions.toList sortBy(_.partNumber)
  private var partitions = List[DevicePartition]()
  private val partitionsStringProp = new SimpleObjectProperty(List[String]())
  updateAvailablePartitions()

  override def initialize(fxmlFileLocation: URL, resources: ResourceBundle) {
    def loadPopup(popup: Popup, file: String) {
      val loader = new FXMLLoader(getClass.getResource(file))
      val node = loader.load[Parent]()
      popup.getContent.add(node)
      ()
    }

    loadPopup(installPopup, "/fxml/choosePartitions-installPopup.fxml")
    loadPopup(infoPopup, "/fxml/choosePartitions-infoPopup.fxml")

    selectAll.setOnAction { _ =>
      profile.oses.foreach { settings =>
        settings.select.set(selectAll.isSelected)
      }
    }

    installAll.setOnAction { _ =>
      val status =
        if (installAll.isIndeterminate) {
          OSPartitionAction.Copy
        } else if (installAll.isSelected) {
          OSPartitionAction.Format
        } else {
          OSPartitionAction.None
        }
      profile.oses.foreach { settings =>
        settings.partitionAction.set(status)
      }
    }

    attachDelayedPopup(installPopup, installAll)

    setupAll.setOnAction { _ =>
      profile.oses.foreach { settings =>
        settings.setup.set(if (settings.isPartitionInstall) true else setupAll.isSelected)
      }
    }

    bootloaderAll.setOnAction { _ =>
      profile.oses.foreach { settings =>
        settings.bootloader.set(if (settings.isPartitionInstall) true else bootloaderAll.isSelected)
      }
    }

    // Initial partitions selection
    selectPartitions(redo = false)

    // Note: rows 1 (labels) and 2 (checkboxes) already used
    profile.oses.foldLeft(2) { (idx, settings) =>
      elements.getRowConstraints.add(new RowConstraints(30) { setValignment(VPos.CENTER) })
      elements.addRow(idx, osRow(settings) : _*)
      idx + 1
    }

    updatePartitionsPane()
  }

  private def updateRequirements() {
    val nok = profile.oses.foldLeft(false) { (nok, settings) =>
      var missingRequirements = List[String]()

      if (settings.isSelected) {
        if (settings.isPartitionInstall) {
          if (settings.partition.get.isEmpty) {
            missingRequirements :+= "Installation partition no set"
          }
          if (settings.isoPattern.isDefined && settings.iso.get.isEmpty) {
            missingRequirements :+= "ISO source not specified"
          }
        }

        // We just need to create an instance to check its requirements
        val osInstall = OSInstall(settings, null, () => {})
        val unmet = USBInstall.checkRequirements(osInstall.requirements())
        if (unmet.nonEmpty) {
          missingRequirements :+= unmet.mkString("Missing executable(s): ", ", ", "")
        }
      }
      val osNok = missingRequirements.nonEmpty

      osLabels.get(settings).foreach { label =>
        if (osNok) {
          val tooltip = new Tooltip(missingRequirements.mkString("\n"))
          label.setTooltip(tooltip)
          label.setTextFill(Color.RED)
          detachDelayedPopup(label)
        } else {
          label.setTooltip(null)
          label.setTextFill(Color.BLACK)
          attachDelayedPopup(infoPopup, label, updateInfoPopup(settings))
        }
      }

      nok || osNok
    }

    stepPane.next.disable = nok
  }

  override def setStepPane(stepPane: StepPane) {
    this.stepPane = stepPane

    updateRequirements()

    profile.oses.foreach { settings =>
      subscriptions ::= RichObservableValue.listen[Any](
        settings.select,
        settings.partitionAction,
        settings.setup,
        settings.bootloader,
        settings.partition,
        settings.iso
      ) {
        updateRequirements()
      }
    }
  }

  private def updatePartitionsPane() {
    val partitions = new GridPane
    partitions.setPadding(new Insets(10))
    partitions.setHgap(5)
    partitions.setVgap(3)
    for (alignment <- List(HPos.RIGHT, HPos.LEFT, HPos.LEFT, HPos.LEFT))
      partitions.getColumnConstraints.add(new ColumnConstraints { setHalignment(alignment) })

    devicePartitions.foldLeft(0) { (idx, partition) =>
      if (partition.mounted) {
        val button = new Button("Unmount")
        button.setOnAction { _ =>
          val CommandResult(result, _, stderr) = partition.umount

          if (result != 0) {
            logger.error(s"Cannot unmount partition[${partition.dev}]: $stderr")
            Dialogs.error(
              owner = Some(USBInstall.stage),
              title = Some("Cannot unmount partition"),
              headerText = Some(partition.dev.toString),
              contentText = Some(stderr)
            )
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
    ()
  }

  private def osRow(settings: OSSettings): List[Node] = {
    // Columns:
    //  - select checkbox
    //  - OS label
    //  - install checkbox
    //  - setup checkbox
    //  - bootloader checkbox
    //  - partition combobox
    //  - image combobox

    // Notes:
    // For checkbox with indeterminate state, there is no single property to
    // listen to to get all state changes. We have to set the action callback
    // which is only triggered by UI actions (not internal property changes).
    // So to react on such changes internally, it is easier to listen to *our*
    // settings property.

    // Column: select checkbox
    val osSelect = new CheckBox

    // Column: OS label
    val osLabel = new Label(settings.label)
    osLabel.setStyle("-fx-font-weight:bold")

    osLabels += settings -> osLabel

    // Column: install checkbox
    val osInstall = new CheckBox
    osInstall.setAllowIndeterminate(true)
    attachDelayedPopup(installPopup, osInstall)

    def partitionActionToUI(v: OSPartitionAction.Value) {
      v match {
        case OSPartitionAction.Copy =>
          osInstall.setIndeterminate(true)
          osInstall.setSelected(true)

        case OSPartitionAction.Format =>
          osInstall.setIndeterminate(false)
          osInstall.setSelected(true)

        case OSPartitionAction.None =>
          osInstall.setIndeterminate(false)
          osInstall.setSelected(false)
      }
    }

    // Column: setup checkbox
    val osSetup = new CheckBox
    osSetup.setSelected(settings.setup.get)
    osSetup.selectedProperty.listen { selected =>
      settings.setup.set(selected)
    }
    subscriptions ::= settings.setup.listen { newValue =>
      osSetup.setSelected(newValue)
    }

    // Column: bootloader checkbox
    val osBootloader = new CheckBox
    osBootloader.setSelected(settings.bootloader.get)
    osBootloader.selectedProperty.listen { selected =>
      settings.bootloader.set(selected)
    }
    subscriptions ::= settings.bootloader.listen { newValue =>
      osBootloader.setSelected(newValue)
    }

    // Column: partition combobox
    val osPartition = new ComboBox[String]
    osPartition.setPromptText("Partition")
    osPartition.getItems.setAll(partitionsStringProp.get:_*)
    def selectPartition() {
      settings.partition.get.foreach { partition =>
        osPartition.getSelectionModel.select(partition.dev.toString)
      }
    }
    selectPartition()
    partitionsStringProp.listen { newValue =>
      osPartition.getItems.setAll(newValue:_*)
      selectPartition()
    }
    settings.partition.get.foreach { partition =>
      osPartition.getSelectionModel.select(partition.dev.toString)
    }
    osPartition.getSelectionModel.selectedItemProperty.listen { selected =>
      // Note: first change those settings, to shorten change cyclic
      // propagation when swapping partition with another OS.
      partitions.find(_.dev.toString == selected).foreach { partition =>
        val current = settings.partition.get
        settings.partition.set(Some(partition))

        // Swap partitions if previously selected for other OS
        profile.oses.filterNot(_ == settings).find(_.partition.get.contains(partition)).foreach { os =>
          os.partition.set(current)
        }
      }
    }
    subscriptions ::= settings.partition.listen { newValue =>
      settings.partition.get.fold(osPartition.getSelectionModel.select(-1)) { partition =>
        osPartition.getSelectionModel.select(partition.dev.toString)
      }
      settings.partition.set(newValue)
    }

    // Column: image combobox
    val osISO = settings.isoPattern.map { regex =>
      val available = profile.isos.filter { path =>
        regex.pattern.matcher(path.getFileName.toString).find()
      }

      val osISO = new ComboBox[String]
      osISO.getItems.setAll(available.map(_.getFileName.toString):_*)
      osISO.getSelectionModel.selectedIndexProperty.listen { selected =>
        settings.iso.set(Some(available(selected.intValue())))
      }
      // Select outside ctor to trigger settings assignation
      osISO.getSelectionModel.select(0)

      osISO
    }

    val nodes = List(osSelect, osLabel, osInstall, osSetup, osBootloader, osPartition) ++ osISO.toList

    def settingsChanged(): Unit = {
      val isSelected = settings.isSelected
      val isPartitionInstall = settings.isPartitionInstall

      osLabel.setDisable(!isSelected)
      osInstall.setDisable(!isSelected)
      osSetup.setDisable(!isSelected || isPartitionInstall)
      osBootloader.setDisable(!isSelected || isPartitionInstall)
      osPartition.setDisable(!isSelected)
      if (isPartitionInstall) {
        osSetup.setSelected(true)
        osBootloader.setSelected(true)
      }
    }

    // OS selection: link setting to node, and disable other nodes when OS is
    // not selected.
    osSelect.selectedProperty().listen { selected =>
      settings.select.set(selected)
    }
    subscriptions ::= settings.select.listen { newValue =>
      osSelect.setSelected(newValue)
      settingsChanged()
    }
    osSelect.setSelected(settings.select.get)

    // Partition action: link setting to node, and force setup+bootloader upon
    // install.
    osInstall.setOnAction { _ =>
      settings.partitionAction.set {
        if (osInstall.isIndeterminate) {
          OSPartitionAction.Copy
        } else if (osInstall.isSelected) {
          OSPartitionAction.Format
        } else {
          OSPartitionAction.None
        }
      }
    }
    subscriptions ::= settings.partitionAction.listen { newValue =>
      partitionActionToUI(newValue)
      settingsChanged()
    }
    partitionActionToUI(settings.partitionAction.get)

    settingsChanged()

    nodes
  }

  private def availablePartitions() =
    devicePartitions.filter { partition =>
      val size = partition.size()
      !partition.mounted && (size > 1 * 1024 * 1024)
    }

  private def updateAvailablePartitions() {
    partitions = availablePartitions()
    partitionsStringProp.set(partitions.map(_.dev.toString))
  }

  private def selectPartitions(redo: Boolean) {
    profile.oses.foldLeft(partitions) { (devicePartitions, os) =>
      // First reset settings for other devices
      if (os.partition.get.exists(_.device != device))
        os.partition.set(None)

      // What we want is to the select the best fitting partition when either:
      //   - we are redoing the selection
      //   - the saved partition is not available
      //   - there is no saved partition
      // The best fitting partition is selected amongst the remaining available
      // ones, unless the OS is not to be installed, in which case it is set to
      // None.
      //
      // In particular, the saved partition of a 'not to install' OS is still
      // reserved for this OS until we redo the selection or an OS configured
      // earlier in the list needs to select a partition and finds this one as
      // fitting.
      if (redo || !os.partition.get.exists(devicePartitions.contains(_)))
        os.partition.set {
          if (devicePartitions.isEmpty || !os.isSelected) {
            None
          } else {
            Some(
              devicePartitions.find(_.size() >= os.size).getOrElse(
                // Note: double reverse gives us the 'first' partition when more
                // than one have the same size
                devicePartitions.reverse.sortBy(_.size()).reverse.head
              )
            )
          }
        }

      import RichOptional._
      devicePartitions.optional[DevicePartition](os.partition.get, (parts, part) => parts.filterNot(_ == part))
    }
    ()
  }

  def onAutoSelectPartitions(@unused event: ActionEvent) {
    autoSelectPartitions.getParent.requestFocus()
    selectPartitions(redo = true)
  }

  private def checkPopup(popup: Popup, node: Node): Boolean = {
    // Hide current popup if not attached to targeted node
    if (Option(popup.getOwnerNode).exists(_ ne node) && popup.isShowing) {
      popup.hide()
      true
    }
    else false
  }

  private def showPopup(popup: Popup, node: Node) {
    // Show popup if necessary
    if (!popup.isShowing || checkPopup(popup, node)) {
      // First display the popup right next to the targeted node. This is
      // necessary to prevent it stealing the mouse (triggering a 'mouse exited'
      // event on the targeted node while mouse is still over it).
      // Then adjust the Y position so that the middle of the node and the popup
      // are aligned.
      // Note: we need to show the popup before being able to get its height.
      val bounds = node.getBoundsInLocal
      val pos = node.localToScreen(bounds.getMaxX, bounds.getMinY + (bounds.getMaxY - bounds.getMinY) / 2)

      popup.show(node, pos.getX, pos.getY)
      popup.setAnchorY(pos.getY - popup.getHeight / 2)
    }
  }

  private def attachDelayedPopup(popup: Popup, node: Node, onShow: => Unit = {}) {
    // Note: we could use ControlsFX PopOver, but we would face the same kind
    // of issues with popup (transparent background) stealing the mouse.
    // It would either require to:
    //  - move the popup away from the node; but then the arrow pointing to
    //    the node may seem too far away
    //  - check mouse position when 'exiting' node and listen to mouse over
    //    popup to determine when we really need to hide the popup
    // It may not be worth the benefit for the kind of popup we have here.
    //
    // So stick to a simple customized JavaFX popup.
    // Helpers:
    //  - '-fx-background-radius' style to have round corners
    //  - '-fx-background-color' to set background (transparent otherwise)
    //  - '-fx-effect' to add shadow
    //  - see http://stackoverflow.com/questions/17551774/javafx-styling-pop-up-windows
    @volatile var cancellable: Option[monix.execution.Cancelable] = None

    node.setOnMouseExited { _ =>
      cancellable.foreach(_.cancel())
      cancellable = None
      popup.hide()
    }

    node.setOnMouseEntered { _ =>
      checkPopup(popup, node)

      import scala.concurrent.duration._

      cancellable = Some(JFXSystem.scheduleOnce(1.seconds) {
        cancellable = None
        onShow
        showPopup(popup, node)
      })
    }
  }

  private def detachDelayedPopup(node: Node) {
    node.setOnMouseExited { _ => }
    node.setOnMouseEntered { _ => }
  }

  private def updateInfoPopup(settings: OSSettings) {
    val root = infoPopup.getContent.get(0)
    val partitionInfo = root.lookup("#partitionInfo").asInstanceOf[Label]
    val syslinuxInfo = root.lookup("#syslinuxInfo").asInstanceOf[Label]
    val persistenceInfo = root.lookup("#persistenceInfo").asInstanceOf[Label]

    partitionInfo.setText(s"(${settings.partitionFilesystem}) ${settings.partitionLabel}")
    val syslinux = settings.syslinuxVersion.map { version =>
      val name = SyslinuxInstall.getSource(profile, version).map(_.getFileName.toString).getOrElse("n/a")
      s"($version) $name"
    }.getOrElse("n/a")
    syslinuxInfo.setText(syslinux)
    persistenceInfo.setText(settings.persistent.get.toString)
  }

}
