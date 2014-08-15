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
import javafx.stage.{Window, WindowEvent}
import org.controlsfx.control.action.Action
import org.controlsfx.dialog.{Dialog, Dialogs, DialogStyle}
import suiryc.scala.javafx.concurrent.JFXSystem
import suiryc.scala.javafx.event.EventHandler._
import suiryc.scala.javafx.stage.{Stages => sfxStages}
import suiryc.scala.misc.RichOptional
import usbinstall.controllers.{StepChangeController, ToolBarController}


object Stages
  extends Logging
{

  object DialogActions {
    val Ok_Cancel = List(Dialog.Actions.OK, Dialog.Actions.CANCEL)
  }

  protected def changeScene(title: String, scene: Scene, size: Option[(Double, Double)] = None) {
    val stage = USBInstall.stage
    val pos =
      if (!USBInstall.firstScene) {
        val x = stage.getX
        val y = stage.getY
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

    sfxStages.trackMinimumDimensions(stage, size)
  }

  protected def showDialogStage[T](show: => T): T =
    if (Platform.isFxApplicationThread)
      show
    else
      JFXSystem.await(show)

  protected def makeDialogStage(owner: Option[Window], title: String, masthead: Option[String], message: Option[String]): Dialogs = {
    import RichOptional._

    Dialogs.create()
      .owner(owner.getOrElse(USBInstall.stage))
      .style(DialogStyle.NATIVE)
      .title(title)
      .optional[String](masthead, _.masthead(_))
      .optional[String](message, _.message(_))
  }

  protected def dialogStage[T](owner: Option[Window], title: String, masthead: Option[String], message: String, actions: List[Action], show: Dialogs => T): T = {
    val msg = if (message != "") Some(message) else None
    val dialog = makeDialogStage(owner, title, masthead, msg)

    if (actions.nonEmpty)
      dialog.actions(actions:_*)

    showDialogStage {
      show(dialog)
    }
  }

  def confirmStage(owner: Option[Window], title: String, masthead: Option[String], message: String, actions: List[Action] = Nil): Action =
    dialogStage(owner, title, masthead, message, actions, _.showConfirm)

  def infoStage(owner: Option[Window], title: String, masthead: Option[String], message: String): Unit =
    dialogStage(owner, title, masthead, message, Nil, _.showInformation)

  def warningStage(owner: Option[Window], title: String, masthead: Option[String], message: String): Action =
    dialogStage(owner, title, masthead, message, Nil, _.showWarning)

  def errorStage(owner: Option[Window], title: String, masthead: Option[String], message: String): Action =
    dialogStage(owner, title, masthead, message, Nil, _.showError)

  def errorStage(owner: Option[Window], title: String, masthead: Option[String], ex: Throwable): Action = {
    val dialog = makeDialogStage(owner, title, masthead, None)
    showDialogStage {
      dialog.showException(ex)
    }
  }

  protected def toolBar(pane: StepPane, paneController: Option[Any]) = {
    val loader = new FXMLLoader(getClass.getResource("/fxml/toolBar.fxml"))
    val root = loader.load[Parent]()
    val controller = loader.getController[ToolBarController]()
    pane.subscriptionHolders ::= controller
    controller.setPaneController(paneController)

    root
  }

  protected def stepChange(pane: StepPane) = {
    val loader = new FXMLLoader(getClass.getResource("/fxml/stepChange.fxml"))
    val root = loader.load[Parent]()
    val controller = loader.getController[StepChangeController]()
    controller.setStepPane(pane)

    root
  }

  def step(tuple: (StepPane, Option[Any])) = {
    val (pane, controller) = tuple
    val grid = new GridPane
    grid.setAlignment(Pos.TOP_CENTER)
    grid.getColumnConstraints.add(new ColumnConstraints() { setHgrow(Priority.ALWAYS) })
    grid.getRowConstraints.add(new RowConstraints() { setVgrow(Priority.NEVER) })
    grid.getRowConstraints.add(new RowConstraints() { setVgrow(Priority.ALWAYS) })
    grid.addColumn(0, toolBar(pane, controller), pane, stepChange(pane))

    new Scene(grid)
  }

  def chooseDevice() {
    changeScene("Choose device", step(Panes.chooseDevice()))
  }

  def choosePartitions() {
    changeScene("Choose partitions", step(Panes.choosePartitions()))
  }

  def install() {
    changeScene("Install", step(Panes.install()), Some(800, 600))
  }

}
