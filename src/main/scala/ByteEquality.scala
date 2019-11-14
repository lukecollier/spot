

object ByteEquality {
  val MAX_CONTENT_SIZE: Long = Long.MaxValue

  sealed trait Equality
  case object Same extends Equality
  case object Different extends Equality

  def areEqual(left: Array[Byte], right: Array[Byte]) =
    if ((left diff right).isEmpty) {
      Same
    } else {
      Different
    }


}
