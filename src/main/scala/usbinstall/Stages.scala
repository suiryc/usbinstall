package usbinstall

import javafx.application.Platform
import javafx.fxml.FXMLLoader
import javafx.geometry.Pos
import javafx.scene.{Parent, Scene}
import javafx.scene.control.ButtonType
import javafx.scene.layout.{
  ColumnConstraints,
  GridPane,
  Priority,
  RowConstraints
}
import suiryc.scala.javafx.stage.{Stages => sfxStages}
import usbinstall.controllers.{StepChangeController, ToolBarController}


object Stages {

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
        Some((x, y))
      }
      else {
        stage.setOnCloseRequest { _ =>
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

  def step(tuple: (StepPane, Option[Any])): Scene = {
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
    changeScene("Install", step(Panes.install()), Some((800.0, 600.0)))
  }

}
