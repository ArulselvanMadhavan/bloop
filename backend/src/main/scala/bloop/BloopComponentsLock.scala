package bloop

import java.io.File
import java.util.concurrent.Callable

import xsbti.GlobalLock

object BloopComponentsLock extends GlobalLock {
  override def apply[T](file: File, callable: Callable[T]): T = synchronized { callable.call() }
}
