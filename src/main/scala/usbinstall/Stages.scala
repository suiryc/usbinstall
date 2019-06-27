package usbinstall

import javafx.application.Platform
import javafx.fxml.FXMLLoader
import javafx.geometry.Pos
import javafx.scene.{Parent, Scene}
import javafx.scene.control.ButtonType
import javafx.scene.layout.{ColumnConstraints, GridPane, Priority, RowConstraints}
import suiryc.scala.javafx.stage.Stages.StageLocation
import suiryc.scala.javafx.stage.{Stages => sfxStages}
import usbinstall.controllers.{StepChangeController, ToolBarController}

object Stages {

  object DialogButtons {
    val Ok_Cancel: List[ButtonType] = List(ButtonType.OK, ButtonType.CANCEL)
  }

  protected def changeScene(title: String, scene: Scene): Unit = {
    val stage = USBInstall.stage
    // Try to keep the stage center at the same spot
    val center =
      if (!USBInstall.firstScene) {
        val x = stage.getX + stage.getWidth / 2
        val y = stage.getY + stage.getHeight / 2
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

    sfxStages.onStageReady(stage, USBInstall.firstScene) {
      val width = stage.getWidth
      val height = stage.getHeight
      val x = center.map(_._1 - width / 2).getOrElse(stage.getX)
      val y = center.map(_._2 - height / 2).getOrElse(stage.getY)
      val loc = StageLocation(x, y, width, height, maximized = false)
      sfxStages.setMinimumDimensions(stage)
      // Note: setting stage size and keeping it while changing scene
      // does not play well (at least under Gnome). Default dimension
      // being good enough, don't change it.
      sfxStages.setLocation(stage, loc, setSize = false)
    }

    stage.show()
    USBInstall.firstScene = false
  }

  protected def toolBar(pane: StepPane, paneController: Option[Any]): Parent = {
    val loader = new FXMLLoader(getClass.getResource("/fxml/toolBar.fxml"))
    val root = loader.load[Parent]()
    val controller = loader.getController[ToolBarController]()
    pane.subscriptionHolders ::= controller
    controller.setPaneController(paneController)

    root
  }

  protected def stepChange(pane: StepPane): Parent = {
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

  def chooseProfile(): Unit = {
    changeScene("Choose profile", step(Panes.chooseProfile()))
  }

  def chooseDevice(): Unit = {
    changeScene("Choose device", step(Panes.chooseDevice()))
  }

  def choosePartitions(): Unit = {
    changeScene("Choose partitions", step(Panes.choosePartitions()))
  }

  def install(): Unit = {
    changeScene("Install", step(Panes.install()))
  }

}
