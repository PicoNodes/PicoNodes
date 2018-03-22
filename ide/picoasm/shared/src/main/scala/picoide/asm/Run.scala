package picoide.asm

import java.nio.charset.Charset
import java.nio.file.{Files, Paths}

object Run extends App {
  val parser = new PicoAsmParser()

  if (args.length < 1) {
    System.out.println("Usage: java -jar picoasm.jar <input>")
    System.exit(1)
  }
  val inPath       = Paths.get(args(0))
  val in           = new String(Files.readAllBytes(inPath), Charset.forName("UTF-8"))
  val instructions = parser.parseAll(parser.instructions, in)
  println(instructions)
  println(instructions.get.flatMap(_.assemble))
}
