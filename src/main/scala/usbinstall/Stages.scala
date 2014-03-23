package usbinstall

import grizzled.slf4j.Logging
import javafx.beans.property.{DoubleProperty, ReadOnlyDoubleProperty}
import javafx.beans.value.{ChangeListener, ObservableValue}
import org.controlsfx.dialog.Dialogs
import scala.language.postfixOps
import scalafx.Includes._
import scalafx.application.{JFXApp, Platform}
import scalafx.event.ActionEvent
import scalafx.geometry.{Insets, Pos}
import scalafx.scene.Scene
import scalafx.scene.control.Button
import scalafx.scene.layout.{
  ColumnConstraints,
  GridPane,
  Priority,
  RowConstraints,
  VBox
}
import scalafx.stage.WindowEvent
import scalafxml.core.{ExplicitDependencies, FXMLView}
import suiryc.scala.javafx.concurrent.JFXSystem
import suiryc.scala.misc.RichOptional


object Stages
  extends Logging
{

  protected def changeScene(title: String, scene: Scene) {
    val (stage, pos) =
      Option(USBInstall.stage) map { stage =>
        val x = stage.x()
        val y = stage.y()
        stage.minWidth = 0
        stage.minHeight = 0
        stage.hide()
        (stage, Some(x, y))
      } getOrElse {
        USBInstall.stage = new JFXApp.PrimaryStage {
          onCloseRequest = { (event: WindowEvent) =>
            close()
            Platform.exit()
          }
        }
        (USBInstall.stage, None)
      }

    stage.title = title
    stage.scene = scene
    pos foreach { pos =>
      stage.x = pos._1
      stage.y = pos._2
    }
    stage.show()

    /* After show(), the stage dimension returned by JavaFX does not seem to
     * include the platform decorations (at least under Linux). Somehow those
     * appear to be included later, once we return.
     * However changes in those dimensions can be tracked, so as a hack we can
     * still wait a bit to get them.
     * Note: it appears JavaFX do not go directly to the actual size, but
     * shrinks down before, so take that into account.
     */
    def trackMinimumDimension(label: String, stageMinProp: DoubleProperty,
      stageProp: ReadOnlyDoubleProperty, sceneProp: ReadOnlyDoubleProperty)
    {
      import scala.concurrent.duration._

      val sceneValue = sceneProp.get()

      logger trace(s"Initial minimum $label stage[${stageProp.get()}] scene[${sceneProp.get()}]")
      stageMinProp.set(stageProp.get())
      if (stageProp.get() <= sceneValue) {
        val changeListener = new ChangeListener[Number] {
          override def changed(arg0: ObservableValue[_ <: Number], arg1: Number, arg2: Number) {
            if ((sceneProp.get() == sceneValue) && (stageProp.get() > sceneValue)) {
              logger trace(s"Retained minimum $label stage[${stageProp.get()}] scene[${sceneProp.get()}]")
              stageProp.removeListener(this)
              stageMinProp.set(stageProp.get())
            }
          }
        }
        stageProp.addListener(changeListener)
        /* Make sure to unregister ourself in any case */
        JFXSystem.scheduleOnce(1.seconds) {
          stageProp.removeListener(changeListener)
        }
      }
    }

    trackMinimumDimension("width", stage.minWidthProperty(), stage.widthProperty(), stage.scene().widthProperty())
    trackMinimumDimension("height", stage.minHeightProperty(), stage.heightProperty(), stage.scene().heightProperty())
  }

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

  def stepChange(pane: Panes.StepPane) =
    FXMLView(getClass.getResource("stepChange.fxml"),
      new ExplicitDependencies(Map("stepPane" -> pane)))

  def step(pane: Panes.StepPane) =
    new Scene {
      root = new GridPane {
        alignment = Pos.TOP_CENTER

        val stepPane = stepChange(pane)

        columnConstraints.add(new ColumnConstraints() { hgrow = Priority.ALWAYS } delegate)
        rowConstraints.add(new RowConstraints() { vgrow = Priority.ALWAYS } delegate)

        GridPane.setConstraints(pane, 0, 0)
        GridPane.setConstraints(stepPane, 0, 1)

        children ++= List(pane, stepPane)
      }
    }

  def chooseDevice() {
    changeScene("Choose device", step(Panes.chooseDevice))
  }

  def choosePartitions() {
    changeScene("Choose partitions", step(Panes.choosePartitions))
  }

  def install() {
    changeScene("Install", step(Panes.install))
  }

}
