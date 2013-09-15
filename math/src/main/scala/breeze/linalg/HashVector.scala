package breeze.linalg

import breeze.collection.mutable.OpenAddressHashArray
import breeze.linalg.operators._
import breeze.storage.{ConfigurableDefault, DefaultArrayValue}
import breeze.generic.{CanMapValues, URFunc}
import support.{CanZipMapValues, CanMapKeyValuePairs, CanCopy}
import breeze.math.{TensorSpace, Ring}
import scala.reflect.ClassTag
import scala.util.hashing.MurmurHash3
import breeze.macros.{sequence, expandArgs, expand}
import breeze.math.Complex
import scala.math.BigInt

/**
 * A HashVector is a sparse vector backed by an OpenAddressHashArray
 * @author dlwh
 */
class HashVector[@specialized(Int, Double, Float) E](val array: OpenAddressHashArray[E]) extends Vector[E] with VectorLike[E, HashVector[E]] {
  def activeIterator: Iterator[(Int, E)] = array.activeIterator

  def activeValuesIterator: Iterator[E] = array.activeValuesIterator

  def activeKeysIterator: Iterator[Int] = array.activeKeysIterator

  def apply(i: Int): E = array(i)

  def update(i: Int, v: E) {
    array(i) = v
  }

  def activeSize: Int = array.activeSize

  def length: Int = array.length

  def copy: HashVector[E] = new HashVector(array.copy)

  def repr = this

  override def ureduce[A](f: URFunc[E, A]): A = {
    f.apply(array.data, 0, 1, array.data.length, array.isActive _)
  }

  final def iterableSize: Int = array.iterableSize
  def data = array.data
  final def index = array.index
  final def isActive(i: Int) = array.isActive(i)


  override def toString = {
    activeIterator.mkString("HashVector(",", ", ")")
  }

  def allVisitableIndicesActive:Boolean = false

  override def hashCode() = {
    var hash = 47
    // we make the hash code based on index * value, so that zeros don't affect the hashcode.
    val dv = array.default.value(array.defaultArrayValue)
    var i = 0
    while(i < activeSize) {
      if(isActive(i)) {
        val ind = index(i)
        val v = data(i)
        if(v != dv) {
          hash = MurmurHash3.mix(hash, v.##)
          hash = MurmurHash3.mix(hash, ind)
        }
      }

      i += 1
    }

    MurmurHash3.finalizeHash(hash, activeSize)
  }
}


object HashVector extends HashVectorOps_Int 
                          with HashVectorOps_Float 
                          with HashVectorOps_Double
                          with HashVectorOps_Complex
                          with DenseVector_HashVector_Ops
                          with HashVector_DenseVector_Ops {
  def zeros[@specialized(Double, Float, Int) V: ClassTag:DefaultArrayValue](size: Int) = {
    new HashVector(new OpenAddressHashArray[V](size))
  }
  def apply[@specialized(Double, Float, Int) V:DefaultArrayValue](values: Array[V]) = {
    implicit val man = ClassTag[V](values.getClass.getComponentType.asInstanceOf[Class[V]])
    val oah = new OpenAddressHashArray[V](values.length)
    for( (v,i) <- values.zipWithIndex) oah(i) = v
    new HashVector(oah)
  }

  def apply[V:ClassTag:DefaultArrayValue](values: V*):HashVector[V] = {
    apply(values.toArray)
  }
  def fill[@specialized(Double, Int, Float) V:ClassTag:DefaultArrayValue](size: Int)(v: =>V):HashVector[V] = apply(Array.fill(size)(v))
  def tabulate[@specialized(Double, Int, Float) V:ClassTag:DefaultArrayValue](size: Int)(f: Int=>V):HashVector[V]= apply(Array.tabulate(size)(f))

  def apply[V:ClassTag:DefaultArrayValue](length: Int)(values: (Int, V)*) = {
    val r = zeros[V](length)
    for( (i, v) <- values) {
      r(i) = v
    }
    r
  }

  // implicits



  // implicits
  class CanCopyHashVector[@specialized(Int, Float, Double) V:ClassTag:DefaultArrayValue] extends CanCopy[HashVector[V]] {
    def apply(v1: HashVector[V]) = {
      v1.copy
    }
  }

  implicit def canCopyHash[@specialized(Int, Float, Double) V: ClassTag: DefaultArrayValue] = new CanCopyHashVector[V]

  implicit def canMapValues[V, V2: ClassTag: DefaultArrayValue]:CanMapValues[HashVector[V], V, V2, HashVector[V2]] = {
    new CanMapValues[HashVector[V], V, V2, HashVector[V2]] {
      /**Maps all key-value pairs from the given collection. */
      def map(from: HashVector[V], fn: (V) => V2) = {
        HashVector.tabulate(from.length)(i => fn(from(i)))
      }

      /**Maps all active key-value pairs from the given collection. */
      def mapActive(from: HashVector[V], fn: (V) => V2) = {
        val out = new OpenAddressHashArray[V2](from.length)
        var i = 0
        while(i < from.iterableSize) {
          if(from.isActive(i))
            out(from.index(i)) = fn(from.data(i))
          i += 1
        }
        new HashVector(out)
      }
    }
  }

  implicit def canMapPairs[V, V2: ClassTag: DefaultArrayValue]:CanMapKeyValuePairs[HashVector[V], Int, V, V2, HashVector[V2]] = {
    new CanMapKeyValuePairs[HashVector[V], Int, V, V2, HashVector[V2]] {
      /**Maps all key-value pairs from the given collection. */
      def map(from: HashVector[V], fn: (Int, V) => V2) = {
        HashVector.tabulate(from.length)(i => fn(i, from(i)))
      }

      /**Maps all active key-value pairs from the given collection. */
      def mapActive(from: HashVector[V], fn: (Int, V) => V2) = {
        val out = new OpenAddressHashArray[V2](from.length)
        var i = 0
        while(i < from.iterableSize) {
          if(from.isActive(i))
          out(from.index(i)) = fn(from.index(i), from.data(i))
          i += 1
        }
        new HashVector(out)
      }
    }
  }

  class CanZipMapValuesHashVector[@specialized(Int, Double, Float) V, @specialized(Int, Double) RV:ClassTag:DefaultArrayValue] extends CanZipMapValues[HashVector[V],V,RV,HashVector[RV]] {
    def create(length : Int) = zeros(length)

    /**Maps all corresponding values from the two collection. */
    def map(from: HashVector[V], from2: HashVector[V], fn: (V, V) => RV) = {
      require(from.length == from2.length, "Vector lengths must match!")
      val result = create(from.length)
      var i = 0
      while (i < from.length) {
        result(i) = fn(from(i), from2(i))
        i += 1
      }
      result
    }
  }
  implicit def zipMap[V, R:ClassTag:DefaultArrayValue] = new CanZipMapValuesHashVector[V, R]
  implicit val zipMap_d = new CanZipMapValuesHashVector[Double, Double]
  implicit val zipMap_f = new CanZipMapValuesHashVector[Float, Float]
  implicit val zipMap_i = new CanZipMapValuesHashVector[Int, Int]


  implicit def negFromScale[@specialized(Int, Float, Double)  V, Double](implicit scale: BinaryOp[HashVector[V], V, OpMulScalar, HashVector[V]], field: Ring[V]) = {
    new UnaryOp[HashVector[V], OpNeg, HashVector[V]] {
      override def apply(a : HashVector[V]) = {
        scale(a, field.negate(field.one))
      }
    }
  }


  implicit val space_d = TensorSpace.make[HashVector[Double], Int, Double]
  implicit val space_f = TensorSpace.make[HashVector[Float], Int, Float]
  implicit val space_i = TensorSpace.make[HashVector[Int], Int, Int]
}



trait DenseVector_HashVector_Ops { this: HashVector.type =>
  import breeze.math.PowImplicits._


  @expand
  @expand.exclude(Complex, OpMod)
  @expand.exclude(BigInt, OpPow)
  implicit def dv_hv_UpdateOp[@expandArgs(Int, Double, Float, Long, BigInt, Complex) T,
  @expandArgs(OpMulScalar, OpDiv, OpSet, OpMod, OpPow) Op <: OpType]
  (implicit @sequence[Op]({_ * _}, {_ / _}, {(a,b) => b}, {_ % _}, {_ pow _})
  op: BinaryOp[T, T, Op, T]):BinaryUpdateOp[DenseVector[T], HashVector[T], Op] = new BinaryUpdateOp[DenseVector[T], HashVector[T], Op] {
    def apply(a: DenseVector[T], b: HashVector[T]):Unit = {
      require(a.length == b.length, "Vectors must have the same length")
      val ad = a.data
      var aoff = a.offset
      val astride = a.stride

      var i = 0
      while(i < a.length) {
        ad(aoff) = op(ad(aoff), b(i))
        aoff += astride
        i += 1
      }

    }
  }

  // this shouldn't be necessary but it is:
  @expand
  @expand.exclude(Complex, OpMod)
  @expand.exclude(BigInt, OpPow)
  implicit def dv_hv_op[@expandArgs(Int, Double, Float, Long, BigInt, Complex) T,
  @expandArgs(OpAdd, OpSub, OpMulScalar, OpDiv, OpSet, OpMod, OpPow) Op <: OpType] = DenseVector.pureFromUpdate(implicitly[BinaryUpdateOp[DenseVector[T], HashVector[T], Op]])


  @expand
  implicit def dv_hv_Update_Zero_Idempotent[@expandArgs(Int, Double, Float, Long, BigInt, Complex) T,
  @expandArgs(OpAdd, OpSub) Op <: OpType]
  (implicit @sequence[Op]({_ + _},  {_ - _})
  op: BinaryOp[T, T, Op, T]):BinaryUpdateOp[DenseVector[T], HashVector[T], Op] = new BinaryUpdateOp[DenseVector[T], HashVector[T], Op] {
    def apply(a: DenseVector[T], b: HashVector[T]):Unit = {
      require(a.length == b.length, "Vectors must have the same length")
      val ad = a.data
      val bd = b.data
      val bi = b.index
      val bsize = b.iterableSize

      var i = 0
      while(i < bsize) {
        val aoff = a.offset + bi(i) * a.stride
        if(b.isActive(i))
          ad(aoff) = op(ad(aoff), bd(i))
        i += 1
      }
    }
  }


  @expand
  implicit def canDot_DV_HV[@expandArgs(Int, Long, BigInt, Complex) T](implicit @sequence[T](0, 0l, BigInt(0), Complex.zero) zero: T): BinaryOp[DenseVector[T], HashVector[T], breeze.linalg.operators.OpMulInner, T] = {
    new BinaryOp[DenseVector[T], HashVector[T], breeze.linalg.operators.OpMulInner, T] {
      def apply(a: DenseVector[T], b: HashVector[T]) = {
        var result: T = zero

        val bd = b.data
        val bi = b.index
        val bsize = b.iterableSize

        val adata = a.data
        val aoff = a.offset
        val stride = a.stride

        var i = 0
        while(i < bsize) {
          if(b.isActive(i))
            result += adata(aoff + bi(i) * stride) * bd(i)
          i += 1
        }
        result
      }
    }
  }


}

trait HashVector_DenseVector_Ops extends DenseVector_HashVector_Ops { this: HashVector.type =>
  import breeze.math.PowImplicits._


  @expand
  @expand.exclude(Complex, OpMod)
  @expand.exclude(BigInt, OpPow)
  implicit def sv_dv_UpdateOp[@expandArgs(Int, Double, Float, Long, BigInt, Complex) T,
  @expandArgs(OpAdd, OpSub, OpMulScalar, OpDiv, OpSet, OpMod, OpPow) Op <: OpType]
  (implicit @sequence[Op]({_ + _}, {_ - _}, {_ * _}, {_ / _}, {(a,b) => b}, {_ % _}, {_ pow _})
  op: BinaryOp[T, T, Op, T]):BinaryUpdateOp[HashVector[T], DenseVector[T], Op] = new BinaryUpdateOp[HashVector[T], DenseVector[T], Op] {
    def apply(a: HashVector[T], b: DenseVector[T]):Unit = {
      require(a.length == b.length, "Vectors must have the same length")

      var i = 0
      while(i < b.length) {
        a(i) = op(a(i), b(i))
        i += 1
      }
    }
  }

  @expand
  @expand.exclude(Complex, OpMod)
  @expand.exclude(BigInt, OpPow)
  implicit def sv_dv_op[@expandArgs(Int, Double, Float, Long, BigInt, Complex) T,
                        @expandArgs(OpAdd, OpSub, OpMulScalar, OpDiv, OpSet, OpMod, OpPow) Op <: OpType]
  (implicit @sequence[Op]({_ + _}, {_ - _}, {_ * _}, {_ / _}, {(a,b) => b}, {_ % _}, {_ pow _})
  op: BinaryOp[T, T, Op, T]):BinaryOp[HashVector[T], DenseVector[T], Op, DenseVector[T]] = {
    new BinaryOp[HashVector[T], DenseVector[T], Op, DenseVector[T]] {
      def apply(a: HashVector[T], b: DenseVector[T]) = {
        require(a.length == b.length, "Vectors must have the same length")
        val result = DenseVector.zeros[T](a.length)

        var i = 0
        while(i < b.length) {
          result(i) = op(a(i), b(i))
          i += 1
        }

        result
      }
    }
  }

  @expand
  implicit def canDot_SV_DV[@expandArgs(Int, Long, BigInt, Complex) T](implicit @sequence[T](0, 0l, BigInt(0), Complex.zero) zero: T): BinaryOp[HashVector[T], DenseVector[T], breeze.linalg.operators.OpMulInner, T] = {
    new BinaryOp[HashVector[T], DenseVector[T], breeze.linalg.operators.OpMulInner, T] {
      def apply(a: HashVector[T], b: DenseVector[T]) = {
        require(b.length == a.length, "Vectors must be the same length!")
        b dot a
      }
    }
      //      Vector.canDotProductV_T.register(this)
  }


}