package com.vesperin.partition.spi

import scala.collection.immutable.Iterable
import scala.util.parsing.combinator.JavaTokenParsers

/**
  * @author Huascar Sanchez
  */
class JavaSignatureParser extends JavaTokenParsers {
  // Each of the lines of the grammar gets turned into a case class.  There are also methods
  // with the same name (starting with a lower-case letter) that implement the parser for the
  // grammar element.

  def methodSignature: Parser[MethodSignature] = {
    val argumentSignature = "(" ~> rep( typeSignature ) <~ ")"
    val result = ( argumentSignature ~ typeSignature ) ^^ { case a ~ b => MethodSignature( a, b ) }
    result
  }

  // This is the heart of the parser.  Take each line of the grammar and turn it into
  // a Parser[X].

  def typeSignature: Parser[TypeSignature] = {
    // I use TypeDescriptor to mean all the single-character elements for Java primitives
    sealed abstract class TypeDescriptor extends TypeSignatureElement {
      override def typesUsed: Set[JavaName] = Set.empty // never care about primitives
    }

    case object TypeDescriptorZ extends TypeDescriptor {
      override def toJava = "boolean"
    }

    case object TypeDescriptorC extends TypeDescriptor {
      override def toJava = "char"
    }

    case object TypeDescriptorB extends TypeDescriptor {
      override def toJava = "byte"
    }

    case object TypeDescriptorS extends TypeDescriptor {
      override def toJava = "short"
    }

    case object TypeDescriptorI extends TypeDescriptor {
      override def toJava = "int"
    }

    case object TypeDescriptorF extends TypeDescriptor {
      override def toJava = "float"
    }

    case object TypeDescriptorJ extends TypeDescriptor {
      override def toJava = "long"
    }

    case object TypeDescriptorD extends TypeDescriptor {
      override def toJava = "double"
    }

    case object TypeDescriptorV extends TypeDescriptor {
      override def toJava = "void"
    }

    def typeDescriptor: Parser[TypeDescriptor] = {
      def typeDescriptorZ: Parser[TypeDescriptor] = "Z" ^^ { _ => TypeDescriptorZ }
      def typeDescriptorC: Parser[TypeDescriptor] = "C" ^^ { _ => TypeDescriptorC }
      def typeDescriptorB: Parser[TypeDescriptor] = "B" ^^ { _ => TypeDescriptorB }
      def typeDescriptorS: Parser[TypeDescriptor] = "S" ^^ { _ => TypeDescriptorS }
      def typeDescriptorI: Parser[TypeDescriptor] = "I" ^^ { _ => TypeDescriptorI }
      def typeDescriptorF: Parser[TypeDescriptor] = "F" ^^ { _ => TypeDescriptorF }
      def typeDescriptorJ: Parser[TypeDescriptor] = "J" ^^ { _ => TypeDescriptorJ }
      def typeDescriptorD: Parser[TypeDescriptor] = "D" ^^ { _ => TypeDescriptorD }
      def typeDescriptorV: Parser[TypeDescriptor] = "V" ^^ { _ => TypeDescriptorV }

      typeDescriptorZ | typeDescriptorC | typeDescriptorB | typeDescriptorS | typeDescriptorI |
      typeDescriptorF | typeDescriptorJ | typeDescriptorD | typeDescriptorV
    }

    ( typeDescriptor | fieldTypeSignature ) ^^ TypeSignature
  }

  def fieldTypeSignature: Parser[FieldTypeSignature] = {
    case class ArrayTypeSignature( xs: TypeSignature ) extends FieldTypeSignatureElement {
      override def toJava: String = xs.toJava + "[]"
      override def typesUsed: Set[JavaName] = xs.typesUsed
    }

    def arrayTypeSignature: Parser[ArrayTypeSignature] = "[" ~> typeSignature ^^ { ArrayTypeSignature }

    ( classTypeSignature | arrayTypeSignature | typeVar ) ^^ FieldTypeSignature
  }

  def classTypeSignature: Parser[ClassTypeSignature] = {
    val nestedClasses = "." ~> javaName ~ opt( typeArgs ) ^^ { case a ~ b => NestedClass( a, b ) }

    ( "L" ~> javaName ~ opt( typeArgs ) ~ rep( nestedClasses ) <~ ";" ) ^^
      { case jname ~ optionalTypeArgs ~ extensionElements => ClassTypeSignature( jname, optionalTypeArgs, extensionElements ) }
  }

  def typeArgs: Parser[TypeArgs] = "<" ~> rep( typeArg ) <~ ">" ^^ TypeArgs

  def typeArg: Parser[TypeArg] = {
    // Signatures use signed FieldTypeSignatures for things like
    // java.util.List<? super Number> -- Ljava/util/List<-Ljava/lang/Number;>
    // and * (star) for
    // java.util.List<?> -- Ljava/util/List<*>;

    case class FieldTypeSignatureWithSign( sign: SignForFieldTypeSignature, fts: FieldTypeSignature ) extends HasToJavaMethod with TypeArgElement {
      override def toJava: String = sign.toJava + fts.toJava
      override def typesUsed: Set[JavaName] = fts.typesUsed
    }

    sealed abstract class SignForFieldTypeSignature extends HasToJavaMethod

    case object Plus extends SignForFieldTypeSignature {
      override def toJava = "? extends "
    }

    case object Minus extends SignForFieldTypeSignature {
      override def toJava = "? super "
    }

    case object Star extends TypeArgElement {
      override def toJava: String = "?"
      override def typesUsed: Set[JavaName] = Set.empty
    }

    def typeStar: Parser[TypeArgElement] = "*" ^^ { _ => Star }

    def fieldTypeSignatureWithSign: Parser[FieldTypeSignatureWithSign] = {
      def typePlusOrMinus: Parser[SignForFieldTypeSignature] = {
        def plus = "+" ^^ { _ => Plus }
        def minus = "-" ^^ { _ => Minus }
        plus | minus
      }

      typePlusOrMinus ~ fieldTypeSignature ^^
        { case sign ~ fts => FieldTypeSignatureWithSign( fts = fts, sign = sign ) }
    }

    ( typeStar | fieldTypeSignatureWithSign | fieldTypeSignature ) ^^ { x => TypeArg( x ) }
  }

  def typeVar: Parser[TypeVar] = "T" ~> javaName <~ ";" ^^ TypeVar

  def javaName: Parser[JavaName] =
    rep1sep( javaIdentifier, "/" ) ^^ (ids => JavaName(ids.mkString(".")))

  def javaIdentifier = new Parser[String] {
    val IsStartChar = new Object {
      def unapply( x: (Char, Int) ): Option[(Char, Int)] = if ( Character.isJavaIdentifierStart( x._1 ) ) Some( x._1, x._2 ) else None
    }

    val IsPartChar = new Object {
      import scala.PartialFunction._
      def unapply( x: (Char, Int) ): Option[(Char, Int)] = condOpt( x ) {
        case ( xchar, xi ) if Character.isJavaIdentifierPart( xchar ) => ( xchar, xi )
      }
    }

    def apply( in: Input ): ParseResult[String] = {
      val inputList = in.source.toString.toList.zipWithIndex
      val inputListFromOffset = inputList.dropWhile { case ( _, i ) => i < in.offset }
      def find( il: List[( Char, Int )], acc: List[Char], previousOffset: Option[Int] ): ParseResult[String] = {
        il match {
          case IsStartChar( ac, ai ) :: Nil => Success( ( ac :: acc.reverse ).mkString, in.drop( previousOffset getOrElse ( ai + 1 ) ) )
          case IsStartChar( ac, ai ) :: IsPartChar( bc, bi ) :: t => find( ( ac, ai ) :: t, bc :: acc, Some( bi ) )
          case IsStartChar( ac, _) :: ( _, _) :: _ =>
            val s = ( ac :: acc.reverse ).mkString
            Success( s, in.drop( s.length ) )
          case _ => Failure( "not a java identifier", in )
        }
      }
      val r = find( inputListFromOffset, List(), None )
      r
    }
  }
}

case class MethodSignature( paramSignature: List[TypeSignature], resultSignature: TypeSignature ) extends
  HasTypesUsedMethod {
  lazy val paramSignatureTypes: List[JavaName] = paramSignature flatMap { _.typesUsed }
  lazy val typesUsed: Set[JavaName] = ( paramSignatureTypes ++ resultSignature.typesUsed ).toSet

  lazy val toJava: String = {
    val a = List(resultSignature.toJava, "methodName", "(")
    val b = JavaSignatureParser.interpolate( paramSignature map { _.toJava }, "," )
    val c = List( ")" )

    ( a ++ b ++ c ) mkString " "
  }
}

case class TypeSignature( x: TypeSignatureElement ) extends
  HasToJavaMethod with HasTypesUsedMethod {
  override def toJava: String = x.toJava
  override def typesUsed: Set[JavaName] = x.typesUsed
}

case class FieldTypeSignature( x: FieldTypeSignatureElement ) extends
  TypeSignatureElement with TypeArgElement {
  override def toJava: String = x.toJava
  override def typesUsed: Set[JavaName] = x.typesUsed
}

case class ClassTypeSignature( ids: JavaName, optionalTypeArgs: Option[TypeArgs], extension: List[NestedClass] ) extends
  FieldTypeSignatureElement {
  override def toJava: String = ids.toJava + optionalTypeArgs.map( _.toJava ).mkString + ( extension map { _.toJava } mkString "." )
  override def typesUsed: Set[JavaName] = {
    val a = optionalTypeArgs.map { _.typesUsed } getOrElse List.empty
    val b = extension flatMap { _.typesUsed }
    ids.typesUsed ++ a ++ b
  }
}

// NestedClass isn't one of the top level elements, but it makes it easier to understand the
// code that parses the ( . Id TypeArgs? )* part of a ClassTypeSignature
case class NestedClass( javaName: JavaName, typeArgs: Option[TypeArgs] ) extends
  HasToJavaMethod with HasTypesUsedMethod {
  override def toJava: String = "." + javaName.toJava + typeArgs.map { _.toJava }.mkString
  val elements: Iterable[JavaName] with (JavaName with Int) ⇒ Boolean = typeArgs map {_.typesUsed} getOrElse List.empty
  override def typesUsed: Set[JavaName] = javaName.typesUsed ++ ( typeArgs map { _.typesUsed } getOrElse List.empty )
}

case class TypeArgs( typeArgs: List[TypeArg] ) extends
  HasToJavaMethod with HasTypesUsedMethod {
  override def toJava: String = typeArgs.map( _.toJava ).mkString( "<", ", ", ">" )
  override def typesUsed: Set[JavaName] = typeArgs.flatMap{ _.typesUsed }.toSet
}

case class TypeArg( t: TypeArgElement ) extends
  HasToJavaMethod with HasTypesUsedMethod {
  override def toJava: String = t.toJava
  override def typesUsed: Set[JavaName] = t.typesUsed
}

case class TypeVar( t: JavaName ) extends
  FieldTypeSignatureElement {
  override def toJava: String = t.toJava
  override def typesUsed: Set[JavaName] = t.typesUsed
}

// It's been a long time since CS 101, so I forget exactly what the bits of a grammar
// are called.  These are the things on the right-hand side.  For example, the first
// line of the grammar is TypeSignature, and it consists of any of the elements for a java
// primitive (Z, C, B etc) or a FieldTypeSignature.  So I mark all of the case classes
// for the primitives and for FieldTypeSignature with TypeSignatureElement.

trait TypeSignatureElement extends HasToJavaMethod with HasTypesUsedMethod
trait FieldTypeSignatureElement extends HasToJavaMethod with HasTypesUsedMethod
trait TypeArgElement extends HasToJavaMethod with HasTypesUsedMethod

// Any element that can be converted to Java code implements this trait.  It's used to
// turn the parsed signature back into Java.
trait HasToJavaMethod {
  def toJava: String
}

trait HasTypesUsedMethod {
  def typesUsed: Set[JavaName]
}

// JavaIdentifier is already taken, so I'm using JavaName instead.  (There's probably a way
// to use JavaIdentifier from the parser, but I'll leave that as an exercise for the reader.)
case class JavaName( s: String ) extends HasToJavaMethod with HasTypesUsedMethod {
  override val toJava: String = s
  override def typesUsed = Set( this )
}

object JavaSignatureParser {
  def parseType( s: String ): TypeSignature = {
    val p = new JavaSignatureParser
    p.parseAll( p.typeSignature, s ).get
  }

  def parseMethod( s: String ): MethodSignature = {
    val p = new JavaSignatureParser
    p.parseAll( p.methodSignature, s ).get
  }

  def interpolate[T]( xs: Iterable[T], sep: T ): List[T] = ( xs zip Stream.continually( sep ) )
    .foldLeft( List.empty[T] ) { case ( acc, ( a, b ) ) => b :: a :: acc }
    .reverse.dropRight( 1 )
}