package usbinstall.util

import scalafx.scene.control.TextArea


class LogArea extends TextArea {

  editable = false

  def appendLine(s: String) {
    val current = this.getText()

    if (current == "") this.setText(s)
    else this.appendText(s"\n$s")
  }

  def prependLine(s: String) {
    val current = this.getText()

    if (current == "") this.setText(s)
    else this.setText(s"$s\n$current")
  }

}
