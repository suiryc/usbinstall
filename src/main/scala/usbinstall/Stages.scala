package usbinstall

import grizzled.slf4j.Logging
import javafx.application.Platform
import javafx.fxml.FXMLLoader
import javafx.geometry.Pos
import javafx.scene.{Parent, Scene}
import javafx.scene.layout.{
  ColumnConstraints,
  GridPane,
  Priority,
  RowConstraints
}
import javafx.stage.WindowEvent
import org.controlsfx.control.action.Action
import org.controlsfx.dialog.{Dialogs, DialogStyle}
import suiryc.scala.javafx.concurrent.JFXSystem
import suiryc.scala.javafx.event.EventHandler._
import suiryc.scala.javafx.stage.{Stages => sfxStages}
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

    sfxStages.trackMinimumDimensions(stage)
  }

  protected def showDialogStage[T](show: => T): T =
    if (Platform.isFxApplicationThread)
      show
    else
      JFXSystem.await(show)

  protected def makeDialogStage(title: String, masthead: Option[String], message: Option[String] = None): Dialogs = {
    import RichOptional._

    Dialogs.create()
      .owner(USBInstall.stage)
      .style(DialogStyle.NATIVE)
      .title(title)
      .optional[String](masthead, _.masthead(_))
      .optional[String](message, _.message(_))
  }

  protected def dialogStage[T](title: String, masthead: Option[String], message: String, show: Dialogs => T): T = {
    val msg = if (message != "") Some(message) else None
    val dialog = makeDialogStage(title, masthead, msg)
    showDialogStage {
      show(dialog)
    }
  }

  def infoStage(title: String, masthead: Option[String], message: String): Unit =
    dialogStage(title, masthead, message, _.showInformation)

  def warningStage(title: String, masthead: Option[String], message: String): Action =
    dialogStage(title, masthead, message, _.showWarning)

  def errorStage(title: String, masthead: Option[String], message: String): Action =
    dialogStage(title, masthead, message, _.showError)

  def errorStage(title: String, masthead: Option[String], ex: Throwable): Action = {
    val dialog = makeDialogStage(title, masthead)
    showDialogStage {
      dialog.showException(ex)
    }
  }

  protected def toolBar(pane: StepPane) = {
    val loader = new FXMLLoader(getClass.getResource("toolBar.fxml"))
    val root = loader.load[Parent]()
    val controller = loader.getController[ToolBarController]()
    pane.subscriptionHolders ::= controller

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
