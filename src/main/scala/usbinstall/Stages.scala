package usbinstall

import grizzled.slf4j.Logging
import javafx.application.Platform
import javafx.fxml.FXMLLoader
import javafx.geometry.Pos
import javafx.scene.{Parent, Scene}
import javafx.scene.control.{Alert, ButtonType, Label, TextArea}
import javafx.scene.layout.{
  ColumnConstraints,
  GridPane,
  Priority,
  RowConstraints
}
import javafx.stage.{Window, WindowEvent}
import suiryc.scala.RichOption._
import suiryc.scala.javafx.concurrent.JFXSystem
import suiryc.scala.javafx.event.EventHandler._
import suiryc.scala.javafx.scene.control.Dialogs
import suiryc.scala.javafx.stage.{Stages => sfxStages}
import usbinstall.controllers.{StepChangeController, ToolBarController}


object Stages
  extends Logging
{

  object DialogButtons {
    val Ok_Cancel = List(ButtonType.OK, ButtonType.CANCEL)
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

  protected def jfxExecute[T](f: => T): T =
    JFXSystem.await(f, logReentrant = false)

  protected def makeDialogStage(kind: Alert.AlertType, owner: Option[Window], title: String, headerText: Option[String], contentText: Option[String]): Alert =
    // Note: it is mandatory to create the 'Alert' inside JavaFX thread.
    jfxExecute {
      val alert = new Alert(kind)
      alert.initOwner(owner.getOrElse(USBInstall.stage))
      alert.setTitle(title)
      alert.setHeaderText(headerText.orNull)
      alert.setContentText(contentText.orNull)

      alert
    }

  protected def dialogStage[T](kind: Alert.AlertType, owner: Option[Window], title: String, headerText: Option[String], contentText: String, buttons: List[ButtonType]): Option[ButtonType] = {
    val msg = if (contentText != "") Some(contentText) else None
    val dialog = makeDialogStage(kind, owner, title, headerText, msg)

    if (buttons.nonEmpty)
      dialog.getButtonTypes.setAll(buttons:_*)

    jfxExecute {
      dialog.showAndWait
    }
  }

  def confirmStage(owner: Option[Window], title: String, headerText: Option[String], contentText: String, buttons: List[ButtonType] = Nil): Option[ButtonType] =
    dialogStage(Alert.AlertType.CONFIRMATION, owner, title, headerText, contentText, buttons)

  def infoStage(owner: Option[Window], title: String, headerText: Option[String], contentText: String): Unit =
    dialogStage(Alert.AlertType.INFORMATION, owner, title, headerText, contentText, Nil)

  def warningStage(owner: Option[Window], title: String, headerText: Option[String], contentText: String): Option[ButtonType] =
    dialogStage(Alert.AlertType.WARNING, owner, title, headerText, contentText, Nil)

  def errorStage(owner: Option[Window], title: String, headerText: Option[String], contentText: String): Option[ButtonType] =
    dialogStage(Alert.AlertType.ERROR, owner, title, headerText, contentText, Nil)

  def errorStage(owner: Option[Window], title: String, headerText: Option[String], ex: Throwable): Option[ButtonType] =
    Dialogs.error(owner.orElse(Some(USBInstall.stage)), Some(title), headerText, ex)

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
    changeScene("Install", step(Panes.install()), Some(800.0, 600.0))
  }

}
