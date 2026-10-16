package com.github.adamyork.kparticles.platform.engine

import com.github.adamyork.kparticles.platform.AppScope
import com.github.adamyork.kparticles.platform.engine.data.CollisionContact
import com.github.adamyork.kparticles.platform.engine.data.Particle
import me.tatarka.inject.annotations.Inject
import kotlin.math.abs
import kotlin.math.sign

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
@AppScope
@Inject
class CommonContactSolver : ContactSolver {

    private companion object {
        private const val SOLVER_ITERATIONS: Int = 6
    }

    private val normalMasses = DoubleArray(CollisionContact.MAXIMUM_POINTS)
    private val tangentMasses = DoubleArray(CollisionContact.MAXIMUM_POINTS)
    private val normalImpulses = DoubleArray(CollisionContact.MAXIMUM_POINTS)
    private val tangentImpulses = DoubleArray(CollisionContact.MAXIMUM_POINTS)
    private val restitutionTargets = DoubleArray(CollisionContact.MAXIMUM_POINTS)
    private val firstArmX = DoubleArray(CollisionContact.MAXIMUM_POINTS)
    private val firstArmY = DoubleArray(CollisionContact.MAXIMUM_POINTS)
    private val secondArmX = DoubleArray(CollisionContact.MAXIMUM_POINTS)
    private val secondArmY = DoubleArray(CollisionContact.MAXIMUM_POINTS)
    private var firstInverseMass = 0.0
    private var firstInverseInertia = 0.0
    private var secondInverseMass = 0.0
    private var secondInverseInertia = 0.0

    override fun solve(
        firstParticle: Particle,
        secondParticle: Particle?,
        contact: CollisionContact,
        restitution: Double,
        friction: Double,
        staticFriction: Double,
        restitutionThreshold: Double
    ) {
        firstInverseMass = 1.0 / firstParticle.inertialMass()
        firstInverseInertia = 1.0 / firstParticle.momentOfInertia()
        secondInverseMass = if (secondParticle == null) 0.0 else 1.0 / secondParticle.inertialMass()
        secondInverseInertia = if (secondParticle == null) 0.0 else 1.0 / secondParticle.momentOfInertia()
        val normalX = contact.normalX
        val normalY = contact.normalY
        val tangentX = -normalY
        for (pointIndex in 0 until contact.pointCount) {
            firstArmX[pointIndex] = contact.pointX[pointIndex] - firstParticle.transform.position.x
            firstArmY[pointIndex] = contact.pointY[pointIndex] - firstParticle.transform.position.y
            secondArmX[pointIndex] = if (secondParticle == null) 0.0 else contact.pointX[pointIndex] - secondParticle.transform.position.x
            secondArmY[pointIndex] = if (secondParticle == null) 0.0 else contact.pointY[pointIndex] - secondParticle.transform.position.y
            val firstNormalCross = firstArmX[pointIndex] * normalY - firstArmY[pointIndex] * normalX
            val secondNormalCross = secondArmX[pointIndex] * normalY - secondArmY[pointIndex] * normalX
            val firstTangentCross = firstArmX[pointIndex] * normalX - firstArmY[pointIndex] * tangentX
            val secondTangentCross = secondArmX[pointIndex] * normalX - secondArmY[pointIndex] * tangentX
            normalMasses[pointIndex] = 1.0 / (firstInverseMass + secondInverseMass +
                    firstNormalCross * firstNormalCross * firstInverseInertia +
                    secondNormalCross * secondNormalCross * secondInverseInertia)
            tangentMasses[pointIndex] = 1.0 / (firstInverseMass + secondInverseMass +
                    firstTangentCross * firstTangentCross * firstInverseInertia +
                    secondTangentCross * secondTangentCross * secondInverseInertia)
            normalImpulses[pointIndex] = 0.0
            tangentImpulses[pointIndex] = 0.0
            val approachSpeed = relativeSpeedAlong(firstParticle, secondParticle, pointIndex, normalX, normalY)
            restitutionTargets[pointIndex] = if (approachSpeed < -restitutionThreshold) -restitution * approachSpeed else 0.0
        }
        for (iteration in 0 until SOLVER_ITERATIONS) {
            for (pointIndex in 0 until contact.pointCount) {
                val normalSpeed = relativeSpeedAlong(firstParticle, secondParticle, pointIndex, normalX, normalY)
                val accumulatedNormal = normalImpulses[pointIndex]
                val updatedNormal = maxOf(accumulatedNormal + normalMasses[pointIndex] * (restitutionTargets[pointIndex] - normalSpeed), 0.0)
                val appliedNormal = updatedNormal - accumulatedNormal
                normalImpulses[pointIndex] = updatedNormal
                applyImpulse(firstParticle, secondParticle, pointIndex, normalX * appliedNormal, normalY * appliedNormal)
                val tangentSpeed = relativeSpeedAlong(firstParticle, secondParticle, pointIndex, tangentX, normalX)
                val accumulatedTangent = tangentImpulses[pointIndex]
                var updatedTangent = accumulatedTangent - tangentMasses[pointIndex] * tangentSpeed
                if (abs(updatedTangent) > staticFriction * updatedNormal) {
                    updatedTangent = sign(updatedTangent) * friction * updatedNormal
                }
                val appliedTangent = updatedTangent - accumulatedTangent
                tangentImpulses[pointIndex] = updatedTangent
                applyImpulse(firstParticle, secondParticle, pointIndex, tangentX * appliedTangent, normalX * appliedTangent)
            }
        }
        var totalNormalImpulse = 0.0
        for (pointIndex in 0 until contact.pointCount) {
            totalNormalImpulse += normalImpulses[pointIndex]
        }
        firstParticle.dynamics.angularVelocity = firstParticle.dynamics.angularVelocity
            .coerceIn(-Particle.MAXIMUM_ANGULAR_VELOCITY, Particle.MAXIMUM_ANGULAR_VELOCITY)
        firstParticle.dynamics.angularVelocity =
            firstParticle.angularVelocityAfterRolling(totalNormalImpulse, firstParticle.collisionRadius())
        if (secondParticle != null) {
            secondParticle.dynamics.angularVelocity = secondParticle.dynamics.angularVelocity
                .coerceIn(-Particle.MAXIMUM_ANGULAR_VELOCITY, Particle.MAXIMUM_ANGULAR_VELOCITY)
            secondParticle.dynamics.angularVelocity =
                secondParticle.angularVelocityAfterRolling(totalNormalImpulse, secondParticle.collisionRadius())
        }
    }

    private fun relativeSpeedAlong(
        firstParticle: Particle,
        secondParticle: Particle?,
        pointIndex: Int,
        directionX: Double,
        directionY: Double
    ): Double {
        val firstDynamics = firstParticle.dynamics
        val firstPointVelocityX = firstDynamics.velocity.x - firstDynamics.angularVelocity * firstArmY[pointIndex]
        val firstPointVelocityY = firstDynamics.velocity.y + firstDynamics.angularVelocity * firstArmX[pointIndex]
        var secondPointVelocityX = 0.0
        var secondPointVelocityY = 0.0
        if (secondParticle != null) {
            val secondDynamics = secondParticle.dynamics
            secondPointVelocityX = secondDynamics.velocity.x - secondDynamics.angularVelocity * secondArmY[pointIndex]
            secondPointVelocityY = secondDynamics.velocity.y + secondDynamics.angularVelocity * secondArmX[pointIndex]
        }
        return (secondPointVelocityX - firstPointVelocityX) * directionX + (secondPointVelocityY - firstPointVelocityY) * directionY
    }

    private fun applyImpulse(
        firstParticle: Particle,
        secondParticle: Particle?,
        pointIndex: Int,
        impulseX: Double,
        impulseY: Double
    ) {
        val firstDynamics = firstParticle.dynamics
        firstDynamics.velocity.x -= impulseX * firstInverseMass
        firstDynamics.velocity.y -= impulseY * firstInverseMass
        firstDynamics.angularVelocity -=
            firstInverseInertia * (firstArmX[pointIndex] * impulseY - firstArmY[pointIndex] * impulseX)
        if (secondParticle == null) return
        val secondDynamics = secondParticle.dynamics
        secondDynamics.velocity.x += impulseX * secondInverseMass
        secondDynamics.velocity.y += impulseY * secondInverseMass
        secondDynamics.angularVelocity +=
            secondInverseInertia * (secondArmX[pointIndex] * impulseY - secondArmY[pointIndex] * impulseX)
    }
}
