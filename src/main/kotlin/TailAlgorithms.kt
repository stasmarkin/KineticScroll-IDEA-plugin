package com.stasmarkin.kineticscroll

import kotlin.math.*

private const val N = 12
private const val ln2 = 0.6931471805599453

fun TrailMovement.withScrollMode(mode: InertiaDirection) = when (mode) {
  InertiaDirection.NONE -> NoTrail
  InertiaDirection.VERTICAL -> VerticalTrail(this)
  InertiaDirection.BOTH -> this
}

interface TrailMovement {
  fun finished(from: Long): Boolean
  fun deltaX(from: Long, to: Long): Double
  fun deltaY(from: Long, to: Long): Double
}

class VerticalTrail(impl: TrailMovement) : TrailMovement by impl {
  override fun deltaX(from: Long, to: Long): Double = 0.0
}

object NoTrail : TrailMovement {
  override fun finished(from: Long): Boolean = true
  override fun deltaX(from: Long, to: Long): Double = 0.0
  override fun deltaY(from: Long, to: Long): Double = 0.0
}

abstract class DistanceBasedTrail(
  val vX: Double,
  val vY: Double,
  val initTs: Long
) : TrailMovement {

  abstract fun distance(t: Long, v0: Double): Double
  abstract val endX: Long
  abstract val endY: Long
  abstract val end: Long

  override fun finished(from: Long): Boolean = end <= from

  override fun deltaX(fromUnsafe: Long, toUnsafe: Long): Double {
    val from = fromUnsafe.coerceIn(initTs, endX) - initTs
    val to = toUnsafe.coerceIn(initTs, endX) - initTs
    if (from == to) return 0.0
    return distance(to, vX) - distance(from, vX)
  }

  override fun deltaY(fromUnsafe: Long, toUnsafe: Long): Double {
    val from = fromUnsafe.coerceIn(initTs, endY) - initTs
    val to = toUnsafe.coerceIn(initTs, endY) - initTs
    if (from == to) return 0.0
    return distance(to, vY) - distance(from, vY)
  }
}

class ExponentialSlowdownTrail(
  vX: Double,
  vY: Double,
  initTs: Long,
  pUnsafe: Int
) : DistanceBasedTrail(vX, vY, initTs) {

  private val p = pUnsafe.coerceAtLeast(0) + 1

  // v(t) = v0 / 2^(t/p)
  // s(t) = - v0 * p / ln(2) / 2^(t/p)
  override fun distance(t: Long, v0: Double): Double {
    return -v0 * p / ln2 / 2.0.pow(1.0 * t / p)
  }

  // 1/2^N -- minimal considerable speed. Let's count time we hit that speed
  // 1/2^N = v0 / 2^(t/p)
  // 2^(t/p) = 2^N * v0
  // 2^(t/p) = 2^(N + (log2(v0)))
  // t/p = N + (log2(v0))
  // t = p * (N + log2(v0))
  // log2(v0) = ln(v0) / ln2
  // since v0 = (vX^2 + vY^2)^0.5, ln(v0) = 0.5 * ln(vX^2 + vY^2)
  override val end = initTs + (p * (N + 0.5 * ln(vX * vX + vY * vY) / ln2)).toLong()
  override val endX = end
  override val endY = end
}


class LinearSlowdownTrail(
  vX: Double,
  vY: Double,
  initTs: Long,
  param1000: Int
) : DistanceBasedTrail(vX, vY, initTs) {

  private val p = 0.001 * (param1000) / 1000 + 0.008 * (1000 - param1000) / 1000

  // v(t) = v0 - p * t
  // v(t0) = v0 - p * t0 , v(t0) = 0
  // t0 = v0 / p
  override val endX = initTs + (abs(vX) / p).toLong()
  override val endY = initTs + (abs(vY) / p).toLong()
  override val end = max(endX, endY)

  override fun distance(t: Long, v0: Double): Double {
    return v0 * t - sign(v0) * p * t * t / 2
  }
}