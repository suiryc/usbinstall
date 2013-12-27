package usbinstall

import org.controlsfx.dialog.Dialogs
import scalafx.Includes._
import scalafx.application.JFXApp
import scalafx.geometry.{Insets, Pos}
import scalafx.scene.Scene
import scalafx.scene.control.Button
import scalafx.scene.layout.{
  ColumnConstraints,
  GridPane,
  RowConstraints,
  VBox
}
import usbinstall.util.RichOptional


object Stages {

  import Panes._

  def errorStage(title: String, masthead: Option[String], ex: Throwable) {
    import RichOptional._

    Dialogs.create()
      .owner(USBInstall.stage:javafx.stage.Window)
      .nativeTitleBar()
      .title(title)
      .optional[String](masthead, _.masthead(_))
      .showException(ex)
  }

  def errorStage(title: String, masthead: Option[String], error: String) {
    import RichOptional._

    Dialogs.create()
      .owner(USBInstall.stage:javafx.stage.Window)
      .nativeTitleBar()
      .title(title)
      .optional[String](masthead, _.masthead(_))
      .optional(error != "", _.message(error))
      .showError()
  }

  def stepChange(pane: StepPane) = new GridPane {
    rowConstraints.add(new RowConstraints(30))
    val columnInfo = new ColumnConstraints()
    columnInfo.setPercentWidth(50)
    columnConstraints.add(columnInfo)
    columnConstraints.add(columnInfo)

    val previous = pane.previous
    if (previous.visible) {
      val button = new Button {
        text = previous.label
        disable = previous.disabled.value
        alignmentInParent = Pos.BASELINE_CENTER
      }
      GridPane.setConstraints(button, 0, 0)
      /* Note: those subscriptions on tied objects do not need to be cancelled
       * for parent stage to be GCed. */
      previous.disabled.onChange { (_, _, disabled) =>
        button.disable = disabled
      }
      button.armed.onChange { (_, _, clicked) =>
        if (clicked) previous.armed
      }

      children += button
    }

    val next = pane.next
    if (next.visible) {
      val button = new Button {
        text = next.label
        disable = next.disabled.value
        alignmentInParent = Pos.BASELINE_CENTER
      }
      GridPane.setConstraints(button, 1, 0)
      /* Note: those subscriptions on tied objects do not need to be cancelled
       * for parent stage to be GCed. */
      next.disabled.onChange { (_, _, disabled) =>
        button.disable = disabled
      }
      button.armed.onChange { (_, _, clicked) =>
        if (clicked) next.armed
      }

      children += button
    }
  }

  def step(xtitle: String, pane: StepPane) =
    new JFXApp.PrimaryStage {
      title = xtitle
      minWidth = 400
      minHeight = 400

      scene = new Scene {
        root = new VBox {
          padding = Insets(5)
          spacing = 5
          alignment = Pos.CENTER
          content = List(pane, stepChange(pane))
        }
      }

      /*
      println(s"$this created")
      override def finalize {
        println(s"$this finalized")
      }
      */
    }

  def chooseDevice: JFXApp.PrimaryStage =
    step("Choose device", Panes.chooseDevice)

  def choosePartitions: JFXApp.PrimaryStage =
    step("Choose partitions", Panes.choosePartitions)

}
