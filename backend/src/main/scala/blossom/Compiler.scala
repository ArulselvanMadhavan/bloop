package blossom

import xsbti.compile._
import sbt.internal.inc._
import java.util.Optional
import java.io.File
import java.nio.file.Path

import xsbti.{Logger, T2}

case class CompileInputs(
    scalaInstance: ScalaInstance,
    compilerCache: CompilerCache,
    sourceDirectories: Array[Path],
    classpath: Array[Path],
    classesDir: Path,
    baseDirectory: Path,
    previousResult: PreviousResult,
    logger: Logger
)

object Compiler {
  val logger = QuietLogger

  private final class ZincClasspathEntryLookup(previousResult: PreviousResult)
      extends PerClasspathEntryLookup {
    override def analysis(classpathEntry: File): Optional[CompileAnalysis] =
      previousResult.analysis
    override def definesClass(classpathEntry: File): DefinesClass =
      Locate.definesClass(classpathEntry)
  }

  def compile(compileInputs: CompileInputs): CompileResult = {
    def getInputs(compilers: Compilers): Inputs = {
      val options = getCompilationOptions(compileInputs)
      val setup   = getSetup(compileInputs)
      Inputs.of(compilers, options, setup, compileInputs.previousResult)
    }

    def getCompilationOptions(inputs: CompileInputs): CompileOptions = {
      val sources = inputs.sourceDirectories.distinct
        .flatMap(src => IO.getAll(src, "glob:**.{scala,java}"))
        .distinct

      CompileOptions
        .create()
        .withClassesDirectory(inputs.classesDir.toFile)
        .withSources(sources.map(_.toFile))
        .withClasspath(inputs.classpath.map(_.toFile) ++ inputs.scalaInstance.allJars ++ Array(
          inputs.classesDir.toFile))
    }

    def getSetup(compileInputs: CompileInputs): Setup = {
      val skip          = false
      val empty         = Array.empty[T2[String, String]]
      val lookup        = new ZincClasspathEntryLookup(compileInputs.previousResult)
      val reporter      = new LoggedReporter(100, logger)
      val compilerCache = new FreshCompilerCache
      val cacheFile     = compileInputs.baseDirectory.resolve("cache").toFile
      val incOptions    = IncOptions.create()
      val progress      = Optional.empty[CompileProgress]
      Setup.create(lookup, skip, cacheFile, compilerCache, incOptions, reporter, progress, empty)
    }

    val scalaInstance       = compileInputs.scalaInstance
    val cacheId             = CompilerCache.CacheId.fromInstance(scalaInstance)
    val compilers           = compileInputs.compilerCache.get(cacheId)
    val inputs              = getInputs(compilers)
    val incrementalCompiler = ZincUtil.defaultIncrementalCompiler
    incrementalCompiler.compile(inputs, logger)
  }
}
