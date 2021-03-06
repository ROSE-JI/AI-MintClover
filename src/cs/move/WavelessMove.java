/**
 * Copyright (c) 2012-2016 Robert Maupin (Chase)
 * 
 * This software is provided 'as-is', without any express or implied
 * warranty. In no event will the authors be held liable for any damages
 * arising from the use of this software.
 * 
 * Permission is granted to anyone to use this software for any purpose,
 * including commercial applications, and to alter it and redistribute it
 * freely, subject to the following restrictions:
 * 
 *    1. The origin of this software must not be misrepresented; you must not
 *    claim that you wrote the original software. If you use this software
 *    in a product, an acknowledgment in the product documentation would be
 *    appreciated but is not required.
 * 
 *    2. Altered source versions must be plainly marked as such, and must not be
 *    misrepresented as being the original software.
 * 
 *    3. This notice may not be removed or altered from any source
 *    distribution.
 */
package cs.move;

import java.awt.geom.Line2D;

import robocode.Rules;
import robocode.util.Utils;
import cs.Mint;
import cs.State;
import cs.util.Tools;
import cs.util.Vector;

/**
 * This is a simplified minimum risk movement. It's only goal is to get the
 * robot to a position where it can optimally utilize it's surfing movement.
 * 
 * @author Robert Maupin (Chase)
 *
 */
public class WavelessMove {

	private final Mint bot;
	private State lastState;

	private final Move move;
	private State state;

	public WavelessMove(final Mint cntr, final Move mvnt) {
		move = mvnt;
		bot = cntr;
	}

	/**
	 * Calculates the risk at a given location. This does not consider the route to
	 * that position.
	 * 
	 * @param pos
	 *            the location to calculate the risk of
	 * @return the risk of that location
	 */
	private double calculateRisk(Vector pos) {
		// basic risk is how close it is to the target
		double risk = 100.0 / pos.distanceSq(getTargetPosition());

		// additional risk for being close to the edge of the field
		for (double[] edge : State.wavelessField.getEdges()) {
			risk += 5.0 / (1.0 + Line2D.ptSegDistSq(edge[0], edge[1], edge[2], edge[3], pos.x, pos.y));
		}

		/*
		 * Get points between enemy location and corner and add risk! these are really
		 * bad places to be! Our hitbox is larger here if nothing else!
		 */
		for (double[] corner : State.wavelessField.getCorners()) {
			Vector targetPos = getTargetPosition();
			corner[0] = (corner[0] + targetPos.x) / 2.0;
			corner[1] = (corner[1] + targetPos.y) / 2.0;
			if (targetPos.distanceSq(corner[0], corner[1]) < 22500) {
				risk += 5.0 / (1.0 + pos.distanceSq(corner[0], corner[1]));
			}
		}

		return risk;
	}

	/**
	 * Do the minimum risk movement.
	 */
	private void doMinRiskMovement() {
		// Do minimal risk movement
		Vector target = state.robotPosition.clone();
		Vector bestTarget = state.robotPosition;
		double angle = 0;

		double bestRisk = calculateRisk(bestTarget);
		double enemyDistance = state.robotPosition.distance(getTargetPosition());

		// a little dynamic distancing
		// enemyDistance += 18*max((enemyDistance-36-50)/100.0,1.0);
		enemyDistance += Tools.limit(-18, -24.48 + 0.18 * enemyDistance, 18);

		while (angle < Math.PI * 2) {
			double targetDistance = Math.min(200, enemyDistance);

			target.setLocationAndProject(state.robotPosition, angle, targetDistance);

			if (State.wavelessField.contains(target)) {
				double risk = calculateRisk(target);

				if (risk < bestRisk) {
					bestRisk = risk;
					bestTarget = target.clone();
				}
			}

			angle += Math.PI / 32.0;
		}

		double travelAngle = state.robotPosition.angleTo(bestTarget);

		double forward = state.robotPosition.distance(bestTarget);

		double angleToTurn = Utils.normalRelativeAngle(travelAngle - state.robotBodyHeading);
		int direction = 1;

		if (Math.abs(angleToTurn) > Math.PI / 2.0) {
			angleToTurn = Utils.normalRelativeAngle(angleToTurn - Math.PI);
			direction = -1;
		}

		// Slow down so we do not ram head long into the walls and can instead
		// turn to avoid them
		double maxVelocity = Rules.MAX_VELOCITY;

		if (!State.battlefield
				.contains(state.robotPosition.clone().project(state.robotBodyHeading, state.robotVelocity * 3.25))) {
			maxVelocity = 0;
		}

		if (!State.battlefield
				.contains(state.robotPosition.clone().project(state.robotBodyHeading, state.robotVelocity * 5))) {
			maxVelocity = 4;
		}

		if (angleToTurn > 0.7 && state.robotVelocity < 7) {
			maxVelocity = 0;
		}

		bot.setMaxVelocity(maxVelocity);
		bot.setTurnBody(angleToTurn);
		bot.setMove(forward * direction);

		move.updateNextPosition(angleToTurn, maxVelocity, direction);
	}

	/**
	 * Execute the waveless movement.
	 */
	public void execute() {
		final int initialTurns = (int) Math.ceil(3.0 / State.coolingRate) + 4;
		double safeTurns = state.robotGunHeat / State.coolingRate;
		if (state.time < initialTurns) {
			/*
			 * Do we have enough time to move around before they can start firing?
			 */
			if (safeTurns > 4) {
				doMinRiskMovement();
			} else {
				/*
				 * Stop down and face perpendicular to them to get ready for them to fire.
				 */
				move.path.calculatePath(state.robotPosition, getTargetPosition(), state.robotBodyHeading,
						state.robotVelocity, state.robotOrbitDirection);

				bot.setTurnBody(move.path.getAngleToTurn());
				bot.setMaxVelocity(0);
				bot.setMove(0);

				move.updateNextPosition(move.path.getAngleToTurn(), 0, 1);
			}
		} else {
			// TODO use enemy gun heat to determine if we should stop and turn perpendicular
			doMinRiskMovement();
		}

	}

	/**
	 * Get the position of the enemy.
	 * 
	 * @return the postion of the enemy
	 */
	private Vector getTargetPosition() {
		Vector pos = lastState.targetPosition;
		if (pos == null) {
			return State.battlefield.getCenter();
		}
		return pos;
	}

	/**
	 * Update the waveless movement with the current state.
	 * 
	 * @param state
	 *            the current state
	 */
	public void update(final State state) {
		lastState = this.state;
		this.state = state;
	}

}
