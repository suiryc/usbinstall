package usbinstall

import grizzled.slf4j.Logging
import scalafx.Includes._
import scalafx.beans.property.{BooleanProperty, ReadOnlyBooleanProperty}
import scalafx.collections.ObservableBuffer
import scalafx.geometry.{Insets, Pos}
import scalafx.scene.Node
import scalafx.scene.control.{
  Button,
  CheckBox,
  ComboBox,
  Label,
  ListView,
  SelectionMode
}
import scalafx.scene.layout.{
  ColumnConstraints,
  GridPane,
  HBox,
  Pane,
  RowConstraints,
  StackPane,
  VBox
}
import scalafx.event.subscriptions.Subscription
import suiryc.scala.io.{PathFinder, AllPassFileFilter}
import suiryc.scala.misc.{RichOptional, Units}
import suiryc.scala.sys.CommandResult
import suiryc.scala.sys.linux.{Device, DevicePartition}
import usbinstall.os.{OSInstall, OSInstallStatus, OSKind, OSSettings}
import usbinstall.settings.{InstallSettings, Settings}
import usbinstall.util.{LogArea, Util}


object Panes
  extends Logging
{

  import Stages._

  abstract class AbstractStepButton(val visible: Boolean, xdisabled: Boolean, val label: String) {
    def armed: Unit

    val disable = new BooleanProperty()
    def disable_=(v: Boolean) = disable.value = v
    val disabled: ReadOnlyBooleanProperty = disable

    disable.value = xdisabled
  }

  object NoButton extends AbstractStepButton(false, false, "") {
    override def armed = {}
  }

  class StepButton(pane: StepPane, f: => Boolean, override val label: String)
    extends AbstractStepButton(true, false, label)
  {
    override def armed = {
      if (f) pane.cancelSubscriptions
    }
  }

  class PreviousButton(pane: StepPane, f: => Boolean)
    extends StepButton(pane, f, "Previous")

  class NextButton(pane: StepPane, f: => Boolean)
    extends StepButton(pane, f, "Next")

  trait StepPane extends Pane {
    var subscriptions: List[Subscription] = Nil
    def cancelSubscriptions {
      subscriptions foreach { _.cancel }
      subscriptions = Nil
    }
    val previous: AbstractStepButton
    val next: AbstractStepButton
  }

  val devices = (PathFinder("/") / "sys" / "block" * AllPassFileFilter).get().map { block =>
    Device(block)
  }.filter { device =>
    device.ueventProps.get("DRIVER") match {
      case Some("sd") => true
      case _ => false
    }
  }.toList.foldLeft(Map.empty[String, Device]) { (devices, device) =>
    devices + (device.dev.toString() -> device)
  }

  def chooseDevice = {
    val vendorLabel = new Label {
      text = "Vendor:"
      style = "-fx-font-weight:bold"
      alignmentInParent = Pos.BASELINE_RIGHT
    }
    GridPane.setConstraints(vendorLabel, 0, 0)
    val vendorValue = new Label {
      text = ""
      alignmentInParent = Pos.BASELINE_LEFT
    }
    GridPane.setConstraints(vendorValue, 1, 0)

    val modelLabel = new Label {
      text = "Model:"
      style = "-fx-font-weight:bold"
      alignmentInParent = Pos.BASELINE_RIGHT
    }
    GridPane.setConstraints(modelLabel, 0, 1)
    val modelValue = new Label {
      text = ""
      alignmentInParent = Pos.BASELINE_LEFT
    }
    GridPane.setConstraints(modelValue, 1, 1)

    val sizeLabel = new Label {
      text = "Size:"
      style = "-fx-font-weight:bold"
      alignmentInParent = Pos.BASELINE_RIGHT
    }
    GridPane.setConstraints(sizeLabel, 0, 2)
    val sizeValue = new Label {
      text = ""
      alignmentInParent = Pos.BASELINE_LEFT
    }
    GridPane.setConstraints(sizeValue, 1, 2)

    val grid = new GridPane {
      val rowInfo = new RowConstraints(height = 20, prefHeight = 20, maxHeight = 40)
      val colInfo = new ColumnConstraints(minWidth = 50, prefWidth = 200, maxWidth = 400)

      padding = Insets(5)
      hgap = 5

      for (_ <- 0 to 2) {
        rowConstraints.add(rowInfo)
      }
      columnConstraints.add(new ColumnConstraints(minWidth = 70, prefWidth = 100, maxWidth = 200))
      columnConstraints.add(new ColumnConstraints(minWidth = 100, prefWidth = 200, maxWidth = 400))

      children ++= Seq(vendorLabel, vendorValue,
        modelLabel, modelValue,
        sizeLabel, sizeValue
      )
    }

    def resetDeviceInfo {
      vendorValue.text = ""
      modelValue.text = ""
      sizeValue.text = ""
    }

    val devicesPane = new StackPane {
      padding = Insets(5)

      val deviceList = new ListView[String] {
        //maxWidth = 200
        items = ObservableBuffer(devices.keys.toList.map(_.toString).sorted)
        selectionModel().selectionMode = SelectionMode.SINGLE
        /* Note: we need to reset the setting, because assigning the same value
         * is not seen as a value change. */
        InstallSettings.device() = None
      }

      deviceList.selectionModel().selectedItem.onChange { (_, _, newValue) =>
        devices.get(newValue) match {
          case oDevice @ Some(device) =>
            InstallSettings.device() = oDevice
            Settings.core.oses foreach { os =>
              if (os.partition().exists(_.device != device))
                os.partition() = None
            }
            vendorValue.text = device.vendor
            modelValue.text = device.model
            device.size.either match {
              case Right(size) =>
                sizeValue.text = Units.storage.toHumanReadable(size)

              case Left(e) =>
                sizeValue.text = "<unknown>"
                errorStage("Cannot get device info", Some(s"Device: ${device.dev}"), e)
                /*deviceList.selectionModel().select(-1)
                resetDeviceInfo*/
            }

          case _ =>
            deviceList.selectionModel().select(-1)
            resetDeviceInfo
        }
      }

      content = deviceList
    }

    new HBox with StepPane {
      padding = Insets(5)
      spacing = 5
      alignment = Pos.CENTER
      content = List(devicesPane, grid)

      override val previous = NoButton

      override val next = new NextButton(this, {
        InstallSettings.device() map { device =>
          USBInstall.stage = Stages.choosePartitions
          //System.gc()
          true
        } getOrElse(false)
      }) {
        disable.value = true

        /* Note: subscriptions on external object need to be cancelled for
         * this pane to be GCed. */
        subscriptions ::= InstallSettings.device.property.onChange { (_, _, device) =>
          Option(device) match {
            case Some(_) =>
              disable.value = false

            case _ =>
              disable.value = true
          }
        }
      }

    }
  }


  def choosePartitions = {
    var subscriptions_extra: List[Subscription] = Nil
    val partitions = InstallSettings.device().get.partitions.toList filter { partition =>
      val size = partition.size()
      size > 1 * 1024 * 1024
    } sortBy(_.partNumber)

    def osRow(settings: OSSettings, idx: Int): List[Node] = {
      val osLabel = new Label {
        text = settings.label
        style = "-fx-font-weight:bold"
        alignmentInParent = Pos.BASELINE_RIGHT
      }
      GridPane.setConstraints(osLabel, 0, idx)

      val osFormat = new CheckBox {
        selected = settings.format()
        alignmentInParent = Pos.BASELINE_CENTER
      }
      GridPane.setConstraints(osFormat, 1, idx)
      osFormat.selected.onChange { (_, _, selected) =>
        settings.format() = selected
      }
      /* Note: subscriptions on external object need to be cancelled for
       * this pane to be GCed. */
      subscriptions_extra ::= settings.format.property.onChange { (_, _, newValue) =>
        osFormat.selected = newValue
      }

      val osInstall = new CheckBox {
        allowIndeterminate = true
        alignmentInParent = Pos.BASELINE_CENTER
      }
      GridPane.setConstraints(osInstall, 2, idx)
      osInstall.armed.onChange { (_, _, armed) =>
        if (!armed) {
          settings.installStatus() = if (osInstall.indeterminate.value) OSInstallStatus.Installed
            else if (osInstall.selected.value) OSInstallStatus.Install
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

      /* Note: subscriptions on external object need to be cancelled for
       * this pane to be GCed. */
      subscriptions_extra ::= settings.installStatus.property.onChange { (_, _, newValue) =>
        installStatusToUI(newValue)
      }
      installStatusToUI(settings.installStatus())

      val osPartition = new ComboBox[String] {
        //maxWidth = 200
        promptText = "Partition"
        items = ObservableBuffer(partitions.map(_.dev.toString))
        alignmentInParent = Pos.BASELINE_CENTER
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
      /* Note: subscriptions on external object need to be cancelled for
       * this pane to be GCed. */
      subscriptions_extra ::= settings.partition.property.onChange { (_, _, newValue) =>
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
          //maxWidth = 200
          items = ObservableBuffer(available.map(_.getName()))
          alignmentInParent = Pos.BASELINE_LEFT
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

    val settingsPane = new GridPane {
      padding = Insets(5)
      hgap = 5

      val osFormat = new CheckBox {
        selected = false
        alignmentInParent = Pos.BASELINE_CENTER
      }
      GridPane.setConstraints(osFormat, 1, 0)
      osFormat.selected.onChange { (_, _, selected) =>
        Settings.core.oses foreach { settings =>
          settings.format() = selected
        }
      }

      val osInstall = new CheckBox {
        allowIndeterminate = true
        selected = false
        indeterminate = false
        alignmentInParent = Pos.BASELINE_CENTER
      }
      GridPane.setConstraints(osInstall, 2, 0)
      osInstall.armed.onChange { (_, _, armed) =>
        if (!armed) {
          val status = if (osInstall.indeterminate.value) OSInstallStatus.Installed
            else if (osInstall.selected.value) OSInstallStatus.Install
            else OSInstallStatus.NotInstalled
          Settings.core.oses foreach { settings =>
            settings.installStatus() = status
          }
        }
      }

      children ++= Set(osFormat, osInstall)

      /* label */
      columnConstraints.add(new ColumnConstraints(minWidth = 100, prefWidth = 150, maxWidth = 300))
      /* format */
      columnConstraints.add(new ColumnConstraints(minWidth = 20, prefWidth = 30, maxWidth = 40))
      /* install status */
      columnConstraints.add(new ColumnConstraints(minWidth = 20, prefWidth = 30, maxWidth = 40))
      /* partition */
      columnConstraints.add(new ColumnConstraints(minWidth = 100, prefWidth = 150, maxWidth = 300))
      /* iso */
      columnConstraints.add(new ColumnConstraints(minWidth = 200, prefWidth = 400, maxWidth = 800))

      Settings.core.oses.foldLeft(1) { (idx, partition) =>
        rowConstraints.add(new RowConstraints(height = 20, prefHeight = 30, maxHeight = 40))
        children ++= (osRow(partition, idx) map { n => n:javafx.scene.Node })
        idx + 1
      }
      rowConstraints.add(new RowConstraints(height = 20, prefHeight = 30, maxHeight = 40))
    }

    /* Initial partitions selection */
    Settings.core.oses.foldLeft(partitions) { (partitions, os) =>
      import RichOptional._

      if (!os.partition().isDefined &&
        (os.installStatus() != OSInstallStatus.NotInstalled) &&
        !partitions.isEmpty
      )
        os.partition() = Some(
          partitions.filter(_.size() >= os.size).headOption.getOrElse(
            /* Note: double reverse gives us the 'first' partition when more
             * than one have the same size
             */
            partitions.reverse.sortBy(_.size()).reverse.head
          )
        )

      partitions.optional[DevicePartition](os.partition(), (parts, part) => parts.filterNot(_ == part))
    }

    val partitionsPane = new GridPane {
      padding = Insets(5)
      hgap = 5
      alignment = Pos.TOP_LEFT

      partitions.foldLeft(0) { (idx, partition) =>
        val label = new Label {
          text = s"${partition.dev.toString}: ${Units.storage.toHumanReadable(partition.size())}"
        }
        GridPane.setConstraints(label, 0, idx)
        children += label

        if (partition.mounted) {
          val button = new Button {
            text = "Unmount"
            alignmentInParent = Pos.BASELINE_CENTER
          }
          GridPane.setConstraints(button, 1, idx)
          button.armed.onChange { (_, _, armed) =>
            if (!armed) {
              val CommandResult(result, stdout, stderr) = partition.umount

              if (result != 0) {
                error(s"Cannot unmount partition[${partition.dev}]: $stderr")
                Stages.errorStage("Cannot unmount partition", Some(partition.dev.toString()), stderr)
              }

              USBInstall.stage = Stages.choosePartitions
            }
          }

          children += button
        }

        rowConstraints.add(new RowConstraints(height = 20, prefHeight = 30, maxHeight = 40))

        idx + 1
      }
    }

    new HBox with StepPane {
      padding = Insets(5)
      spacing = 5
      alignment = Pos.TOP_CENTER
      content = List(settingsPane, partitionsPane)

      subscriptions :::= subscriptions_extra

      override val previous = new PreviousButton(this, {
        USBInstall.stage = Stages.chooseDevice
        true
      })

      override val next = new NextButton(this, {
        USBInstall.stage = Stages.install
        true
      }) {
        disable.value = true

        private def updateDisable {
          disable.value = Settings.core.oses.exists { settings =>
            settings.enabled && !settings.installable
          }
        }

        /* Note: subscriptions on external object need to be cancelled for
         * this pane to be GCed. */
        Settings.core.oses foreach { settings =>
          subscriptions ::= settings.installStatus.property.onChange(updateDisable)
          subscriptions ::= settings.partition.property.onChange(updateDisable)
          subscriptions ::= settings.iso.property.onChange(updateDisable)
        }
      }
    }
  }


  def install = {
    println(Settings.core.oses)

    val stepsArea = new LogArea

    val activityArea = new LogArea

    /* Note: for correct behaviour, actions on UI elements must be done inside
     * the JavaFX UI thread.
     */
    import suiryc.scala.javafx.concurrent.JFXExecutor.executor
    scala.concurrent.Future[Unit] {
      println("Test")
      (1 to 40) foreach { i =>
        activityArea.appendLine(s"Test $i")
        stepsArea.prependLine(s"Test $i")
      }
    }

    /* XXX - access lazy vals (mount points) */
    /* XXX - loop on oses to prepare/... */
    /* XXX - catch issues */
    /* XXX - how to proxy log messages (steps and info) from os install to GUI ? */
    Settings.core.oses foreach { settings =>
      if (settings.kind == OSKind.GPartedLive) {
        val os = OSInstall(settings)

        OSInstall.prepare(os)
        OSInstall.install(os)
        OSInstall.postInstall(os)
      }
    }

    new HBox with StepPane {
      padding = Insets(5)
      spacing = 5
      alignment = Pos.TOP_CENTER
      maxHeight = Double.MaxValue
      content = List(stepsArea, activityArea)

      override val previous = NoButton

      override val next = NoButton

    }
  }

}
