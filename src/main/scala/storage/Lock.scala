package storage

import java.io.File

/**
 * Created with IntelliJ IDEA.
 * User: Kos
 * Date: 14.10.13
 * Time: 21:59
 * To change this template use File | Settings | File Templates.
 */
object Lock {

  def createLock(name: String): Boolean = {
    // createNewFile() returns false if file already exists
    new File(name + ".lock").createNewFile()
  }

  def isLocked(name: String): Boolean = {
    new File(name + ".lock").exists()
  }

  def removeLock(name: String) = {
    new File(name + ".lock").delete()
  }

  def createLock(dir: String, name: String): Boolean = {
    // createNewFile() returns false if file already exists
    new File(dir, name + ".lock").createNewFile()
  }

  def isLocked(dir: String, name: String): Boolean = {
    new File(dir, name + ".lock").exists()
  }

  def removeLock(dir: String, name: String) = {
    new File(dir, name + ".lock").delete()
  }
}
