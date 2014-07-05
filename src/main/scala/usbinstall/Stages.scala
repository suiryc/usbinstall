package usbinstall

import grizzled.slf4j.Logging
import javafx.application.Platform
import javafx.beans.property.{DoubleProperty, ReadOnlyDoubleProperty}
import javafx.beans.value.{ChangeListener, ObservableValue}
import javafx.fxml.FXMLLoader
import javafx.geometry.{Insets, Pos}
import javafx.scene.{Parent, Scene}
import javafx.scene.control.Button
import javafx.scene.layout.{
  ColumnConstraints,
  GridPane,
  Priority,
  RowConstraints,
  VBox
}
import javafx.stage.{Stage, WindowEvent}
import org.controlsfx.dialog.{Dialogs, DialogStyle}
import suiryc.scala.javafx.concurrent.JFXSystem
import suiryc.scala.javafx.event.EventHandler._
import suiryc.scala.misc.RichOptional


object Stages
  extends Logging
{

  protected def changeScene(title: String, scene: Scene) {
    val stage = USBInstall.stage
    val pos =
      if (!USBInstall.firstScene) {
        val x = stage.getX()
        val y = stage.getY()
        stage.setMinWidth(0)
        stage.setMinHeight(0)
        stage.hide()
        Some(x, y)
      }
      else {
        stage.setOnCloseRequest { (event: WindowEvent) =>
          stage.close()
          Platform.exit()
        }
        None
      }

    stage.setTitle(title)
    stage.setScene(scene)
    pos foreach { pos =>
      stage.setX(pos._1)
      stage.setY(pos._2)
    }
    stage.show()
    USBInstall.firstScene = false

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

    trackMinimumDimension("width", stage.minWidthProperty, stage.widthProperty, stage.getScene().widthProperty)
    trackMinimumDimension("height", stage.minHeightProperty, stage.heightProperty, stage.getScene().heightProperty)
  }

  protected def errorStage(title: String, masthead: Option[String], error: Either[Throwable, String]) {
    import RichOptional._

    val dialog = Dialogs.create()
      .owner(USBInstall.stage)
      .style(DialogStyle.NATIVE)
      .title(title)
      .optional[String](masthead, _.masthead(_))

    val show = () => error match {
      case Left(ex) =>
        dialog.showException(ex)

      case Right(error) =>
        dialog.optional(error != "", _.message(error))
          .showError()
    }

    if (Platform.isFxApplicationThread)
      show()
    else
      JFXSystem.await(show())
  }

  def errorStage(title: String, masthead: Option[String], ex: Throwable) {
    errorStage(title, masthead, Left(ex))
  }

  def errorStage(title: String, masthead: Option[String], error: String) {
    errorStage(title, masthead, Right(error))
  }

  protected def toolBar(pane: StepPane) = {
    val loader = new FXMLLoader(getClass.getResource("toolBar.fxml"))
    val root = loader.load[Parent]()
    val controller = loader.getController[ToolBarController]()
    controller.setStepPane(pane)

    root
  }

  protected def stepChange(pane: StepPane) = {
    val loader = new FXMLLoader(getClass.getResource("stepChange.fxml"))
    val root = loader.load[Parent]()
    val controller = loader.getController[StepChangeController]()
    controller.setStepPane(pane)

    root
  }

  def step(pane: StepPane) = {
    val grid = new GridPane
    grid.setAlignment(Pos.TOP_CENTER)
    grid.getColumnConstraints().add(new ColumnConstraints() { setHgrow(Priority.ALWAYS) })
    grid.getRowConstraints().add(new RowConstraints() { setVgrow(Priority.NEVER) })
    grid.getRowConstraints().add(new RowConstraints() { setVgrow(Priority.ALWAYS) })
    grid.addColumn(0, toolBar(pane), pane, stepChange(pane))

    new Scene(grid)
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
